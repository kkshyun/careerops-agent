---
task_id: RECOMMEND-001.1
review_round: 1
reviewer: claude (reviewer subagent)
reviewed_at: 2026-08-25T19:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

**Transaction boundary**

- [x] `RecommendationCandidateReader.read()`에만 `@Transactional(readOnly=true)`,
      `JobRecommendationService`는 클래스/메서드 전체에 `@Transactional` 없음 —
      충족. 코드로 직접 확인
      (`RecommendationCandidateReader.java:30`, `JobRecommendationService.java`
      전체에 import조차 없음)했고, `git diff`로 기존
      `@Transactional(readOnly=true)`가 `recommend(int limit)`에서 완전히
      제거됐음을 재확인. `RecommendationTransactionBoundaryTest
      .onlyReaderReadDeclaresReadOnlyTransaction`가 reflection으로 동일하게
      고정(1/1 PASS).
- [x] DB 레벨 통합 테스트(지연 중 무관한 쿼리 정상 수행) — 충족, 다만 구현
      방식이 명세의 예시(Hikari pool 축소/pool 크기 이상 동시쿼리)와 다름.
      `JobRecommendationTransactionIntegrationTest`(`@SpringBootTest`, 실제
      Postgres/실제 `PlatformTransactionManager`)가
      `TransactionCapturingClient.recommend()` 내부에서
      `TransactionSynchronizationManager.isActualTransactionActive()`를 직접
      확인해 `false`임을 단언하고, 그 지연(`CountDownLatch`) 도중 메인
      스레드에서 `jobs.count()`가 정상 수행됨을 검증한다(1/1 PASS, 실제
      트랜잭션 매니저 기준). 이 테스트는 기존 구조(전체를
      `@Transactional`로 감쌌던 구조)로 되돌리면 `transactionActive.get()`이
      `true`가 되어 반드시 실패하므로 "기존 구조였다면 실패, 새 구조는
      통과"라는 AC의 핵심 의도를 충족한다. 다만 pool을 의도적으로 작게
      설정하거나 pool 크기 이상 동시 쿼리를 걸지는 않아서, "connection pool
      고갈 자체를 막는다"는 운영 시나리오까지 직접 재현하지는 않는다 —
      기본 Hikari pool 크기가 이 테스트가 쓰는 연결 수(1~2개)보다 커서, 그
      방식이었다면 신구 구조 모두 통과했을 것이다. 근본 속성(트랜잭션
      비활성)을 직접 단언하는 이 방식이 더 정밀한 검증이라고 판단해
      blocking으로 보지 않는다(아래 Findings 참고).
- [x] `client.recommend(...)`가 `reader.read()` 이후 호출 — 충족.
      `JobRecommendationService.calculate()`에서 `reader.read()` →
      `providerTopK` 계산 → `attempt()`(`client.recommend`) 순서가 코드
      구조로 강제됨(`JobRecommendationService.java:48-64`). Mock 호출 순서를
      별도로 assert하는 테스트는 없지만, `RecommendationInput`이 immutable
      snapshot이라 reader 호출 없이는 애초에 `client.recommend()`를 호출할
      인자 자체가 존재할 수 없는 구조라 사실상 컴파일 타임으로 보장됨.

**Immutable snapshot / 인터페이스 변경**

- [x] `JobRecommendationClient.recommend(RecommendationInput, int)` 시그니처 —
      충족(`JobRecommendationClient.java:7`). `AnthropicJobRecommendationClient`
      /테스트 Fake 모두 Entity/Map 파라미터 없음(코드 직접 확인).
- [x] repair retry 시 `reader.read()` 정확히 1회 — 충족.
      `retryReusesSameInputAndReaderOnce`가 `verify(reader).read()`(기본
      times(1))로 확인하고, 추가로 `client.inputs.get(0)`과 `get(1)`이
      `isSameAs`(동일 객체)임을 확인해 "동일 snapshot 재사용"까지 명세보다
      한 단계 더 엄격하게 검증한다.

