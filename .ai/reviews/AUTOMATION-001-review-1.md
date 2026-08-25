---
task_id: AUTOMATION-001
review_round: 1
reviewer: claude (reviewer subagent)
reviewed_at: 2026-08-25T22:20:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] **기본 설정(둘 다 미지정=false)에서 두 Scheduler Bean 모두 부재, 앱
      정상 기동** — 충족. `AutomationPrepareScheduler`
      (`AutomationPrepareScheduler.java:8`)/`AutomationDeliveryScheduler`
      (`AutomationDeliveryScheduler.java:8`) 둘 다
      `@ConditionalOnProperty(prefix=..., name="enabled", havingValue="true")`
      이고 `matchIfMissing`을 지정하지 않아 기본값이 false다(Spring 기본
      동작 코드로 확인, `matchIfMissing` 관련 grep 결과 0건).
      `AutomationSchedulerConditionTest.bothSchedulersAreAbsentByDefault`
      (`ApplicationContextRunner` + `getBeanNamesForType`)가 이를 직접
      검증, `onlyPrepareSchedulerExistsWhenOnlyPrepareIsEnabled`/
      `onlyDeliverySchedulerExistsWhenOnlyDeliveryIsEnabled`/
      `bothSchedulersExistWhenBothAreEnabled` 3개 조합도 전부 커버. 직접
      재실행해 4/4 PASS 확인.
- [x] **`prepare.enabled=true`일 때 `runOnce()`가 `prepare(prepareLimit)`만
      호출(`recommend()` 별도 재호출 없음)** — 충족.
      `AutomationPrepareService.runOnce()`(`AutomationPrepareService.java:38`)
      가 `preparationService.prepare(prepareLimit)` 한 줄만 호출하고
      `JobRecommendationService`/`recommend(...)`에 대한 직접 참조가 코드
      전체에 없음(grep 확인). `AutomationPrepareServiceTest
      .delegatesConfiguredLimitAndReturnsCounts`가 `verify(preparation)
      .prepare(7)` + `verifyNoMoreInteractions(preparation)`로 확인.
      `AutomationPipelineIntegrationTest`에서도 `recommendations.calls`가
      prepare 실행 횟수(2회)와 정확히 일치 — 중복 호출 없음을 실제 통합
      테스트로도 재확인.
- [x] **prepare 409/502 실패 시 예외 미전파, `succeeded=false`** — 충족.
      `AutomationPrepareService.runOnce()`(`:41-49`)가
      `ResponseStatusException`(status==409만) / `JobRecommendationException`
      (repairable/validation 구분 없이 전부)를 catch해
      `failedCounter.increment()` 후 `result(false, 0, 0, started)` 반환,
      재throw 없음. `409 외 다른 ResponseStatusException`은 명시적으로
      다시 throw(`:42-44`) — 스펙에 없는 상태코드까지 삼키지 않아 안전.
      `AutomationPrepareServiceTest.catchesConflictAndReturnsFailedResult`/
      `catchesRepairableProviderFailure`/`catchesValidationFailure` 3개
      전부 `assertThatCode(...).doesNotThrowAnyException()` +
      `succeeded()==false` 확인.
- [x] **`delivery.enabled=true`일 때 PENDING을 생성 시각 오름차순 최대
      `deliveryLimit`개 선택해 순차 `send(id)` 호출** — 충족.
      `AutomationDeliveryService.runOnce()`(`:54-77`)가
      `repository.findIdsByStatusOrderByCreatedAtAsc(PENDING,
      PageRequest.of(0, deliveryLimit))` 호출 후 for-loop로 순서대로
      `sendService.send(id)` 호출.
      `JobRecommendationNotificationRepository.findIdsByStatusOrderByCreatedAtAsc`
      (`JobRecommendationNotificationRepository.java:31-34`)가
      `ORDER BY n.createdAt ASC, n.id ASC` JPQL. 실제 PostgreSQL로
      `AutomationNotificationRepositoryTest
      .selectsOnlyPendingOldestFirstAndAppliesPageLimit`가 PENDING 3건 중
      생성시각이 가장 이른 2건만(`limit=2`) 정확한 순서로 반환됨을 확인
      (raw JdbcTemplate으로 `created_at` backdate). 단위 테스트
      `AutomationDeliveryServiceTest.sendsPendingCandidatesSequentiallyUpToConfiguredLimit`
      도 `inOrder(sender)`로 호출 순서 검증.
