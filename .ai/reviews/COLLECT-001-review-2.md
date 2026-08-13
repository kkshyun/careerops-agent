---
task_id: COLLECT-001
review_round: 2
reviewer: claude
reviewed_at: 2026-08-13T22:10:00+09:00
verdict: PASS
---

## 배경

1차 리뷰(`.ai/reviews/COLLECT-001-review-1.md`)는 PASS했으나, 이후 사용자가
실제 승인받은 API가 1차 구현이 가정한 data.go.kr 게이트웨이가 아니라
ALIO 자체 개방데이터 사이트(`opendata.alio.go.kr`)라는 것이 밝혀졌다.
Codex가 같은 thread에서 계약을 정정했고, **Claude(Tech Lead)가 실제
승인된 키로 진짜 End-to-End 검증을 이미 완료했다**(fetched=50/saved=50
최초 실행, fetched=50/saved=0/skipped=50 재실행으로 dedup 확인,
`/actuator/prometheus`에서 `careerops_collector_run_total`(2,
result=success)/`careerops_collector_fetched_total`(100)/
`careerops_collector_saved_total`(50)/`careerops_job_creation_total`(50)
전부 확인됨). 이번 2차 리뷰는 그 실제 E2E 검증을 반복하지 않고(트래픽
쿼터 절약), **코드가 정정된 계약과 정확히 일치하는지**와 **자동 테스트가
여전히 fixture 전용인지**를 검증하는 데 집중했다. 실제 저장된 50건은
named volume(`careerops-agent_postgres_data`)에 그대로 남아 있음을
`docker compose up -d` 후 `SELECT count(*) FROM job_postings WHERE
source='ALIO'` → `50`으로 직접 재확인했다(수집 자체를 재실행하지는
않음).

## 코드-명세 대조 (정정된 API 사양 기준)

- [x] `application.yml`의 `careerops.collector.alio.base-url` —
  `https://opendata.alio.go.kr/new/v1/recruit`
  (`backend/src/main/resources/application.yml:19`), 명세와 정확히 일치.
- [x] `RestClientAlioJobClient.java` — path `/list.do`
  (`RestClientAlioJobClient.java:30`), header 3개 전부 존재: `swaggerType: Y`
  (`:36`), `Content-Type: application/json`(`.contentType(MediaType.APPLICATION_JSON)`,
  `:37`), `Accept: application/json`(`.accept(MediaType.APPLICATION_JSON)`,
  `:38`), 빈 JSON body `"{}"`(`.body("{}")`, `:39`) 모두 확인. 성공 판정이
  `!"200".equals(response.resultCode())`로 바뀜(`:45`) — 기존 `"0".equals(...)`
  data.go.kr 관례가 남아있지 않음.
- [x] `AlioJobListResponse.java` — `result`가 `List<AlioJobItem>` flat
  구조(`AlioJobListResponse.java:9`), `item` 래퍼 없음. `resultCode`는
  `String`으로 선언(`:10`) — Jackson 3 coercion으로 JSON 숫자 `200`도
  자동 흡수되는 것을 fixture(`alio-list-response-valid.json`은 JSON 숫자
  `200`, `alio-list-response-with-invalid-item.json`은 JSON 문자열
  `"200"`)로 양쪽 다 테스트하고 있어(`AlioJobListResponseParsingTest`가
  전자를 파싱해 `"200"`으로 확인) 명세에서 주장한 coercion 동작이 실제로
  검증됨.
- [x] `AlioJobResultItem` 삭제 확인 — 파일 자체가 저장소에 없고(`find`
  결과 없음), 살아있는 Java 코드 어디에도 참조가 없다(`grep -rn
  "AlioJobResultItem" --include="*.java"` 결과 0건). `AlioCollectorService`/
  `AlioJobMapper` 모두 `AlioJobItem`을 `.item()` 호출 없이 직접 사용
  (`AlioCollectorService.java:59,66,72`, `AlioJobMapper.java:17`).
  `AlioJobResultItem` 문자열이 남아있는 곳은 `.ai/tasks/COLLECT-001.md`와
  `.ai/reviews/COLLECT-001-review-1.md`뿐이며, 둘 다 "무엇이 왜 잘못됐는지"를
  보존하기 위한 이력 기록이라 문제 없음(Task 명세 자체가 명시적으로
  보존을 지시함).
- [x] fixture JSON 2개(`backend/src/test/resources/fixtures/alio/*.json`) —
  둘 다 `result` 배열 원소가 `item`으로 감싸지 않고 필드를 직접 담는 flat
  구조로 갱신됨. 실제 데이터를 복사한 흔적 없음(`example.invalid` 도메인,
  "합성 공공기관 A/B/C/D" 등 합성 값만 사용).

## Out of Scope 대조

