---
task_id: NOTIFY-001
title: 채용공고 추천 알림 준비 — unseen recommendation persistence, PENDING까지
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-24T00:00:00+09:00
codex_thread_id: 01a03318-5b91-7181-a3ef-8d4b880f30cd
---

## Context

RECOMMEND-001(ADR-0031)은 `POST /api/jobs/recommendations?limit=5`로
OPEN JobPosting 전체를 candidate로 Claude 구조화 출력 1회로 batch
ranking하지만 결과를 저장하지 않는다(on-demand 계산만). 실제 E2E에서
47~76초가 걸린다.

문제: 저장이 없으므로 수집→추천→알림을 반복 자동화하면 "이 공고를 이미
사용자에게 알려줬는가?"를 판단할 수 없다. NOTIFY-001은 이 문제를
해결한다 — RECOMMEND-001의 추천 결과 중 아직 알리지 않은 공고를
notification candidate로 저장하고, 동일 JobPosting이 반복 알림 대상으로
생성되지 않도록 DB 수준에서 중복을 방지한다.

이번 Phase에서는 실제 메시지를 보내지 않는다. RECOMMEND → unseen 판별
→ persistence → PENDING까지만. 실제 Kakao API 호출은 후속 KAKAO-001.

**중요 발견**: `JobRecommendationService.recommend()`는 이미
`@Transactional(readOnly=true)`이고 그 안에서 Anthropic 호출(47~76초)이
그대로 실행된다 — RECOMMEND-001이 이미 그 시간 동안 DB 커넥션을 하나
점유한다(RECOMMEND-001 코드 변경은 이번 범위 밖). NOTIFY-001은 이 위에
**추가 트랜잭션을 얹지 않는 것**으로 대응한다(§Technical Notes 참고).

상세 설계 근거는 ADR-0032 참고.

## Scope

- 신규 `notification` 패키지: `JobRecommendationNotification` entity,
  `NotificationStatus`(PENDING/SENT/FAILED 3-state enum, 이번 Task는
  PENDING만 생성), `JobRecommendationNotificationRepository`,
  `NotificationPreparationService`, `JobRecommendationNotificationController`.
- `POST /api/notifications/job-recommendations?limit=5` (미지정 5,
  1~20 범위 밖 400): 내부적으로 기존 `JobRecommendationService.recommend(20)`
  빈을 직접 재사용(HTTP 재호출 아님, Controller→Controller 금지)해
  Top20 pool을 얻고, 이미 notified된 jobId를 skip하며 score 내림차순
  순서로 최대 `limit`개까지 새 PENDING notification을 생성한다.
- `GET /api/notifications/job-recommendations?status=PENDING`
  (status optional, `@PageableDefault(size=20) Pageable`,
  `JobApplicationListResponse`와 동일한 `content/totalElements/
  totalPages/page/size` 응답 패턴).
- dedupe key: `job_posting_id` 단일 키, DB `UNIQUE` 제약으로 정합성
  보장(`job_applications.uk_job_applications_job_posting_id`와 동일
  스타일). `findByJobPostingId` 사전 체크 + `save()` +
  `DataIntegrityViolationException` catch(레이스 대응)로 concurrency
  처리 — 새 distributed lock 없음, `ON CONFLICT` 네이티브 SQL 없음
  (COLLECT-006에서 이미 기각된 대안 재확인).
- snapshot 저장: `recommendationScore`, `reason`(200자, 저장 직전
  재truncate)만. `companyName`/`title`/`applicationEndAt`은 저장하지
  않고 `JobPosting` FK로 응답 시 재조회.
- matched PKB IDs(`careerExperienceIds` 등)는 저장하지 않는다.
- insert 직전 `JobPosting.status`를 배치 재조회해 `"OPEN"`이 아니면
  해당 건은 생성하지 않는다(그 사이 CLOSED로 바뀐 경우).
- 이미 notified된 것 skip 후 부족하면 Top20 pool에서 다음 unseen까지
  순차적으로 채운다(Top pool 소진 semantics).
- 3단계 transaction boundary: (1) `recommend(20)` 호출 — 감싸는
  트랜잭션 없음, (2) dedupe/OPEN 재확인 — 메모리 가공 + 짧은 배치
  조회, (3) row별 독립 저장(전체를 감싸는 `@Transactional` 없음,
  `JobApplicationService.create()` 패턴 재사용).
- Metrics: `careerops.notification.job-recommendation.request`(Counter,
  `result`=`success`|`pkb_empty`|`provider_error`|`validation_failed`),
  `.duration`(Timer), `.created`(DistributionSummary),
  `.skipped`(DistributionSummary).