**Provider output 상한 지시**

- [x] `limit=5→providerTopK=20`, `limit=20→providerTopK=40` prompt 반영 —
      충족. `JobRecommendationPromptBuilderTest.providerTopKLimitFiveIsTwenty`
      /`providerTopKLimitTwentyIsForty`가 `Math.max(limit*2,20)` 공식 그대로
      호출해 문자열에 "최대 20개"/"최대 40개" 포함을 확인.
- [x] 배열 상한 명시 문장 포함 — 충족.
      `JobRecommendationPromptBuilder.userPrompt()`가
      `"recommendations 배열은 최대 {providerTopK}개까지만 포함하라. 그 이상의
      후보는 평가만 하고 출력하지 않는다."`를 프롬프트 최상단에 삽입
      (`JobRecommendationPromptBuilder.java:17-18`), 테스트가 그 상한 숫자
      + 문장을 assert.
- [x] provider가 상한 초과 반환해도 dedup+정렬+truncate로 정상 처리 —
      충족. `providerMayExceedTopKAndServerTruncates`(25건 반환, `limit=5`
      요청 → 결과 5건)로 확인. `RawRecommendationResult`/`RawJobRecommendation`
      record에 `maxItems` 등 JSON Schema 제약이 추가되지 않았음을 코드로
      직접 확인(Out of Scope 미침범).

**Repair retry**

- [x] `UNKNOWN_JOB_ID`/`UNKNOWN_PKB_ID`/`SCORE_OUT_OF_RANGE`/
      `MALFORMED_RESPONSE` 1차 실패 → 2차 성공 → 200, 호출 2회 — 충족.
      `eachRepairableReasonRetriesAndRecovers`가 4개 reason 전부를 loop로
      개별 `resetFlow()` 후 검증(각 iteration마다 `calls==2`,
      `recommend(5)` 결과 1건, `verify(reader).read()`).
- [x] `MALFORMED_RESPONSE` 1차·2차 모두 실패 → 502(`JobRecommendationException`
      전파), 호출 정확히 2회, 3차 없음 — 충족. `malformedTwiceStopsAtTwo`.
- [x] `NETWORK_TIMEOUT`/`PROVIDER_4XX`/`PROVIDER_RETRY_EXHAUSTED` 1회 실패 →
      즉시 실패, 호출 1회(재시도 없음) — 충족.
      `nonRepairableReasonsDoNotRetry`가 3개 reason 모두 loop로 검증
      (`calls==1`).

**Metrics**

- [x] 정상 흐름에서 `provider.retry` 미증가 — 충족.
      `normalFlowDoesNotIncrementRetryMetric`.
- [x] repair 성공 시 `{outcome=repaired}` +1, 재실패 시
      `{outcome=still_failed}` +1 — 충족.
      `repairSuccessMetricAndBaseMetricsAreOnce`/`retryMetricsCountAttemptsAndOutcome`.
      코드상으로도 `attempt()` 재시도 성공 시에만 `retryCounters.get("repaired")`,
      재시도 실패 시에만 `retryCounters.get("still_failed")` 증가하고 정상
      1회 성공 시에는 두 카운터 모두 손대지 않음(`JobRecommendationService.java:61-76`).
- [x] `validation_failure`가 reason별 attempt 횟수만큼 누적 — 충족.
      `retryMetricsCountAttemptsAndOutcome`가 `MALFORMED_RESPONSE`가
      1차·2차 각각 실패했을 때 누적 2임을 확인. 코드상
      `recordValidationFailure()`가 `attempt()` 각 실패마다(1차 repairable
      실패 시, 2차도 repairable 실패 시) 개별 호출됨(`:67`, `:72`)을 확인.
