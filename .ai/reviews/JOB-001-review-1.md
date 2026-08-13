---
task_id: JOB-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-13T19:50:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] **생성 성공** — 충족. 실제 curl로 검증:
  `POST /api/jobs`에 `companyName`/`title`/`employmentType`/`jobCategory`/`location`/
  `applicationStartAt`/`applicationEndAt`/`source`/`sourceUrl`/`externalId` 포함
  유효 JSON 전송 → `HTTP_STATUS:201`, 응답 본문에 서버 생성 `id`(15),
  `createdAt`(`2026-08-13T10:47:26.934388Z`) 및 요청 필드 값 그대로 포함 확인.
  구현: `backend/src/main/java/com/careerops/backend/job/JobPostingController.java:25-29`
  (`@ResponseStatus(HttpStatus.CREATED)`), `JobPostingService.java:26-34`.
  자동 테스트로도 커버: `JobPostingControllerTest.java:37-59`
  (`createsJobPostingAndIncrementsMetric`).

- [x] **생성 검증 실패** — 충족. 실제 curl로 검증:
  `{"companyName":"...","title":"","source":"MANUAL"}` 전송 → `HTTP_STATUS:400`.
  구현: `JobPostingCreateRequest.java:10-19`의 `@NotBlank` +
  `JobPostingController.java:27`의 `@Valid`.
  자동 테스트: `JobPostingControllerTest.java:62-73`
  (`rejectsBlankRequiredFieldWithoutSaving` — `repository.count()` 불변 확인,
  즉 DB 미저장까지 검증).

- [x] **조회 성공** — 충족. 실제 curl: 방금 생성한 `id=15`로
  `GET /api/jobs/15` → `HTTP_STATUS:200`, 생성 시 저장한 필드 값과 동일한 JSON.
  구현: `JobPostingController.java:31-34`, `JobPostingService.java:36-46`.
  자동 테스트: `JobPostingControllerTest.java:75-90` (`getsExistingJobPosting`).

- [x] **조회 실패(존재하지 않는 id)** — 충족. 실제 curl:
  `GET /api/jobs/999999999` → `HTTP_STATUS:404`.
  구현: `JobPostingService.java:42-45`
  (`orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))`).
  자동 테스트: `JobPostingControllerTest.java:92-96`
  (`returnsNotFoundForUnknownId`, `Long.MAX_VALUE` 사용).

- [x] **Repository 저장/조회 흐름 검증** — 충족.
  `JobPostingRepositoryTest.java:19-47` (`savesAndFindsJobPostingById`)에서
  `save()`/`findById()` 후 전체 필드(10개 + `createdAt`) 값 일치를 assert.
  `returnsEmptyForUnknownId`(49-52)도 포함. `@DataJpaTest` +
  `@AutoConfigureTestDatabase(replace = Replace.NONE)`로 실제 로컬 Postgres
  사용(H2 아님) 확인 — `JobPostingRepositoryTest.java:12-13`.

- [x] **Flyway 마이그레이션 자동 적용 + 스키마 일치** — 충족.
  `./gradlew bootRun` 로그에서 직접 확인:
  `Successfully validated 1 migration`, `Current version of schema "public": 1`,
  `Schema "public" is up to date`, 이후 Hibernate EntityManagerFactory
  초기화 성공(스키마 불일치였다면 `ddl-auto=validate`로 기동 자체가 실패했을 것).
  `application.yml:8`에 `ddl-auto: validate` 설정 확인.

- [x] **Product Metric 계측 확인** — 충족. 앱 기동 후
  `POST /api/jobs` 1회, `GET /api/jobs/{id}`를 존재 id/미존재 id 각 1회
  호출한 뒤 `curl http://localhost:8080/actuator/prometheus` 직접 실행 결과:
  ```
  careerops_job_creation_total 1.0
  careerops_job_read_total{result="found"} 1.0
  careerops_job_read_total{result="not_found"} 1.0
  ```
  잘못된 이름 `careerops_job_total`은 `grep`으로 검색 시 매치 없음(exit 1) —
  Technical Notes에서 승인된 이름 정정(`careerops.job.creation`)이 코드/
  실제 노출 결과 모두에 정확히 반영됨을 확인.
  구현: `JobPostingService.java:19-24` (Counter 등록부).

- [x] **CORE-001 회귀 없음** — 충족. 직접 curl:
  `/actuator/health` → `.status=UP`, `.components.db.status=UP`,
  `.components.redis.status=UP` 모두 확인(응답 전체를 review에 확인함).
  `/actuator/prometheus` → `HTTP_STATUS:200`.

- [x] **전체 테스트 통과** — 충족. 직접 실행:
  `cd backend && ./gradlew clean test` (Docker Compose postgres/redis
  healthy 상태에서) → `BUILD SUCCESSFUL`.
  JUnit XML 직접 확인 결과 test_count=7, test_pass_count=7, 실패 0:
  - `BackendApplicationTests`: tests=1, failures=0, errors=0
  - `JobPostingControllerTest`: tests=4, failures=0, errors=0
  - `JobPostingRepositoryTest`: tests=2, failures=0, errors=0
  `./gradlew clean build`도 별도로 실행해 `BUILD SUCCESSFUL` 확인.

