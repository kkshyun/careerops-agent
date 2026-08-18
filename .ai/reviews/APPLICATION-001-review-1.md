---
task_id: APPLICATION-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T16:13:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `POST /api/applications` 유효 요청 → 201 + `JobApplicationResponse`, `status` 생략 시 `INTERESTED` 기본값 — `JobApplicationService.create()` (`backend/src/main/java/com/careerops/backend/application/JobApplicationService.java:36`), 테스트: `JobApplicationControllerTest.createsWithDefaultStatusAndReturnsPostingSnapshot`.
- [x] 존재하지 않는 `jobPostingId` → 404 — `JobApplicationService.create()`:30-31, 테스트: `returnsNotFoundForUnknownPostingAndDoesNotSave`(count 불변까지 검증).
- [x] 중복 `jobPostingId` → 409, 두 번째 row 미생성 — 사전 `existsByJobPostingId` 체크(:32-34) + `DataIntegrityViolationException` catch(:38-42) 이중 방어. 테스트: `rejectsDuplicatePostingWithoutSavingSecondRow`(`repository.count()` 불변 확인).
- [x] `GET /api/applications/{id}` snapshot 필드 반환, 404 — `findResponseById` JOIN 쿼리 사용. 테스트: `getsApplicationAndReturnsNotFoundForUnknownId`.
- [x] `GET /api/applications` 필터 없이 `updatedAt DESC` — `JobApplicationRepository.search()` JPQL `ORDER BY a.updatedAt DESC`. 테스트: `JobApplicationRepositoryTest.filtersByStatusAndSortsByUpdatedAtDescending`, `JobApplicationControllerTest.listsInUpdatedAtDescendingOrderAndFiltersByStatus`.
- [x] `status=SUBMITTED` 필터 — 위 테스트에서 함께 검증(SUBMITTED 2건만 반환, PLANNED 1건 제외).
- [x] pagination 21건 이상, size=20 기본값 — `JobApplicationRepositoryTest.paginatesSearchResults`(21건) + `JobApplicationControllerTest.usesDefaultPaginationAndClampsPageSize`(101건, size=500 요청 시 100으로 clamp까지 확인).
- [x] PATCH `status`만 변경 시 `memo` 유지 — `patchesFieldsIndependentlyAndUpdatesTimestamp` 1차 patch.
- [x] PATCH `memo`만 변경 시 `status` 유지 + `updatedAt` 갱신 — 같은 테스트 2차 patch + `updatedAt` 비교 assert.
- [x] PATCH 존재하지 않는 id → 404 — `returnsNotFoundWhenPatchingUnknownId`.
- [x] DELETE 204, 이후 GET 404, 존재하지 않는 id → 404 — `deletesApplicationWithoutDeletingJobPosting`.
- [x] 삭제 후 연관 `JobPosting` 조회 가능(회귀 없음) — 같은 테스트에서 `GET /api/jobs/{id}` 200 확인.
- [x] `source="MANUAL"` JobPosting에 등록 정상 동작 — 대부분 테스트가 `savePosting(..., "MANUAL", ...)` 사용.
- [x] `source="ALIO"`(recruitmentSteps/attachments 포함) JobPosting에 등록 정상 동작 — `createsForAlioPostingWithEnrichment`(RecruitmentStep/Attachment 저장 후 등록 확인).
- [x] 기존 JobPosting API 회귀 없음 — `git status`로 `job`/`collect` 패키지 미수정 확인 + 전체 테스트 통과.
- [x] 기존 COLLECT 테스트 회귀 없음 — 전체 테스트 스위트에 `AlioCollectionSchedulerTest`/`AlioCollectorServiceTest`/`CollectControllerTest` 포함, 모두 통과.
- [x] `cd backend && ./gradlew test` 전체 실패 0건 — 아래 테스트 결과 참고.

## 코드 검토 상세

