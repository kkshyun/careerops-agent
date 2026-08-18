---
task_id: PKB-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T19:30:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

1. `POST` `name`만 → 201, 나머지 필드 null — **충족**.
   `CertificationControllerTest.createsMinimalAndFullCertification` L27-32:
   `{"name":"정보처리기사"}`만 보내고 `issuer`/`acquiredDate`/`expirationDate`/
   `credentialId`/`description`이 전부 `isEmpty()`임을 확인.
2. `POST` 전체 필드 포함 → 201, 응답이 요청과 일치 — **충족**. 같은 테스트
   L34-43: issuer/acquiredDate/expirationDate/credentialId/description 6개
   필드 모두 요청값과 응답값을 `jsonPath`로 1:1 대조.
3. `POST` `name` 누락 → 400, row 미생성 — **충족**.
   `rejectsMissingNameAndReversedDatesWithoutCreatingRows` L49-50: `{}` 요청
   → 400. `repository.count()`를 두 요청(name 누락 + 날짜 역순) 이후 한 번만
   비교하지만, 두 요청 중 하나라도 row를 생성했다면 `before`와 달라지므로
   실질적으로 두 케이스 모두 감시된다.
4. `POST` `expirationDate < acquiredDate` → 400, row 미생성 — **충족**. 같은
   테스트 L51-55, 사유는 3번과 동일.
5. `GET` pagination(`@PageableDefault(size=20)`, size 100 clamp) — **충족**.
   `listsWithPaginationClampDefaultSizeAndFixedOrdering` L64-65(파라미터
   없이 호출 → `$.size==20`), L69-70(`size=1000` → `$.size==100`, clamp
   실동작), L71-74(`page=1&size=2` → `$.page==1`, 실제 슬라이스 확인).
   `CertificationService.findAll()`(`CertificationService.java:24-28`)의
   `Math.min(pageable.getPageSize(), 100)`으로 구현 일치.
6. `GET` `acquiredDate DESC NULLS LAST` 정렬 실동작 — **충족**. 컨트롤러
   테스트(위 5번과 같은 메서드, L60-68: 최근→과거→날짜없음 순서 확인)와
   `CertificationRepositoryTest.ordersByAcquiredDateDescendingWithNullsLast`
   (L32-40, repository 레벨에서도 동일 순서 재확인) 양쪽에서 검증.
   `CertificationRepository.java:9` JPQL이 `JobPostingRepository`의
   `ORDER BY j.applicationEndAt ASC NULLS LAST` 문법을 그대로 재사용.
7. `GET /{id}` 존재/미존재 200/404 — **충족**.
   `getsExistingCertificationAndReturnsNotFoundForMissingOne` L78-84.
8. `PATCH` 일부 필드만 변경 시 나머지 유지 — **충족(부분 커버리지)**.
   `patchesOnlyProvidedFieldsAndValidatesMergedDates` L91-96: `description`만
   변경 요청 → `name`/`issuer`/`credentialId`가 기존값 그대로임을 확인.
   다만 이 어서션에 `acquiredDate`/`expirationDate` 유지 여부는 포함되지
   않음(경미, 아래 Findings 참고). `CertificationService.update()`
   (`CertificationService.java:37-42`)의 `if (request.X() != null)` 패턴은
   `CareerExperienceService.update()`와 동일 구조.
9. `PATCH` 병합된 acquiredDate/expirationDate 기준 cross-field 검증 —
   **충족, 명세 예시와 정확히 일치**. 같은 테스트 L88-99: 초기
   `acquiredDate=2025-02-01`/`expirationDate=2027-02-01`로 생성 → 1차 PATCH는
   `description`만 보내 날짜 미변경 → 2차 PATCH가 `expirationDate=2025-01-31`만
   보내고 `acquiredDate`는 생략 → 서비스가 기존 `acquiredDate`(2025-02-01)를
   유지한 채 병합 검증해 400 반환. `CertificationService.update()` L34-36의
   병합 로직(`request.acquiredDate() == null ? entity.getAcquiredDate() :
   request.acquiredDate()`)과 완전히 대응.
10. `PATCH` 존재하지 않는 id → 404 — **충족**. 같은 테스트 L100-102.
11. `DELETE` 204 → 단건조회 404, 존재하지 않는 id → 404 — **충족**.
    `deletesExistingCertificationAndReturnsNotFoundAfterward` L106-113.
12. 기존 JobPosting/COLLECT/JobApplication/ApplicationStage/CareerExperience
    회귀 없음 — **충족**. `git status --short`로 `job`/`collector`/
    `application` 패키지와 `CareerExperience*` 파일이 전혀 수정되지 않고
    신규 파일만 추가됐음을 확인(아래 Findings의 "패키지 격리" 참고). 전체
    스위트 재실행 결과 회귀 없음.
13. `cd backend && ./gradlew test` 전체 실패 0건 — **충족**. 아래 "테스트
    결과" 참고.

## 테스트 결과

reviewer가 직접 재실행(Claude가 사전에 108/108을 보고했으나, 독립적으로
재확인함).

- 사전조건: `docker compose ps` → `careerops-agent-postgres-1`/
  `careerops-agent-redis-1` 둘 다 `healthy`(이미 기동 중).
- `./gradlew test --tests "com.careerops.backend.career.Certification*"` →
  `BUILD SUCCESSFUL`.