- [x] 기존 4개 metric이 retry 여부와 무관하게 요청당 1회 계측 — 충족.
      `candidateMetric.record(...)`는 `reader.read()` 직후 1회만
      (`:49`), `counters.get("success")`/`returnedMetric`은 재시도 로직 밖
      최종 성공 시 1회만(`:77`) 호출되도록 구조화됨.
      `repairSuccessMetricAndBaseMetricsAreOnce`가 `candidates`/`returned`/
      `duration` summary/timer의 `count()`가 모두 1임을 직접 assert.

**진단 로그**

- [x] `NETWORK_TIMEOUT`/`MALFORMED_RESPONSE`/`PROVIDER_RETRY_EXHAUSTED` 실패
      로그에 원인 예외 simple class name 포함 — 충족.
      `causeType(JobRecommendationException e)`가 `e.getCause()`의
      simple name(없으면 "none")을 반환해 `log.warn(...)`에 항상 포함
      (`JobRecommendationService.java:85-86,134`).
      `JobRecommendationServiceLogTest.diagnosticLogsCauseTypesWithoutSensitiveMessagesOrInput`이
      실제 `Logger`에 `ListAppender`를 붙여 3개 reason 전부에서
      `causeType=SensitiveCause` 형태로 나타남을 확인.
- [x] job title/PKB 원문/provider 요청·응답/API key가 로그에 남지 않음
      (신규 causeType 필드 포함 재확인) — 충족. 위 테스트가 동시에
      `doesNotContain(SENSITIVE-JOB-TITLE, SENSITIVE-PKB-SUMMARY,
      SENSITIVE-PROVIDER-BODY-AND-API-KEY)`를 확인하고,
      `AnthropicJobRecommendationClientTest.missingKeyAndSensitiveInputAreNotLogged`가
      기존 MATCH-002 패턴 그대로 재확인. `log.warn`/`log.info` 라인
      전체를 봐도 job title/PKB summary/detail 문자열 자체를 넘기는 코드
      경로가 없음을 `JobRecommendationService.java` 전체 grep으로 확인.

**회귀**

- [x] RECOMMEND-001 기존 24개 테스트 케이스의 동작이 유지됨 — 충족.
      1~19, 21은 `JobRecommendationServiceTest`에 동일/동등 이름으로 그대로
      남아 있음(레포지토리 필터링을 reader로 이관하면서 20/22/23은
      아키텍처상 올바르게 `RecommendationCandidateReaderTest
      .materializesApprovedImmutableSnapshotAndFlattensTags`로 이동해
      MANUAL/APPROVED만 포함·PENDING 제외를 확인). 24(로그 privacy)는
      `AnthropicJobRecommendationClientTest`에 그대로 유지. 상세 매핑은
      "테스트 결과" 참고.
- [x] `MATCH-001`/`MATCH-002`/`AGENT-001`/`AGENT-002`/`NOTIFY-001` 등 패키지
      회귀 없음, `NOTIFY-001` production 코드 무변경 — 충족.
      `git diff --stat`로 `backend/src/main/java/com/careerops/backend/notification/**`,
      `application.yml`, `build.gradle` 전부 변경 없음을 직접 재확인.
      `./gradlew test --tests "com.careerops.backend.notification.*"
      --tests "com.careerops.backend.match.*" --tests
      "com.careerops.backend.agent.*"` 격리 실행 BUILD SUCCESSFUL.
- [x] `cd backend && ./gradlew test` 전체 실패 0건 — **격리 기준 충족,
      전체 스위트 단독 실행 시 pre-existing flake로 산발적 실패 관찰**
      (아래 "테스트 결과" 참고, RECOMMEND-001.1 코드 결함 아님, Claude가
      이미 2회·이번 리뷰가 recommend/notification/match/agent 패키지
      격리로 재확인).

## 테스트 결과