- 로그: jobId/counts/durationMs만. reason/companyName/title 원문은
  로그 금지.

## Out of Scope

Kakao API 연동, 문자/SMS, 이메일, Slack, Push notification, 실제 메시지
전송, 카카오 OAuth/토큰 저장, scheduler 자동 호출(Collector scheduler
포함 — `@Scheduled` 일절 추가하지 않음), 마감일/면접 일정/ApplicationStage
알림, retry worker, delivery worker, DLQ, Redis, distributed lock,
AGENT-001/AGENT-002 호출, MATCH-002 개별 호출, frontend, notification
preference UI, multi-user 설계, recommendation algorithm 변경(RECOMMEND-001
코드 무변경), matched PKB IDs persistence, `sent_at` 등 SENT/FAILED
전이 컬럼(KAKAO-001에서 추가), 단건 조회 API, generic Notification
추상화, `(status, created_at)` index(현재 규모에서 근거 부족).
AUTOMATION/KAKAO/NOTIFY-002는 후속 Phase.

## Acceptance Criteria

- [x] `POST /api/notifications/job-recommendations`가 `JobRecommendationService`
      빈을 직접 호출한다(HTTP self-call 없음, 코드로 확인 가능).
- [x] `NotificationPreparationService.prepare()`를 감싸는 `@Transactional`이
      없다(RECOMMEND-001의 47~76초 호출 위에 추가 DB 트랜잭션이
      얹히지 않음).
- [x] 최초 호출: unseen 추천이 존재하면 최대 `limit`개 PENDING
      notification이 생성된다.
- [x] 동일 jobId로 두 번째 호출 시 재생성되지 않는다(`createdCount=0`
      또는 그 jobId 제외, `alreadyNotifiedCount`에 반영).
- [x] 기존 row가 FAILED/SENT여도 재생성되지 않는다(resend 아님).
- [x] recommendationScore가 재계산으로 달라져도 기존 row는 갱신되지
      않는다.
- [x] `job_posting_id`에 DB `UNIQUE` 제약이 있고, 동일 값 2회
      `saveAndFlush()` 시 두 번째가 `DataIntegrityViolationException`을
      던진다.
- [x] 두 스레드가 동일 jobId로 동시에 prepare를 시도해도 최종 DB row는
      정확히 1개다(실제 PostgreSQL로 검증).
- [x] limit 미지정 시 5, limit=20 정상, limit=0/21은 400.
- [x] unseen이 limit보다 적으면 있는 만큼만 생성한다(강제로 채우지
      않음).
- [x] RECOMMEND 내부 호출이 409(PKB empty)/502(provider/validation)를
      던지면 notification row가 0개이고 그 상태 그대로 전파된다.
- [x] insert 직전 재확인에서 CLOSED로 바뀐 jobId는 생성되지 않고
      나머지는 정상 생성된다.
- [x] 응답의 companyName/title/applicationEndAt은 저장된 값이 아니라
      JobPosting DB 재조회 값이다.
- [x] reason은 저장 전 200자로 재truncate된다.
- [x] `GET .../job-recommendations?status=PENDING`이 PENDING만 반환하고
      pageable이 정상 동작한다.
- [x] 로그에 reason/companyName/title 원문이 남지 않는다.
- [x] 아래 테스트 계획 31개 케이스가 통과한다.
- [x] `./gradlew test` 전체 통과(기존 276개 포함 회귀 없음).

## Technical Notes

- 참고 구현 패턴: `JobApplicationService.create()`(existsBy 사전체크 +
  `save()` + `DataIntegrityViolationException` catch → 409, 정확한
  dedupe 선례), `job_applications` V5 migration의
  `uk_job_applications_job_posting_id UNIQUE (job_posting_id)`,
  `JobPostingController`의 `@PageableDefault(size=20) Pageable` +
  `JobApplicationListResponse`(content/totalElements/totalPages/page/size).
  COLLECT-006 Technical Notes(`ON CONFLICT` 기각 이유, Postgres
  aborted-transaction 제약, `AlioCollectorConcurrencyTest` 동시성 테스트
  패턴)를 그대로 재참고.
- `JobPosting.status`는 Java enum이 아니라 plain `String`이다(`"OPEN"`/
  `"CLOSED"` literal). `JobPostingStatus` enum은 존재하지 않는다 —
  문자열 그대로 비교한다.
- `NotificationStatus`는 `@Enumerated(EnumType.STRING)` +
  `@Column(length=20)`(ImportCandidateStatus와 동일 패턴), 3-state
  전부 정의하되 이번 Task의 실제 production 경로에서는 PENDING만
  생성한다. `updateStatus()` 같은 전이 메서드는 미리 추가하지 않는다
  (KAKAO-001에서 실제 필요에 맞게 추가).
