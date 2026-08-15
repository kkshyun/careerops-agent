---
task_id: JOB-002
review_round: 2
reviewer: claude
reviewed_at: 2026-08-15T21:42:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] **필터 없이 목록 조회** — 충족.
      `JobPostingRepositoryTest.sortsByApplicationEndAtAscendingWithNullLast`
      (`search(null,null,null,null)` → 오름차순 + null 마지막)와
      `JobPostingControllerTest.usesDefaultPaginationAndReturnsSecondPage`
      (`GET /api/jobs` 파라미터 없음 → 기본 size=20)가 함께 이 동작을
      검증한다. 재실행 결과 통과.
- [x] **status 필터** — 충족.
      `JobPostingRepositoryTest.searchesByEachFilterAndCombinesFiltersWithAnd`
      의 `search("OPEN", null, null, null)` → `matching`만 반환(동일
      `companyName`의 `CLOSED` 레코드 제외) 확인, 통과.
- [x] **careerLevel 부분 일치** — 충족. 같은 테스트에서
      `careerLevel="신입+경력"`로 저장된 `matching`이
      `search(null, "신입", null, null)` 결과(`containsExactly(matching,
      closed)`)에 포함됨을 확인, 통과.
- [x] **companyName 부분 일치** — 충족. `search(null, null, "한국전력",
      null)` → `hasSize(2)`(`companyName="한국전력공사"` 레코드 2건),
      통과.
- [x] **jobCategory 부분 일치** — 충족. `search(null, null, null,
      "정보기술")` → `hasSize(2)`(`jobCategory="정보기술,경영·행정·사무"`
      레코드 포함), 통과.
- [x] **필터 조합(AND)** — 충족. `search("OPEN", "신입", null, null)` →
      `containsExactly(matching)`만(둘 다 만족), `CLOSED`+`신입`인
      `closed`는 제외됨을 확인, 통과. Controller 레벨
      `getsFilteredJobsWithListResponseFieldsAndFixedOrder`도 4개 필터
      동시 적용 케이스로 재확인.
- [x] **정렬 검증** — 충족.
      `sortsByApplicationEndAtAscendingWithNullLast`가 3건(하나는
      `applicationEndAt=null`) 저장 후 `earliest, latest, noDeadline`
      순서(오름차순 + null 마지막) `containsExactly`로 검증, 통과.
- [x] **Pagination** — 충족. `paginatesSearchResults`(21건 저장,
      `page=0/size=20` → `content.size()==20`, `totalElements==21`,
      `totalPages==2`; `page=1/size=20` → 나머지 1건)와 Controller
      `usesDefaultPaginationAndReturnsSecondPage`(동일 시나리오를
      `GET /api/jobs` / `GET /api/jobs?page=1&size=20`로 재확인) 모두
      통과.
- [x] **size 상한** — 충족.
      `JobPostingControllerTest.clampsRequestedPageSizeToOneHundred`
      (101건 저장, `?size=500` → `content.size()==100`,
      `totalElements==101`, `totalPages==2`, `size==100`, 400 아님) 통과.
      `JobPostingService.java:58` `Math.min(pageable.getPageSize(), 100)`
      로직과 일치.
- [x] **응답 필드** — 충족. `JobPostingListResponse.java:15-22`가
      `content`(`JobPostingResponse::from` 재사용)/`totalElements`/
      `totalPages`/`page`/`size` 5개 필드를 정확히 매핑.
      `getsFilteredJobsWithListResponseFieldsAndFixedOrder`가
      `companyName`/`title`/`status`/`createdAt` 등 `content[0]` 필드
      노출과 5개 최상위 필드 구성을 함께 검증, 통과. round 1에서
      "필터 전부 non-null인 이 케이스만 통과"였던 문제는 이번엔
      필터 없음/일부만/전부/조합 케이스 전부 통과하므로 해소됨.
- [x] **기존 엔드포인트 회귀 없음** — 충족. `JobPostingControllerTest`의
      `createsJobPostingAndIncrementsMetric`,
      `rejectsBlankRequiredFieldWithoutSaving`,
      `getsExistingJobPosting`, `returnsNotFoundForUnknownId`와
      `JobPostingRepositoryTest`의 `savesAndFindsJobPostingById`,
      `returnsEmptyForUnknownId`, `findsBySourceAndExternalId` 모두
      diff 없이 그대로 유지되고 전체 통과 목록에 포함됨.
      `JobPostingController.java`/`JobPostingService.java`의 기존
      `create`/`findById` 메서드는 diff 대상이 아님(`git diff` 확인,
      추가된 코드는 전부 `search` 관련 신규 메서드/엔드포인트).
