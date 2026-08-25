---
task_id: KAKAO-001
title: 채용공고 추천 알림 카카오톡 발송 — Send-to-me, SENDING atomic claim, refresh-token 기반 OAuth
phase: review
planned_by: claude
implemented_by: codex
status: passed_pending_real_e2e
created_at: 2026-08-25T00:00:00+09:00
codex_thread_id: 01a038ac-dd8c-7602-b58c-cb71cc7de2af
---

## Context

NOTIFY-001(ADR-0032)은 RECOMMEND-001의 추천 결과 중 아직 알리지 않은 공고를
`JobRecommendationNotification`(status=PENDING)으로 저장하는 것까지만 했다.
실제 메시지 전송은 명시적으로 후속 Phase로 미뤄졌다(`NotificationStatus`에
SENT/FAILED는 정의만 해두고 전이 로직/컬럼은 없음).

KAKAO-001은 이 PENDING notification을 사용자 본인의 카카오톡 "나와의 채팅방"
(Send-to-me)으로 전송하고, 결과에 따라 SENT 또는 FAILED로 상태를 관리한다.
**추천을 새로 계산하지 않는다** — 기존 PENDING notification만 소비한다.

설계 조사(architect subagent, 2026-08-25, developers.kakao.com 공식 문서
기준)에서 확인한 핵심 사실:

- Send-to-me(`POST https://kapi.kakao.com/v2/api/talk/memo/default/send`)는
  완전 무료이고 비즈니스 심사가 필요 없으며, 확인된 범위에서 일간/월간 quota가
  없다. 유료인 것은 카카오톡 "공유"(Kakao Talk Share, 프론트엔드 SDK 전용,
  REST API 미지원) 쪽이며 이번 기능과 무관하다.
- **공식 idempotency 메커니즘이 없다** — network timeout 시 자동 재전송하면
  중복 발송 위험이 그대로 남는다. 따라서 자동 retry를 도입하지 않고, timeout은
  `DELIVERY_UNKNOWN`으로 명시적으로 구분한다. 이 시스템의 delivery semantics는
  "요청 1회당 최대 1회의 능동적 전송 시도"(at-most-one-active-attempt)이지
  exactly-once가 아니다.
- refresh_token 응답 rotation 규칙: 남은 유효기간이 1개월 미만일 때만 새
  `refresh_token`이 응답에 포함된다 — 있으면 교체, 없으면 기존 값 유지.
- 이 프로젝트의 외부 REST client 컨벤션은 `WebClient`가 아니라 **`RestClient`**
  다(`RestClientAlioJobClient` 패턴, `AlioJobClient` interface + production
  impl 분리 + 전용 `XxxApiException(Reason)`).

상세 근거는 ADR-0034 참고.

## Scope

### OAuth token lifecycle

- app credential(`CAREEROPS_KAKAO_REST_API_KEY`, `CAREEROPS_KAKAO_CLIENT_SECRET`)
  은 `.env`/`application.yml`(`careerops.kakao.*`)로만 관리, DB 저장/git commit
  금지.
- 신규 `kakao_oauth_token` 테이블(사실상 singleton, 0~1행)에 `refresh_token`
  과 `refresh_token_expires_at`(nullable — 초기 seed 시점엔 정확한 만료 시각을
  알 수 없으므로 정보성으로만 사용, 어떤 분기 로직도 이 값에 의존하지 않는다),
  `updated_at`만 저장한다. **access_token은 DB에 저장하지 않는다** — 매 전송
  요청마다 `grant_type=refresh_token`으로 즉시 새 access_token을 발급받아 그
  자리에서만 쓰고 버린다(캐싱/만료 추적 로직 자체를 없앤다).
- 최초 부트스트랩: `CAREEROPS_KAKAO_INITIAL_REFRESH_TOKEN`(.env, 1회성 seed
  값)이 설정돼 있고 `kakao_oauth_token`에 행이 하나도 없으면, 앱 기동 시(또는
  최초 send 요청 시) 이 값으로 1행을 생성한다. 이후로는 DB만 신뢰하고 이 env
  값은 무시한다(재부팅 시 DB row가 이미 있으면 seed는 아무 것도 하지 않음).
  OAuth connect endpoint(authorize redirect/callback)는 만들지 않는다 — 사용자가
  Kakao Developers 콘솔에서 최초 1회 수동으로 refresh_token을 발급받아
  `.env`에 입력하는 절차를 문서로 안내한다(§Technical Notes).
- refresh 응답에 새 `refresh_token`이 있으면 DB 값을 원자적으로 교체, 없으면
  기존 값 유지.

### 상태 전이 및 concurrency

