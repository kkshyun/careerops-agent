---
task_id: COLLECT-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-13T20:35:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

### `[자동]` (9개)

- [x] **정상 수집** — 충족. `AlioCollectorServiceTest.collectsMapsSavesAndRecordsMetrics`
  (`backend/src/test/java/com/careerops/backend/collector/AlioCollectorServiceTest.java:33-56`)에서
  fixture(`alio-list-response-valid.json`, 2건)로 `service.collect(50)` 호출 →
  `CollectResult("ALIO", 2, 2, 0, 0, "success")` 확인, `repository.count()`가
  2건 증가, 저장된 레코드의 `source="ALIO"` 확인. 실제 HTTP 계층도
  `CollectControllerTest.collectsAlioCaseInsensitively`
  (`CollectControllerTest.java:34-47`)에서 `POST /api/collect/ALIO` →
  `200 OK`, 응답 body에 `fetched=2`/`saved=2`/`source="ALIO"` 확인.
  `./gradlew clean test` 재실행으로 직접 통과 재확인(아래 "테스트 결과").

- [x] **매핑 정확성** — 충족. 같은 테스트(`AlioCollectorServiceTest.java:47-52`)에서
  `companyName`/`title`/`source`/`sourceUrl`/`applicationStartAt`/
  `applicationEndAt`이 fixture의 `instNm`/`recrutPbancTtl`/`srcUrl`/
  `pbancBgngYmd`/`pbancEndYmd`(`yyyyMMdd`→`LocalDate`)와 정확히 일치함을
  확인, `externalId="1001"`(`recrutPblntSn` 문자열화)로 레코드를 찾아
  대조. `AlioJobMapper.java:17-30`을 직접 읽어
  `JobPostingCreateRequest` 필드 순서(`companyName,title,employmentType,
  jobCategory,location,applicationStartAt,applicationEndAt,source,
  sourceUrl,externalId`)와 매퍼의 인자 순서가 정확히 일치함을 대조 확인
  — 매핑 표(Technical Notes §2)와 코드가 어긋나는 지점 없음.

- [x] **필수 필드 누락 항목 처리** — 충족.
  `AlioCollectorServiceTest.skipsInvalidItemsAndDuplicates`
  (`AlioCollectorServiceTest.java:58-72`)에서 `alio-list-response-with-invalid-item.json`
  (정상 1건 + `instNm=""` 1건 + `recrutPbancTtl` 누락 1건, 총 3건) 응답으로
  `first = CollectResult("ALIO", 3, 1, 0, 2, "success")` 확인 —
  나머지 정상 항목은 정상 저장, `careerops.collector.failed{reason="invalid_item"}`
  카운터 증가. `AlioCollectorService.java:73-80`에서 `Validator.validate()`로
  `@NotBlank` 위반(빈 문자열/누락 모두)을 잡아 해당 item만 `failed++`,
  `continue`(전체 실패로 전파되지 않음) 구조 확인.

- [x] **중복 skip** — 충족. 같은 테스트에서 동일 fixture로 두 번째
  `service.collect(50)` 호출 → `second = CollectResult("ALIO", 3, 0, 1, 2,
  "success")`(`saved=0`, `skipped=1`), `repository.count()`가 첫 호출
  이후와 동일(`rowsAfterFirst`)함을 확인 — 무한 적재 안 됨.
  `AlioCollectorService.java:81-84`에서
  `repository.existsBySourceAndExternalId(...)`로 저장 전 존재 확인 후
  skip하는 구조 확인. `JobPostingRepository.java:6`에
  `existsBySourceAndExternalId` 쿼리 메서드 추가만 있고 DB unique 제약은
  없음(신규 Flyway migration 없음, 아래 "Out of Scope 대조" 참고).

- [x] **외부 API 실패 시 동작** — 충족.
  `AlioCollectorServiceTest.recordsFailedRunWhenClientFails`
  (`AlioCollectorServiceTest.java:74-85`)와
  `CollectControllerTest.returnsBadGatewayWithoutSavingWhenClientFails`
  (`CollectControllerTest.java:49-61`) 둘 다 확인. `FixtureAlioJobClient.failWith(...)`로
  `AlioApiException`을 던지게 설정 → `POST /api/collect/alio` 호출 시
  `502 Bad Gateway`, `repository.count()` 불변,
  `careerops.collector.run{source=alio,result=failed}` 카운터가 1 증가.
  `CollectController.java:31-35`에서 `AlioApiException` → `ResponseStatusException(BAD_GATEWAY)`
  변환 확인.