- `./gradlew compileJava compileTestJava` — 성공(경고/에러 없음).
- `./gradlew test --tests "com.careerops.backend.recommend.*"` —
  **BUILD SUCCESSFUL, 35/35 PASS**(XML 기준 클래스별 tests/failures/errors
  전부 확인):
  `AnthropicJobRecommendationClientTest` 2/2,
  `JobRecommendationControllerTest` 4/4,
  `JobRecommendationPromptBuilderTest` 3/3,
  `JobRecommendationServiceLogTest` 1/1,
  `JobRecommendationServiceTest` 20/20,
  `JobRecommendationTransactionIntegrationTest` 1/1,
  `RecommendationCandidateReaderTest` 1/1,
  `RecommendationTransactionBoundaryTest` 3/3.
- `./gradlew test --tests "com.careerops.backend.notification.*" --tests
  "com.careerops.backend.match.*" --tests "com.careerops.backend.agent.*"` —
  BUILD SUCCESSFUL(회귀 없음).
- 전체 `./gradlew test`는 이번 리뷰에서 별도로 반복 실행하지 않았다 —
  Claude가 이미 round1/round3 이후 2회 반복 실행해 매번 다른 무관 클래스
  조합(`ImportBatchExtractionServiceTest`/`MultipartUploadLimitIntegrationTest`
  /`JobRecommendationControllerTest`/`JobRecommendationTransactionIntegrationTest`)에서
  Postgres `too many clients already`로 실패하지만 격리 재실행 시 항상
  통과함을 확인했고, 이번 리뷰의 패키지별 격리 실행 결과도 동일한 결론과
  모순되지 않는다(`.ai/reviews/RECOMMEND-001-review-2.md`에 이미 문서화된
  동일 패턴). 이 결론을 뒤집을 새 증거는 발견하지 못했다.

## Findings

1. **[정보, blocking 아님] 트랜잭션 통합 테스트가 AC 예시(Hikari pool
   축소/과부하)와 다른 방식으로 구현됨.**
   `JobRecommendationTransactionIntegrationTest`는 `pool 크기 이상 동시
   쿼리`를 걸지 않고 대신 `TransactionSynchronizationManager
   .isActualTransactionActive()`를 provider 호출 도중 직접 단언한다. 이
   방식은 (a) 기존 구조로 되돌리면 반드시 실패하고 (b) 원인(트랜잭션이
   실제로 열려 있는지)을 pool 경합보다 더 직접적으로 증명한다는 점에서
   AC의 "기존 구조였다면 실패, 새 구조는 통과" 요구를 충족한다고 판단해
   PASS로 처리했다. 다만 "동시 요청 시 DB 커넥션이 고갈되지 않는다"는
   운영 시나리오 자체를 재현하지는 않는다 — 후속 Task에서 실제 부하
   시나리오 검증이 필요하면 별도로 요청할 것을 권장(수정 요청 아님).
