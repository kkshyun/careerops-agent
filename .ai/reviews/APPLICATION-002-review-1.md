---
task_id: APPLICATION-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T17:15:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `POST .../stages` 유효 요청 → 201, `sortOrder` 1부터 자동 할당, `result` 기본값 `PENDING` — `ApplicationStageService.create()` (`backend/src/main/java/com/careerops/backend/application/ApplicationStageService.java:26-42`). 테스트: `ApplicationStageControllerTest.createsWithAutomaticOrderAndDefaultResultAndAllowsRepeatedType`(sortOrder 1,2,3 검증, `result` PENDING 검증).
- [x] 존재하지 않는 `applicationId` → 404 — `findApplication()`(`ApplicationStageService.java:78-81`). 테스트: `rejectsUnknownApplicationAndMissingStageType`.
- [x] 같은 `stageType` 반복 생성 가능 — DB/코드 어디에도 `stageType` 단독 제약 없음(엔티티/마이그레이션 확인). 테스트: `createsWithAutomaticOrderAndDefaultResultAndAllowsRepeatedType`에서 INTERVIEW 2회 생성 후 count=3 확인.
- [x] `sortOrder` 생략 시 1,2,3 순 자동 증가 — `findTopByJobApplicationIdOrderBySortOrderDesc(...).map(+1).orElse(1)`(`ApplicationStageService.java:28-31`). 같은 테스트로 검증.
- [x] 이미 사용 중인 `sortOrder` 명시 지정 → 409 — `DataIntegrityViolationException` catch → `HttpStatus.CONFLICT`(`ApplicationStageService.java:39-41,88-90`). 테스트: `rejectsDuplicateExplicitOrderWithoutSaving`(단, "새 row가 생성되지 않는다" 부분은 status 코드만 확인하고 `repository.count()` 명시 assertion은 없음 — DB UNIQUE 제약상 구조적으로 보장되므로 기능적 결함은 아니지만 테스트 엄밀성 관점의 사소한 보완 포인트, 아래 Findings 참고).
- [x] `GET .../stages` sortOrder ASC 반환 — `findByJobApplicationIdOrderBySortOrderAsc`. 테스트: `listsInOrderAndGetsOnlyFromOwningApplication`.
- [x] `GET .../stages/{id}` 단건 조회, 존재하지 않는 id/다른 application 소속 → 404 — `findByIdAndJobApplicationId` 소속 검증(`ApplicationStageService.java:83-86`). 테스트: 같은 메서드에서 owner/other/미존재 id 3가지 케이스 모두 확인.
- [x] `PATCH` `scheduledAt`만 변경 시 나머지 필드 유지 — `update()`가 각 필드 `!= null`일 때만 반영(`ApplicationStageService.java:60-64`). 테스트: `patchesFieldsIndependentlyWithoutChangingApplicationStatus` 1차 patch.
- [x] `PATCH` `result` → `PASSED` 반영, `JobApplication.status` 불변 — 같은 테스트 2차 patch + 마지막에 `applicationRepository...getStatus()`가 `SUBMITTED` 그대로임을 assert.
- [x] `PATCH` `memo`만 변경 시 나머지 필드 유지 — 같은 테스트 3차 patch(`stageType` 불변까지 확인).
- [x] `DELETE` 204, 이후 단건조회 404, 존재하지 않는 id → 404 — `deletesOnlyTargetStageAndHandlesUnknownIds`.
- [x] 삭제 후 다른 stage/`JobApplication` 그대로 조회 가능 — 같은 테스트에서 `remaining` stage/`application` 존재 확인.
- [x] `JobApplication` 삭제 시 `ApplicationStage` CASCADE 삭제 — migration `ON DELETE CASCADE`(`V6__create_application_stages_table.sql:3`). 테스트: `ApplicationStageRepositoryTest.deletingApplicationCascadesToStages`(round 2에서 `TestEntityManager.flush()+clear()`로 stale reference 문제 수정 후 정상 통과 확인).
- [x] `GET /api/applications` 목록 응답에 `stages` 없음 — `JobApplicationResponse`에 필드 자체가 없음. 테스트: `listResponseDoesNotContainStagesEvenWhenStagesExist`(`$.content[0].stages` doesNotExist, stage 1건 존재 상태에서 확인).
- [x] 목록 조회 N+1 없음 — 코드 경로 확인: `JobApplicationService.search()`(`JobApplicationService.java:59-62`)가 `applicationStageRepository`를 전혀 참조하지 않음(grep 확인). Technical Notes에 명시된 "코드 경로상 참조 없음"으로 검증하는 방식과 일치. (테스트의 stage 개수는 1건뿐이라 "여러 건씩"이라는 AC 문구를 문자 그대로 재현하지는 않지만, 코드 구조상 애초에 stage 쿼리 자체가 호출되지 않으므로 개수와 무관하게 회귀 불가능 — 기능적으로 결함 아님.)
- [x] `GET /api/applications/{id}` 상세에 `stages` sortOrder ASC 포함 — `JobApplicationService.findById()`(`JobApplicationService.java:51-56`). 테스트: `getsApplicationDetailWithStagesSortedByOrder`.
- [x] APPLICATION-001 회귀 — 기존 `JobApplicationControllerTest`/`JobApplicationRepositoryTest` 케이스 전부 유지, `findById` 반환 타입 변경에 따라 `stages` 관련 assertion만 추가(`doesNotExist`/새 상세 테스트). 전체 스위트 통과로 회귀 없음 확인.
- [x] 기존 JobPosting/COLLECT 회귀 없음 — `git status --short`상 `job`/`collect` 패키지 파일 변경 없음, 전체 테스트 스위트에 관련 테스트 포함되어 통과.
- [x] `cd backend && ./gradlew test` 전체 실패 0건 — 아래 테스트 결과 참고(직접 재실행 확인).

