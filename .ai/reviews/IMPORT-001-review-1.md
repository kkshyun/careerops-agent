---
task_id: IMPORT-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-13T22:05:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `[자동]` **생성 성공** — 충족. `ManualImportController.importJob()`이 `result=="saved"`일 때
      `HttpStatus.CREATED`를 반환한다(`ManualImportController.java:28`). 응답 본문 매핑은
      `ManualImportService.importJob()` → `JobPostingService.create()` → `JobPostingResponse.from()`
      경로로 `job.source=="MANUAL"`(`ManualImportService.java:16,60`), `job.externalId==null`
      (`ManualImportService.java:62`)이 강제된다. 직접 curl로 재확인: `POST
      /api/import/jobs/manual`에 `sourceUrl=https://www.jobkorea.co.kr/...`, `companyName`,
      `title` 등 전체 필드를 보내 `201` + `result:"saved"` + `job.source:"MANUAL"` +
      `job.externalId:null` + `job.id:152` + `job.createdAt` 채워짐을 확인. 테스트
      `ManualImportControllerTest.savesJobWithOptionalFieldsMapped()`도 동일 시나리오를 커버.
- [x] `[자동]` **선택 필드 매핑** — 충족. `ManualJobImportRequest`→`JobPostingCreateRequest` 매핑
      (`ManualImportService.toCreateRequest()`, `ManualImportService.java:51-64`)이 필드를 그대로
      복사한다. 테스트 `savesJobWithOptionalFieldsMapped()`(포함 케이스)와
      `savesJobWithOmittedOptionalFieldsAsNull()`(미포함 케이스, `.doesNotExist()` 검증)로 자동
      검증됨. curl로 전체 필드 포함 요청도 응답에 `employmentType/jobCategory/location/
      applicationStartAt/applicationEndAt`이 그대로 반영됨을 재확인.
- [x] `[자동]` **source 강제 확인** — 충족. `ManualJobImportRequest`(record, `dto/
      ManualJobImportRequest.java:10-21`)에 `source`/`externalId` 컴포넌트가 애초에 존재하지
      않는다. 클라이언트가 API 계약상 `source`를 지정할 방법이 없다.
- [x] `[자동]` **URL 필수** — 충족. `@NotBlank`(`ManualJobImportRequest.java:11`)가 없음/빈
      문자열을 거부하며, Bean Validation 실패는 Controller 진입 전 400으로 처리되어
      Repository에 도달하지 않는다. 테스트
      `rejectsMissingBlankMalformedAndForbiddenSchemeUrls`의 `{}`, `{"sourceUrl":""}`,
      `{"sourceUrl":" "}` 케이스가 400 + `repository.count()` 불변을 검증.
- [x] `[자동]` **URL 형식 검증** — 충족. `@URL(regexp="^https?://.+", ...)`이 scheme 없는
      `"not-a-url"`을 거부. 테스트 케이스로 커버되고, `./gradlew test`에서 실제 Hibernate
      Validator 동작으로 통과 확인(추측이 아닌 실행 결과).
- [x] `[자동]` **금지 scheme 거부** — 충족. `javascript:alert(1)`, `file:///etc/passwd`,
      `ftp://example.com/a` 모두 자동 테스트로 400 확인. 직접 curl로도 3개 scheme 전부
      `400`을 재확인(아래 "직접 실행한 검증" 참고).
- [x] `[자동]` **companyName/title 필수** — 충족. `@NotBlank`(`ManualJobImportRequest.java:14-15`)
      로 강제, 테스트 `rejectsMissingAndBlankCompanyNameOrTitle`의 6개 케이스가 400 +
      `repository.count()` 불변을 검증.
