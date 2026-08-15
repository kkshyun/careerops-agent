---
task_id: COLLECT-002
title: JobPosting 필드 확장(경력구분/필요학력/기관코드 신설, 고용형태 의미 정정) + 진행/마감 상태 갱신
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-15T00:00:00+09:00
codex_thread_id: 01a00542-e44e-77d2-8108-caa239bff249
---

## Context

COLLECT-001로 ALIO 연동 자체(외부 API 호출 → DTO → `JobPosting` 저장 →
dedup skip)는 이미 완료되어 실제 서비스키로 E2E 검증까지 끝났다
(`.ai/tasks/COLLECT-001.md`). 그러나 ALIO 응답에는 아직 `JobPosting`에
반영하지 않은 필드가 더 있고, 기존 매핑 중 하나는 이름과 실제 의미가
어긋나 있었다.

이번 Task 착수 전 실제 서비스키로 `POST /new/v1/recruit/list.do`를 직접
호출해 응답 필드를 재확인했다(추측 아님, 2026-08-15 확인):

- `hireTypeNmLst`(예: `"비정규직"`) — **진짜 고용형태**. 기존
  `AlioJobMapper`는 이 필드를 쓰지 않고 있었다.
- `recrutSeNm`(예: `"신입"`) — **경력구분**. 기존 `AlioJobMapper`가
  이 값을 `employmentType`(고용형태)에 매핑하고 있었다 — 이름과 실제
  의미가 어긋난 상태(ADR-0009 참고).
- `acbgCondNmLst`(예: `"학력무관"`) — 필요학력. 매핑 안 됨.
- `ongoingYn`(`"Y"`/`"N"`) — 진행/마감 상태. 매핑 안 됨.
- `pblntInstCd`(예: `"C0059"`) — 기관코드. 매핑 안 됨.
- **기관유형/기관분류에 해당하는 사람이 읽을 수 있는 필드는 이 응답에
  없다** — 코드(`pblntInstCd`, `pbadmsStdInstCd`)만 있다. 유형/분류명을
  얻으려면 별도 참조 데이터(코드정의서/기관목록 API)가 필요하며, 이번
  Task Out of Scope다(`docs/ROADMAP.md` "Phase 2 이후 후보" 참고).
- `steps`(전형단계)/`files`(첨부파일)는 목록 API에서 항상 빈 배열임을
  재확인(COLLECT-001과 동일 결론). 상세조회(`/detail.do`) 연동이 필요하며
  이번 Task Out of Scope다.

## Scope

1. `JobPosting`에 필드 4개 신설: `careerLevel`(경력구분),
   `educationRequirement`(필요학력), `status`(진행/마감),
   `institutionCode`(기관코드). Flyway 마이그레이션
   `V2__add_job_posting_fields.sql`.
2. `AlioJobMapper` 매핑 정정: `employmentType`은 `hireTypeNmLst`로,
   `careerLevel`(신규)은 `recrutSeNm`으로 매핑(기존 employmentType↔recrutSeNm
   매핑을 교체). `educationRequirement`←`acbgCondNmLst`,
   `institutionCode`←`pblntInstCd` 추가. `status`←`ongoingYn`을
   `"Y"`→`"OPEN"`, `"N"`→`"CLOSED"`, 그 외/누락→`null`로 변환.
3. `AlioJobItem`에 `hireTypeNmLst`, `acbgCondNmLst`, `ongoingYn`,
   `pblntInstCd` 필드 추가.
4. `JobPostingCreateRequest`/`JobPostingResponse`에 신규 필드 4개 추가
   (`@Size(max = 255)`, nullable — 기존 `employmentType`/`jobCategory`와
   동일한 검증 수준).
5. **재수집 시 상태 갱신**: `AlioCollectorService`가 이미 존재하는
   `source`+`externalId`를 다시 만나면, 기존 방식(전부 skip)을 바꿔
   `status`만 비교해 다르면 갱신한다(아래 "상태 갱신 설계" 참고). `status`가
   같으면 기존과 동일하게 skip.