`git status`/`git diff` 기준으로 이번 세션(1·2차 구현 전체, 아직
커밋되지 않은 working tree 상태)에서 바뀐 파일은 `.env.example`(1줄
추가, `JOB_ALIO_API_KEY=`), `backend/build.gradle`(1줄,
`spring-boot-starter-restclient` — 1차 리뷰에서 이미 승인된 그 항목과
동일), `JobPostingRepository.java`(`existsBySourceAndExternalId` 쿼리
메서드 1개), `application.yml`, `docs/ARCHITECTURE.md`/`docs/DECISIONS.md`/
`docs/METRICS.md`/`.claude/skills/codex-implement/SKILL.md`(문서·프로세스
기록), `collector/` 패키지 전체(신규)뿐이다. 이 중 2차 라운드(계약 정정)
자체가 추가로 건드린 범위는 `application.yml`의 base-url,
`RestClientAlioJobClient`의 path/header/body/성공판정, `AlioJobListResponse`
의 `result` 타입, `AlioJobResultItem` 삭제, fixture 2개뿐으로 — 명세의
"정정된 API 사양"이 요구하는 범위와 정확히 일치하고 그 밖의 리팩터링/새
추상화/새 의존성은 없다. `CollectController`도 여전히
`if (!"alio".equalsIgnoreCase(source))` 하드코딩 분기 하나뿐(레지스트리/
전략 패턴 없음), 페이지네이션 루프 없음(`fetchList(1, numOfRows)` 고정),
DB unique 제약 없음(신규 Flyway migration 없음) — 1차 리뷰가 확인한
Out of Scope 준수 상태가 그대로 유지됨.

## 직접 실행한 테스트

1. `docker compose up -d` → postgres/redis 정상 기동(healthy). 기동 직후
   `docker compose exec -T postgres psql -U careerops -d careerops -c
   "SELECT count(*) FROM job_postings WHERE source='ALIO';"` → `50` —
   Claude가 앞서 실제 키로 검증하며 저장한 50건이 named volume에 그대로
   남아있음을 확인(이번 리뷰가 재수집한 것 아님).
2. `cd backend && ./gradlew clean test` — `BUILD SUCCESSFUL`. JUnit XML을
   직접 파싱해 재확인: **16/16 통과, 0 failure/0 error/0 skipped**
   (`BackendApplicationTests` 1, `JobPostingControllerTest` 4,
   `JobPostingRepositoryTest` 2, `AlioJobListResponseParsingTest` 1,
   `AlioJobMapperTest` 2, `AlioCollectorServiceTest` 3,
   `CollectControllerTest` 3 = 16). 기존 JOB-001/CORE-001 테스트 회귀 없음.
3. 네트워크 격리 확인: `build/test-results/test/*.xml`,
   `build/reports/tests/test`에서 `opendata.alio.go.kr`/`apis.data.go.kr`
   문자열 검색 → 매치 없음. 구조적으로도 `AlioTestConfiguration`이
   `FixtureAlioJobClient`를 `@Primary` bean으로 등록해
   `RestClientAlioJobClient`(실제 운영 구현체, 여전히 `@Component`로
   컨텍스트에 로드됨)를 오버라이드하므로, 모든 테스트가 fixture만
   사용하고 운영 구현체는 생성자에서 `RestClient` 객체만 빌드할 뿐 실제
   `.fetchList()` 호출은 어떤 테스트에서도 발생하지 않음
   (`RestClientAlioJobClient.java:17-24`, `FixtureAlioJobClient.java`).
4. Secret 노출 확인: `.env.example`에는 `JOB_ALIO_API_KEY=`(키 이름만),
   `git grep`으로 추적 파일 전체에서 실제 키 값 패턴 없음(1차 리뷰와
   동일 결론 재확인). fixture 2개는 합성 데이터.
5. **정리**: `docker compose down` 실행(볼륨 삭제 `-v` 사용 안 함).
   `docker compose ps -a` → 빈 결과. `docker volume ls | grep careerops` →
   `careerops-agent_postgres_data` 존재 확인(실제 저장된 50건 보존됨).

## 테스트 결과

test_count = 16, test_pass_count = 16 (직접 `./gradlew clean test`
재실행 + JUnit XML 직접 파싱으로 확인).

## Acceptance Criteria — 자동 9개 재확인 (1차 리뷰 이후 로직 변경분 반영)

1차 리뷰에서 이미 코드/테스트 라인 단위로 확인된 항목들이고, 이번 라운드는
DTO/클라이언트 구조만 바뀌었을 뿐 시나리오·판정 로직은 동일하게
`AlioCollectorServiceTest`/`CollectControllerTest`/
`AlioJobListResponseParsingTest`/`AlioJobMapperTest`가 커버한다. 직접
테스트 실행(16/16)과 코드 읽기로 9개 전부 재확인:

- [x] 정상 수집 — `AlioCollectorServiceTest.collectsMapsSavesAndRecordsMetrics`,
  `CollectControllerTest.collectsAlioCaseInsensitively` 통과.