- [x] **회귀 없음 (`./gradlew test` 전체 통과)** — 충족. 아래 테스트
      결과 참고.

## Round 1 지적 사항 해소 여부

- **근본 원인(`LIKE CONCAT('%', :param, '%')` → `character varying ~~
  bytea`)** — 해소 확인. `JobPostingRepository.java:16-24`에서 `CONCAT`을
  완전히 제거하고 `j.careerLevel LIKE :careerLevel` 등 단순 파라미터
  바인딩으로 변경. `JobPostingService.java:75-77`에 `likePattern(String
  value)` private 헬퍼(`"%" + value + "%"`, null이면 null)를 추가해
  Java 쪽에서 패턴을 만들어 전달. round 1 요청 텍스트와 정확히 일치하는
  구현.
- **null 필터 조합 시 실패하던 5개 테스트** — 전부 재현 확인 후 통과.
  이번 라운드에 새로 발견된 원인(dev DB 65건과의 데이터 오염)도
  `careerops_test` 격리로 해소.

## 인프라 격리(ADR-0010) 검증

- `docker exec ... psql -d careerops -c "SELECT count(*) FROM
  job_postings"` → **65**(변경 전과 동일, dev 데이터 불변 확인).
- `docker exec ... psql -d careerops_test -c "SELECT count(*) FROM
  job_postings"` → 테스트 실행 전/후 모두 **0**(트랜잭션 롤백으로 항상
  빈 상태 유지, dev와 완전 분리 확인).
- `backend/build.gradle` diff: `tasks.named('test') { environment
  'SPRING_DATASOURCE_URL', 'jdbc:postgresql://localhost:5432/careerops_test'
  }` 추가만 있고, 신규 production/test dependency 없음(`dependencies {
  ... }` 블록 diff 없음).
- `docker-compose.yml` diff: postgres 서비스에 `./docker/postgres-init:
  /docker-entrypoint-initdb.d:ro` mount 한 줄 추가만 있음. 기존
  `postgres_data` 볼륨/포트/healthcheck 등은 변경 없음.
- `docker/postgres-init/01-create-test-db.sh`: `CREATE DATABASE
  careerops_test OWNER $POSTGRES_USER;` 1개 문만 있음. `careerops`
  DB에 대한 DROP/DELETE/ALTER 등 파괴적 구문 없음(추가적 DDL만).
- 테스트 코드(`JobPostingRepositoryTest.java`,
  `JobPostingControllerTest.java`) 전체 diff에 `deleteAll()`이나
  UUID/prefix 기반 필터링 로직 없음 — ADR-0010에서 명시적으로 기각한
  대안들이 실제로 코드에 스며들지 않았음을 확인.
- **Finding(비차단, 문서 정확성)**: `docs/DECISIONS.md`의 ADR-0010
  본문(437번째 줄 부근 "결정" 섹션)이 여전히 "테스트 쪽은
  `backend/src/test/resources/application.properties`에
  `spring.datasource.url`만 재정의하는 override 1개만 추가한다"라고
  서술한다. 하지만 Task 명세("추가 Scope" 섹션, round 3 기록)와 실제
  코드(해당 `application.properties` 파일은 삭제됨, 격리 메커니즘은
  `backend/build.gradle`의 `tasks.named('test') { environment
  'SPRING_DATASOURCE_URL', ... }`)가 다르다. ADR이 "왜/어떻게"를
  기록하는 문서인데 실제 최종 구현과 다른 방법을 "결정"으로 적어두면
  향후 다른 사람이 이 ADR만 보고 재현하려 할 때 (round 3에서 이미
  겪은) 같은 실패("OS 환경변수가 설정 파일 override보다 우선")를
  반복할 위험이 있다. 애플리케이션 코드/테스트 동작에는 영향 없고
  Acceptance Criteria 대상도 아니므로 판정을 막지는 않지만, ADR-0010의
  "결정"/"영향" 문단을 실제 최종 방식(`build.gradle` env 강제)으로
  갱신할 것을 권고한다(Claude가 문서만 수정하면 되는 범위, Codex
  재호출 불필요).

