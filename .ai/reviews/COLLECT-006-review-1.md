---
task_id: COLLECT-006
review_round: 1
reviewer: claude
reviewed_at: 2026-08-16T19:10:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

- [x] **UNIQUE 제약 실제 적용** — 충족. `V4__add_job_postings_source_external_id_unique.sql`
      (`backend/src/main/resources/db/migration/V4__add_job_postings_source_external_id_unique.sql:1-2`)이
      명세와 동일한 plain `UNIQUE (source, external_id)`이고 데이터 정리 SQL이
      없다. `JobPostingRepositoryTest.rejectsDuplicateSourceAndExternalId`
      (`backend/src/test/java/com/careerops/backend/job/JobPostingRepositoryTest.java:77-88`)가
      두 번째 `saveAndFlush()`에서 `DataIntegrityViolationException`을 검증하고
      실제로 통과한다.
- [x] **순차 재수집 시 중복 없음(회귀)** — 충족.
      `repeatedMultiPageCollectionDoesNotCreateDuplicatesOrRefetchDetails`
      (`AlioCollectorServiceTest.java:292-`)가 유지되고 통과.
- [x] **동시 신규 발견 시 최종 행 수 = 1** — 충족.
      `AlioCollectorConcurrencyTest.concurrentCreateReturnsOneCanonicalRowAndRecordsOneConflict`
      (`backend/src/test/java/com/careerops/backend/collector/AlioCollectorConcurrencyTest.java:51-80`)가
      `CyclicBarrier`로 두 스레드를 동기화해 `createOrGetExisting()`을 동시
      호출하고, `countBySourceAndExternalId==1`, `isNew` 정확히 한쪽만 true,
      양쪽 `getId()` 동일을 검증한다. 클래스 레벨 `@Transactional` 없음
      (`AlioCollectorConcurrencyTest.java:32-33`) — 스레드 간 commit 가시성
      요건 충족.
- [x] **conflict metric** — 충족. 같은 테스트에서
      `careerops.collector.conflict{source=alio}`가 `conflictsBefore + 1`인지
      확인(`AlioCollectorConcurrencyTest.java:78-79`).
      `JobPostingService`의 `conflictCounter` 등록/증가 위치도 명세 그대로
      (`JobPostingService.java:29-31`, `:52`).
- [x] **conflict가 전체 collection run을 실패시키지 않음** — 충족(단, 명세
      문구와 약간 다름). `concurrentCollectorInstancesBothSucceedWithoutDuplicateRows`
      (`AlioCollectorConcurrencyTest.java:82-108`)가 두 개의 별도
      `AlioCollectorService` 인스턴스(동일 싱글톤이 아님 — 인스턴스 락을
      우회해 실제 DB race를 강제로 재현하기 위한 의도적 설계)로 같은
      `externalId`를 가진 신규 item에 대해 동시에 `collect(1)`을 호출,
      양쪽 `result()=="success"`, `saved` 합계 1, 최종 row 1개를 확인한다.
      다만 AC 문구의 "다중 페이지 fixture"가 아니라 단일 페이지 1-item
      fixture다 — 핵심 merge 로직 검증은 되지만 문구와 완전히 일치하진
      않음(사소, blocking 아님).
- [x] **run-level lock 동작** — 충족.
      `AlioCollectorServiceTest.rejectsConcurrentRunImmediatelyAndUnlocksAfterCompletion`
      (`AlioCollectorServiceTest.java:133-159`)가 `FixtureAlioJobClient`의
      `blockNextFetch()`/`awaitFetchEntered()`/`releaseBlockedFetch()`
      (`FixtureAlioJobClient.java:108-123`)로 첫 `collect()`를 fetch 도중
      블로킹시킨 뒤, 두 번째 `collect()`가 1초 이내에
      `AlioCollectionInProgressException`을 던지는지, 락 해제 후 세 번째
      `collect()`가 정상 처리되는지까지 검증한다.
      `AlioCollectorService.collect()`의 `tryLock()`/`finally { unlock() }`
      구조(`AlioCollectorService.java:59-67`)도 명세와 일치.
- [x] **`CollectController` 409** — 충족.
      `CollectControllerTest.returnsConflictWithoutSavingWhenCollectionIsAlreadyRunning`
      (`CollectControllerTest.java:82-98`)가 락 경합 중 HTTP 409와
      `repository.count()` 불변을 확인. `CollectController.java:33-34`의
      catch 순서(`AlioCollectionInProgressException`을 `AlioApiException`보다
      먼저)도 올바름.
- [x] **`AlioCollectionScheduler` 회귀 + 락 경합 시 failure 아님** — 충족.
      `recordsLockContentionAsSkippedInsteadOfFailure`
      (`AlioCollectionSchedulerTest.java:102-124`)가 `skipped+1`,
      `failure` 불변을 확인. `AlioCollectionScheduler.java:50-52`에서
      `AlioCollectionInProgressException`을 `AlioApiException`/`RuntimeException`보다
      먼저 catch, `runCounter("skipped")` 증가 + INFO 로그(WARN 아님) —
      명세와 정확히 일치.