- [x] 매핑 정확성 — 같은 테스트에서 `companyName`/`title`/`sourceUrl`/
  `applicationStartAt`/`applicationEndAt`/`externalId` 일치 확인.
- [x] 필수 필드 누락 처리 — `skipsInvalidItemsAndDuplicates`에서
  `instNm=""`, `recrutPbancTtl` 누락 항목이 각각 `invalid_item`으로 집계.
- [x] 중복 skip — 같은 테스트 2회 호출로 `saved=0, skipped=1` 확인,
  `repository.count()` 불변.
- [x] 외부 API 실패 시 동작 — `recordsFailedRunWhenClientFails`,
  `returnsBadGatewayWithoutSavingWhenClientFails`에서 502/미저장/
  `run{result=failed}` +1 확인.
- [x] 지원하지 않는 source — `rejectsUnsupportedSourceWithoutSaving`에서
  400/미저장 확인.
- [x] DTO parsing 단위 테스트 — `AlioJobListResponseParsingTest`에서
  flat `result` 구조, `resultCode="200"`, 필드 값 일치 확인(정정된 구조
  기준으로 갱신됨).
- [x] Mapper 단위 테스트 — `AlioJobMapperTest` 2개, 날짜 파싱/
  externalId 변환/null 처리 확인.
- [x] Product Metric 계측 — `fetched`/`saved`/`failed`/`run` 4종 모두
  `MeterRegistry.counter(...)`로 직접 조회해 검증.
- [x] 외부 API 미접근으로도 전체 통과 — 16/16, 네트워크 문자열 검색
  매치 없음(위 "직접 실행한 테스트" 3번).
- [x] Git tracked file에 secret 없음 — 위 "직접 실행한 테스트" 4번.

`[수동]` 3개는 이미 Claude가 실제 키로 완료했다고 보고했고(fetched=50/
saved=50 최초, fetched=50/saved=0/skipped=50 재실행, Prometheus 4개
지표 확인), 이번 라운드에서 재검증하지 않았다(트래픽 쿼터 절약 목적,
지시사항에 따름). 다만 DB에 실제 50건이 남아있음을 이번 리뷰가 독립적으로
재확인했다(위 1번).

## Findings

이슈 없음(Acceptance Criteria/Out of Scope/원칙 위반, secret 노출 모두
미발견). 참고 사항 1건(비블로킹):

- **[참고, 비블로킹]** `docs/ARCHITECTURE.md:107-108`이 여전히
  "COLLECT-001부터 첫 실제 외부 Source(ALIO 공공기관 채용정보
  조회서비스 — **data.go.kr 공식 Open API**)를 연결하는 코드가..."로
  적혀 있다 — 이는 1차 구현(잘못된 Source) 시점에 작성된 문장이고,
  2차 정정(`opendata.alio.go.kr`) 이후에도 갱신되지 않았다. 코드/테스트/
  fixture는 전부 정정된 계약을 정확히 따르고 있어 동작에는 전혀 영향이
  없고, Task 명세의 Acceptance Criteria에도 이 문서 갱신이 포함되어
  있지 않아 PASS 판정을 막지 않는다. 다만 이 프로젝트 원칙(추측 대신
  근거 기반 확인, 문서와 실제 계약의 정합성)에 비추면 실제로 틀린
  서술이 남아있는 상태이므로, 이후 어느 라운드에서든(다음 Task 착수
  전이면 충분) `docs/ARCHITECTURE.md`의 이 문장을
  `opendata.alio.go.kr` 기준으로 한 줄 정정해두는 것을 권장한다.

## 다음 액션

- **PASS.** 코드가 "정정된 API 사양"과 정확히 일치함을 파일:라인 단위로
  대조 확인했고(`base-url`, path, header 3종 + body, 성공판정,
  `AlioJobListResponse.result` flat 구조, `AlioJobResultItem` 삭제 및
  잔존 참조 없음, fixture 갱신), Out of Scope 위반·신규 미승인
  dependency·secret 노출 없음, `./gradlew clean test` 16/16 통과를 직접
  재현했고 운영 구현체가 어떤 자동 테스트에서도 실제 호출되지 않음을
  구조적으로 확인했다. 실제 API E2E 성공 자체(fetched/saved/재실행
  dedup/Prometheus)는 Claude가 이미 실제 키로 검증 완료했고, 이번
  라운드는 그 위에 코드-명세 정합성만 독립적으로 재검증했다. 완료
  처리하고 `.ai/metrics/metrics.jsonl`에 review phase(round 2) 최종
  상태 기록할 것(호출한 Claude가 수행 — 리뷰어는 수정하지 않음).
  비블로킹 참고사항(`docs/ARCHITECTURE.md` 문서 갱신 누락) 1건은
  급하지 않으나 다음 기회에 정리 권장.
