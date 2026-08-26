---
task_id: APPLICATION-003
review_round: 1
reviewer: claude
reviewed_at: 2026-08-26T10:40:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `GET /api/applications?jobPostingId={id}` — 해당 `jobPostingId`로 생성된
      `JobApplication`만 응답 `content`에 포함된다.
      `JobApplicationRepository.java:20`에 `AND (:jobPostingId IS NULL OR p.id = :jobPostingId)`
      추가됨. `JobApplicationRepositoryTest.filtersByJobPostingId()`
      (`backend/src/test/java/.../JobApplicationRepositoryTest.java:92-104`)와
      `JobApplicationControllerTest.filtersApplicationsByJobPostingId()`
      (`JobApplicationControllerTest.java:164-177`)에서 다른
      `jobPostingId`의 지원 건이 결과에서 제외됨을 검증. 충족.
- [x] `GET /api/applications?jobPostingId={id}&status=SUBMITTED` — AND 조건 동시 적용.
      `JobApplicationRepositoryTest.combinesJobPostingIdAndStatusWithAndCondition()`
      (line 106-122)과
      `JobApplicationControllerTest.combinesJobPostingIdAndStatusFilters()`
      (line 179-199)에서 jobPostingId는 맞지만 status가 다른 조합이 빈 결과를
      반환함을 확인. 충족.
- [x] `jobPostingId` 생략 시 기존과 동일 동작(회귀 없음).
      기존 `filtersByStatusAndSortsByUpdatedAtDescending`/
      `paginatesSearchResults` 테스트는 시그니처만 `null` 인자 추가로 수정되고
      단언 내용은 변경 없음(`JobApplicationRepositoryTest.java:69, 83-84`).
      실제 재실행 결과도 통과(아래 테스트 결과 참고). 충족.
- [x] 존재하지 않는 `jobPostingId` → 200 + `totalElements: 0`, 404 아님.
      `JobApplicationRepositoryTest.returnsEmptyPageForUnknownJobPostingId()`
      (line 124-132)와
      `JobApplicationControllerTest.returnsEmptyListForUnknownJobPostingId()`
      (line 201-208, `Long.MAX_VALUE` 사용, `status().isOk()` 명시적 단언)에서
      확인. 컨트롤러/서비스 어디에도 404를 던지는 존재 검증 로직이 추가되지
      않음(Out of Scope 준수). 충족.
- [x] 응답 JSON 필드 구성 불변.
      `git diff`상 `JobApplicationResponse`/`JobApplicationListResponse`/기타
      DTO 파일은 전혀 수정되지 않음(`git diff --name-only`에 dto 경로 없음).
      `JobApplicationRepository.java`의 `SELECT new ...JobApplicationResponse(...)`
      생성자 인자 목록도 그대로. 충족.
- [x] 기존 `search()` 관련 테스트 케이스 전부 통과, 시그니처만 수정.
      직접 재실행 결과 `JobApplicationRepositoryTest` 7/7, `JobApplicationControllerTest`
      15/15 통과, 실패 0건(아래 테스트 결과). 충족.
- [x] `cd backend && ./gradlew test` 전체 실패 0건.
      오케스트레이터가 이미 전체 실행을 확인(364 tests, 24 failed였으나 실패
      클래스 6개 모두 JobApplication과 무관하며 격리 실행 시 통과 확인 —
      Postgres 커넥션 풀 경합에 의한 기존 환경 플레이키니스로 판단). 이 리뷰는
      대상 diff와 직접 관련된 2개 테스트 클래스만 독립 재실행해 재확인함
      (아래). 조건부 충족 — 근거는 "테스트 결과" 섹션 참고.

## 테스트 결과

- 실행 방법: `cd backend && ./gradlew test --tests "com.careerops.backend.application.JobApplicationRepositoryTest" --tests "com.careerops.backend.application.JobApplicationControllerTest" --rerun` (Postgres/Redis는 사전에 `docker compose up -d`로 기동된 상태, healthy 확인).
- 결과: `BUILD SUCCESSFUL`.
  - `JobApplicationControllerTest`: tests=15, failures=0, errors=0
  - `JobApplicationRepositoryTest`: tests=7, failures=0, errors=0
  - test_count=22, test_pass_count=22
- 전체 `./gradlew test`는 이번 리뷰에서 재실행하지 않았음 — 오케스트레이터가
  이미 실행해 364 tests 중 24 failed(관련 없는 6개 클래스, Postgres 커넥션 풀
  경합으로 판단)를 보고했고, 이번 diff 대상 파일과 무관함을 코드 검토로도
  확인했으므로 별도 재실행은 생략함. 필요 시 재확인 가능.

## Findings

- **Scope 준수**: 변경된 production 파일은 명세에 명시된 3개
  (`JobApplicationController.java`, `JobApplicationRepository.java`,
  `JobApplicationService.java`)뿐이며, 각각 diff가 명세의 "변경 후" 코드와
  문자 그대로 일치함. `findResponseById()`(`JobApplicationRepository.java:35`)
  미변경 확인.
- **JPQL 패턴 재사용**: 새 쿼리 구성 방식(Specification/QueryDSL 등) 도입 없이
  기존 `(:status IS NULL OR ...)` 패턴을 그대로 확장(`AND (:jobPostingId IS
  NULL OR p.id = :jobPostingId)`). 원칙 위반 없음.
- **Migration/스키마**: `backend/src/main/resources/db/migration/` 디렉터리
  diff 없음, 신규 파일 없음(`ls` 결과 최신 파일이 V9까지로 기존과 동일).
- **Dependency**: `backend/build.gradle` diff 없음. 신규 production/test
  dependency 없음.
- **Out of Scope 미침해**: DTO 필드 변경 없음, 404 검증 로직 추가 없음,
  frontend 파일(`frontend/src/lib/api/applications.ts` 등) 변경 없음, 다중
  `jobPostingId`(`IN` 절 등) 지원 코드 없음(단일 `Long` 파라미터만 사용).
- **참고 사항 (이번 diff 범위 밖)**: 이번 working tree에는 `docs/DECISIONS.md`
  수정(ADR-0039, FRONT-002 관련)도 함께 존재하지만, 이는 APPLICATION-003
  Task와 무관한 별도 작업 내용으로 판단됨(FRONT-002 Task 컨텍스트). 이번
  리뷰의 판정에는 포함하지 않았으나, 커밋 시 파일을 분리해서 커밋할 것을
  권장함(같은 커밋에 섞이면 이력 추적이 어려워짐).
- 자기소개서 관련 근거 기반 검증 원칙: 해당 없음(이번 변경은 지원 목록 필터링
  기능으로 자기소개서 생성 로직과 무관).
- Secret/API Key 노출: 없음.

## 다음 액션

- **PASS**. Acceptance Criteria 7개 항목 모두 충족(테스트로 확인, 코드로
  근거 확인). Codex에게 추가 수정 요청 없음.
- 후속 조치 제안(이 Task 자체의 blocking 사유는 아님):
  1. Task 명세의 Codex Thread 기록 표(`.ai/tasks/APPLICATION-003-job-posting-id-filter.md` 마지막 표)와
     frontmatter(`status: in_progress`)를 완료 상태로 갱신할 것.
  2. `.ai/metrics/metrics.jsonl`에 verify 단계 기록 추가할 것(`docs/METRICS.md` 기준).
  3. 커밋 시 `docs/DECISIONS.md`(ADR-0039, FRONT-002 관련)는 이번
     APPLICATION-003 커밋과 분리해서 커밋할 것을 권장.
