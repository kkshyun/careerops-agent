---
task_id: KAKAO-001
review_round: 1
reviewer: claude (reviewer subagent)
reviewed_at: 2026-08-25T21:10:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] **atomic conditional UPDATE claim(PENDING/FAILED만), 동시 요청 2개 중
      provider 실제 호출 정확히 1회(실제 PostgreSQL 검증)** — 충족.
      `JobRecommendationNotificationRepository.claimForSending()`
      (`JobRecommendationNotificationRepository.java:34-40`)이
      `WHERE id=:id AND status IN ('PENDING','FAILED')` native UPDATE로
      `affectedRows`를 반환하고, `NotificationDeliveryTransactions.claim()`
      이 `==1`을 boolean으로 변환(`NotificationDeliveryTransactions.java:12`).
      `NotificationSendIntegrationTest
      .concurrentSendClaimsExactlyOnceAndProviderRunsWithoutTransactionOrHeldRowLock`
      가 `@SpringBootTest`+실제 Postgres+`ExecutorService`(2 threads)로 동일
      id에 대해 동시 send를 실행, `messages.calls.get()==1`을 단언. 직접
      재실행해 4/4 PASS 확인(아래 "테스트 결과" 참고).
- [x] **Kakao HTTP 호출 시점에 DB transaction 비활성**
      (`isActualTransactionActive()` 캡처) — 충족.
      `NotificationSendService.send()`(`NotificationSendService.java:24-56`)
      자체는 `@Transactional`이 아니고, DB 접근은
      `NotificationDeliveryTransactions`의 개별 `@Transactional` 메서드
      (`claim`/`snapshot`/`sent`/`failed`)로만 분리돼 각자 짧게 열렸다
      닫힌다 — `tokenClient.refresh()`/`messageClient.sendToMe()` 호출은
      이 메서드들 사이(트랜잭션 밖)에서 실행되는 구조. 통합 테스트의
      `CapturingMessageClient`/`CapturingTokenClient`가 각 호출 시점에
      `TransactionSynchronizationManager.isActualTransactionActive()`를
      캡처해 `false`임을 단언(`NotificationSendIntegrationTest.java:39`,
      `100-109`). ADR-0034 결정 6과 정확히 일치.
- [x] **성공 시 SENDING→SENT, `sentAt` 저장, 200 응답 계약** — 충족.
      `NotificationSendService.send()`가 성공 경로에서
      `transactions.sent(id, sentAt)` 호출 후
      `NotificationSendResponse(id, SENT, sentAt, jobId)` 반환
      (`NotificationSendService.java:49-52`). `markSent()`가 `WHERE
      status='SENDING'` 조건부 UPDATE(`JobRecommendationNotificationRepository.java:42-48`).
      컨트롤러 테스트 `sendSuccessReturnsContract`가 JSON 필드
      4개(`notificationId`/`status`/`sentAt`/`jobId`) + `accessToken`/
      `result_code` 미노출을 단언.
- [x] **provider 실패/timeout/token 실패/sourceUrl null → SENDING→FAILED +
      failureCode가 commit된 뒤 502 반환(트랜잭션 롤백으로 유실 안 됨)** —
      충족. `NotificationSendService.fail()`이 `transactions.failed(...)`
      를 먼저 호출(독립 `@Transactional` 커밋)한 뒤에야
      `KakaoDeliveryException`을 throw(`NotificationSendService.java:57-65`).
      `NotificationSendIntegrationTest
      .everyProviderAndTokenFailureIsCommittedBeforeBadGatewayAndFailedCanRetry`
      가 PROVIDER_ERROR/PROVIDER_5XX/DELIVERY_UNKNOWN/TOKEN_REFRESH_FAILED
      4가지 전부에서 실제 DB 조회로 FAILED+failureCode 커밋을 확인, 이후
      재시도 성공까지 검증. `RestClientKakaoMessageClient`가
      `ResourceAccessException`(timeout)→`DELIVERY_UNKNOWN`,
      4xx→`PROVIDER_ERROR`, 5xx→`PROVIDER_5XX`,
      `result_code!=0`→`PROVIDER_ERROR`로 정확히 분기
      (`RestClientKakaoMessageClient.java:36-53`).