## 코드 검토 상세

1. **FK 패턴** — `ApplicationStage.java:28-30`이 `JobApplication.java`의 기존 `@ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(nullable = false)` 패턴과 완전히 동일. raw `Long` 컬럼 아님.
2. **Enum 값** — `StageType.java`: `DOCUMENT/CODING_TEST/WRITTEN/INTERVIEW/FINAL/OTHER` 정확히 일치. `StageResult.java`: `PENDING/PASSED/FAILED/CANCELLED` 정확히 일치, entity 컬럼 `nullable = false`(`ApplicationStage.java:46`).
3. **Migration** — `V6__create_application_stages_table.sql`이 명세 SQL과 문자 그대로 일치. `ON DELETE CASCADE` 있음, `UNIQUE(job_application_id, sort_order)` 있음(`uk_application_stages_application_sort_order`), `stage_type` 단독 index 없음.
4. **`sortOrder` 자동 할당/409 변환** — `create()`에서 `request.sortOrder() == null`일 때만 `findTopByJobApplicationIdOrderBySortOrderDesc` 기반 자동 계산, 명시 지정 시 그대로 사용. `saveAndFlush` 후 `DataIntegrityViolationException`을 `ResponseStatusException(CONFLICT)`로 변환 — APPLICATION-001의 `jobPostingId` 중복 catch 패턴과 동일 구조. `update()`에도 동일 catch 적용(PATCH로 `sortOrder` 변경 시 충돌도 방어).
5. **`stageType` 반복 허용** — entity/migration/service 어디에도 `stageType` 단독 유일성 제약 없음(grep 확인, `label`만으로 사용자가 구분하는 설계 그대로).
6. **Nested API 5종** — `ApplicationStageController.java`에 `POST/GET(list)/GET(single)/PATCH/DELETE` 전부 `/api/applications/{applicationId}/stages` 하위로 매핑. 서비스 레이어에서 모든 메서드가 `findApplication(applicationId)`로 부모 존재 확인 먼저 수행하고, 단건/PATCH/DELETE는 `findStage()`(`findByIdAndJobApplicationId`)로 소속 검증까지 수행(`ApplicationStageService.java:44-76`).
7. **PATCH 컨벤션** — `update()`가 `label/sortOrder/scheduledAt/result/memo` 각각 `!= null`일 때만 반영, `stageType`은 애초에 `ApplicationStageUpdateRequest`에 필드 자체가 없어 수정 경로 자체가 없음(`dto/ApplicationStageUpdateRequest.java`).
8. **상세/목록 응답 분리** — `JobApplicationController.findById()`가 `JobApplicationDetailResponse` 반환(`JobApplicationController.java:40`), `search()`/`create()`/`update()`는 기존 `JobApplicationResponse` 그대로. `JobApplicationService.findById()`는 `findResponseById()`(기존 JOIN) + `applicationStageRepository.findByJobApplicationIdOrderBySortOrderAsc()` 2-query 조합(`JobApplicationService.java:51-56`, 명세 §8과 정확히 일치). `search()`(`:59-62`)는 `applicationStageRepository`를 참조하지 않아 목록 경로에 stage 쿼리 없음 확인.
9. **`status` 자동 전이 없음** — `grep -rn "updateStatus"`가 `JobApplication.java`(getter 정의)와 `JobApplicationService.update()`의 `request.status()` 분기 한 곳에서만 매치. `ApplicationStage`/`ApplicationStageService` 어디에도 `JobApplication.updateStatus` 호출 없음. `JobApplication.java`에 `@OneToMany` 역참조 필드도 없음(명세 §1 요구사항대로).
10. **`@Transactional` 미사용** — `grep -rn "@Transactional" backend/src/main/java/com/careerops/backend/application/` 결과 0건.
11. **신규 dependency 없음** — `git diff -- backend/build.gradle` 출력 없음(무변경).
12. **`job`/`collector` 패키지 미수정** — `git status --short`에 해당 경로 파일 전혀 없음.
13. **ADR-0017 vs 구현 일치** — `docs/DECISIONS.md`에 추가된 ADR-0017의 "CASCADE + 단방향 `@ManyToOne`만, `CascadeType`/`@OneToMany` 없음" 내용이 실제 migration/entity와 정확히 일치.