- [x] **지원하지 않는 source** — 충족.
  `CollectControllerTest.rejectsUnsupportedSourceWithoutSaving`
  (`CollectControllerTest.java:63-71`)에서 `POST /api/collect/unknown` →
  `400 Bad Request`, `repository.count()` 불변 확인.
  실제 curl로도 직접 재확인: `curl -X POST http://localhost:8080/api/collect/unknown`
  → `HTTP:400` (아래 "직접 실행 검증" 참고).

- [x] **DTO parsing 단위 테스트** — 충족.
  `AlioJobListResponseParsingTest.parsesNestedItemResponse`
  (`AlioJobListResponseParsingTest.java:13-23`)에서 fixture JSON을
  `tools.jackson.databind.ObjectMapper`로 `AlioJobListResponse`에
  역직렬화 → `resultCode`/`totalCount`/`result` 배열 크기(2)/중첩
  `item.instNm`/`item.recrutPblntSn`/`item.pbancBgngYmd` 값이 원문과
  일치함을 확인. `AlioJobResultItem(AlioJobItem item)` 래퍼 구조가
  스펙의 `{"result":[{"item":{...}}]}` 중첩을 정확히 반영.

- [x] **Mapper 단위 테스트** — 충족. `AlioJobMapperTest.java` 두 테스트:
  `mapsAlioFieldsAndParsesDates`(14-30)에서 정상 케이스 + 날짜 파싱
  (`yyyyMMdd`→`LocalDate`) + `externalId` 문자열 변환(`123L`→`"123"`) 확인,
  `mapsMissingOptionalAndInvalidDateFieldsToNull`(32-46)에서
  `recrutSeNm=""`/`ncsCdNmLst=null`/`workRgnNmLst=" "`(공백)이 각각
  `employmentType`/`jobCategory`/`location`에 `null`로, 잘못된 날짜
  문자열(`"invalid"`)과 `srcUrl="not-a-url"`이 각각 `null`로 반영됨을 확인.

- [x] **Product Metric 계측 단위 검증** — 충족. 4종
  (`careerops.collector.fetched`/`.saved`/`.failed`/`.run`) 모두
  `AlioCollectorServiceTest`(3개 테스트에 걸쳐: fetched/saved/run=success,
  failed=invalid_item, run=failed) 및 `CollectControllerTest`(run=failed)에서
  `MeterRegistry.counter(name, tags...)`로 실제 증가분을 직접 조회해
  검증. 태그(`source=alio`, `result=success|failed`,
  `reason=invalid_item`)가 명세와 정확히 일치.

- [x] **외부 API 미접근으로도 전체 테스트 통과** — 충족, 내가 직접
  `cd backend && ./gradlew clean test` 재실행(Codex 자기보고를 그대로
  신뢰하지 않고 재검증)해 확인. 초기 실행은 `SPRING_DATASOURCE_URL` 등
  env var 미설정으로 컨텍스트 로딩 실패했으나(리뷰 환경 이슈, 구현 결함
  아님), 저장소 루트 `.env`(gitignore됨)를 `source`한 뒤 재실행하니
  **`BUILD SUCCESSFUL`, 16개 테스트 전부 통과(0 failure/error)** —
  JUnit XML 직접 파싱으로 클래스별 확인:
  `BackendApplicationTests`(1), `JobPostingControllerTest`(4),
  `JobPostingRepositoryTest`(2), `AlioJobListResponseParsingTest`(1),
  `AlioJobMapperTest`(2), `AlioCollectorServiceTest`(3),
  `CollectControllerTest`(3) = 16/16. 기존 JOB-001/CORE-001 테스트 회귀
  없음. `RestClientAlioJobClient`는 `@SpringBootTest` 컨텍스트에
  bean으로는 로드되지만(`@Import(AlioTestConfiguration)`의 `@Primary`
  `FixtureAlioJobClient`가 실제 주입 대상), 생성자가 `RestClient` 객체를
  빌드만 하고 소켓 연결을 하지 않아 어떤 테스트에서도 실제 호출이 발생하지
  않음을 코드로 확인(`RestClientAlioJobClient.java:16-23`). 테스트 결과
  XML에서 `apis.data.go.kr` 문자열 검색 결과 매치 없음.