6. `CollectResult`에 `updated`(int) 필드 추가.
7. `JobPostingRepository`에 `findFirstBySourceAndExternalId(String source,
   String externalId)` 추가. 기존 `existsBySourceAndExternalId`는 이
   Task로 대체되어 다른 사용처가 없다면 삭제한다(삭제 전 grep으로 다른
   사용처가 정말 없는지 확인할 것).
8. `JobPostingService`에 상태만 갱신하는 메서드 추가(예:
   `updateStatus(JobPosting jobPosting, String status)`) — `JobPosting`
   엔티티에 `status`를 바꾸는 메서드(예: `updateStatus(String status)`)를
   추가해야 한다. 엔티티의 다른 필드는 그대로 불변 유지, `status`만 mutable
   하게 연다.

## Out of Scope

- 기관유형/기관분류 텍스트 매핑 — 응답에 필드 자체가 없음(위 Context 참고).
  코드(`institutionCode`)만 저장한다.
- ALIO 상세조회(`/detail.do`) 연동, `steps`/`files` 저장.
- `status` 외 다른 필드의 갱신(재수집해도 `companyName`/`title`/
  `jobCategory` 등은 최초 저장값 유지 — 전체 필드 동기화는 별도 Task).
- 마감일(`applicationEndAt`) 경과 기준 자동 상태 전환 — Scheduler가 없으므로
  재수집이 실제로 일어날 때만 `status`가 갱신된다.
- 기존 로컬 DB에 이미 저장된 레코드에 대한 백필 마이그레이션(ADR-0009).
- `GET /api/jobs`에 `status`/`careerLevel` 등 기준 필터 파라미터 추가.
- 사람인 등 다른 Provider, `Company` Entity 분리, 신규 Prometheus metric
  (아래 "Metrics" 참고 — 늘리지 않는다).

## 상태 갱신 설계

- `AlioCollectorService.collect()`의 기존 dedup 분기(`repository
  .existsBySourceAndExternalId(...)` → `skipped++`)를
  `repository.findFirstBySourceAndExternalId(...)`로 바꾼다.
- 결과가 있으면(`Optional` present): 새로 매핑된 `request.status()`와
  기존 엔티티의 `getStatus()`를 비교한다.
  - 다르면: `jobPostingService.updateStatus(existing, request.status())` 호출,
    `updated++`.
  - 같으면(둘 다 null인 경우 포함): 기존과 동일하게 `skipped++`.
- 결과가 없으면(신규): 기존과 동일하게 `jobPostingService.create(request)`,
  `saved++`.
- `CollectResult(source, fetched, saved, skipped, updated, failed, result)`로
  필드 하나 추가(순서는 Codex 재량, JSON 필드명만 `updated`로 고정).

## Metrics

새 Prometheus metric은 추가하지 않는다 — `docs/METRICS.md` "지표를 늘리는
것 자체가 목표가 아니다" 원칙, 그리고 COLLECT-001이 `skipped`를 별도
metric 없이 응답 body로만 노출했던 것과 동일하게, 이번 `updated`도 응답
body(`CollectResult.updated`)로만 노출한다. 기존
`careerops_collector_saved_total`은 신규 저장에만, 상태 갱신은 어떤
counter도 증가시키지 않는다(신규 저장이 아니므로).

## Acceptance Criteria

`[자동]` = fixture만으로 검증. `[수동]` = 실제 키로 확인. 자동 항목은
저장소 루트에서 `docker compose up -d`(PostgreSQL)가 기동 중이어야 한다.

- [ ] `[자동]` **마이그레이션/기동**: `V2__add_job_posting_fields.sql` 적용
      후 애플리케이션이 정상 기동한다(`ddl-auto=validate` 통과). `psql`
      또는 테스트로 `job_postings` 테이블에 `career_level`,
      `education_requirement`, `status`, `institution_code` 컬럼이
      존재함을 확인한다.
- [ ] `[자동]` **Mapper 정정 검증**: `AlioJobMapperTest`에서 `hireTypeNmLst`
      값이 `employmentType`에, `recrutSeNm` 값이 `careerLevel`에,
      `acbgCondNmLst` 값이 `educationRequirement`에, `pblntInstCd` 값이
      `institutionCode`에 정확히 매핑됨을 검증한다(기존 "recrutSeNm →
      employmentType" 매핑 테스트가 있다면 "recrutSeNm → careerLevel"로
      수정).