- `NotificationStatus`에 `SENDING` 추가(PENDING/SENDING/SENT/FAILED).
- atomic conditional UPDATE로 claim: `UPDATE ... SET status='SENDING',
  last_attempt_at=now() WHERE id=:id AND status IN ('PENDING','FAILED')`.
  `affectedRows==1`인 요청만 실제로 Kakao를 호출한다 — **FAILED도 재요청 시
  다시 claim 가능**(전용 `/retry` endpoint를 따로 만들지 않는다).
- Kakao HTTP 호출(token refresh + 메시지 전송)은 어떤 DB 트랜잭션도 열려있지
  않은 상태에서 실행한다. 호출 전후로 각각 짧은 트랜잭션(claim, 최종 상태 전이)
  만 존재한다 — ADR-0032가 확립한 경계 원칙을 그대로 계승.
- 최종 상태 전이도 조건부 UPDATE(`WHERE status='SENDING'`)로 수행한다:
  성공 시 `SENDING→SENT`(+`sent_at`), 실패 시 `SENDING→FAILED`(+`failure_code`).
- `SENT`/`SENDING` 상태에 대한 재요청은 409(변경 없음, provider 미호출).
- crash로 `SENDING`에 영구히 멈추는 경우 이번 Phase에서 자동 복구 worker를
  만들지 않는다(known limitation, §Out of Scope).

### 메시지 구성

- `KakaoRecommendationMessageFormatter`: pure/deterministic, LLM 미사용.
  입력은 notification snapshot(`recommendationScore`, `reason`)과 `JobPosting`
  DB 실제값(`companyName`, `title`, `applicationEndAt`, `sourceUrl`)뿐 — 없는
  정보를 생성하지 않는다.
- Kakao Default **Text** 템플릿만 사용(`object_type=text`, `text`≤200자,
  `link.web_url`=`JobPosting.sourceUrl`, 이번 Task에서 버튼은 추가하지 않는다).
  `link`는 Kakao API상 필수 파라미터이므로 `sourceUrl`이 null인 JobPosting은
  provider를 호출하지 않고 `failureCode=INVALID_MESSAGE_DATA`로 즉시 FAILED
  처리한다.
- 총 200자 예산 안에서 고정 부분(회사명/제목/마감일 레이블)을 먼저 채우고
  남는 예산만큼 `reason`을 `String.length()`(Java char) 기준으로 truncate한다.

### Kakao client 추상화

- `KakaoMessageClient`(interface) / `RestClientKakaoMessageClient`(impl,
  `RestClient.Builder` 주입) — `sendToMe(accessToken, text, linkUrl)`,
  실패 시 `KakaoApiException(Reason)` 던짐(`Reason`:
  `PROVIDER_ERROR`/`PROVIDER_5XX`/`DELIVERY_UNKNOWN`).
- `KakaoTokenClient`(interface) / `RestClientKakaoTokenClient`(impl) —
  `refresh(refreshToken)` → `KakaoTokenRefreshResult(accessToken,
  newRefreshTokenOrNull, newRefreshTokenExpiresAtOrNull)`, 실패 시
  `KakaoApiException(Reason.TOKEN_REFRESH_FAILED)`.
- Service(`NotificationSendService`)는 `RestClient`/`WebClient`를 직접 쓰지
  않는다 — 반드시 위 두 interface를 통해서만 호출.

### API

- `POST /api/notifications/job-recommendations/{id}/send` (단건만, body 없음).
  - 200: `{ notificationId, status: "SENT", sentAt, jobId }`.
  - 404: notification id 없음.
  - 409: 이미 `SENT` 또는 현재 `SENDING`.
  - 502: provider/token 실패로 `FAILED` 전이(빈 body, 기존
    `JobRecommendationException`→502 패턴과 동일). **FAILED 상태는 반드시
    DB에 commit된 뒤에** 502 예외가 컨트롤러로 전파된다(짧은 상태 전이
    트랜잭션이 먼저 끝남 — 롤백으로 FAILED가 유실되는 실수 방지가 이번
    Task의 핵심 검증 대상).
  - batch/전체 발송 endpoint는 만들지 않는다(§Out of Scope).
- 기존 `GET .../job-recommendations?status=PENDING`, prepare API는 변경 없음.

### Metrics/로깅

- `careerops.kakao.send.request`(Counter, `result`=`success`|`provider_error`|
  `provider_5xx`|`delivery_unknown`|`token_refresh_failed`|
  `invalid_message_data`, claim 성공 후 실제 attempt에 대해서만 계측 — 404/409는
  단순 HTTP 검증이라 계측하지 않는다).