- [x] **Git tracked file에 secret 없음** — 충족.
  `git ls-files | grep -iE '\.env$|\.env\.|application-local|application-secrets|\.pem$|\.key$'` →
  `.env.example`만 매치(placeholder 값 `replace-with-a-local-password`,
  실제 secret 아님). `secret|password|credential` 패턴 매치 없음.
  신규 파일(`V1__create_job_postings_table.sql`, Java 소스 전체)에
  하드코딩된 값 없음(`application.yml`도 전부 환경변수 참조,
  `application.yml:3-5` `${SPRING_DATASOURCE_URL}` 등).

- [x] **실제 curl 시연 (수동)** — 충족. 위 항목들에서 수행한 curl 결과가
  사람이 보기에 합리적: JSON 필드명이 요청 필드와 1:1 대응, 날짜는
  `YYYY-MM-DD`(`LocalDate`), `createdAt`은 ISO-8601 UTC Instant
  (`2026-08-13T10:47:26.934388Z`) — 형식이 명세와 일치.

## Out of Scope 위반 여부

`backend/src/main/java/com/careerops/backend/job/` 디렉터리 전체를 확인.
BaseEntity, `@ControllerAdvice`/`@RestControllerAdvice`, 공통 응답 wrapper,
`Company` Entity, Crawling Source별 클래스, `JobPostingService`
interface/impl 분리, Lombok/MapStruct 사용, 인증/인가 코드, cross-field
validation — 전부 없음. `JobPostingService`는 단일 클래스(`JobPostingService.java`),
`JobPostingRepository`는 인터페이스 하나(`extends JpaRepository`)뿐으로
명세와 정확히 일치. 목록조회/검색/수정/삭제 엔드포인트 없음
(`JobPostingController.java`에 `POST`, `GET /{id}` 두 개뿐).

## build.gradle Dependency 대조

Technical Notes §5 최종 목록과 `backend/build.gradle:20-33`을 줄 단위로
대조한 결과 정확히 일치:

- `spring-boot-starter-validation` ✓
- `spring-boot-starter-flyway` ✓ (자동구성 분리 대응, 3차 승인 반영)
- `flyway-database-postgresql` ✓
- 명시적 `flyway-core` — **부재 확인**(승인된 대로 제거됨, `spring-boot-starter-flyway`가 transitively 포함)
- `spring-boot-starter-data-jpa-test`(test) ✓ (1차 승인 반영)
- `spring-boot-starter-webmvc-test`(test) ✓ (1차 승인 반영)
- 버전 미명시(BOM 관리) — 전체 dependency 라인에 버전 문자열 없음 확인.

`docs/DECISIONS.md`(ADR-0006 신규), `docs/METRICS.md`(Product Metrics 섹션
갱신, 이름 정정 반영), `docs/ARCHITECTURE.md`(Phase 2 진행 중 반영) 모두
Technical Notes 및 코드와 일치하는 내용으로 갱신됨을 확인.

## 테스트 결과

- test_count = 7, test_pass_count = 7 (직접 `./gradlew clean test` 재실행,
  JUnit XML 직접 파싱으로 확인 — Codex 보고를 신뢰하지 않고 재검증함)
- `./gradlew clean build`도 별도 실행, `BUILD SUCCESSFUL`.
- 사전조건: 리뷰 시작 시 `docker compose ps -a`로 기존 컨테이너 없음(깨끗한
  상태) 확인 후 직접 `docker compose up -d` 실행, postgres/redis 모두
  `healthy` 상태 도달 확인.

## Findings

이슈 없음(Acceptance Criteria/Out of Scope/원칙 위반 모두 미발견). 다만
참고 사항 하나(블로킹 아님):

- **[참고, 비블로킹]** `backend/.gradle-user-home/`(약 314MB, Gradle 캐시/
  데몬/wrapper 디렉터리)가 untracked 상태로 저장소 루트 안에 생성되어
  있음. `.gitignore`에는 `.gradle/`만 등록돼 있고 `.gradle-user-home/`
  패턴은 없어(`.gitignore:10`), 향후 누군가 `git add -A` 등을 실행하면
  실수로 커밋될 위험이 있다(현재는 미staged 상태이며 이번 커밋 대상에는
  포함되지 않았음 — secret은 아니지만 불필요한 대용량 바이너리 캐시).
  아마 Codex 실행 환경(샌드박스)이 기본 `~/.gradle` 대신
  `-Dgradle.user.home` 등으로 로컬 디렉터리를 사용하도록 강제된 결과로
  보인다. 권장: `.gitignore`에 `backend/.gradle-user-home/` 한 줄 추가(선택
  사항이며 이번 라운드의 PASS 판정에는 영향 없음 — Codex thread에 굳이
  전달하지 않아도 되고, 다음 라운드나 별도로 가볍게 처리해도 무방함).

## 다음 액션

- **PASS.** JOB-001의 모든 Acceptance Criteria가 실제 실행(빌드/테스트/curl/
  metric)으로 검증됐고 Out of Scope 위반이나 원칙 위반이 없다. 완료
  처리하고 `.ai/metrics/metrics.jsonl`에 review phase 최종 상태를 기록할 것
  (이 기록은 호출한 Claude가 수행 — 리뷰어는 수정하지 않음).