- [x] **존재하지 않는 id → 404, provider 미호출** — 충족.
      `send()`: claim 실패 시 `snapshot(id)`가 `Optional.empty()`면
      `ResponseStatusException(NOT_FOUND)`(`NotificationSendService.java:27-28`).
      `NotificationSendServiceTest.missingNotificationIs404AndAlreadyClaimedIs409`
      + `verifyNoInteractions(tokens, messages)`로 확인.
- [x] **SENT 재요청 → 409, provider 미호출, 상태 불변** — 충족(위 테스트,
      claim 목록에 SENT 미포함이라 claim 실패 → snapshot 존재 → 409).
- [x] **SENDING 재요청(동시 시나리오) → 409, provider 미호출** — 충족.
      `concurrentSendClaimsExactlyOnceAndProviderRunsWithoutTransactionOrHeldRowLock`
      의 `second` 요청이 `ResponseStatusException(CONFLICT)`로 확인, 실제
      DB 동시성 테스트로 커버.
- [x] **FAILED 재요청 → 재claim되어 재시도, 성공 시 SENT 전이** — 충족.
      `claimForSending` WHERE 절에 `'FAILED'` 포함, `/retry` 전용 endpoint
      없음(§Out of Scope 준수). `failedNotificationCanBeClaimedAndRetried`
      + 통합 테스트 `everyProviderAndTokenFailureIs...` 마지막 구간에서
      `tokens.failure=false` 후 `service.send()` → SENT 확인.
- [x] **메시지 텍스트가 DB 값만 사용, 없는 정보 생성 안 함** — 충족.
      `KakaoRecommendationMessageFormatter.format()`은 순수 함수, 입력이
      `NotificationSendSnapshot`(DB JOIN projection)뿐이고 LLM/외부 호출
      없음(`KakaoRecommendationMessageFormatter.java` 전체 확인). 자기소개서
      관련 "근거 기반 검증" 원칙과 같은 결의 요구사항이며 위반 없음.
      `usesOnlySnapshotValuesDeterministically`가 companyName/title/
      applicationEndAt/recommendationScore/reason 전부 포함 + 동일 입력→
      동일 출력(determinism)을 확인.
- [x] **200자 truncate가 정확한 경계에서 적용** — 충족.
      `truncatesReasonAtExactTwoHundredCharacterBoundary`가 `hasSize(200)`
      + reason 299 vs 300자 입력 시 앞 199자가 완전히 동일함을 확인(char
      단위 truncate 경계 검증). `format()` 로직
      (`KakaoRecommendationMessageFormatter.java:8-16`)이 fixed 파트 먼저
      채우고 남는 예산만큼 `reason.substring(0, min(...))`으로 계산 —
      명세와 정확히 일치.
- [x] **refresh_token rotation: 응답에 있으면 교체, 없으면 유지** — 충족.
      `KakaoTokenStore.rotateIfPresent()`가 `newToken==null||isBlank()`이면
      즉시 return(no-op), 아니면 `token.rotate(...)`
      (`KakaoTokenStore.java:25-30`). 통합 테스트
      `refreshRotationIsPersistedAndMissingRotationKeepsCurrentValue`가
      1차 rotate 값이 2차(응답에 refresh_token 없음) 이후에도 유지됨을
      실제 DB 조회로 확인.
- [x] **로그에 access_token/refresh_token/client_secret/메시지 본문 원문
      미노출** — 충족. `NotificationSendServiceTest.logsNeverContainSecretsOrMessageBody`
      가 `ListAppender`로 `sensitive-access`/`sensitive-refresh`/민감
      회사명·제목·메시지본문이 로그에 없음을 확인.
      `NotificationSendService`의 `log.info`/`log.warn` 라인
      (`NotificationSendService.java:51,61-62`)은 notificationId/jobId/
      success/failureCode만 포함 — 코드 직접 확인으로도 일치.
      `RestClientKakaoMessageClient`/`RestClientKakaoTokenClient`에는 로그
      호출 자체가 없음(원문 body/토큰이 예외 메시지에도 담기지 않음, HTTP
      status 코드만 포함).