- [ ] `[자동]` **status 매핑 검증**: `ongoingYn="Y"` → `status="OPEN"`,
      `ongoingYn="N"` → `status="CLOSED"`, `ongoingYn`이 없거나 다른 값이면
      `status=null`.
- [ ] `[자동]` **신규 저장 시 필드 반영**: fixture로 `POST /api/collect/alio`
      호출 시 새로 저장된 `JobPosting`의 4개 신규 필드가 fixture 값과
      일치한다.
- [ ] `[자동]` **상태 갱신**: 동일 `source`+`externalId`로 이미
      `status="OPEN"`인 `JobPosting`이 저장돼 있는 상태에서, 같은
      `externalId`지만 `ongoingYn="N"`(→`status="CLOSED"`)인 fixture로
      재수집하면 (1) 해당 레코드의 `status`만 `"CLOSED"`로 바뀌고
      `companyName`/`title` 등 다른 필드는 그대로 유지되며, (2)
      `JobPostingRepository.count()`는 증가하지 않고, (3) `CollectResult
      .updated`가 1 이상이다.
- [ ] `[자동]` **상태 불변 시 skip 유지**: 기존과 동일한 `status`로
      재수집하면 `updated=0`이고 기존 COLLECT-001 dedup 동작(재저장 없음)이
      그대로 유지된다.
- [ ] `[자동]` **API 응답 노출**: `JobPostingResponse`(`GET /api/jobs/{id}`)에
      4개 신규 필드가 포함된다.
- [ ] `[자동]` **회귀 없음**: `cd backend && ./gradlew test`가 이번 Task
      신규 테스트 포함 전체 실패 0건으로 통과한다(JOB-001/COLLECT-001/
      IMPORT-001 기존 테스트 포함).
- [ ] `[자동]` **Git tracked file에 secret 없음**: 신규 dependency 없음,
      실제 키 값이 어떤 커밋 파일에도 없다.
- [ ] `[수동]` **실제 키로 재검증**: `JOB_ALIO_API_KEY` 설정 후
      `POST /api/collect/alio` 호출, 저장된 데이터의 `employmentType`/
      `careerLevel`/`educationRequirement`/`status`/`institutionCode` 값이
      사람이 보기에 합리적인지 확인한다.

## Technical Notes

### 패키지/파일 변경 범위

```
backend/src/main/java/com/careerops/backend/job/
├── JobPosting.java                  # 필드 4개 추가 + status용 mutator 1개
├── JobPostingRepository.java        # findFirstBySourceAndExternalId 추가,
│                                     # existsBySourceAndExternalId 대체(사용처 재확인 후 삭제)
├── JobPostingService.java           # updateStatus(JobPosting, String) 추가
└── dto/
    ├── JobPostingCreateRequest.java # 필드 4개 추가
    └── JobPostingResponse.java      # 필드 4개 추가 + from() 갱신

backend/src/main/java/com/careerops/backend/collector/
├── CollectResult.java               # updated 필드 추가
└── alio/
    ├── AlioJobItem.java             # hireTypeNmLst/acbgCondNmLst/ongoingYn/pblntInstCd 추가
    ├── AlioJobMapper.java           # 매핑 정정 + status 변환 로직
    └── AlioCollectorService.java    # dedup → 상태 비교/갱신 분기로 교체

backend/src/main/resources/db/migration/
└── V2__add_job_posting_fields.sql   # 신규

backend/src/test/java/com/careerops/backend/
├── collector/AlioJobMapperTest.java              # 매핑 정정 반영
├── collector/AlioCollectorServiceTest.java       # 상태 갱신 시나리오 추가
├── collector/AlioJobListResponseParsingTest.java # 신규 필드 파싱 확인(fixture 갱신 필요 시)
├── job/JobPostingRepositoryTest.java             # findFirstBySourceAndExternalId
└── job/JobPostingControllerTest.java             # Response 신규 필드 노출 확인
```