- migration 초안(Codex가 실제 다음 버전 번호로 작성, 파일명은 구현
  시점 최신 V 확인 후 결정):
  ```sql
  CREATE TABLE job_recommendation_notifications (
      id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
      job_posting_id BIGINT NOT NULL REFERENCES job_postings(id),
      recommendation_score DOUBLE PRECISION NOT NULL,
      reason VARCHAR(200) NOT NULL,
      status VARCHAR(20) NOT NULL,
      created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
      CONSTRAINT uk_job_recommendation_notifications_job_posting_id UNIQUE (job_posting_id)
  );
  ```
  별도 index 추가하지 않음(UNIQUE가 자동으로 btree index 생성, 현재
  규모에서 `(status, created_at)` 복합 index 근거 없음).
- unseen selection 알고리즘:
  ```
  raw = jobRecommendationService.recommend(20)  // 이미 score desc/jobId asc 정렬
  existingJobIds = repository.findExistingJobPostingIds(raw의 jobId 목록)  // 배치 1쿼리
  candidateIds = raw 중 existingJobIds에 없는 것
  openJobIds = jobPostingRepository.findAllById(candidateIds) 중 status=="OPEN"인 것  // 배치 1쿼리, insert 직전 재확인
  created = []
  for rec in raw (순서 그대로):
      if rec.jobId in existingJobIds: alreadyNotifiedCount++; continue
      if rec.jobId not in openJobIds: continue   // CLOSED로 바뀜, alreadyNotifiedCount 아님
      if created.size == limit: break
      try: save(...); created.add(rec)
      catch DataIntegrityViolationException: alreadyNotifiedCount++  // race
  ```
- controller에 `JobRecommendationException`(502)/`ResponseStatusException`
  전파용 로컬 `@ExceptionHandler`를 새로 등록해야 한다(전역
  `@ControllerAdvice` 없음 — RECOMMEND-001 round3에서 이미 겪은 문제와
  동일 패턴, 컨트롤러마다 개별 등록 필요).
- `application.yml` 변경 없음(새 LLM client/timeout 없음, 기존
  `JobRecommendationService` 빈을 그대로 재사용).
- 신규 production dependency 없음.

## Test Plan

Fake/mock `JobRecommendationService` 사용 (실제 Anthropic 미호출).

**Service 단위 (21개)**: 1 정상 흐름 5개 생성, 2 일부 duplicate,
3 전부 duplicate(createdCount=0, 200), 4 limit=5/unseen 20건 상위
5개만(score desc 순서), 5 limit=20/unseen 3건뿐(강제로 안 채움),
6 limit 미지정→default 5, 7 RECOMMEND 409 전파→row 0개, 8 RECOMMEND
502(provider_error) 전파→row 0개, 9 RECOMMEND 502(validation_failed)
전파→row 0개, 10 OPEN 0건→200+createdCount=0, 11 insert 직전 CLOSED
전환→해당 건만 skip, 12 두 번째 요청 재생성 안 됨(DB round-trip),
13 기존 FAILED row 재생성 안 됨, 14 기존 SENT row 재생성 안 됨,
15 score 재계산돼도 기존 row 갱신 안 됨, 16 reason 201자 이상 저장
시 200자 truncate, 17 응답 company/title이 FK 재조회 값(저장값
아님), 18 PKB id 필드가 entity/DB에 없음, 19 응답 DTO 필드 조합
검증, 20 로그 privacy(ListAppender), 21 dedupe pre-check 배치 1쿼리
검증(N+1 없음).

**Controller MockMvc (6개)**: 22 limit 미지정→prepare(5) 호출/200,
23 limit=20→prepare(20)/200, 24 limit=0→400/service 미호출,
25 limit=21→400/service 미호출, 26 JobRecommendationException 전파→502,
27 ResponseStatusException(409) 전파→409.

**Read API (2개)**: 28 status=PENDING 필터, 29 status 없이 전체+pageable.

**DB-level 실제 PostgreSQL (2개)**: 30 UNIQUE 제약(`saveAndFlush()`
2회, 2번째 `DataIntegrityViolationException`), 31 동시성(두 스레드
동일 jobId 동시 저장, `CyclicBarrier`, 클래스 레벨 `@Transactional`
없음 — `AlioCollectorConcurrencyTest` 패턴, 최종 count=1).

회귀: recommend/match/agent/applicationdraft/job/career/application/
collector/pkbimport 전체 + `./gradlew test` 전체 통과(기존 276 + 신규
31 목표).