- [x] `[자동]` **Duplicate semantics** — 충족. `ManualImportService.importJob()`이
      `repository.findFirstBySourceAndSourceUrl(...)` 존재 시 `create()`를 호출하지 않고 기존
      레코드를 `"duplicate"`로 반환(`ManualImportService.java:39-48`),
      `ManualImportController`가 `duplicate`일 때 `200`을 반환. 테스트
      `returnsExistingJobForDuplicateAndMaintainsMetricInvariant()`로 자동 검증. 직접 curl로
      동일 URL 2회 호출 재확인: 1차 `201`/`result:"saved"`/`job.id:152`, 2차 `200`/
      `result:"duplicate"`/`job.id:152`(동일).
- [x] `[자동]` **Product Metric — saved/duplicate** — 충족. `Counter.builder("careerops.manual.import")
      .tag("result", "saved"/"duplicate")`(`ManualImportService.java:30-35`)로 등록,
      `duplicateCounter.increment()`/`savedCounter.increment()`가 각 분기에서 호출됨
      (`ManualImportService.java:41,46`). 테스트로도 검증되고, 직접 `/actuator/prometheus`로
      `careerops_manual_import_total{result="saved"} 1.0`, `{result="duplicate"} 1.0` 확인.
- [x] `[자동]` **Product Metric — 기존 카운터와의 일관성** — 충족(이 Task의 핵심 불변식).
      `duplicate` 분기는 `JobPostingService.create()`를 호출하지 않으므로
      `careerops.job.creation`이 증가하지 않는다. 테스트 `returnsExistingJobForDuplicateAndMaintainsMetricInvariant()`가 `meterRegistry.counter("careerops.job.creation").count()`가
      duplicate 호출 전후로 `creationBefore + 1`(1차 saved에서만 증가)임을 정확히 검증.
      직접 curl 시나리오(saved 1회 + duplicate 1회 + 400 거부 4회)에서
      `/actuator/prometheus` 결과 `careerops_job_creation_total 1.0`으로 정확히 1만큼만
      증가 — duplicate/invalid 요청은 전혀 증가시키지 않음을 확인.
- [x] `[자동]` **기존 회귀 없음** — 충족. `cd backend && set -a && source ../.env && set +a &&
      ./gradlew clean test` 직접 실행 결과 전체 32개 테스트 전부 통과(0 실패, 0 에러).
      기존 JOB-001(`JobPostingControllerTest`, `JobPostingRepositoryTest`), COLLECT-001
      (`CollectControllerTest`, `AlioCollectorServiceTest` 등) 테스트 포함.
- [x] `[자동]` **Git tracked file에 secret 없음** — 충족. `manualimport` 패키지 신규 파일
      전체에 `password|secret|api[_-]?key|token` grep 결과 없음. `git status --porcelain`상
      새 `.env` 계열 파일 없음, `git ls-files`에 `.env.example`만 추적됨(기존과 동일).
- [x] `[수동]` **URL 미접속 확인** — 충족(이 Task의 핵심 제약). `com.careerops.backend.manualimport`
      패키지(main+test) 전체를 `RestClient|RestTemplate|HttpClient|java\.net\.http|
      URLConnection|WebClient|okhttp|Jsoup|Playwright|Selenium|Socket\(` 패턴으로 grep한 결과
      **일치 없음** — outbound 네트워크 호출 코드가 애초에 존재하지 않는다. 직접 실제
      잡코리아 URL 문자열(`https://www.jobkorea.co.kr/Recruit/GI_Read/12345678`)로
      `POST /api/import/jobs/manual`을 호출해 정상 저장(`201`, `job.id:152`) 확인.
- [x] `[수동]` **Prometheus 노출 확인** — 충족. `curl -s
      http://localhost:8080/actuator/prometheus | grep careerops_manual_import` 결과
      `careerops_manual_import_total{result="duplicate"} 1.0`,
      `careerops_manual_import_total{result="saved"} 1.0` 두 라인 모두 정상 노출(이름 잘림
      없음, JOB-001에서 겪은 문제 재발 안 함).

## Out of Scope 준수 확인

- 새 컬럼/Flyway migration: `git status --porcelain backend/src/main/resources/db/migration/`
  결과 변경 없음(`V1__create_job_postings_table.sql`만 존재, §7 스키마 변경 불필요 확인과
  일치).