`backend/src/test/resources/fixtures/alio/*.json` — `hireTypeNmLst`/
`acbgCondNmLst`/`ongoingYn`/`pblntInstCd` 값을 포함하도록 갱신한다(실제
API 응답을 복사하지 않고 확인된 스키마 기준으로 합성 — COLLECT-001과
동일 원칙). 상태 갱신 시나리오 검증용으로 `ongoingYn` 값이 다른 두 번째
fixture(또는 첫 fixture를 변형한 fixture)가 필요하다.

### `ManualImportService`도 함께 확인

`ManualImportService`가 `JobPostingCreateRequest`를 생성자로 만드는 경로가
있다면, 신규 4개 필드에 대해 `null`을 전달하도록 함께 수정한다(수동 등록은
ALIO 데이터가 아니므로 이 필드들을 채울 근거가 없다 — 값을 추측해 채우지
않는다).

### 왜 `existsBySourceAndExternalId`를 `findFirstBySourceAndExternalId`로
### 바꾸는가

상태 비교를 하려면 존재 여부(`boolean`)만으로는 부족하고 실제 엔티티가
필요하다. 두 메서드를 모두 유지하는 것은 중복이므로, 사용처가
`AlioCollectorService` 하나뿐임을 확인했다면(구현 시점에 다시 grep으로
재확인) 기존 메서드를 삭제하고 하나로 합친다.

### `JobPosting` 엔티티에 mutator를 추가하는 이유

JOB-001/COLLECT-001은 `JobPosting`을 생성자 이후 불변으로 유지해왔다.
이번 Task가 그 원칙에 예외를 만드는 이유는 "재수집 시 상태만 최신화한다"는
요구가 실제로 존재하기 때문이다(과도한 setter 남발이 아니라, `status` 단
하나에 한정된 최소 mutator). 다른 필드에는 setter를 추가하지 않는다.

### Dependency

신규 production/test dependency 없음.

## Test Plan

- `[자동]` `AlioJobMapperTest` — 정정된 매핑 + `status` 변환 규칙(정상/누락/
  예외 값) 단위 테스트.
- `[자동]` `AlioJobListResponseParsingTest` — 신규 필드가 포함된 fixture
  JSON이 `AlioJobItem`으로 올바르게 역직렬화되는지.
- `[자동]` `AlioCollectorServiceTest` — 신규 저장 시나리오(신규 필드 반영),
  상태 갱신 시나리오(status 변경 시 updated 증가, 다른 필드 불변), 상태
  불변 시 skip 유지 시나리오.
- `[자동]` `JobPostingRepositoryTest` — `findFirstBySourceAndExternalId`
  존재/미존재 케이스.
- `[자동]` `JobPostingControllerTest` — 신규 필드가 `GET /api/jobs/{id}`
  응답에 포함되는지.
- `[자동]` `cd backend && ./gradlew test` 전체 통과. 사전조건: 저장소
  루트에서 `docker compose up -d`.
- `[수동]` 실제 키로 `POST /api/collect/alio` 1회 호출, 저장된 신규 필드
  값 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | COLLECT-002 명세 기반 최초 구현 지시(필드 4개 신설, 매핑 정정, 상태 갱신 로직, fixture/테스트 보강, 신규 dependency 없음 전제) | 전 파일 구현 완료, fixture/테스트 보강까지 마쳤으나 sandbox 제약(`~/.gradle` wrapper lock 쓰기 거부)으로 `./gradlew test`를 스스로 실행하지 못하고 결과 미확인 상태로 보고. Claude가 로컬(Docker Compose PostgreSQL 기동 후)에서 직접 `./gradlew test` 실행 → **35/35 전체 통과**(최초 1회 실패는 이 세션 셸에 `.env`가 로드되지 않아 `SPRING_DATASOURCE_URL`이 비어 발생한 환경 문제였고, `.env` 로드 후 재실행하니 해소됨 — Codex 코드 결함 아님). Codex가 `.ai/metrics/metrics.jsonl`에 직접 self-report 줄을 추가했던 것은 `codex-implement` Skill 2.5 원칙(오케스트레이터만 metrics 기록)에 따라 되돌림 |