- [x] **기존 status 갱신 동작 회귀 없음** — 충족.
      `updatesOnlyStatusWhenExistingPostingStatusChanges`,
      `updatesExistingStatusWhenPostingAppearsOnLaterPage` 유지 및 통과.
      `handleExisting()` 추출(`AlioCollectorService.java:145-152`)이
      신규(`createOrGetExisting` 충돌) 분기와 기존 분기 양쪽에서 재사용되어
      중복 코드 없음(`AlioCollectorService.java:111-127`).
- [x] **detail enrichment 회귀 없음** — 충족.
      `enrichesNewPostingOnceAndDoesNotRefetchWhenRediscovered`,
      `isolatesOneDetailFailureWhileSavingAllListItems`,
      `enrichesPreviouslyUnfetchedPostingDuringStatusUpdate` 유지 및 통과.
- [x] **pagination 회귀 없음** — 충족. 관련 4개 테스트
      (`paginatesWithFixedPageSizeSlicesAtCallerLimitAndRecordsPages` 등)
      유지 및 통과.
- [ ] **`GET /api/jobs` 회귀 없음** — **미충족.**
      `JobPostingControllerTest.getsFilteredJobsWithListResponseFieldsAndFixedOrder()`가
      `DataIntegrityViolationException`으로 실패(아래 "테스트 결과" 및
      "원인 분석" 참고). Codex가 이 파일을 이번 diff에서 전혀 수정하지 않았다
      (`git status`에 `JobPostingControllerTest.java` 없음).
- [ ] **`./gradlew test` 전체 통과** — **미충족.** 66개 중 2개 실패(아래
      상세). `careerops_test` 격리/`AlioCollectorConcurrencyTest`의
      `@AfterEach` 정리 로직 자체는 올바르게 작성되어 있음(정리 필터가
      `sourceUrl.contains("collect-006")`이고 두 테스트 모두 sourceUrl에
      `"collect-006"`이 포함되도록 구성되어 매칭 확인함 —
      `AlioJobMapper.java:31`의 `item.srcUrl()` → `sourceUrl` 매핑과
      `AlioJobItem` 필드 순서 확인 완료). 하지만 전체 통과 자체가 안 됨.

## 원인 분석 (오케스트레이터 추정 검증)

오케스트레이터의 추정이 **정확했다.** 두 실패 테스트 모두 private `save()`
헬퍼가 `JobPosting` 생성자의 마지막 인자(`externalId`)에 `companyName`을
그대로 재사용한다:

- `backend/src/test/java/com/careerops/backend/job/JobPostingControllerTest.java:168-174`
  ```java
  private JobPosting save(
          String companyName, String status, String careerLevel, String jobCategory, LocalDate applicationEndAt) {
      return repository.save(new JobPosting(
              companyName, "공고", null, careerLevel, null, status, null, jobCategory, null,
              null, applicationEndAt, "ALIO", null, companyName   // <- 마지막 인자 = externalId = companyName
      ));
  }
  ```
  `getsFilteredJobsWithListResponseFieldsAndFixedOrder()`
  (`JobPostingControllerTest.java:107-113`)가 `"한국전력공사"`로 3번
  `save()`를 호출 → `source="ALIO", externalId="한국전력공사"`가 3번 반복돼
  2번째 호출부터 `V4` UNIQUE 제약 위반.

- `backend/src/test/java/com/careerops/backend/job/JobPostingRepositoryTest.java:140-146`도
  동일한 패턴. `searchesByEachFilterAndCombinesFiltersWithAnd()`
  (`JobPostingRepositoryTest.java:91-96`)가 `"한국전력공사"`로 2번 `save()`
  호출(OPEN 1번, CLOSED 1번) → 2번째 호출에서 동일하게 위반.

이 UNIQUE 제약이 도입되기 전에는 문제없었던 패턴이 이번 Task로 깨진 것이
맞다. 같은 클래스의 다른 테스트(`usesDefaultPaginationAndReturnsSecondPage`,
`clampsRequestedPageSizeToOneHundred`, `sortsByApplicationEndAtAscendingWithNullLast`,
`paginatesSearchResults`)는 매 호출마다 `companyName`이 서로 달라
(`"기관" + index` 등) externalId도 자동으로 달라지므로 우연히 위반을
피했다 — 근본 원인은 두 실패 테스트에 국한되지 않고 "헬퍼가 externalId를
companyName에서 파생시킨다"는 설계 자체다.

### 구체적 수정 요청 (Codex에게 그대로 전달)