- `careerops.kakao.send.duration`(Timer, claim부터 최종 상태 전이까지).
- `careerops.kakao.token.refresh`(Counter, `result`=`success`|`failure`).
- 로그 금지: `Authorization` 헤더, access_token/refresh_token 값 전체,
  `template_object`/메시지 본문(reason/companyName/title 포함), Kakao 원문
  에러 응답 바디 전체.
- 로그 허용: notificationId, jobId, 성공/실패, `failureCode`, HTTP status,
  latency, "token refreshed: true/false"(boolean만).

## Out of Scope

친구에게 보내기, 친구 목록 조회, Friends picker, Kakao Channel, 알림톡,
비즈메시지, 다중 사용자 발송, recipient UUID, Kakao Talk Share, OAuth
connect endpoint(authorize redirect/callback 구현), custom Kakao 템플릿
(template_id 등록), 전용 `/retry` endpoint(FAILED는 `/send` 재호출로 처리),
batch/전체 발송 endpoint, scheduler, 자동 주기 발송, deadline reminder,
ApplicationStage reminder, SENDING crash에 대한 자동 recovery worker/lease/
sweeper, 자동 network-timeout retry, SMS/email/frontend, multi-user 설계,
Redis, message queue, distributed lock, AGENT/MATCH/RECOMMEND 재호출,
access_token DB 캐싱. AUTOMATION-001/KAKAO-002는 후속 Phase.

이번 Task 전체에서 실제 Anthropic API 호출 0회, 실제 Kakao API 호출은
자동 테스트에서 0회(Fake client만 사용) — 실제 Kakao E2E는 전체 자동 검증
PASS 후 사용자가 명시적으로 승인할 때 최대 1회만 수행한다(§Test Plan 실제
E2E 절 참고).

## Acceptance Criteria

- [x] `POST .../{id}/send`가 PENDING 또는 FAILED notification을 atomic
      conditional UPDATE로 SENDING claim한다(동시 요청 2개 중 provider 실제
      호출은 정확히 1회, 실제 PostgreSQL로 검증).
- [x] Kakao 메시지/토큰 HTTP 호출 시점에 DB transaction이 active하지 않다
      (`TransactionSynchronizationManager.isActualTransactionActive()` 캡처
      패턴으로 검증).
- [x] 성공 시 `SENDING→SENT`, `sentAt` 저장, 응답 200 `{notificationId,
      status:"SENT", sentAt, jobId}`.
- [x] provider 실패(4xx/5xx/result_code!=0)/timeout/token refresh 실패/
      `sourceUrl` null 각각에서 `SENDING→FAILED` + 해당 `failureCode`가
      **commit된 뒤** 502가 반환된다(트랜잭션 롤백으로 FAILED가 유실되지
      않음을 자동 테스트로 증명).
- [x] 존재하지 않는 notification id → 404, provider 미호출.
- [x] 이미 `SENT`인 notification 재요청 → 409, provider 미호출, 상태 불변.
- [x] `SENDING`인 notification 재요청(동시 요청 시나리오) → 409, provider
      미호출.
- [x] `FAILED` notification 재요청 → 다시 claim되어 재시도되고, 성공하면
      SENT로 전이된다.
- [x] 메시지 텍스트가 DB 값(`companyName`/`title`/`applicationEndAt`/
      `reason`/`recommendationScore`)만 사용하고, 없는 정보를 생성하지
      않는다(formatter 단위 테스트로 증명).
- [x] reason이 길어 총 200자를 초과하면 server-side deterministic
      truncate가 정확한 경계에서 적용된다.
- [x] refresh 응답에 새 `refresh_token`이 있으면 DB가 교체되고, 없으면
      기존 값이 유지된다.
- [x] 로그에 access_token/refresh_token/client_secret/메시지 본문 원문이
      노출되지 않는다(`ListAppender` 기반 privacy 테스트).
- [x] 3개 메트릭이 정상 계측된다.
- [x] `./gradlew test` 전체 통과, 기존 NOTIFY-001/RECOMMEND-001.1/MATCH/
      AGENT/collector/job/career/application/pkbimport 회귀 없음.
- [x] 자동 테스트 전체가 `FakeKakaoMessageClient`/`FakeKakaoTokenClient`만
      사용하고 실제 Kakao/Anthropic API를 호출하지 않는다.

## Technical Notes