- [x] **Git tracked file에 secret 없음** — 충족.
  `git ls-files | grep -iE '\.env$|\.env\.'` → `.env.example`만 매치,
  값은 `JOB_ALIO_API_KEY=`(키 이름만, 값 없음) 확인.
  `git grep "JOB_ALIO_API_KEY"`로 전체 추적 파일 검색 결과 `.env.example`과
  `application.yml`의 `${JOB_ALIO_API_KEY:}` 참조만 매치, 실제 키 값
  문자열은 어디에도 없음. fixture 2개 파일도 합성 데이터(`example.invalid`
  도메인 사용)이고 실제 API 응답을 복사한 흔적 없음.

### `[수동]` (3개) — 이번 리뷰에서 검증 불가, 명세 확인만

- [ ] **실제 발급 키로 1회 이상 실 호출** — 검증 불가(실제 `data.go.kr`
  키 미발급 전제). 명세 확인: Acceptance Criteria에 명시되어 있고
  `RestClientAlioJobClient`가 구현돼 있어 사용자가 키 발급 후 그대로
  수행 가능한 상태임을 확인(코드 경로 존재 확인만, 실 호출 검증은 아님).
- [ ] **반복 호출 시 미중복 확인(실 데이터)** — 검증 불가(위와 동일 이유).
  애플리케이션 레벨 dedup 로직 자체는 `[자동]` "중복 skip" 항목에서
  fixture로 이미 검증됨.
- [ ] **Prometheus 노출 확인** — 검증 불가(실 데이터 기준으로는). 다만
  참고로, 실 키(로컬 `.env`에 설정된 값)로 앱을 기동해 실제로 502가 나는
  상태에서도 `/actuator/prometheus`에
  `careerops_collector_run_total{result="failed",source="alio"}`,
  `careerops_collector_fetched_total{source="alio"}`,
  `careerops_collector_saved_total{source="alio"}`,
  `careerops_collector_failed_total{reason="fetch_error",source="alio"}`가
  정상 노출되는 것을 직접 확인했다 — 메트릭 노출 배선 자체는 동작함이
  구조적으로 확인됨(다만 이 값은 실제 성공 응답이 아니라 502 실패 케이스라
  이 `[수동]` 항목의 "성공" 시나리오를 대체하지는 않는다).

## Out of Scope 대조

- 새 Flyway migration 없음(`backend/src/main/resources/db/migration/`에
  `V1__create_job_postings_table.sql` 하나뿐) — DB unique 제약 미추가 확인.
- `CollectController.java:28`에 `if (!"alio".equalsIgnoreCase(source))`
  하드코딩 분기 하나뿐, Source 레지스트리/전략 패턴/`Collector` 공통
  인터페이스 없음.
- WireMock 등 HTTP mocking 프레임워크 미도입(`build.gradle`에 신규 test
  dependency 없음, `ADR-0007`에 기각 이유 기록).
- `RestClientAlioJobClient.fetchList(pageNo, numOfRows)`가 고정
  `pageNo=1`만 호출(`AlioCollectorService.java:48`), 페이지네이션 루프 없음.
- `/detail` 오퍼레이션 미사용, `files`/`steps` 미저장.
- `Company` Entity 분리 없음(JOB-001 결정 유지).
- `recrutSe`/`hireTypeLst` 등 코드값 enum화 없음, 자유 문자열 그대로 저장.

## build.gradle Dependency 대조

`git diff backend/build.gradle` 결과 신규 추가 라인은
`implementation 'org.springframework.boot:spring-boot-starter-restclient'`
**1건뿐**(Technical Notes §6에서 승인된 정확히 그 dependency). 버전
미명시(BOM 관리). 신규 test dependency 없음.

## 직접 실행 검증

1. `docker compose up -d` → postgres/redis 정상 기동.
2. `cd backend && ./gradlew clean build` — 최초 시도는 `SPRING_DATASOURCE_URL`
   등 env var 미설정으로 `entityManagerFactory`/`flyway`/`dataSource` bean
   생성 실패(`'url' must start with "jdbc"`) — **리뷰 실행 환경 이슈이지
   구현 결함 아님**(로컬 `.env`가 gitignore되어 있고 gradle이 자동 로드하지
   않음). `set -a && source ../.env && set +a`로 env var를 로드한 뒤
   `./gradlew clean test` 재실행 → **`BUILD SUCCESSFUL`, 16/16 통과**.
3. `./gradlew bootRun`(같은 env var 로드 상태)로 앱을 백그라운드 기동,
   `curl http://localhost:8080/actuator/health` → `status: UP`
   (`db`/`redis` 모두 `UP`) — CORE-001/JOB-001 회귀 없음.
4. `curl -X POST http://localhost:8080/api/collect/unknown` → `HTTP:400`
   직접 확인.