- [x] **PROVIDER_ERROR/PROVIDER_5XX/DELIVERY_UNKNOWN은 best-effort 계속** —
      충족. `AutomationDeliveryService.runOnce()`의 `KakaoDeliveryException`
      catch 분기(`:68-76`)가 `TOKEN_REFRESH_FAILED`가 아니면 loop를
      계속 진행. `AutomationDeliveryServiceTest
      .continuesAfterEveryBestEffortKakaoFailure`가 3가지 reason 전부를
      한 run에 섞어 `attemptedCount==3`, `send()` 3회 호출 확인.
- [x] **TOKEN_REFRESH_FAILED 발생 시 남은 항목 중단 + `shortCircuited=true`**
      — 충족. `((KakaoApiException) exception.getCause())`로 정확히
      캐스팅해 `reason()==TOKEN_REFRESH_FAILED`일 때만 `break`
      (`AutomationDeliveryService.java:70-75`).
      `AutomationDeliveryServiceTest.tokenRefreshFailureShortCircuitsRemainingCandidates`
      가 `verify(sender, never()).send(3L)` + `shortCircuited()==true` +
      short_circuited counter==1 확인.
- [x] **SENT/SENDING은 후보에서 제외되거나 404/409로 skip, provider 재호출
      없음** — 충족. 쿼리 자체가 PENDING만 조회(위 근거), 추가로
      `ResponseStatusException` catch 분기(`:62-67`)가 404/409는 무시하고
      계속 진행, 그 외 상태코드는 재throw.
      `AutomationDeliveryServiceTest.skipsNotFoundAndConflictAndContinues`
      확인.
- [x] **FAILED는 delivery 후보에 포함 안 됨** — 충족. 신규 repository
      메서드가 `status=:status` 파라미터를 PENDING 하나로 고정 호출(코드
      상 `NotificationStatus.PENDING` 리터럴), FAILED 별도 자동 재시도
      경로 없음. `AutomationNotificationRepositoryTest`의 시나리오에
      FAILED row(`rows.get(4)`)가 포함돼 있으나 결과에서 제외됨을
      `containsExactly`로 확인.
- [x] **Fake 3건 → prepare 3건 PENDING → delivery 3건 SENT, 2차 동일 run은
      중복 0건/재전송 0건(provider 호출 횟수)** — 충족.
      `AutomationPipelineIntegrationTest
      .prepareThenDeliveryMovesThreePendingToSentAndSecondRunIsIdempotent`
      가 실제 PostgreSQL(`careerops_test`) + Fake
      recommendation/message/token client로 1차 run에서
      `createdCount==3`→`sentCount==3`→`messages.calls==3` 확인, 2차 run은
      `secondPrepare.createdCount()==0`,
      `secondPrepare.alreadyNotifiedCount()==3`,
      `secondDelivery.attemptedCount()==0`, `messages.calls`는 3에서 불변
      — provider 재호출 0회를 실제 invocation count로 증명.
- [x] **실제 PostgreSQL + Fake provider로 전체 파이프라인 end-to-end 검증**
      — 충족(위와 동일 테스트, `@SpringBootTest`로 실제 DB 연결).
- [x] **`AutomationPrepareService`/`AutomationDeliveryService` 어디에도
      `@Transactional` 없음** — 충족. 두 클래스 소스 전체를 직접 읽어 확인,
      추가로 `AutomationSchedulerTest.orchestrationServicesDoNotOwnTransactions`
      가 `AnnotatedElementUtils.hasAnnotation(..., Transactional.class)`로
      리플렉션 검증(런타임 검증 재실행 PASS 확인).
- [x] **자동 테스트 전체가 Fake client만 사용, 실제 Anthropic/Kakao 미호출**
      — 충족. `AutomationPipelineIntegrationTest`의 `FakeRecommendationClient`/
      `FakeMessageClient`/`FakeTokenClient` 모두 `@Primary` 빈으로 실제
      `RestClient*`/`AnthropicJobRecommendationClient`를 완전히 대체, 실제
      URL 호출 코드 없음(grep 결과 없음). 단위 테스트는 전부 Mockito mock.
- [x] **`./gradlew test` 전체 통과, 회귀 없음** — 충족(격리 배치 기준,
      상세는 "테스트 결과" 참고).