- 참고 구현 패턴: `RestClientAlioJobClient`/`AlioJobClient`/`AlioApiException`
  (외부 REST client interface+impl+전용 exception 분리, 이번 Task의 1차
  참고 선례 — 원 설계 조사에서 언급됐던 "WebClient 선례"는 실재하지 않으며
  실제 컨벤션은 `RestClient`다), `JobApplicationService.create()`(존재 확인
  → 시도 → `DataIntegrityViolationException` catch → 409 패턴),
  `JobRecommendationNotificationRepository.search()`(JOIN 생성자 표현식으로
  N+1 없이 snapshot 조회), `JobRecommendationTransactionIntegrationTest`
  (`TransactionSynchronizationManager.isActualTransactionActive()` 캡처용
  `@TestConfiguration`+`@Primary` 대체 빈 패턴), `AlioCollectorConcurrencyTest`
  /`ImportCandidateConcurrencyTest`(실제 PostgreSQL 동시성 테스트 패턴).
- claim 이후 메시지 구성에 필요한 `JobPosting` 필드(`companyName`/`title`/
  `applicationEndAt`/`sourceUrl`)는 `JobRecommendationNotification.jobPosting`
  이 `FetchType.LAZY`이므로, claim과는 별도로(트랜잭션 밖에서 lazy 접근 시
  `LazyInitializationException`을 피하기 위해) `search()`와 동일한 JOIN
  생성자 표현식 projection(`findSnapshotById`)으로 짧은 트랜잭션 안에서
  eager 조회한다.
- migration 초안(Codex가 실제 최신 V번호로 파일명 확정, 현재 최신은 V16
  — 절대 기존 migration 수정 금지):
  ```sql
  ALTER TABLE job_recommendation_notifications
      ADD COLUMN sent_at TIMESTAMP(6) WITH TIME ZONE,
      ADD COLUMN last_attempt_at TIMESTAMP(6) WITH TIME ZONE,
      ADD COLUMN failure_code VARCHAR(40);

  CREATE TABLE kakao_oauth_token (
      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      refresh_token VARCHAR(500) NOT NULL,
      refresh_token_expires_at TIMESTAMP(6) WITH TIME ZONE,
      updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
  );
  ```
  `kakao_oauth_token`은 singleton 성격이지만(사용자 1인), 이번 규모에서
  `CHECK (id = 1)` 같은 강제 제약을 추가하지 않는다 — 애플리케이션 레벨에서
  "행이 없으면 seed, 있으면 그 1행만 사용"으로 충분하다(과잉설계 방지).
- `application.yml`에 `careerops.kakao` 섹션 추가:
  ```yaml
  careerops:
    kakao:
      rest-api-key: ${CAREEROPS_KAKAO_REST_API_KEY:}
      client-secret: ${CAREEROPS_KAKAO_CLIENT_SECRET:}
      initial-refresh-token: ${CAREEROPS_KAKAO_INITIAL_REFRESH_TOKEN:}
      auth-base-url: https://kauth.kakao.com
      api-base-url: https://kapi.kakao.com
      connect-timeout-seconds: 5
      request-timeout-seconds: 10
  ```
  `.env.example`에 `CAREEROPS_KAKAO_REST_API_KEY=`,
  `CAREEROPS_KAKAO_CLIENT_SECRET=`, `CAREEROPS_KAKAO_INITIAL_REFRESH_TOKEN=`
  키 이름만 추가(값 없음).
- 신규 production dependency 없음(`RestClient`는 이미 Spring Boot Starter
  Web에 포함, ALIO에서 이미 사용 중).
- 컨트롤러에 로컬 `@ExceptionHandler(KakaoDeliveryException.class)` →
  `@ResponseStatus(BAD_GATEWAY)`(빈 body) 추가 필요(전역 `@ControllerAdvice`
  없음, 컨트롤러마다 개별 등록 컨벤션 유지). 기존 `ResponseStatusException`
  핸들러가 404/409를 그대로 커버한다.
- **사용자 수동 설정(Kakao Developers 콘솔, 코드 구현과 별개로 필요)**:
  (1) 앱 생성, (2) 카카오 로그인 활성화 + Redirect URI 등록, (3) 카카오
  로그인 > 동의항목에서 "카카오톡 메시지 전송(talk_message)" 활성화,
  (4) 보안에서 Client Secret 발급 및 사용 설정, (5) REST API 테스트 도구로
  최초 1회 로그인해 authorization code → `/oauth/token` 수동 호출로 초기
  refresh_token 확인, (6) 그 값을 로컬 `.env`의
  `CAREEROPS_KAKAO_INITIAL_REFRESH_TOKEN`에 입력. 이 단계는 Codex 구현과
  무관하게 사용자가 직접 수행해야 하며, 실제 Kakao E2E 전에 완료돼 있어야
  한다(값 자체를 채팅에 붙여넣지 않도록 안내).

## Test Plan