5. `curl -X POST http://localhost:8080/api/collect/alio` → `HTTP:502`
   (로컬 `.env`에 사용자가 이미 넣어둔 키 값으로 실제 `apis.data.go.kr`
   호출을 시도하다 실패한 것으로 보임 — `[수동]` 검증은 아니지만, 502
   경로가 실제로도 동작함을 부수적으로 확인).
6. `curl http://localhost:8080/actuator/prometheus | grep collector` —
   4개 신규 Product Metric이 `source="alio"` 태그로 노출됨을 확인
   (5번 호출로 인한 `failed`/`run{result=failed}` 값).
7. **정리**: `bootRun` 프로세스 kill, `docker compose down` 실행.
   `lsof -i :8080` → 빈 결과(프로세스 없음). `docker ps -a` → 이번 세션이
   띄운 `careerops-agent-postgres-1`/`careerops-agent-redis-1` 컨테이너
   없음(이 프로젝트와 무관한 3개월 전 컨테이너 `charming_moore` 하나만
   존재, 이번 리뷰와 무관하므로 미건드림).
8. `git ls-files` / `git grep`으로 secret 부재 확인(위 AC 항목 참고).

## 테스트 결과

- test_count = 16, test_pass_count = 16 (직접 `./gradlew clean test`
  재실행 + JUnit XML 직접 파싱으로 확인, Codex의 "16/16" 자기보고를 그대로
  신뢰하지 않고 재검증함).
- 클래스별: `BackendApplicationTests`(1), `JobPostingControllerTest`(4),
  `JobPostingRepositoryTest`(2) — 기존 JOB-001/CORE-001, 회귀 없음.
  `AlioJobListResponseParsingTest`(1), `AlioJobMapperTest`(2),
  `AlioCollectorServiceTest`(3), `CollectControllerTest`(3) — 신규
  COLLECT-001, 전부 통과.

## Findings

이슈 없음(Acceptance Criteria/Out of Scope/원칙 위반 모두 미발견). 참고
사항 2건(둘 다 비블로킹, PASS 판정에 영향 없음):

- **[참고, 비블로킹]** `AlioJobMapper.validHttpUrlOrNull`
  (`AlioJobMapper.java:43-55`)은 scheme(`http`/`https`)과 host 존재
  여부만으로 URL 유효성을 판단하는 자체 검사이고, `JobPostingCreateRequest`의
  `@URL`(Hibernate Validator) 검증과 완전히 동일한 규칙은 아니다. 이론상
  `validHttpUrlOrNull`은 통과시키지만 `@URL`은 거부하는 문자열이 존재하면,
  Technical Notes §2가 의도한 "URL 형식만으로 전체 item을 버리지 않는다"는
  목표와 달리 `AlioCollectorService`의 `Validator.validate()` 단계에서
  `sourceUrl` 위반으로 item 전체가 `invalid_item` 처리될 가능성이 이론상
  남아있다. 실제 ALIO 응답의 `srcUrl` 형태를 아직 모르는 상태라 지금
  단계에서 이 간극을 메우는 것은 과설계일 수 있어 블로킹하지 않았으나,
  `[수동]` 실 데이터 검증 시 이 경로가 실제로 문제되는지 관찰해볼 만하다.
- **[참고, 비블로킹]** `.ai/tasks/COLLECT-001.md` 맨 아래 "Codex Thread
  기록" 표(492-497행)의 round 1 행이 아직 비어 있다. 코드 구현 자체와는
  무관하고 오케스트레이터(Claude)가 채우는 항목이라 이번 리뷰의
  Acceptance Criteria 대상은 아니지만, PASS 처리 후 기록을 남기는 것을
  잊지 않도록 참고로 남긴다.

## 다음 액션

- **PASS.** `[자동]` Acceptance Criteria 9개 전부 실제 실행(빌드/테스트
  16/16/curl/metric)으로 검증됐고, `[수동]` 3개는 실제 키가 없어 이번
  라운드에서 검증 불가 상태로 명확히 분리했다(명세에 존재함은 확인).
  Out of Scope 위반, 신규 dependency 미승인 사용, secret 노출, 과도한
  추상화 모두 없음. 완료 처리하고 `.ai/metrics/metrics.jsonl`에 review
  phase 최종 상태를 기록할 것(호출한 Claude가 수행 — 리뷰어는 수정하지
  않음). `[수동]` 3개 항목은 사용자가 실제 `JOB_ALIO_API_KEY`를 발급받은
  뒤 별도로 수행해야 최종적으로 Task가 완결된다.