- `./gradlew test`(전체 스위트) → `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml`을 직접 파싱해 합산: **test_count = 108,
  실패/에러 = 0**(`failures`+`errors` 합계 0). `TEST-...CertificationControllerTest.xml`
  `tests="6"`, `TEST-...CertificationRepositoryTest.xml` `tests="2"` —
  Claude가 보고한 "기존 100 + Controller 6 + Repository 2 = 108"과 정확히
  일치.
- test_pass_count = 108/108.

## Findings

- **[원칙 확인]** `CertificationService`의 `create()`/`update()`/`delete()`
  어디에도 `@Transactional`이 없음(`CertificationService.java` 전체에
  `org.springframework.transaction.annotation.Transactional` import 자체가
  없음). Task 명세와 ADR-0020의 명시적 요구("단일 row CRUD이므로
  `@Transactional`을 붙이지 않는다")를 정확히 준수. `CareerExperienceService`
  (부모+자식 aggregate라 `@Transactional` 3곳에 적용)와의 의도적 차이를
  Codex가 정확히 인지하고 구현했음을 확인.
- **[Out of Scope 준수 확인]** migration(`V8__create_career_certifications_table.sql`)에
  `score`/provenance(`sourceType`/`sourceReference`)/tag/FK/index/UNIQUE
  제약이 전혀 없고 명세의 CREATE TABLE 문과 컬럼명/타입/nullable까지
  1:1로 일치. DTO 4종에도 score/provenance 필드 없음. Controller `GET`
  목록에 클라이언트 지정 정렬/필터 파라미터가 없고(`Pageable pageable`만
  받음), `CertificationService.findAll()`이 `pageable.getSort()`를 버리고
  `PageRequest.of(pageable.getPageNumber(), size)`(sort 미지정)만 repository에
  전달해 클라이언트가 `?sort=`를 보내도 무시되도록 구현됨(`CareerExperienceService.findAll()`과
  동일 패턴) — Out of Scope 위반 없음.
- **[패키지 격리 확인]** `git status --short` 결과 `career` 패키지 내
  `CareerExperience*` 4개 파일은 전혀 수정되지 않았고(트래킹 대상 아님,
  즉 unchanged), `job`/`collector`/`application` 패키지도 무변경. 신규
  파일 11개만 추가됨(Certification entity/repository/service/controller,
  DTO 4종, migration, 테스트 2개) — 명세의 패키지 구조 요구와 정확히
  일치.
- **[경미]** AC 8(PATCH 부분 수정 시 나머지 필드 유지) 테스트에서
  `name`/`issuer`/`credentialId`/`description`은 확인하지만
  `acquiredDate`/`expirationDate`가 patch 이후에도 유지되는지는 응답
  JSON으로 직접 assert하지 않음(간접적으로는 AC 9 테스트의 2차 PATCH가
  "기존 acquiredDate가 유지된 채 검증에 사용됨"을 보여주므로 사실상
  커버되지만, 명시적 `jsonPath` 어서션은 없음). 코드 리뷰로 `update()`의
  `if (request.acquiredDate() != null) ...` 패턴이 다른 필드와 동일하게
  구현된 것을 확인했으므로 프로덕션 코드 결함은 아님. 블로킹 아님, PASS
  판정에 영향 없음 — 후속 라운드에서 원하면 보강 가능.
- **[경미]** AC 3/4가 하나의 테스트(`rejectsMissingNameAndReversedDatesWithoutCreatingRows`)에서
  `repository.count()`를 두 개의 실패 케이스 이후 한 번만 비교(PKB-001
  review round 1에서 지적된 것과 같은 종류의 커버리지 갭). 두 케이스 중
  하나라도 잘못 row를 생성하면 최종 count 비교에서 걸리므로 실질적
  위험은 낮으나, "name 누락"과 "날짜 역순" 각각 개별적으로 count를
  확인하는 편이 더 엄밀함. 블로킹 아님.
- Secret/API Key 커밋 없음. 신규 production/test dependency 없음(명세와
  일치, `build.gradle` 변경 없음 — `git status --short`에 없음). 자기소개서
  관련 로직 없음(이번 Task 범위 아님) — 근거 기반 검증 원칙 위반 사항
  없음. `.ai/metrics/metrics.jsonl`을 Codex가 자체 append하지 않음(PKB-001
  round 1에서 있었던 Skill 위반이 이번엔 재발하지 않음, `git diff
  .ai/metrics/metrics.jsonl`은 Claude가 작성한 `plan`/`implement`(진행중)
  라인 2개만 있고 review 이후 라인은 아직 없음 — 정상).

## 다음 액션

**PASS** — Acceptance Criteria 13개 전항목 충족, 전체 테스트 108/108
통과(reviewer가 독립적으로 재실행하여 확인), `@Transactional` 미적용/
Out of Scope 준수/패키지 격리 모두 확인됨. 경미한 테스트 커버리지 갭
2건(AC 8의 날짜 필드 유지 미검증, AC 3/4 count 검증 결합)은 있으나
프로덕션 코드 결함으로 이어지지 않으므로 블로킹하지 않음.

- `.ai/metrics/metrics.jsonl`에 PKB-002 `review`/`done` phase 라인을
  기록하고 Task를 완료 처리할 것을 호출자(Claude)에게 권고.
- 위 경미 사항 2건은 필수 수정 요청이 아니므로 별도 Codex round 없이
  종료 가능. 다만 Claude가 원하면 다음 PKB Task(PKB-003/PKB-004) 진행
  시 동일 패턴(merge 후 날짜 유지 명시적 assert, 개별 count 검증)을
  Task 명세나 Codex 지시에 미리 포함시키는 것을 제안.