Fake/mock `KakaoMessageClient`/`KakaoTokenClient` 사용(실제 Kakao/Anthropic
API 자동 테스트에서 미호출).

**Service 단위**: 정상 send→SENT/`sentAt`, provider 실패(4xx)→FAILED
`PROVIDER_ERROR`, provider 5xx→FAILED `PROVIDER_5XX`, timeout→FAILED
`DELIVERY_UNKNOWN`, refresh 실패→FAILED `TOKEN_REFRESH_FAILED`(메시지
API 미호출), `sourceUrl` null→FAILED `INVALID_MESSAGE_DATA`(provider
미호출), SENT 재요청→409/미호출/상태불변, SENDING 재요청→409/미호출,
FAILED 재요청→재시도 성공 가능, 존재하지 않는 id→404, 메시지 내용이
DB 값만 사용(하드코딩/LLM 없음 증명), reason truncate 경계값, refresh
응답에 새 refresh_token 있음→DB 교체, 없음→DB 유지, FAILED 커밋 후 502
전파 순서(트랜잭션 캡처로 검증).

**Controller MockMvc**: 200/404/409/502 각 케이스, 응답 DTO 필드 검증
(provider `result_code`/token 값 미노출).

**DB-level 실제 PostgreSQL**: 동시 send 2건(동일 id, `CyclicBarrier`)→
provider 실제 호출 정확히 1회, 최종 상태 정확, claim 동안 row lock을
유지하지 않음(Kakao 호출 중 다른 커넥션이 차단되지 않음을 함께 검증).
Kakao 호출 시점 `isActualTransactionActive()==false` 검증.

**Privacy**: `ListAppender`로 access_token/refresh_token/client_secret/
메시지 본문 원문이 로그에 없음을 검증(NOTIFY-001과 동일 기법).

**Metrics**: 3개 지표 계측값 검증.

**회귀**: notification(NOTIFY-001)/recommend(RECOMMEND-001.1)/match/agent/
applicationdraft/job/career/application/collector/pkbimport 전체 +
`./gradlew test` 전체 통과.

**실제 Kakao E2E(자동 테스트 범위 밖, 전체 자동 검증 PASS 후 사용자 승인
시에만 수행, 최대 1회)**: 사용자의 §Technical Notes 수동 설정이 완료돼
있어야 한다. 수행 전 Claude가 반드시 보고: 사용할 notification id, 실제
전송될 메시지 preview(DB 실제 값 기준), 예상 Kakao API 호출 횟수(정확히
2회 — token refresh 1회 + 메시지 전송 1회), 무료 quota 재확인. 사용자
명시 승인 후에만 `POST .../{id}/send` 1회 호출 → 실제 카카오톡 "나와의
채팅방" 수신 확인 → DB `status=SENT`/`sentAt` 확인 → 서버 로그에 secret
미노출 확인. Anthropic API는 이 단계에서도 호출하지 않는다.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | KAKAO-001 전체 신규 구현(entity/repository/status 확장, KakaoMessageClient/KakaoTokenClient interface+impl, KakaoRecommendationMessageFormatter, NotificationSendService, V17 migration, application.yml/.env.example, 테스트 25개) | 구현/테스트 코드 작성 완료. Codex sandbox가 Gradle 실행(zip.lck 권한, local socket) 자체를 차단해 컴파일/테스트 결과 미검증 상태로 보고. Claude가 로컬 compileJava/compileTestJava 실행 |
| 2 | Claude가 로컬에서 발견한 compileTestJava 에러 3건(`NotificationSendSnapshot` 생성자 `Long` 파라미터에 int literal 전달) 수정 요청 | 3개 파일 수정(`1,2` → `1L,2L`). Codex 환경에서는 여전히 Gradle 실행 불가 보고 |
| 3 | Claude가 로컬 실행으로 발견한 notification 패키지 17/48 실패의 근본 원인(Jackson 2/3 패키지 불일치 — `RestClientKakaoMessageClient`가 `com.fasterxml.jackson.databind.ObjectMapper`를 주입받으려 했으나 프로젝트는 Jackson 3 `tools.jackson.databind.ObjectMapper` 사용, `ImportBatchExtractionService` 선례 인용)를 정확히 짚어 수정 요청 | `RestClientKakaoMessageClient.java`만 수정(ObjectMapper import + JsonProcessingException→JacksonException). Claude가 로컬 재실행해 notification/kakao 패키지 48/48 PASS, 격리 회귀(recommend/match/agent/applicationdraft/collector/job/career/application/pkbimport) 340/340 PASS 확인(전체 동시 실행 시 발생하는 실패는 pre-existing DB 커넥션 풀 flake로 재확인, KAKAO-001 결함 아님) |