- [x] **3개 메트릭 정상 계측** — 충족.
      `careerops.kakao.send.request`(result 태그, success/provider_error/
      provider_5xx/delivery_unknown/token_refresh_failed/invalid_message_data
      전부 코드에 존재 — `KakaoApiException.Reason.metricTag()` +
      `fail()`의 하드코딩 `"invalid_message_data"`),
      `careerops.kakao.send.duration`(Timer, claim부터 최종 전이까지 —
      `Timer.Sample duration = Timer.start(meters)`가 claim 성공 직후
      시작, `stop(duration)`이 성공/실패 각 분기에서 호출),
      `careerops.kakao.token.refresh`(success/failure). 단위 테스트에서
      success/duration/token.refresh 계측을 직접 assert, `invalid_message_data`/
      나머지 result 태그는 코드 경로상 자명하게 도달(전용 counter 값 assert는
      없으나 blocking 아님 — 아래 Findings #1).
- [x] **`./gradlew test` 전체 통과(기존 패키지 회귀 없음)** — **격리 기준
      충족, 전체 동시 실행 시 pre-existing flake 재확인**(아래 "테스트
      결과" 상세, KAKAO-001 코드 결함 아님 — 원인이 정확히 동일한 Postgres
      `too many clients already`임을 stacktrace로 직접 재확인).
- [x] **자동 테스트 전체가 Fake client만 사용, 실제 Kakao/Anthropic 미호출** —
      충족. `NotificationSendServiceTest`는 Mockito mock, `NotificationSendIntegrationTest`는
      `@TestConfiguration`+`@Primary`로 `CapturingMessageClient`/
      `CapturingTokenClient`(둘 다 실제 HTTP 호출 없음)로 `RestClientKakaoMessageClient`/
      `RestClientKakaoTokenClient` production bean을 완전히 대체
      (`NotificationSendIntegrationTest.java:92-95`). production
      `RestClient*` impl은 `@SpringBootTest` 컨텍스트에 로드되지만 실제로
      호출되지 않음 — grep 결과 테스트 코드 어디에도
      `kapi.kakao.com`/`kauth.kakao.com` 관련 실제 URL 호출 없음.

## ADR-0034 일치 여부

결정 1(access_token 미저장/매 요청 refresh)/2(plaintext 저장, 암호화 계층
없음)/3(OAuth connect endpoint 없음, `.env` seed)/4(rotation 규칙)/5(SENDING
atomic claim, FAILED도 claim 대상)/6(트랜잭션 경계 분리)/7(FAILED commit 후
502) 전부 코드로 직접 확인, 위반 없음. `KakaoOauthToken`에 `access_token`
컬럼 자체가 없음(migration+entity 둘 다 확인), 암호화 관련 dependency
추가 없음.

## Out of Scope 준수 확인

친구 메시지/Friends picker/Kakao Channel/알림톡/비즈메시지 — 관련 코드
없음(grep 결과 없음). Custom 템플릿(`template_id`) — `RestClientKakaoMessageClient`가
`object_type=text`(Default Text) 고정, `template_id` 필드 없음. 전용
`/retry` endpoint — 컨트롤러에 `/send` 1개 endpoint만 추가됨
(`JobRecommendationNotificationController.java:39-40`). batch/전체 발송
endpoint — 없음. scheduler 연동 — `com.careerops.backend.scheduler` 하위
grep 결과 kakao 관련 코드 없음. access_token DB 캐싱 — `KakaoOauthToken`
엔티티에 access_token 필드 자체 없음, `KakaoTokenStore`는 refresh_token만
다룸. Redis/message queue/distributed lock — 신규 미도입.

## 테스트 결과

- `./gradlew test --tests "com.careerops.backend.notification.*"` —
  **BUILD SUCCESSFUL**, XML 기준 48/48 PASS (0 failures/errors):
  `JobRecommendationNotificationControllerTest` 11,
  `JobRecommendationNotificationDatabaseTest` 2,
  `KakaoRecommendationMessageFormatterTest` 2,
  `NotificationPreparationServiceTest` 21,
  `NotificationSendIntegrationTest` 4,
  `NotificationSendServiceTest` 8. Codex Thread 기록의 "48/48"과 독립
  재실행으로 일치 확인.
- `./gradlew test --tests "com.careerops.backend.recommend.*" --tests
  "com.careerops.backend.match.*" --tests "com.careerops.backend.agent.*"
  --tests "com.careerops.backend.applicationdraft.*"` — BUILD SUCCESSFUL
  (회귀 없음).
- `./gradlew test --tests "com.careerops.backend.collector.*" --tests
  "com.careerops.backend.job.*" --tests "com.careerops.backend.career.*"
  --tests "com.careerops.backend.application.*" --tests
  "com.careerops.backend.pkbimport.*"` — BUILD SUCCESSFUL(회귀 없음).
- `cd backend && ./gradlew test`(전체, 배치 분리 없이) — **340 tests
  completed, 24 failed**. 실패 클래스: `NotificationSendIntegrationTest`
  (4/4 실패, KAKAO-001 신규 테스트), `ImportBatchControllerTest`(8),
  `ImportBatchExtractionServiceTest`(6),
  `MultipartUploadLimitIntegrationTest`(1),
  `JobRecommendationControllerTest`(4),
  `JobRecommendationTransactionIntegrationTest`(1). **6개 클래스 전부의
  실패 스택트레이스를 직접 열어 확인한 결과, 예외 없이 동일한 근본
  원인**: `FlywayAutoConfiguration` → `HikariPool-N - Connection is not
  available, request timed out ... SQL State: 53300 ... FATAL: sorry, too
  many clients already`(Postgres `max_connections` 초과로 인한
  `ApplicationContext` 로딩 자체 실패). `NotificationSendIntegrationTest`
  도 예외가 아니라 정확히 이 패턴에 포함됨 — KAKAO-001 고유의 동시성/
  트랜잭션 버그가 아니라 이 저장소 전반에 이미 존재하는
  `@SpringBootTest` 컨텍스트 병렬 로딩 flake(RECOMMEND-001.1에서 문서화된
  패턴과 스택트레이스까지 동일)임을 이번 리뷰에서 독립적으로 재확인했다.
  같은 6개 클래스만 다시 배치 실행(`--tests
  "com.careerops.backend.notification.*" --tests
  "com.careerops.backend.pkbimport.*" --tests
  "com.careerops.backend.recommend.*"`)한 결과 BUILD SUCCESSFUL(전부
  통과) — 격리 시 항상 통과한다는 주장과도 일치. **Codex/Claude의 flake
  판단은 타당하다고 결론.**

## Findings

1. **[정보, blocking 아님] `invalid_message_data`/`provider_5xx`/
   `delivery_unknown`/`token_refresh_failed` 각 result 태그 값에 대한
   전용 counter assert 테스트가 없다.** `success`/`failure`(token.refresh)
   /`duration`만 직접 `meters.get(...).counter().count()`로 검증되고,
   나머지 result 태그는 코드 경로상 도달이 보장되지만(각 실패 분기가
   `meters.counter("careerops.kakao.send.request", "result",
   exception.reason().metricTag())` 또는 `"invalid_message_data"`를 항상
   거침) 메트릭 값 자체를 단언하는 테스트는 없다. AC는 "3개 메트릭이
   정상 계측된다"까지만 요구하고 태그별 전수 검증까지 요구하지는 않아
   충족으로 판단했으나, 후속에서 촘촘히 하고 싶다면 각 result 태그별
   counter assert를 추가하면 더 좋다(재작업 요청 아님).
2. **[확인 완료, 문제 없음] `KakaoTokenStore.currentRefreshToken()`의
   `synchronized` 키워드.** 인스턴스 레벨 lock이 `@Transactional` DB
   read/possible-seed-write 구간에만 걸리고 실제 HTTP 호출(`tokenClient.refresh`)
   은 lock 밖에서 일어나 ADR-0034 결정 6(외부 I/O 중 DB 자원 점유 금지)과
   충돌하지 않는다. 개인 프로젝트 저빈도 트래픽에서 이 정도 동기화는
   과잉설계가 아니라고 판단.
3. **[확인 완료, 문제 없음] `.ai/metrics/metrics.jsonl` diff는 `plan`/
   `implement` phase 2줄만 추가됐고 내용이 Task 파일의 "Codex Thread
   기록" 표(3 round, compileTestJava 에러→Jackson 버그→48/48 PASS)와
   정확히 일치 — Claude가 작성한 것으로 판단, Codex가 건드린 흔적 없음.**
4. **[확인 완료, 문제 없음] 신규 production dependency 없음** —
   `git diff backend/build.gradle` 결과 없음(`RestClient`는 기존 ALIO
   client에서 이미 사용 중인 Spring Boot Starter Web 포함 기능).
5. **[확인 완료, 문제 없음] Secret 미커밋** — `.env.example`에는 키
   이름만(값 없음), `.env`는 `.gitignore`에 이미 등록됨, `application.yml`
   은 전부 `${ENV_VAR:}` 참조뿐 리터럴 값 없음.
6. **[해당 없음] 자기소개서 근거 기반 검증 원칙** — 이 Task는 채용공고
   추천 알림 발송이며, `KakaoRecommendationMessageFormatter`가 DB 실제
   값(companyName/title/applicationEndAt/reason/recommendationScore)만
   조합할 뿐 LLM 호출이나 사용자가 제공하지 않은 정보 생성이 없음(코드
   전체 확인).
7. **[정보, blocking 아님] 코드 스타일이 다소 압축적(한 줄에 여러 필드
   선언/세미콜론 나열, 예: `NotificationSendService.java:15-16`,
   `NotificationDeliveryTransactions.java` 전체).** 기존 저장소 컨벤션과
   비교해도 다소 밀도가 높지만 가독성이 심각하게 훼손되는 수준은 아니고,
   기능/테스트에는 영향 없음 — 필수 수정 요청 아님.

## 다음 액션

- **판정: PASS.** Acceptance Criteria 전 항목이 코드+실제 실행 결과로
  확인됐고, ADR-0034 결정 7개 전부 일치, Out of Scope 미침범, Secret
  미커밋, 신규 dependency 없음. notification 패키지 48/48, 인접 회귀
  패키지(recommend/match/agent/applicationdraft, collector/job/career/
  application/pkbimport) 격리 실행 전부 통과. 전체 스위트 동시 실행 시
  관찰되는 24건 실패는 이번 리뷰에서 스택트레이스를 직접 열어 6개 클래스
  전부 동일한 Postgres `too many clients already` 원인임을 재확인했고,
  같은 6개 클래스를 격리 재실행하면 전부 통과함을 직접 검증했다 —
  KAKAO-001 코드 결함이 아니라는 판단에 동의한다. Codex에게 추가로
  요청할 **필수** 수정 사항 없음.
- **권장(선택, blocking 아님)**: Finding #1(메트릭 result 태그별 전수
  assert 테스트 보강)은 완료 처리를 막지 않는 선택 사항이다.
- **완료 처리 절차**: Task 파일(`.ai/tasks/KAKAO-001.md`)의 자동
  Acceptance Criteria 체크박스 전부 체크, `status`를 실제 Kakao E2E
  대기 상태로 전환. §Technical Notes의 사용자 수동 Kakao Developers 콘솔
  설정(REST API key/Client Secret/초기 refresh_token 발급 후
  `.env`(`CAREEROPS_KAKAO_INITIAL_REFRESH_TOKEN` 등) 입력)이 완료돼야
  §Test Plan의 실제 Kakao E2E(최대 1회, 사용자 명시 승인 후)를 진행할 수
  있다. `.ai/metrics/metrics.jsonl`에 이번 review round(round=1,
  first_review_pass=true, test_count=48(notification 패키지) 또는 전체
  340(격리 배치 합산) 기준)를 기록할 것.
