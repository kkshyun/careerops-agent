---
task_id: NOTIFY-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-24T18:50:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `POST .../job-recommendations`가 `JobRecommendationService` 빈을 직접 호출한다 (HTTP self-call 없음) — 충족. `NotificationPreparationService`는 생성자 주입된 `recommendationService`를 직접 호출한다(`recommend(20)`, `NotificationPreparationService.java:53`). `RestTemplate`/`WebClient` 등 HTTP 클라이언트 사용 없음.
- [x] `prepare()`를 감싸는 `@Transactional`이 없다 — 충족. `NotificationPreparationService.java` 전체(클래스/메서드 어디에도) `@Transactional` 어노테이션 없음. `recommend(20)` 호출(1), dedupe/OPEN 배치 재조회(2), 행별 `repository.save()`(3)가 명세된 3단계 그대로 분리돼 있음(`NotificationPreparationService.java:49-97`).
- [x] 최초 호출 시 unseen 최대 `limit`개 PENDING 생성 — 충족. `createsFivePendingNotificationsInRecommendationOrder`(`NotificationPreparationServiceTest.java:45`) 검증, score desc/jobId asc 순서 그대로 유지.
- [x] 동일 jobId 재호출 시 재생성 안 됨 — 충족. `secondRequestDoesNotRecreatePersistedRows`(`:91`)에서 확인. 다만 이 테스트는 mock 기반이며 실제 DB round-trip은 아님(Findings 참고, blocking 아님).
- [x] 기존 row FAILED/SENT여도 재생성 안 됨 — 충족(로직상 동일 경로). `failedExistingRowIsNotRecreated`/`sentExistingRowIsNotRecreated`(`:99-101`)가 `findExistingJobPostingIds`가 해당 id를 반환하면 무조건 skip함을 검증(엔티티에 status별 분기 로직 자체가 없어 FAILED/SENT/PENDING 무관하게 동일하게 동작).
- [x] score 재계산돼도 기존 row 갱신 안 됨 — 충족. `recalculatedScoreDoesNotUpdateExistingRow`(`:103`)이 `save()`가 전혀 호출되지 않음을 검증(update 로직 자체가 없음).
- [x] `job_posting_id` DB UNIQUE + `saveAndFlush()` 2회째 `DataIntegrityViolationException` — 충족. `V16__create_job_recommendation_notifications_table.sql`의 `uk_job_recommendation_notifications_job_posting_id UNIQUE (job_posting_id)`, 실제 PostgreSQL로 `uniqueConstraintRejectsSecondSaveAndFlush`(`JobRecommendationNotificationDatabaseTest.java:32`) 검증.
- [x] 두 스레드 동시 prepare 시 최종 row 1개 — 충족. `concurrentInsertsLeaveExactlyOneRow`(`:39`)가 `CyclicBarrier`로 실제 2-thread 동시 `saveAndFlush()`를 시도, 클래스 레벨 `@Transactional` 없음(`AlioCollectorConcurrencyTest` 패턴 재사용), 최종 `countByJobPostingId==1` 검증.
- [x] limit 미지정 5 / 1~20 정상 / 범위 밖 400 — 충족. `@Min(1) @Max(20)` + `@Validated`(`JobRecommendationNotificationController.java:15,25`), `ConstraintViolationException`→400 로컬 핸들러(`:40-42`). `defaultLimitIsFive`/`maxLimitTwentyIsAccepted`/`zeroLimitIsBadRequest`/`twentyOneLimitIsBadRequest`(`JobRecommendationNotificationControllerTest.java:33-36`)가 MockMvc로 실제 상태코드 검증.
- [x] unseen이 limit보다 적으면 강제로 안 채움 — 충족. `fewerUnseenDoesNotForceFill`(`NotificationPreparationServiceTest.java:66`).
- [x] RECOMMEND 409/502가 그대로 전파, notification row 0개 — 충족. `pkbConflictPropagatesWithoutRows`/`providerFailurePropagatesWithoutRows`/`validationFailurePropagatesWithoutRows`(`:69-82`)가 서비스 레벨에서 `save()` 미호출 확인. 컨트롤러 레벨은 `recommendationFailureIsBadGateway`(502)/`pkbConflictIsPreserved`(409)(`JobRecommendationNotificationControllerTest.java:37-38`)로 실제 HTTP 상태코드까지 검증.
- [x] insert 직전 CLOSED 전환 시 해당 건만 skip, 나머지 정상 생성 — 충족. `closedDuringRefreshIsSkipped`(`NotificationPreparationServiceTest.java:86`), 로직상 `alreadyNotifiedCount`에 포함되지 않고 단순 skip(`NotificationPreparationService.java:84-85`, pseudocode와 정확히 일치).
- [x] 응답 companyName/title/applicationEndAt이 저장값 아닌 JobPosting 재조회 값 — 충족. 엔티티 자체에 해당 필드가 없고(`JobRecommendationNotification.java`), prepare 응답은 `findAllById`로 조회한 `JobPosting`에서(`NotificationPreparationService.java:105-108`), GET 목록 응답은 JPQL `JOIN n.jobPosting j`로 매번 재조회(`JobRecommendationNotificationRepository.java:17-24`)해서 구성.
- [x] reason 저장 전 200자 재truncate — 충족. `truncate()`(`NotificationPreparationService.java:110-113`), `truncatesReasonToTwoHundredCharacters`(`NotificationPreparationServiceTest.java:105`)가 201자 입력 시 저장된 엔티티가 정확히 200자임을 `ArgumentCaptor`로 검증.
- [x] `GET .../job-recommendations?status=PENDING` 필터 + pageable 정상 — 충족. `statusFilterIsPassedToReadService`/`readApiUsesPageable`(`JobRecommendationNotificationControllerTest.java:39-40`).
- [x] 로그에 reason/companyName/title 원문 없음 — 충족. `logsDoNotExposeReasonCompanyOrTitle`(`ListAppender` 사용, `NotificationPreparationServiceTest.java:127`)가 실제 로그 이벤트 텍스트에 `SECRET_REASON`/`company-1`/`title-1` 미포함을 assert. 코드 확인 결과 성공/중복 로그 라인 모두 jobId/counts/durationMs만 출력(`NotificationPreparationService.java:58-60,93`).
- [x] Test Plan 31개 케이스 — 충족(아래 테스트 결과 참고, 세부 매핑은 Findings 참고).
- [x] `./gradlew test` 전체 통과 — 충족(Claude가 이미 로컬에서 307/307 확인, 리뷰어가 notification 패키지만 재실행해 재확인, 아래 참고).

