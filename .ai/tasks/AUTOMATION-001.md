---
task_id: AUTOMATION-001
title: 추천→알림 준비→Kakao 발송 자동화 파이프라인 — 단계별 flag, cron, overlap guard 없음
phase: review
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-25T00:00:00+09:00
codex_thread_id: 01a038ec-7859-7721-a758-6596d8c5b849
---

## Context

지금까지 RECOMMEND-001/NOTIFY-001/KAKAO-001은 전부 수동 HTTP API로만
트리거됐다: `POST /api/notifications/job-recommendations?limit=N`(내부에서
`JobRecommendationService.recommend(20)`을 재사용해 unseen 추천을 PENDING
notification으로 저장)와 `POST /api/notifications/job-recommendations/{id}/send`
(PENDING/FAILED를 atomic claim해 SENDING → Kakao 전송 → SENT/FAILED).

AUTOMATION-001은 이 두 단계를 **새로 구현하지 않고** 기존 Service를 그대로
호출하는 스케줄러로 자동화한다. `docs/PROJECT.md`의 제품 목표("신규/추천/
마감임박 공고를 **매일 아침** 카카오톡으로 전달한다")를 실제로 충족시키는
첫 Task다.

설계 조사(architect subagent, 2026-08-25, 코드 직접 확인)에서 확인한 핵심
사실:

- `NotificationPreparationService.prepare(int limit)`이 정확히 존재하고
  내부에서 `recommendationService.recommend(20)`을 고정 호출한다(재확인
  완료) — AUTOMATION은 `recommend()`를 별도로 다시 호출하지 않고 이
  `prepare(limit)`만 호출한다.
- `NotificationSendService.send(long id)`가 정확히 존재하며, 실패 시
  `KakaoDeliveryException`(cause로 원본 `KakaoApiException` 보관,
  `reason()`으로 `PROVIDER_ERROR`/`PROVIDER_5XX`/`DELIVERY_UNKNOWN`/
  `TOKEN_REFRESH_FAILED` 구분 가능)을 던진다.
- **`JobRecommendationNotificationRepository.search()`는 정렬이 `ORDER BY
  n.createdAt DESC, n.id DESC`로 하드코딩돼 있어 `Pageable`의 정렬을
  무시한다** — "PENDING을 생성 시각 오래된 순으로 최대 N개" 조회하는
  메서드가 현재 없다. 신규 repository 메서드 1개가 필요하다.
- **이 프로젝트에 Redis/분산 락 실사용 코드가 전혀 없다**(`docker-compose`
  에만 떠 있는 미사용 인프라, `grep -rl "Redis" backend/src/main/java`
  결과 없음). 유일한 동시성 선례는 COLLECT-006의 JVM `ReentrantLock`(수동
  API + scheduler라는 **두 개의 진입점**이 충돌했던 문제 때문에 추가됨).
- **`AlioCollectionScheduler`는 자체 동시성 방어가 없다** — 실제 방어는
  `AlioCollectorService` 내부 `ReentrantLock`(non-blocking `tryLock()`)이다.
- Anthropic/Kakao credential이 `.env`에 비어 있어도, 두 client
  (`AnthropicJobRecommendationClient`/`KakaoTokenStore`) 모두 생성자에서는
  검증하지 않고 **실제 호출 메서드 안에서만** lazy하게 blank 체크 후
  예외를 던진다(코드로 확인) — 즉 스케줄러 Bean 자체가 생성되지 않으면
  credential 값과 무관하게 자동 호출 경로가 아예 존재하지 않는다.

상세 근거는 ADR-0035 참고. 사용자가 설계 승인 단계에서 확정한 3가지 결정:
(1) `TOKEN_REFRESH_FAILED` 발생 시 그 run의 남은 delivery 시도를
short-circuit, (2) cron 기반 매일 아침 스케줄(Asia/Seoul), (3) prepare/
delivery를 **단계별로 분리된 두 개의 독립 feature flag**로 관리(단일
`automation.enabled` 아님 — "prepare는 자동, delivery는 수동"같은 조합도
지원해야 함).

## Scope

### 아키텍처 — 두 개의 독립된 stage, 공유 orchestrator 없음

이번 설계는 "하나의 `AutomationService.runOnce()`가 prepare→delivery를
순서대로 처리"하는 대신, **prepare와 delivery를 완전히 독립된 두 pair
(Scheduler+Service)로 분리**한다. 사용자가 stage별 flag 분리를 선택했으므로
각 stage는 서로 다른 시각에, 서로 다른 on/off 상태로 동작할 수 있어야
하고, 이는 하나의 공유 orchestrator보다 두 개의 독립된 컴포넌트로 표현하는
것이 더 정확하다.

**Stage 1 — Prepare**
```
AutomationPrepareScheduler (@Scheduled cron, @ConditionalOnProperty
    prefix="careerops.automation.prepare", name="enabled", havingValue="true")
        ↓
AutomationPrepareService.runOnce()
        ↓
NotificationPreparationService.prepare(prepareLimit)   // 기존 코드 무변경
        ↓ (409/502 예외는 catch해 로그/metric만 남기고 정상 종료 — throw 안 함)
AutomationPrepareRunResult { succeeded, createdCount, alreadyNotifiedCount, durationMs }
```

**Stage 2 — Delivery**
```
AutomationDeliveryScheduler (@Scheduled cron, @ConditionalOnProperty
    prefix="careerops.automation.delivery", name="enabled", havingValue="true")
        ↓
AutomationDeliveryService.runOnce()
        ↓
repository.findIdsByStatusOrderByCreatedAtAsc(PENDING, PageRequest.of(0, deliveryLimit))
        ↓
for each id: NotificationSendService.send(id)   // 기존 코드 무변경
    - 404/409(ResponseStatusException) → skip(다른 경로가 이미 처리함), continue
    - KakaoDeliveryException, reason()==TOKEN_REFRESH_FAILED → break(남은 항목 시도 중단)
    - KakaoDeliveryException, 그 외 reason → continue(best-effort)
        ↓
AutomationDeliveryRunResult { attemptedCount, sentCount, failedCount, shortCircuited, durationMs }
```

두 Service(`AutomationPrepareService`/`AutomationDeliveryService`) 모두
`@Transactional`을 갖지 않는다 — 하위 Service(`NotificationPreparationService`/
`NotificationSendService`/`NotificationDeliveryTransactions`)가 이미 각자
자기 트랜잭션 경계를 책임지므로, orchestration 계층은 그 경계를 감싸거나
늘리지 않는다(ADR-0032/0033/0034가 세 번 연속 확립한 "외부 API 대기 시간
동안 DB connection/transaction을 점유하지 않는다" 원칙을 네 번째로 동일
적용).

### Overlap guard — 만들지 않는다

각 stage의 유일한 트리거는 그 stage 전용 `@Scheduled` 메서드 하나뿐이다
(수동 실행 API를 만들지 않음, §Out of Scope). COLLECT-006이 lock을 추가한
이유는 "수동 API + scheduler"라는 두 개의 서로 다른 진입점이 충돌했기
때문인데, 이번 설계는 각 stage마다 진입점이 정확히 하나뿐이므로 그 문제가
구조적으로 발생할 수 없다. Spring의 `fixedDelay`/cron 기본 동작(이전 실행이
끝난 뒤에야 다음 트리거가 유효해짐)만으로 충분하다 — JVM `AtomicBoolean`/
`ReentrantLock`/DB advisory lock/Redis lock 어떤 것도 추가하지 않는다.

### Feature flag / cron 설계

```yaml
careerops:
  automation:
    prepare:
      enabled: ${CAREEROPS_AUTOMATION_PREPARE_ENABLED:false}
      cron: "0 50 7 * * *"
      zone: Asia/Seoul
      limit: 5
    delivery:
      enabled: ${CAREEROPS_AUTOMATION_DELIVERY_ENABLED:false}
      cron: "0 0 8 * * *"
      zone: Asia/Seoul
      limit: 5
```
- 기본값은 **둘 다 false** — 이번 Task의 최우선 안전장치. `havingValue="true"`
  (matchIfMissing 지정 안 함 → 기본 false)라서 flag가 false인 동안은 해당
  Scheduler Bean 자체가 Spring 컨텍스트에 생성되지 않는다(Anthropic/Kakao
  credential 값과 무관하게 호출 경로 자체가 존재하지 않음을 보장).
- delivery cron(08:00)이 prepare cron(07:50)보다 10분 뒤로, 같은 날 prepare가
  먼저 끝나고 그 결과가 delivery 대상에 포함될 시간을 확보한다. 단, 두
  스케줄러는 서로를 기다리도록 결합되지 않는다(delivery는 그 시각의
  PENDING backlog 전체를 대상으로 하므로, prepare가 아직 안 끝났어도
  delivery는 기존 backlog만으로 정상 동작한다 — §prepare 실패 시 정책과
  동일한 이유).
- `backend/build.gradle`의 `test` task에
  `CAREEROPS_AUTOMATION_PREPARE_ENABLED=false`/
  `CAREEROPS_AUTOMATION_DELIVERY_ENABLED=false`를 방어적으로 추가한다
  (기존 `CAREEROPS_SCHEDULER_ALIO_ENABLED=false`와 동일 선례).

### Prepare 실패 시 정책

`AutomationPrepareService.runOnce()`는 `NotificationPreparationService.prepare()`
가 던지는 `ResponseStatusException`(409, PKB 비어있음)과
`JobRecommendationException`(502 계열, provider/validation 실패)을 catch해
로그/metric만 남기고 정상 종료한다(예외를 다시 던지지 않음) — Delivery
stage는 이 실패와 무관하게 자기 스케줄에 따라 기존 PENDING backlog를
계속 발송 시도한다(두 stage가 독립이므로 자연스럽게 보장됨).

### Delivery 대상 선택 — 신규 repository 메서드

```java
@Query("SELECT n.id FROM JobRecommendationNotification n WHERE n.status = :status " +
       "ORDER BY n.createdAt ASC, n.id ASC")
List<Long> findIdsByStatusOrderByCreatedAtAsc(
        @Param("status") NotificationStatus status, Pageable pageable);
```
`status`는 항상 `PENDING`(FAILED는 포함하지 않는다 — AUTOMATION은 PENDING만
자동 재시도 대상으로 삼는다. 사람이 `/send`를 수동으로 다시 호출하면
FAILED도 재시도되는 기존 경로는 그대로 유효하다, ADR-0034 결정과 상충 없음).
`Pageable`은 `PageRequest.of(0, deliveryLimit)`로 상한만 적용. migration
불필요(기존 컬럼/index로 충분, 이 규모에서 신규 index 근거 없음).

### Delivery 부분 실패 / systemic 실패 정책

- `PROVIDER_ERROR`/`PROVIDER_5XX`/`DELIVERY_UNKNOWN` → best-effort로 남은
  항목 계속 시도(개별 알림 실패가 다른 알림 발송을 막지 않는다).
- `TOKEN_REFRESH_FAILED` → 같은 run의 남은 delivery 시도를 **중단**한다
  (같은 refresh_token/env 설정을 다시 읽을 뿐이므로 나머지 항목도 동일하게
  실패할 것이 사실상 확정적 — 불필요한 실제 Kakao 호출 반복을 막는다).
  이미 실패로 commit된 항목은 그대로 FAILED 유지, 남은 PENDING은 다음 run
  에서 다시 시도된다.
- 404/409(`ResponseStatusException`, 다른 경로가 이미 SENT/SENDING/삭제
  처리한 경우)는 정상적인 skip으로 간주하고 계속 진행한다.

### Metrics

```
careerops.automation.prepare.run          Counter(result=completed|failed)
careerops.automation.prepare.duration     Timer
careerops.automation.delivery.run         Counter(result=completed)
careerops.automation.delivery.duration    Timer
careerops.automation.delivery.candidates  DistributionSummary
careerops.automation.delivery.short_circuited  Counter(reason=token_refresh_failed)
```
기존 `careerops.recommendation.*`/`careerops.notification.job-recommendation.*`/
`careerops.kakao.*`는 하위 Service를 그대로 호출하므로 자연히 함께 증가한다
(중복 계측 아님). notificationId/jobId를 태그로 쓰지 않는다.

### 로깅

기존 컨벤션(jobId/count/durationMs만, title/company/reason/token 원문
금지)을 그대로 따른다:
```
Automation prepare completed succeeded={} created={} alreadyNotified={} durationMs={}
Automation delivery completed attempted={} sent={} failed={} shortCircuited={} durationMs={}
```

## Out of Scope

수동 실행 API(`POST /api/automation/...`), overlap guard(JVM lock/DB
advisory lock/Redis lock — 진입점이 stage당 하나뿐이라 불필요), `AutomationRun`
entity/migration(기존 metrics/log로 충분), 기존 Collector scheduler 변경
(완전히 독립 유지 — JobPosting을 쓰기만 하고 AUTOMATION은 읽기만 해서
겹치는 자원 없음), recommendation/MATCH/AGENT 알고리즘 변경, Kakao 메시지
포맷 변경, OAuth UI, frontend, multi-user, email/SMS/Slack, 새 notification
종류, 마감일/면접 일정 reminder, Redis queue, Kafka, DLQ, Spring Batch,
Temporal/Airflow, stale SENDING 자동 복구(ADR-0034가 이미 known limitation
으로 명시), FAILED 자동 retry(AUTOMATION은 PENDING만 대상), Anthropic/Kakao
실제 API 호출.

이번 Task 전체에서 실제 Anthropic API 호출 0회, 실제 Kakao API 호출 0회
(Fake client만 사용). 실제 외부 호출이 필요하다고 판단되는 acceptance는
없음(전부 자동 검증으로 대체 가능) — 만약 구현 중 그런 경우가 발견되면
실행하지 말고 known limitation으로 기록한다.

## Acceptance Criteria

- [x] 기본 설정(`careerops.automation.prepare.enabled`/`delivery.enabled`
      둘 다 미지정 = false)으로 앱을 기동하면 `AutomationPrepareScheduler`/
      `AutomationDeliveryScheduler` Bean이 Spring 컨텍스트에 존재하지
      않는다(`getBeanNamesForType()`으로 검증) — Anthropic/Kakao credential이
      비어 있어도 앱이 정상 기동된다.
- [x] `prepare.enabled=true`일 때 `AutomationPrepareService.runOnce()`가
      `NotificationPreparationService.prepare(prepareLimit)`을 정확히
      호출한다(`recommend()`를 별도로 다시 호출하지 않음).
- [x] prepare가 409/502로 실패해도 예외가 전파되지 않고, 결과가
      `succeeded=false`로 기록된다.
- [x] `delivery.enabled=true`일 때 `AutomationDeliveryService.runOnce()`가
      PENDING을 생성 시각 오름차순으로 최대 `deliveryLimit`개 선택해
      `NotificationSendService.send(id)`를 순차 호출한다.
- [x] delivery 도중 개별 항목이 `PROVIDER_ERROR`/`PROVIDER_5XX`/
      `DELIVERY_UNKNOWN`으로 실패해도 나머지 항목은 계속 시도된다.
- [x] delivery 도중 `TOKEN_REFRESH_FAILED`가 발생하면 남은 항목 시도를
      중단하고 `shortCircuited=true`가 결과에 기록된다.
- [x] SENT/SENDING 상태인 notification은 delivery 후보에서 애초에
      제외되거나(쿼리가 PENDING만 조회), 404/409로 skip되어 provider가
      다시 호출되지 않는다.
- [x] FAILED notification은 AUTOMATION delivery 후보에 포함되지 않는다
      (PENDING만 대상 — 사람이 수동으로 `/send`를 호출하는 기존 FAILED
      재시도 경로는 영향받지 않음).
- [x] 첫 실행: Fake recommendation 3건 → prepare로 PENDING 3건 생성 →
      delivery로 3건 모두 SENT. 두 번째 동일 run: 동일 추천 결과라도
      notification 중복 생성 0건, 이미 SENT인 notification에 대한 재전송
      0건(실제 provider 호출 횟수로 검증).
- [x] 실제 PostgreSQL(`careerops_test`) + Fake provider로 전체 파이프라인
      (prepare→delivery, PENDING→SENT)이 end-to-end로 검증된다.
- [x] `AutomationPrepareService`/`AutomationDeliveryService` 어디에도
      `@Transactional`이 없다(하위 Service가 각자 트랜잭션 경계를 책임짐).
- [x] 자동 테스트 전체가 Fake recommendation client / Fake Kakao client만
      사용하고 실제 Anthropic/Kakao API를 호출하지 않는다.
- [x] `./gradlew test` 전체 통과, 기존 NOTIFY-001/KAKAO-001/RECOMMEND-001.1/
      MATCH/AGENT/Collector/Job/Application/PKB import 회귀 없음.

## Technical Notes

- 참고 구현 패턴: `AlioCollectionSchedulerTest`(scheduler 메서드를 테스트에서
  직접 호출, `@TestPropertySource`로 flag on, 메트릭 전/후 비교),
  `NotificationSendIntegrationTest`(`@TestConfiguration` + `@Primary` Fake
  client 주입, 실제 `careerops_test` Postgres), `JobRecommendationServiceTest`
  의 `FakeClient implements JobRecommendationClient` 패턴. 전부 그대로
  재사용 가능.
- `NotificationSendService.send(long id)`의 실패 처리부(`fail()`)는 항상
  `KakaoDeliveryException`을 던지고 그 `getCause()`가 원본
  `KakaoApiException`이다 — `AutomationDeliveryService`가
  `((KakaoApiException) exception.getCause()).reason()`으로 systemic
  실패를 판별한다.
- `@Scheduled(cron = "${careerops.automation.prepare.cron}", zone =
  "${careerops.automation.prepare.zone}")` 형태로 시각대를 명시한다(임의로
  하드코딩된 offset 없이 설정값으로 관리, 향후 사용자가 아침 시각을 바꾸고
  싶으면 `application.yml` 값만 수정).
- `AlioCollectionScheduler`/`AlioCollectorService`는 이번 Task에서 전혀
  수정하지 않는다(완전히 독립 유지, §Scope 확인).
- 신규 production dependency 없음.
- `docs/METRICS.md`/`docs/ARCHITECTURE.md`/`docs/ROADMAP.md`는 구현 완료
  후(reviewer PASS 이후) Claude가 갱신한다(이번 Task의 코드 산출물은 아님).

## Test Plan

Fake `JobRecommendationClient`/`KakaoMessageClient`/`KakaoTokenClient` 사용
(실제 Anthropic/Kakao API 자동 테스트에서 미호출).

**Bean 조건부 등록**: 기본 설정에서 두 Scheduler Bean 모두 부재 확인.
`prepare.enabled=true`만/`delivery.enabled=true`만/둘 다 true인 3가지
조합에서 각각 해당 Bean만 존재함을 검증.

**AutomationPrepareService 단위**: prepare 정상 호출/파라미터 전달,
409 catch 후 정상 종료, 502(`JobRecommendationException`, repairable/
validation 각각) catch 후 정상 종료, 결과 DTO 필드 검증.

**AutomationDeliveryService 단위**: PENDING만 오름차순 최대 limit개 선택,
전부 성공, 일부 `PROVIDER_ERROR`/`PROVIDER_5XX`/`DELIVERY_UNKNOWN` 실패 후
나머지 계속(best-effort), `TOKEN_REFRESH_FAILED` 발생 시 이후 항목 미호출
(provider invocation count로 검증) 및 `shortCircuited=true`, 404/409는
skip 후 계속, PENDING 0건일 때 provider 미호출.

**Repository**: `findIdsByStatusOrderByCreatedAtAsc`가 PENDING만, 생성
시각 오름차순, `Pageable` 상한 적용을 실제 DB로 검증.

**Scheduler**: cron 트리거를 기다리지 않고 `scheduler.run()`(또는 해당
메서드명)을 테스트에서 직접 호출, 메트릭 증가 확인.

**Full pipeline integration(실제 PostgreSQL)**: 승인 PKB + OPEN JobPosting
존재 → `AutomationPrepareService.runOnce()` → PENDING 생성 확인 →
`AutomationDeliveryService.runOnce()` → SENT 전이 확인. 동일 시나리오
반복 실행 시 중복 notification 0건, 재전송 0건(Fake client invocation
횟수로 검증).

**회귀**: notification(NOTIFY-001/KAKAO-001)/recommend(RECOMMEND-001.1)/
match/agent/applicationdraft/collector/job/career/application/pkbimport
전체 + `./gradlew test` 전체 통과.

**실제 외부 API E2E**: 없음(§Out of Scope). 실제 Anthropic/Kakao 호출이
필요한 acceptance는 이번 설계에 없다고 판단했다.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | AUTOMATION-001 전체 신규 구현(AutomationPrepareScheduler/Service/RunResult, AutomationDeliveryScheduler/Service/RunResult, JobRecommendationNotificationRepository 신규 조회 메서드, application.yml/build.gradle 설정, 테스트 6개 클래스) | 구현/테스트 코드 작성 완료. Codex sandbox가 Gradle 실행(zip.lck 권한) 자체를 차단해 컴파일/테스트 결과 미검증 상태로 보고. Claude가 로컬 compileJava/compileTestJava 실행(둘 다 성공) 후 automation 패키지 17/17 PASS, 그러나 `AutomationNotificationRepositoryTest` 1건 실패 발견 |
| 2 | Claude가 로컬 실행으로 발견한 실패 원인(raw JdbcTemplate UPDATE에 `java.time.Instant`를 직접 바인딩해 PostgreSQL 드라이버가 SQL 타입을 추론하지 못함)을 정확히 짚어 `java.sql.Timestamp.from(...)`로 수정 요청 | 해당 1줄만 수정. Claude가 로컬 재실행해 해당 테스트 PASS, automation+notification 패키지 전체 재확인(18개 신규 테스트 전부 PASS), 격리 회귀(recommend/match/agent/applicationdraft/collector/job/career/application/pkbimport) 340/340 PASS 확인(총 358/358) |