## Out of Scope 침범 여부

- QueryDSL/Specification 등 신규 쿼리 빌딩 기술 — 미도입, JPQL `@Query`
  그대로 유지.
- 신규 production/test dependency — 없음(`build.gradle`
  `dependencies{}` diff 없음, `test` task 블록만 변경).
- `employmentType`/`educationRequirement`/`institutionCode` 필터,
  게시 시작/종료일 범위 검색, 범용 sort 파라미터 — 코드에 추가된 흔적
  없음(`JobPostingController.java`의 `@RequestParam`은 명세된 4개 +
  `Pageable`뿐).
- 기존 `POST /api/jobs`, `GET /api/jobs/{id}` 계약 — diff 없음(신규
  `search` 메서드/엔드포인트만 추가), 회귀 테스트도 전부 통과.
- 신규 Prometheus metric — `JobPostingService.java`/
  `JobPostingController.java` diff에 `Counter`/`MeterRegistry` 관련
  변경 없음.

## 테스트 결과

- test_count: 41
- test_pass_count: 41 (failures: 0, errors: 0)
- 실행 방법: 저장소 루트 `docker compose ps`로 PostgreSQL(healthy)/
  Redis(healthy) 확인 → `docker exec ... psql -d careerops -c "SELECT
  count(*) FROM job_postings"` = 65 (사전) → `set -a && source .env &&
  set +a` → `cd backend && ./gradlew test --rerun --console=plain`
  직접 재실행(캐시 우회, `> Task :test` 새로 수행 확인) → `BUILD
  SUCCESSFUL`. `build/test-results/test/TEST-*.xml` 전체 파일의
  `tests=`/`failures=`/`errors=` 속성을 Python으로 합산해 41/0/0
  재확인. 테스트 실행 후 `docker exec ... psql -d careerops -c
  "SELECT count(*) FROM job_postings"` = 65(불변), `-d careerops_test`
  = 0(롤백 유지) 재확인.

## Findings

- 버그/Acceptance Criteria 미충족 없음.
- 과도한 추상화 없음 — `likePattern()` private 헬퍼 1개, JPQL `@Query`
  1개 유지, Repository/Service/Controller 역할 분리가 명세 그대로.
- 근거 기반 검증 원칙(자기소개서 관련) — 해당 없음, 이번 Task는 조회
  API.
- Secret 미커밋 — `git diff`에 `api_key|secret|password|token` grep
  결과 없음.
- 신규 production dependency 없음.
- 비차단 문서 정확성 이슈 1건 — 위 "인프라 격리(ADR-0010) 검증" 참고
  (ADR-0010 본문을 최종 구현에 맞게 갱신 권고).
- round 1에서 남겨둔 비차단 관찰 사항(`?sort=` 쿼리 파라미터가 JPQL
  고정 `ORDER BY`와 충돌해 500이 날 수 있는 위험)은 이번에도 AC/Out of
  Scope 대상이 아니므로 판정에 반영하지 않음 — 실사용 전 수동 확인
  권장 사항으로 재확인.

## 다음 액션

- **PASS.** JOB-002 완료 처리 가능. `.ai/metrics/metrics.jsonl`에
  최종 상태(review_round=2, verdict=PASS, test 41/41) 기록 권장.
- 권고(선택, 비차단): `docs/DECISIONS.md` ADR-0010 "결정"/"영향"
  문단을 `backend/src/test/resources/application.properties` 방식이
  아니라 실제 채택된 `backend/build.gradle`의 `tasks.named('test') {
  environment 'SPRING_DATASOURCE_URL', ... }` 방식으로 갱신 — Claude가
  문서만 직접 수정하면 됨(Codex 위임 불필요).
- 참고: 이번 Task는 round 1 FAIL → round 2 근본 원인 수정(CONCAT 제거)
  → 중간에 새로 발견된 DB 오염 문제를 사용자 승인 하에 ADR-0010으로
  해결하는 과정을 거쳤다. 라운드 수 자체는 3회 Codex 상호작용을
  거쳤지만 각 라운드가 서로 다른 새 문제(타입 추론 버그 → 테스트 격리
  인프라 부재)를 다뤘고 명세가 모호해서 반복된 것은 아니므로, Task
  명세 자체의 문제로 보지 않는다.