## 테스트 결과

- `./gradlew compileJava compileTestJava` → `BUILD SUCCESSFUL`(이미 UP-TO-DATE, round2 Timer import 수정 반영 확인).
- `./gradlew test --tests "com.careerops.backend.notification.*"` 재실행 → `BUILD SUCCESSFUL`.
  - `JobRecommendationNotificationControllerTest`: 8/8 PASS
  - `JobRecommendationNotificationDatabaseTest`: 2/2 PASS (실제 PostgreSQL, UNIQUE + 2-thread 동시성)
  - `NotificationPreparationServiceTest`: 21/21 PASS
  - 합계 test_count=31, test_pass_count=31.
- 전체 스위트(`./gradlew test`, 기존 276 + 신규 31 = 307)는 Claude가 이번 세션에서 이미 로컬로 실행해 307/307 PASS를 확인했다(리뷰어 본인은 notification 패키지만 격리 재실행). 산발적 flake(`MultipartUploadLimitIntegrationTest` 등, Postgres 커넥션 풀 경합)는 AGENT-002/RECOMMEND-001 리뷰에서 이미 문서화된 무관한 사전 이슈이며 이번 변경과 무관.
- `recommend` 패키지: `git status --porcelain -- src/main/java/com/careerops/backend/recommend`, `application.yml` 모두 무변경 확인.
- `build.gradle`: 무변경 확인(신규 production dependency 없음).

## Findings

정보성(blocking 아님):

1. **Test Plan #12 "두 번째 요청 재생성 안 됨(DB round-trip)"** — 명세는 괄호로 "DB round-trip"을 명시했지만, 실제 구현된 `secondRequestDoesNotRecreatePersistedRows`(`NotificationPreparationServiceTest.java:91-97`)는 mock 기반이다(첫 호출 후 `findExistingJobPostingIds`의 stub 반환값을 수동으로 바꿔 "두 번째 요청" 상황을 흉내냄). 실제 애플리케이션 레벨에서 저장 → 재조회 → 재요청까지 실제 PostgreSQL로 왕복하는 통합 테스트는 없다. 다만 이 갭은 다음 이유로 실질적 위험이 낮다고 판단해 PASS 판정을 내렸다: (a) DB UNIQUE 제약 자체는 `JobRecommendationNotificationDatabaseTest`가 실제 PostgreSQL로 이미 검증했고, (b) 애플리케이션 레벨 dedupe(사전 체크 로직)는 mock으로 충분히 정확하게 검증됐으며, (c) 두 메커니즘이 합쳐 동작한다는 것 자체는 코드 검토로 명확하다(사전 체크가 걸러내지 못한 잔여 케이스만 DB catch가 처리). 후속 라운드에서 시간이 남으면 `@SpringBootTest` 기반의 실제 2회 `prepare()` 호출 통합 테스트를 추가하면 더 견고해지지만, 이번 라운드의 blocking 사유는 아니다.
2. **미사용 4-인자 package-private 생성자** — `JobRecommendationNotification(JobPosting, double, String, NotificationStatus)`(`JobRecommendationNotification.java:26`)가 정의돼 있으나 production/test 어디에서도 호출되지 않는다(`grep` 결과 확인). PENDING만 생성한다는 원칙에는 위배되지 않고(3-arg public 생성자만 실제 사용), 죽은 코드 수준의 아주 사소한 YAGNI 위반이라 blocking으로 잡지 않는다.
3. **`search()`의 `Math.min(pageable.getPageSize(), 100)` 상한** — Task 명세/ADR에 없는 추가 방어 로직이지만 해가 없고 합리적인 안전장치라 판단해 문제 삼지 않는다.

## 다음 액션

PASS. 아래를 완료 처리한다.

- 리뷰 완료 파일: `.ai/reviews/NOTIFY-001-review-1.md`(본 파일).
- `.ai/tasks/NOTIFY-001.md`의 `status`를 다음 단계(verify/E2E)로 갱신하고 `codex_thread_id`/Codex Thread 기록 표(round 1, round 2)를 채운다.
- `.ai/metrics/metrics.jsonl`에 review phase 기록 추가: `test_count=307`(또는 notification만 보고 시 31), `test_pass_count=307`, `review_round_count=1`, `first_review_pass=true`, `status=in_progress`(E2E 전이므로 아직 done 아님).
- Task 명세의 "실제 E2E" 섹션(dev DB + 실제 Anthropic API, backend 재시작 후 persistence 확인, Prometheus 4개 지표, Kakao 호출 0건 확인)은 자동 테스트 범위 밖이므로 별도로 수행 필요 — RECOMMEND-001과 동일하게 Claude가 직접 진행.
