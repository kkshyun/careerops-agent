---
task_id: APPLICATION-003
title: GET /api/applications에 jobPostingId optional 필터 추가
phase: plan
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a03ba9-a248-7e42-8f62-b6396797ffcd
---

## Context

FRONT-002 조사 중 `/jobs/[id]`에서 "이미 이 채용공고에 지원했는가"를
프론트가 **사전에** 정확히 판단할 방법이 없다는 문제가 드러났다 —
`JobApplicationRepository.search()`는 `status`만 필터링하고
`jobPostingId`로 필터링하지 않아(코드 확인 완료), FRONT-002가 처음
설계했던 "낙관적 생성 시도 + 409 fallback" UX로 우회해야 했다. 사용자가
이 backend 변경을 별도 Task로 승인해, 이번 Task로 `GET /api/applications`에
`jobPostingId` optional 쿼리 파라미터를 추가한다. 이 Task가 먼저 완료되면
FRONT-002(§7 Application 생성 UX)는 낙관적 시도 방식 대신 정확한 사전
확인 방식으로 설계를 바꾼다(`.ai/tasks/FRONT-002.md` §7 참고, 이번
문서 갱신과 함께 이미 반영됨).

이번 Task는 **backend 명세만** 다룬다 — 구현은 이후 Codex에게 별도로
위임한다.

## Scope

`JobApplicationRepository.search()`/`JobApplicationService.search()`/
`JobApplicationController.search()`에 `jobPostingId`(Long, optional)
파라미터를 `status`와 동일한 패턴으로 추가한다.

### 1. `JobApplicationRepository.search()` — JPQL에 조건 추가

현재(`backend/src/main/java/com/careerops/backend/application/JobApplicationRepository.java`):

```java
@Query("""
        SELECT new com.careerops.backend.application.dto.JobApplicationResponse(
            a.id, a.status, a.memo, a.appliedAt, a.createdAt, a.updatedAt,
            p.id, p.companyName, p.title, p.applicationEndAt, p.status)
        FROM JobApplication a JOIN a.jobPosting p
        WHERE (:status IS NULL OR a.status = :status)
        ORDER BY a.updatedAt DESC
        """)
Page<JobApplicationResponse> search(@Param("status") ApplicationStatus status, Pageable pageable);
```

변경 후:

```java
@Query("""
        SELECT new com.careerops.backend.application.dto.JobApplicationResponse(
            a.id, a.status, a.memo, a.appliedAt, a.createdAt, a.updatedAt,
            p.id, p.companyName, p.title, p.applicationEndAt, p.status)
        FROM JobApplication a JOIN a.jobPosting p
        WHERE (:status IS NULL OR a.status = :status)
          AND (:jobPostingId IS NULL OR p.id = :jobPostingId)
        ORDER BY a.updatedAt DESC
        """)
Page<JobApplicationResponse> search(
        @Param("status") ApplicationStatus status,
        @Param("jobPostingId") Long jobPostingId,
        Pageable pageable);
```

`findResponseById(Long id)`는 이 Task에서 변경하지 않는다(단건 조회는
이미 `jobPostingId`와 무관하게 정확히 식별 가능).

### 2. `JobApplicationService.search()` — 파라미터 전달만 추가

현재:

```java
public JobApplicationListResponse search(ApplicationStatus status, Pageable pageable) {
    int clampedSize = Math.min(pageable.getPageSize(), 100);
    PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), clampedSize);
    return JobApplicationListResponse.from(repository.search(status, pageRequest));
}
```

변경 후:

```java
public JobApplicationListResponse search(ApplicationStatus status, Long jobPostingId, Pageable pageable) {
    int clampedSize = Math.min(pageable.getPageSize(), 100);
    PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), clampedSize);
    return JobApplicationListResponse.from(repository.search(status, jobPostingId, pageRequest));
}
```

새 검증 로직(존재하지 않는 `jobPostingId` 거부 등)은 추가하지 않는다 —
존재하지 않는 `jobPostingId`는 단순히 빈 목록(`content: []`,
`totalElements: 0`)을 반환한다(기존 `status` 필터가 매치 없을 때와
동일한 동작 — `JobPosting` 존재 여부를 별도로 검증하는 404 로직을
새로 만들지 않는다, 목록 조회 API의 기존 관례 유지).

### 3. `JobApplicationController.search()` — `@RequestParam` 추가

현재:

```java
@GetMapping
public JobApplicationListResponse search(
        @RequestParam(required = false) ApplicationStatus status,
        @PageableDefault(size = 20) Pageable pageable) {
    return service.search(status, pageable);
}
```

변경 후:

```java
@GetMapping
public JobApplicationListResponse search(
        @RequestParam(required = false) ApplicationStatus status,
        @RequestParam(required = false) Long jobPostingId,
        @PageableDefault(size = 20) Pageable pageable) {
    return service.search(status, jobPostingId, pageable);
}
```

`status`와 `jobPostingId`는 서로 독립적으로 조합 가능해야 한다(둘 다
지정, 하나만 지정, 둘 다 생략 모두 유효한 조합).

## Out of Scope