1. `backend/src/test/java/com/careerops/backend/job/JobPostingControllerTest.java`의
   `save()` 헬퍼(168-174행)에서 `JobPosting` 생성자의 마지막 인자를
   `companyName`이 아니라 호출마다 유일한 값으로 바꾼다. 예:
   `java.util.concurrent.atomic.AtomicInteger`(또는 `java.util.UUID`) 기반의
   유일 값을 생성해 `"ALIO", null, uniqueExternalId` 형태로 넘긴다.
   (companyName과 무관해야 하며, 같은 테스트 실행 안에서 여러 번 호출해도
   서로 겹치지 않아야 한다.)
2. `backend/src/test/java/com/careerops/backend/job/JobPostingRepositoryTest.java`의
   동일한 이름/동일한 패턴의 `save()` 헬퍼(140-146행)도 같은 방식으로
   수정한다.
3. 두 파일 모두 `externalId`를 테스트 로직(검색/필터/정렬 assertion)에서
   직접 참조하지 않으므로, 값 자체는 임의로 유일하기만 하면 되고
   `companyName`/`status`/`careerLevel`/`jobCategory`/`applicationEndAt`
   등 다른 필드나 기존 assertion에는 영향이 없어야 한다. 수정 후
   `./gradlew test --rerun-tasks`로 전체 통과(66/66)를 재확인해달라.
4. (선택, blocking 아님) `docs/ROADMAP.md`에 Task 명세 Context 3번/Out of
   Scope에서 언급한 "`AlioDetailEnrichmentService` 트랜잭션 재구조화"
   후속 Task 후보가 아직 기록되지 않았다. Task 명세가 "후속 Task 후보로
   분리한다(`docs/ROADMAP.md`에 기록)"라고 명시했으므로 반영 요청.

## 테스트 결과

Claude가 `cd backend && ./gradlew test --rerun-tasks` 직접 실행(캐시 배제).
`BUILD FAILED`, `64 tests completed, 2 failed`:

- `JobPostingControllerTest > getsFilteredJobsWithListResponseFieldsAndFixedOrder()`
  — `DataIntegrityViolationException`(unique violation), 위 원인 분석 참고.
- `JobPostingRepositoryTest > searchesByEachFilterAndCombinesFiltersWithAnd()`
  — 동일 원인.

나머지 64개(신규 concurrency/lock 관련 테스트 포함)는 모두 통과 —
`AlioCollectorConcurrencyTest`(2개), `AlioCollectorServiceTest`의 신규 락
테스트, `AlioCollectionSchedulerTest`의 신규 skip 테스트,
`CollectControllerTest`의 신규 409 테스트, `JobPostingRepositoryTest`의
신규 `rejectsDuplicateSourceAndExternalId`를 포함해 전부 정상.

## Findings

- **버그(위 원인 분석 참고, blocking)**: `JobPostingControllerTest`/
  `JobPostingRepositoryTest`의 `save()` 헬퍼가 신규 UNIQUE 제약과 충돌.
  `./gradlew test` 전체 통과 및 `GET /api/jobs` 회귀 없음 AC 미충족.
- **Out of Scope 준수**: `AlioDetailEnrichmentService`,
  `ManualImportService`, `.ai/metrics/metrics.jsonl` 모두 이번 diff에서
  수정되지 않음(`git status`로 확인) — 준수.
- **신규 dependency 없음**: `ReentrantLock`은 JDK 표준 라이브러리, 별도
  등록/기록 불필요.
- **Secret 노출 없음**: diff에 credential/token 패턴 없음.
- **native SQL/분산 락 도입 없음**: migration은 plain `ALTER TABLE ADD
  CONSTRAINT`뿐, `ON CONFLICT`나 Redis 등 사용 안 함 — 명세 그대로.
- **과도한 추상화 없음**: `CreateOutcome` record, `handleExisting()` 추출
  모두 명세에서 제시한 최소 구조 그대로이며 중복 코드 제거 목적에 부합.
- **자기소개서/근거 기반 검증 원칙**: 이번 Task와 무관(해당 없음).
- 사소(선택 사항, blocking 아님): `docs/ROADMAP.md`에 `AlioDetailEnrichmentService`
  트랜잭션 재구조화 후속 Task 기록이 아직 없음(Task 명세 Context 3번에서
  명시적으로 요청한 사항).

## 다음 액션

**NEEDS_REVISION** — 같은 Codex thread에 위 "구체적 수정 요청 1~3"을 그대로
전달(4번은 선택). 두 테스트 헬퍼 파일의 `externalId` 파생 로직만 고치면
되는 국소적인 수정이며, 나머지 Acceptance Criteria(UNIQUE 제약,
`createOrGetExisting`, run-level lock, 409, scheduler skip, 기존 회귀,
conflict metric, concurrency 테스트)는 모두 충족되어 있으므로 이번은
1라운드 통상적인 NEEDS_REVISION으로 판단(Task 명세 자체의 문제는 아님 —
헬퍼 로직 누락이 원인).