## 테스트 결과

- 직접 `./gradlew test --rerun` 실행(사전 `docker compose ps`로 postgres/redis healthy 확인 후): `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml` 16개 파일 파싱 집계: **총 92 tests, 0 failures, 0 errors**. 보고받은 수치(92/92)와 일치, 직접 재확인 완료.
- 신규 테스트 12개: `ApplicationStageRepositoryTest` 3(`findsStagesSortedBySortOrder`/`findsStageOnlyWhenItBelongsToApplication`/`deletingApplicationCascadesToStages`), `ApplicationStageControllerTest` 7(`createsWithAutomaticOrderAndDefaultResultAndAllowsRepeatedType`/`rejectsUnknownApplicationAndMissingStageType`/`rejectsDuplicateExplicitOrderWithoutSaving`/`listsInOrderAndGetsOnlyFromOwningApplication`/`patchesFieldsIndependentlyWithoutChangingApplicationStatus`/`returnsNotFoundWhenPatchingWrongOwnerOrUnknownStage`/`deletesOnlyTargetStageAndHandlesUnknownIds`), `JobApplicationControllerTest` 신규 2(`getsApplicationDetailWithStagesSortedByOrder`/`listResponseDoesNotContainStagesEvenWhenStagesExist`).

## Findings

블로커 없음. 사소한 테스트 엄밀성 보완 제안(PASS 판정에 영향 없음):

1. `rejectsDuplicateExplicitOrderWithoutSaving` 테스트가 409 상태 코드만 확인하고 `stageRepository.count()` 같은 명시적 "새 row 미생성" assertion이 없다. AC 문구("새 row가 생성되지 않는다")를 문자 그대로 검증하려면 APPLICATION-001의 `rejectsDuplicatePostingWithoutSavingSecondRow` 패턴(카운트 비교)을 참고해 보완할 수 있다. 다만 DB UNIQUE 제약상 예외가 던져진 시점에 INSERT가 실제로 반영되지 않으므로 기능적으로는 이미 보장되어 있다.
2. N+1 회귀 테스트(`listResponseDoesNotContainStagesEvenWhenStagesExist`)가 stage 1건짜리 application 1건으로만 검증한다. AC 문구의 "stage가 여러 건씩 존재하는 상태"를 문자 그대로 재현하지는 않지만, `search()` 코드 경로 자체가 `ApplicationStageRepository`를 참조하지 않으므로(Technical Notes가 허용한 검증 방식) 개수와 무관하게 회귀가 구조적으로 불가능하다.
3. `patchesFieldsIndependentlyWithoutChangingApplicationStatus`가 `result` PATCH 이후 "재조회"를 별도 GET으로 하지 않고 PATCH 응답 body만으로 반영을 확인한다. PATCH 응답이 `saveAndFlush()` 결과를 그대로 매핑하므로 실질적으로 동일하지만, 엄밀히는 별도 GET 호출이 더 명확하다.

과도한 추상화, 불필요한 신규 dependency, secret 커밋, 근거 없는 자기소개서 생성 로직 등 원칙 위반 사항 없음.

## 다음 액션

- **PASS**. Codex에게 추가 수정 요청 없음(위 Findings는 선택적 보완 제안일 뿐, blocking 아님).
- `.ai/tasks/APPLICATION-002.md` 상태를 `in_progress` → `done`으로 갱신 필요.
- `.ai/metrics/metrics.jsonl`에 review/done phase 기록 추가 필요(2 rounds: 1차 최초 구현 + 테스트 실패, 2차 테스트 수정 후 92/92 통과, 리뷰 1라운드에서 PASS).