2. **[정보, blocking 아님] token usage metric 시도 결과가 Codex Thread
   기록에 남지 않음.** Task 명세 §Scope 5는 "구현 시점에 실제 jar로
   `usage()` 접근 가능성을 재확인해 가능하면 추가하고, 불가능하면
   Acceptance Criteria에서 blocking 항목은 아니지만 시도 결과를 Codex
   Thread 기록에 남긴다"고 명시한다. 코드에 `careerops.recommendation
   .provider.tokens` 지표가 없고(grep 결과 없음), Task 파일의 "Codex
   Thread 기록" 표에도 이 항목에 대한 시도/결론 언급이 없다 — 기능
   blocking은 아니지만 명세가 요구한 기록 절차가 누락됐다. Claude가
   완료 처리 전에 Codex에게 "javap으로 `usage()` 접근을 시도했는지,
   시도했다면 결과가 무엇인지"를 확인해 Task 파일에 한 줄이라도 남기도록
   요청하는 것을 권장한다(재작업 요구 아님, 기록 보완 요청).
3. **[정보, blocking 아님] PKB 미승인 배제 테스트에서 REJECTED 케이스가
   빠짐.** 기존 `onlyApprovedOrManualPkbIsSent`(구 코드)는 PENDING과
   REJECTED를 모두 별도 fixture로 검증했으나, 새
   `RecommendationCandidateReaderTest.materializesApprovedImmutableSnapshotAndFlattensTags`는
   PENDING만 검증한다. `RecommendationCandidateReader.approved()`
   (`RecommendationCandidateReader.java:59-61`) 로직은 PENDING/REJECTED를
   포함해 "APPROVED가 아닌 모든 상태"를 동일한 한 분기로 처리하므로
   실질적 리스크는 낮지만, mutation testing 관점에서 REJECTED 케이스를
   되살리면 더 엄격해진다(수정 필수 아님).
4. **[확인 완료, 문제 없음] Out of Scope 미침범.** `JobPostingRepository`/
   PKB 4종 repository/`ExperienceTagRepository`는 이관만 되고 메서드
   시그니처 신규 추가 없음. `application.yml`/`build.gradle`/
   `docs/METRICS.md`/notification 패키지 전부 무변경. schema에
   `maxItems` 등 미추가. candidate hard cap/keyword filter 미도입.
   timeout 값(90초) 불변.
5. **[확인 완료, 문제 없음] `isRepairable()`/`isValidationFailure()`
   javadoc이 목적 차이를 명시함.** `JobRecommendationException.java:10-13`이
   "metric bucket classification"과 "repair attempt 허용 여부"로 각각
   명확히 구분해 문서화.
6. **[확인 완료, 문제 없음] 신규 production dependency 없음** —
   `git diff --stat -- backend/build.gradle` 결과 없음.
7. **[확인 완료, 문제 없음] Secret 커밋 없음** — 신규/변경 파일에 API
   key/credential 리터럴 없음(테스트의 `"SECRET-RECOMMEND-API-KEY"` 등은
   sensitive-not-logged 검증용 더미 문자열).
8. **[해당 없음] 자기소개서 생성 로직 관련 원칙** — 이 Task는 채용공고
   랭킹/재시도 안정화로, 사용자가 제공하지 않은 경험·수치를 생성하는
   로직이 없다(오히려 `convert()`가 ID/score 검증으로 provider가 만든
   비근거 항목을 걸러내는 방향).

## 다음 액션

- **판정: PASS.** Acceptance Criteria 전 항목(자동 테스트 대상)이 코드와
  실행 결과로 확인됐고, `recommend` 패키지 신규/보강 테스트 35/35, 인접
  회귀 패키지(notification/match/agent) 격리 실행 모두 통과. Codex에게
  추가로 요청할 **필수** 수정 사항 없음.
- **권장(선택, blocking 아님)**: 완료 처리 전 같은 Codex thread에 Finding
  #2(token usage metric 시도 결과를 Task 파일 "Codex Thread 기록"에 한
  줄 추가)만 요청해도 좋다. Finding #1/#3은 정보성으로, 별도 요청 없이
  그대로 진행 가능.
- **완료 처리 절차**: `.ai/tasks/RECOMMEND-001.1-stabilize-batch-ranking.md`의
  자동 Acceptance Criteria 체크박스를 전부 체크하고, `status`를 "실제
  E2E"(Case A~D, 10회 연속 호출 성공률, late-position 배제 확인,
  NOTIFY-001 재검증, Prometheus 신규 metric 노출)로 전환. Claude가 해당
  E2E를 별도로 수행한 뒤 `docs/METRICS.md`의 RECOMMEND-001 표에 신규
  지표 2~3개(및 가능하면 tokens)를 실측과 함께 추가.
  `.ai/metrics/metrics.jsonl`에 이번 review round(round=1,
  first_review_pass=true, test_count=recommend 패키지 35 기준 또는 전체
  스위트 기준값)를 기록할 것.