- DB unique constraint 추가 없음(migration 파일 불변으로 재확인).
- Collector 공통 interface 추상화 없음 — `ManualImportService`는 단일 클래스, 인터페이스 없음.
- `@ControllerAdvice`/`@RestControllerAdvice` 신규 도입 없음 — `grep -rn "ControllerAdvice"
  backend/src/main/java` 결과 없음.
- 신규 dependency 없음 — `git diff -- backend/build.gradle` 결과 diff 없음(파일 자체가
  변경되지 않음).
- `JobPostingRepository`에 `findFirstBySourceAndSourceUrl` 메서드 1개만 추가됨
  (`git diff` 확인, `Optional` import 1줄 + 메서드 1개, 그 외 변경 없음).
- SSRF 방어 계층 미구현이 `docs/DECISIONS.md` ADR-0008로 근거 남겨짐(신설, 8개 항목의
  향후 구현 체크리스트 포함) — Out of Scope 명세와 일치.

## 테스트 결과

- `ManualImportControllerTest`: 16개(단일 테스트 4개 + `@ParameterizedTest` 7+6케이스), 전부 통과.
- `cd backend && set -a && source ../.env && set +a && ./gradlew clean test`: **32/32 통과, 0
  실패, 0 에러** (BackendApplicationTests, JobPostingControllerTest, JobPostingRepositoryTest,
  CollectControllerTest, AlioCollectorServiceTest, AlioJobMapperTest 등 기존 테스트 전부 포함).
  - 주의: 첫 실행(`.env`를 source하지 않은 상태)에서는 `SPRING_DATASOURCE_URL` 등 환경변수가
    비어 있어 `'url' must start with "jdbc"` 에러로 전체 테스트가 실패했다 — 이는
    Codex 구현 결함이 아니라 COLLECT-001 리뷰에서도 동일하게 확인된 로컬 환경 이슈(gradle이
    `.env`를 자동 로드하지 않음)이며, `set -a && source ../.env && set +a`로 재실행하니
    정상 통과했다.

## 직접 실행한 curl/metric 검증

1. 유효한 요청(전체 필드, 실제 잡코리아 스타일 URL) → `201`, `result:"saved"`,
   `job.source:"MANUAL"`, `job.externalId:null`, `job.id:152`.
2. 동일 URL 재요청 → `200`, `result:"duplicate"`, `job.id:152`(동일).
3. `sourceUrl:"javascript:alert(1)"` → `400`.
4. `sourceUrl:"file:///etc/passwd"` → `400`.
5. `sourceUrl:"ftp://example.com/a"` → `400`.
6. `sourceUrl` 누락 → `400`.
7. `/actuator/prometheus`: `careerops_manual_import_total{result="saved"} 1.0`,
   `{result="duplicate"} 1.0`, `careerops_job_creation_total 1.0` (정확히 1 — duplicate/400
   요청 4건에서는 증가하지 않음, 핵심 불변식 충족).
8. `/actuator/health`: `status:"UP"`, `db:UP`, `redis:UP` — CORE-001 회귀 없음.

## Findings

없음. Acceptance Criteria 전 항목 충족, Out of Scope 위반 없음, 핵심 제약("서버가 URL에
접속하지 않는다")이 코드 자체로(호출 경로 부재) 증명됨.

## 다음 액션

- **PASS**: 완료 처리. `git add`/커밋은 오케스트레이터(Claude) 판단에 맡긴다.
- cleanup 확인: 애플리케이션 프로세스 kill 완료(`lsof -i :8080` → free, `ps aux`에 gradlew/
  bootRun 프로세스 없음), `docker compose down` 완료(컨테이너 2개 제거, `-v` 미사용으로
  `careerops-agent_postgres_data` named volume 보존 확인).
- `.ai/metrics/metrics.jsonl`은 건드리지 않았다(이미 Claude가 plan/implement phase 기록을
  남겨둔 상태 그대로).