- DB schema / migration 변경 — 이번 Task는 JPQL `WHERE` 조건 추가만
  다루며, 테이블 구조(`job_applications` 등)는 전혀 바꾸지 않는다. 새
  migration 파일을 추가하지 않는다.
- `JobApplicationResponse`/`JobApplicationListResponse`/DTO 필드 변경 —
  이번 Task는 필터링 조건만 추가하고 응답 shape는 전혀 바꾸지 않는다.
- `jobPostingId`가 존재하지 않는 `JobPosting`을 가리켜도 404를 반환하는
  검증 — 위 §2 근거로 Out of Scope(빈 목록 반환으로 충분).
- Frontend 변경 — `frontend/src/lib/api/applications.ts`의
  `getApplications()` 시그니처에 `jobPostingId`를 추가하고 `/jobs/[id]`
  UX를 사전 확인 방식으로 바꾸는 작업은 FRONT-002 Task 범위다(이 Task는
  backend만).
- `JobApplicationRepository.findResponseById()` 변경 — 단건 조회는
  이미 `id`로 유일하게 식별되어 `jobPostingId` 필터가 필요 없다.
- 다중 `jobPostingId`(예: `IN` 조건) 지원 — 현재 요구는 단일
  채용공고 기준 존재 여부 확인 하나뿐이라 배열 파라미터는 만들지 않는다.

## Acceptance Criteria

- [ ] `[자동]` `GET /api/applications?jobPostingId={id}` — 해당
      `jobPostingId`로 생성된 `JobApplication`만 응답 `content`에
      포함된다(다른 `jobPostingId`의 지원 내역은 제외).
- [ ] `[자동]` `GET /api/applications?jobPostingId={id}&status=SUBMITTED` —
      두 필터가 AND 조건으로 동시에 적용된다(둘 다 만족하는 것만 반환).
- [ ] `[자동]` `jobPostingId`를 생략하면 기존과 동일하게 전체(또는
      `status`만 필터링된) 목록을 반환한다(회귀 없음 — 기존
      `filtersByStatusAndSortsByUpdatedAtDescending`/
      `paginatesSearchResults` 테스트 통과 유지).
- [ ] `[자동]` 존재하지 않거나 어떤 `JobApplication`도 없는 `jobPostingId`로
      조회하면 200 + 빈 `content`(`totalElements: 0`)를 반환한다(404
      아님).
- [ ] `[자동]` `GET /api/applications` 응답 JSON의 필드 구성(`content`
      각 항목의 필드 목록)은 이번 Task 이전과 동일하다(diff 없음 —
      필터 조건만 추가되고 DTO shape는 불변임을 확인).
- [ ] `[자동]` 기존 `JobApplicationRepositoryTest`/
      `JobApplicationControllerTest`의 `search()` 관련 케이스 전부
      통과(시그니처 변경에 따른 호출부 수정만, 동작 변경 없음).
- [ ] `[자동]` `cd backend && ./gradlew test` 전체 실패 0건.

## Technical Notes

- 변경 파일: `JobApplicationRepository.java`(JPQL),
  `JobApplicationService.java`(`search()` 시그니처),
  `JobApplicationController.java`(`search()` `@RequestParam` 추가). 그
  외 파일은 수정하지 않는다.
- `status` 파라미터와 완전히 동일한 `(:param IS NULL OR ...)` JPQL 패턴을
  재사용한다 — 새로운 쿼리 구성 방식(Specification, QueryDSL 등)을
  도입하지 않는다("최신 기술이라고 무조건 쓰지 않는다" 원칙, 기존
  파일 하나에 이미 있는 패턴을 그대로 확장하는 것이 이 프로젝트
  규모에 맞다).
- 신규 production/test dependency 없음.
- `docs/METRICS.md` 기준 개발 프로세스 지표(`.ai/metrics/metrics.jsonl`,
  plan/implement/review/verify)를 남긴다. 이번 Task는 기존 HTTP
  endpoint의 쿼리 조건만 확장하는 것이라 새 Product Metric(Micrometer)은
  추가하지 않는다.

## Test Plan

- `[자동]` `JobApplicationRepositoryTest`에 `jobPostingId` 필터 케이스
  추가: (1) 특정 `jobPostingId`만 필터링, (2) `status`+`jobPostingId`
  동시 조합, (3) 존재하지 않는 `jobPostingId` → 빈 결과.
- `[자동]` `JobApplicationControllerTest`에 `GET
  /api/applications?jobPostingId=...` MockMvc 케이스 추가(위 3가지
  시나리오 중 최소 1~2개를 HTTP 레벨에서도 확인).
- `[자동]` `cd backend && ./gradlew test` 전체 통과. 사전조건: 저장소
  루트에서 `docker compose up -d`.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | jobPostingId optional 필터를 status와 동일한 JPQL 패턴으로 추가, 4가지 조합 테스트 작성 | Repository/Service/Controller 3개 파일 + 테스트 2개 파일 수정. Codex 샌드박스에서 gradlew 자체 실행 실패(파일-lock 소켓 제한)했으나, Claude가 직접 docker compose up -d 후 재실행해 대상 테스트 22/22 통과 확인. reviewer round1 PASS, 수정 요청 없음. |