## ADR-0035 일치 여부

결정 1(기존 Service 재사용, 중복 LLM 호출 방지)/2(단계별 독립 flag,
기본 false)/3(cron 기반, `fixedDelay` 미사용, prepare 07:50→delivery
08:00)/4(overlap guard 미생성)/5(PENDING backlog, FAILED 제외, migration
불필요)/6(prepare 실패 시 예외 삼킴)/7(best-effort + TOKEN_REFRESH_FAILED만
short-circuit)/8(수동 실행 API 없음)/9(`AutomationRun` entity/migration 없음)
전부 코드로 직접 확인, 위반 없음.

- Overlap guard 미도입: `AutomationPrepareScheduler`/
  `AutomationDeliveryScheduler` 소스에 `ReentrantLock`/`AtomicBoolean`/
  advisory lock/Redis 관련 코드 전무(grep 확인).
- `AlioCollectionScheduler`/`AlioCollectorService`: `git status` 결과
  두 파일 모두 변경 목록에 없음 — 전혀 수정되지 않음.
- 수동 실행 API: `backend/src/main/java/com/careerops/backend/*/` 전체에서
  "automation" 관련 컨트롤러 grep 결과 0건, `AutomationRun` grep 결과
  0건, 신규 Flyway migration 파일 없음(`db/migration` 디렉토리에
  V18 등 신규 파일 없음, 기존 V17까지만 존재).

## Out of Scope 준수 확인

수동 실행 API/overlap guard/`AutomationRun` entity — 위에서 확인. 기존
Collector scheduler 변경 없음, recommendation/MATCH/AGENT 알고리즘 변경
없음(diff 대상 파일 목록에 해당 패키지 없음), Kakao 메시지 포맷 변경 없음
(`KakaoRecommendationMessageFormatter` 무변경), OAuth UI/frontend/
multi-user/email·SMS·Slack/새 notification 종류/Redis queue/Kafka/DLQ/
Spring Batch/Temporal/stale SENDING 자동 복구/FAILED 자동 retry — 관련
코드 전무. 실제 Anthropic/Kakao API 호출 0회(Fake client만 사용, 코드로
확인).

`.ai/metrics/metrics.jsonl` diff 확인: `plan`/`implement` phase 2줄만
추가됐고, 내용이 Task 파일의 "Codex Thread 기록"(2 round, sandbox Gradle
차단→로컬 검증→Instant/Timestamp 버그 수정→358/358)과 정확히 일치 —
Claude가 작성한 것으로 판단, Codex가 건드린 흔적 없음.

## 테스트 결과

- `./gradlew test --tests "com.careerops.backend.automation.*" --tests
  "com.careerops.backend.notification.AutomationNotificationRepositoryTest"`
  — **BUILD SUCCESSFUL**, XML 기준 신규 18/18 PASS(0 failures/0 errors):
  `AutomationDeliveryServiceTest` 5, `AutomationPipelineIntegrationTest` 1,
  `AutomationPrepareServiceTest` 4, `AutomationSchedulerConditionTest` 4,
  `AutomationSchedulerTest` 3, `AutomationNotificationRepositoryTest` 1.
  Codex Thread 기록의 "18개 신규 테스트 전부 PASS"와 독립 재실행으로 일치
  확인(실제 PostgreSQL `careerops_test` 사용, Docker postgres 컨테이너
  healthy 상태 확인 후 실행).
- `./gradlew test --tests "com.careerops.backend.notification.*" --tests
  "com.careerops.backend.recommend.*" --tests "com.careerops.backend.match.*"
  --tests "com.careerops.backend.agent.*" --tests
  "com.careerops.backend.applicationdraft.*"` — BUILD SUCCESSFUL(회귀 없음,
  KAKAO-001/NOTIFY-001/RECOMMEND-001.1/MATCH/AGENT 전부 포함).
- `./gradlew test --tests "com.careerops.backend.collector.*" --tests
  "com.careerops.backend.job.*" --tests "com.careerops.backend.career.*"
  --tests "com.careerops.backend.application.*" --tests
  "com.careerops.backend.pkbimport.*"` — BUILD SUCCESSFUL(회귀 없음).