1. **`ApplicationStatus` enum** — `backend/src/main/java/com/careerops/backend/application/ApplicationStatus.java`: 정확히 `INTERESTED/PLANNED/SUBMITTED/OFFERED/REJECTED/WITHDRAWN` 6개. `IN_PROGRESS`/`DOCUMENT`/`WRITTEN`/`INTERVIEW` 없음. 명세 그대로.
2. **FK 패턴** — `JobApplication.java:29-31`이 `RecruitmentStep.java:11`과 동일하게 `@ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "job_posting_id", nullable = false)`. raw `Long` 컬럼 아님.
3. **Migration** — `V5__create_job_applications_table.sql`이 명세 SQL과 문자 그대로 일치. `ON DELETE` 절 없음, `UNIQUE(job_posting_id)` 있음, 불필요한 index(`status`/`updated_at`) 없음.
4. **중복 등록 이중 방어** — 위 AC 체크 참고. `catch` 블록이 기존 row를 반환하지 않고 명시적으로 409 `ResponseStatusException`을 던짐(`JobApplicationService.java:38-42`).
5. **N+1 방지** — `JobApplicationRepository.search()`/`findResponseById()` 모두 JPQL constructor expression + `JOIN a.jobPosting p`로 단일 쿼리 조립. `create()`/`update()` 후에도 `findResponseById()` 재사용(`JobApplicationService.java:43`, `:63`), entity→DTO 수동 매핑 없음.
6. **`@Transactional` 미사용** — `JobApplicationService`/`JobApplicationController` 전체에서 `Transactional` import/annotation 없음(grep 확인).
7. **PATCH 부분 수정** — `update()`(`JobApplicationService.java:56-64`)가 `request.status()/memo()/appliedAt()` 각각 `!= null`일 때만 개별 반영. null 필드는 무변경.
8. **`appliedAt` 자동 설정 없음** — `create()`/`update()` 어디에도 status 변경에 따른 `appliedAt` 자동 세팅 로직 없음. `LocalDate.now()` 호출 자체가 코드베이스 신규 패키지에 없음(grep 확인).
9. **기존 JobPosting 코드 미수정** — `git status --short`에서 `backend/src/main/java/com/careerops/backend/job/`, `collect` 관련 파일이 전혀 나타나지 않음(신규 `application` 패키지/디렉토리와 `.ai/tasks/APPLICATION-001.md`, migration 파일만 untracked로 표시).
10. **`metrics.jsonl` 미수정** — `git status --short .ai/metrics/metrics.jsonl` 출력 없음(추적 대상이지만 변경 없음).
11. **테스트 커버리지** — `JobApplicationRepositoryTest`(4개: existsByJobPostingId, JOIN 매핑 정확성, status 필터+정렬, pagination), `JobApplicationControllerTest`(10개: 생성 기본값/404/409/단건조회+404/목록 필터+정렬/pagination clamp/PATCH 독립 변경+updatedAt 갱신/PATCH 404/DELETE+JobPosting 무영향+404/ALIO source). Acceptance Criteria 16개 항목 모두 최소 1개 이상 테스트로 커버됨.

## 테스트 결과

- `./gradlew test --rerun` 직접 재실행(사전조건 `docker compose` postgres/redis 컨테이너 healthy 확인 후 실행): `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml` 집계: **총 80 tests, 0 failures, 0 errors** (기존 66 + 신규 `JobApplicationControllerTest` 10 + `JobApplicationRepositoryTest` 4 = 80). 보고받은 수치와 일치.

## Findings

- 사소한 제안(블로커 아님): PATCH로 `memo`에 빈 문자열 `""`을 보내 지우는 시나리오(명세 §5에 명시된 동작)가 코드상으로는 올바르게 구현돼 있으나(`!= null` 체크이므로 `""`도 반영됨) 전용 테스트 케이스는 없음. Acceptance Criteria에는 명시적으로 요구되지 않아 PASS 판정에 영향 없음 — 필요 시 후속 라운드나 APPLICATION-002 착수 전에 가볍게 추가해도 좋음.
- 그 외 과도한 추상화, 불필요한 신규 dependency, secret 커밋, 근거 없는 자기소개서 관련 생성 로직 등 원칙 위반 사항 없음.

## 다음 액션

- **PASS**. Codex에게 추가 수정 요청 없음.
- `.ai/tasks/APPLICATION-001.md` 상태를 `in_progress` → `done`(또는 프로젝트 컨벤션에 맞는 완료 상태)으로 갱신 필요.
- `.ai/metrics/metrics.jsonl`에 이번 Task의 최종 라운드 결과(2 rounds: 1차 최초 구현, 2차 컴파일 오류 수정, 3차 리뷰에서 PASS) 기록 필요 — Claude가 직접 append.