**실제 E2E(자동 테스트 범위 밖, Claude가 dev DB + 실제 Anthropic API로
수행)**: `POST .../job-recommendations?limit=5` 1차 호출로 실제 생성
확인 → `GET .../job-recommendations?status=PENDING`으로 저장 확인 →
동일 API 2차 호출 시 재생성 안 됨 확인 → Prometheus 4개 지표 확인 →
로그 privacy 확인 → **backend 재시작 후 GET으로 persistence 유지
확인**(NOTIFY-001 핵심 acceptance) → Kakao API 호출 0건/실제 메시지
전송 0건 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | notification 패키지 신규 구현(entity/enum/repository/service/controller/DTO 3개), V16 migration, 테스트 31개(Service 21/Controller 8/DB-level 동시성 2) | git status로 recommend 패키지/application.yml 무변경 확인. Codex sandbox가 Gradle 실행을 차단해 컴파일/테스트 미실행 → Claude가 로컬 컴파일 시 `Timer` 타입 모호성(java.util.Timer vs micrometer Timer) 컴파일 에러 발견 → round2 요청 |
| 2 | `NotificationPreparationService.java`에 `import io.micrometer.core.instrument.Timer;` 명시 추가(다른 파일 무변경) | Codex sandbox가 compileJava조차 차단(Gradle wrapper lock 파일 접근 불가)해 컴파일 미확인 보고 → Claude가 로컬 compileJava 성공 확인, 전체 `./gradlew test` 307/307 PASS(기존 276 + notification 31). reviewer 1차 검토 요청 |

## 실제 E2E 결과 (2026-08-24, Claude가 dev DB + 실제 Anthropic API로 수행)

- **최초 3회 시도는 RECOMMEND-001 내부 호출 자체가 실패**(candidates
  452~461건 규모에서 `MALFORMED_RESPONSE`/`UNKNOWN_JOB_ID`/
  `NETWORK_TIMEOUT`, 각 50~81초) → 매번 502가 정상 전파되고
  `GET .../job-recommendations?status=PENDING`으로 notification row가
  **0개**임을 확인(§ADR-0032 결정 "provider failure → row 0개" 실증).
  4번째 재시도에서 200 OK.
- `POST .../job-recommendations?limit=5` 200 OK, 65초, **5건 PENDING
  생성**(jobId 7470/872/1016/974/1024, score 0.95~0.5). `GET
  .../job-recommendations?status=PENDING`으로 5건 저장 확인.
- 동일 API 2차 호출: RECOMMEND가 이번엔 1건(7470)만 반환했고 이미
  notified라 `createdCount=0, alreadyNotifiedCount=1`로 정상 skip —
  재생성 안 됨을 실증.
- **backend 재시작 후** `GET .../job-recommendations?status=PENDING`
  으로 재조회 — 재시작 전 5건이 그대로 유지됨을 확인(NOTIFY-001 핵심
  acceptance).
- 재시작 후 3차 prepare 호출: 200 OK, 68초, RECOMMEND가 이번엔 10건
  반환해 그중 5건(이미 저장된 것)은 `alreadyNotifiedCount`, 나머지
  5건은 신규 생성(jobId 64/7655/59/63/**7552**) — **Case A(7552,
  MATCH-001 0점이었던 한국교통안전공단 AI서비스개발)가 unseen pool
  순차 소진을 통해 결국 알림에 포함됨을 실증**. 최종 누적 10건.
- Prometheus 4개 지표(`request{result=success}`=1,
  `duration_seconds_sum`≈67.4, `.../job_recommendation_sum`(created)=5,
  `.../skipped_sum`=5) 정상 계측.
- 서버 로그(`Notification preparation success created=... alreadyNotified=...
  durationMs=... jobIds=[...]`)에 reason/companyName/title 원문 노출
  없음, jobId/counts/duration만 기록.
- Kakao 관련 코드/로그 0건 확인(`grep -ri kakao` 코드베이스+서버 로그
  전체 매치 없음) — 실제 메시지 전송 없음.
- known limitation(NOTIFY-001 코드 문제 아님, RECOMMEND-001 자체 관찰):
  candidate 규모가 450건을 넘어가면서 RECOMMEND-001의 단일 batch LLM
  호출이 4번 중 3번 실패했다(malformed output/hallucinated id/network
  timeout). RECOMMEND-001 자신의 안정성 이슈로, 향후 candidate 규모가
  계속 커지면(현재 스케줄러가 계속 새 공고를 수집 중) 재검토가 필요할
  수 있다 — 이번 Task 범위 밖이므로 코드 수정 없이 관찰 결과만 기록한다.