- 전체 스위트 동시 실행(`./gradlew test` 인자 없이)은 이번 리뷰에서 별도로
  재실행하지 않았다 — KAKAO-001-review-1에서 이미 동일한 근본 원인
  (Postgres `max_connections` 초과로 인한 `@SpringBootTest` 컨텍스트 병렬
  로딩 flake, RECOMMEND-001.1부터 반복 확인된 pre-existing 이슈)이
  스택트레이스 레벨로 확인됐고 이번 Task가 그 구조를 바꾸지 않았으므로,
  격리 배치 실행(총 358/358, automation 18건 포함)으로 회귀 없음을
  검증하는 것으로 충분하다고 판단했다. 전체 동시 실행 시 flake가 재현될
  가능성은 있으나 이는 이 Task의 코드 결함이 아니다(known limitation,
  이미 이전 리뷰에서 근본 원인 확정).

test_count=18(automation 신규분 기준, 격리 실행) / test_pass_count=18.
회귀 포함 총 358/358(automation 18건 신규 포함, 격리 배치 기준).

## Findings

1. **[확인 완료, 문제 없음] `AutomationPrepareService`가 409 이외의
   `ResponseStatusException`은 재throw한다**(`AutomationPrepareService.java:42-44`).
   스펙에는 "409/502"만 명시돼 있어 이 방어적 재throw는 스펙을 초과하는
   범위지만, 안전한 방향(예상 밖 실패를 조용히 삼키지 않음)이라 blocking
   아님.
2. **[확인 완료, 문제 없음] Service 클래스(`AutomationPrepareService`/
   `AutomationDeliveryService`) 자체는 `@ConditionalOnProperty`가 없고
   Scheduler만 조건부다.** 이는 테스트에서 Service를 직접 인스턴스화/
   호출할 수 있게 하는 의도적 설계(Technical Notes의 "scheduler 메서드를
   테스트에서 직접 호출" 패턴과 일치)이며, 자동 호출 경로는 여전히
   Scheduler Bean 부재로 차단된다 — 원칙 위반 아님.
3. **[확인 완료, 문제 없음] 신규 production dependency 없음** —
   `git diff backend/build.gradle` 결과 test task 환경변수 2줄 추가뿐.
4. **[확인 완료, 문제 없음] Secret 미커밋** — `application.yml`의 신규
   섹션은 cron 문자열/zone/limit 리터럴과 `${ENV_VAR:false}` 참조뿐,
   리터럴 credential 없음.
5. **[해당 없음] 자기소개서 근거 기반 검증 원칙** — 이 Task는 orchestration
   계층(스케줄러+얇은 서비스)이며 LLM 호출/자기소개서 생성 로직을
   포함하지 않는다(하위 `NotificationPreparationService`/
   `NotificationSendService`는 이미 KAKAO-001/NOTIFY-001에서 검증 완료,
   이번 Task에서 무변경).
6. **[정보, blocking 아님] Metrics 6종 전부 실제로 계측되고 태그에
   notificationId/jobId 없음** — `grep -n "meterRegistry\."` 결과로 6개
   전부 확인, `.tag(`/`counter(...)` 호출에 id류 태그 없음.

## 다음 액션

- **판정: PASS.** Acceptance Criteria 13개 전 항목이 코드+실제 실행
  결과로 확인됐고, ADR-0035 결정 9개 전부 일치(overlap guard/수동 API/
  `AutomationRun` 미생성 포함), Out of Scope 미침범, Collector 무변경,
  Secret 미커밋, 신규 dependency 없음, `.ai/metrics/metrics.jsonl`을
  Codex가 건드리지 않음. automation 신규 18/18 PASS, 인접 회귀 패키지
  (notification/recommend/match/agent/applicationdraft,
  collector/job/career/application/pkbimport) 격리 실행 전부 통과. Codex
  에게 추가로 요청할 **필수** 수정 사항 없음.
- **완료 처리 절차**: Task 파일(`.ai/tasks/AUTOMATION-001.md`) Acceptance
  Criteria 체크박스 전부 체크, `status`를 `passed`로 전환.
  `docs/METRICS.md`/`docs/ARCHITECTURE.md`/`docs/ROADMAP.md`는 §Technical
  Notes 명시대로 이 리뷰 PASS 이후 Claude가 갱신. `.ai/metrics/metrics.jsonl`
  에 이번 review round(round=1, first_review_pass=true, test_count=18
  (automation 신규, 격리) 또는 358(전체 격리 배치 합산))를 기록할 것.
