---
task_id: JOB-003
review_round: 1
reviewer: claude
reviewed_at: 2026-08-16T00:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] **상세정보 있는 JobPosting 조회** — 충족.
      `JobPostingControllerTest.getsRecruitmentStepsAndAttachmentsWithPublicFieldsInStableOrder`
      (`backend/src/test/java/com/careerops/backend/job/JobPostingControllerTest.java:107-140`)가
      `RecruitmentStep` 2건 + `Attachment` 2건을 직접 저장 후
      `GET /api/jobs/{id}`로 조회해 `recruitmentSteps`/`attachments`에
      요구된 6/4개 필드만 존재하고 `id`/`recrutStepSn`/`minStepSn`/
      `maxStepSn`/`jobPosting`/`createdAt`(steps),
      `id`/`recrutAtchFileNo`/`jobPosting`/`createdAt`(attachments)이
      `doesNotExist()`임을 검증한다. `JobPostingDetailResponse.from()`
      (`dto/JobPostingDetailResponse.java:35-55`)도 기존
      `JobPostingResponse`의 15개 필드를 그대로 포함한다.
- [x] **RecruitmentStep 정렬** — 충족.
      같은 테스트에서 `sortNo=1`로 동일하고 `recrutStepSn`이 다른(9002L,
      9001L, 삽입 순서는 9002 먼저) 두 건을 저장한 뒤,
      `recruitmentSteps[0].stepGroupName`이 `recrutStepSn=9001`(더 작은
      값, "서류")이어야 함을 검증 — 삽입 순서와 반대이므로 실제 정렬
      로직이 동작함을 확인한다. `RecruitmentStepRepository.java:9`의
      `findByJobPostingIdOrderBySortNoAscRecrutStepSnAsc`가 파생 쿼리로
      `sortNo ASC, recrutStepSn ASC`를 정확히 구현한다.
- [x] **Attachment 정렬** — 충족. 동일한 방식(`recrutAtchFileNo` 8002 먼저
      저장, 8001 나중 저장 → 응답은 8001 "첫번째.hwp"가 먼저)으로 검증됨.
      `AttachmentRepository.java:9`의
      `findByJobPostingIdOrderBySortNoAscRecrutAtchFileNoAsc`가 대응.
- [x] **steps/files 없는 JobPosting** — 충족.
      `getsEmptyDetailArraysForAlioJobWithoutDetails`
      (JobPostingControllerTest.java:143-152)가 ALIO source로 저장 직후(
      steps/attachments 미저장) 조회해 두 필드 모두 `isArray()` +
      `isEmpty()`(빈 배열, null 아님)임을 검증. `JobPostingService.findById`
      (`JobPostingService.java:78-90`)는 항상 repository 조회 결과를
      `.stream().map(...).toList()`로 변환하므로(빈 리스트 조회 시에도
      빈 List 반환, null 아님) 로직상으로도 일관됨.
- [x] **존재하지 않는 JobPosting** — 충족. `returnsNotFoundForUnknownId`
      (변경 없음, JobPostingControllerTest.java:154-157)가 그대로 유지되고
      `findById` 로직의 404 분기(`JobPostingService.java:78-86`)도 기존과
      동일한 `notFoundCounter.increment()` +
      `ResponseStatusException(HttpStatus.NOT_FOUND)` 구조를 그대로
      보존한다(응답 바디/에러 계약 무변경).
- [x] **MANUAL JobPosting 상세조회** — 충족.
      `getsExistingJobPosting`(JobPostingControllerTest.java:83-102)이
      `source="MANUAL"`로 저장한 뒤 기본 필드 검증에
      `recruitmentSteps`/`attachments` `isEmpty()` 단언을 추가했다(
      diff 참고).
- [x] **`GET /api/jobs` 목록 회귀 없음** — 충족.
      `getsFilteredJobsWithListResponseFieldsAndFixedOrder`
      (JobPostingControllerTest.java:188-189)에
      `jsonPath("$.content[0].recruitmentSteps").doesNotExist()` /
      `.attachments").doesNotExist()`가 추가됐다. `JobPostingController
      .search`/`JobPostingService.search`/`JobPostingListResponse`는 diff에
      전혀 등장하지 않아(코드 리딩 확인) 목록 경로는 완전히 무변경이며,
      JOB-002 필터/정렬/pagination 테스트
      (`getsFilteredJobsWithListResponseFieldsAndFixedOrder`,
      `usesDefaultPaginationAndReturnsSecondPage`,
      `clampsRequestedPageSizeToOneHundred`)도 그대로 남아 있다.
- [x] **기존 COLLECT 테스트 회귀 없음** — 충족.
      `AlioCollectorServiceTest.java`, `AlioDetailEnrichmentServiceTest.java`,
      `AlioCollectorConcurrencyTest.java` 모두 `git diff --stat`상 변경 없음
      (0 diff). 이 테스트들이 쓰는 `findByJobPostingId(Long)`(정렬 없음)도
      `RecruitmentStepRepository.java`/`AttachmentRepository.java` diff에서
      삭제/변경되지 않고 새 메서드만 추가됐음을 확인.
- [x] **전체 테스트 통과** — 충족(호출 Claude가 이미 실행/확인:
      `./gradlew test` 66/66 통과, BUILD SUCCESSFUL, JUnit XML
      tests=66/failures=0/errors=0). 신규 테스트 2건(
      `getsRecruitmentStepsAndAttachmentsWithPublicFieldsInStableOrder`,
      `getsEmptyDetailArraysForAlioJobWithoutDetails`)이 이 66건에 포함됨
      (기존 테스트 개수 대비 +2 증가로 정합).
- [x] **Git tracked file에 secret 없음 / 신규 dependency 없음** — 충족.
      `git diff`에 `build.gradle`/`settings.gradle`/`.env` 등의 변경이
      전혀 없고, 5개 수정 파일 + 3개 신규 DTO 모두 순수 애플리케이션
      코드다.

## Scope 세부 확인

- **필드 노출 범위** — `RecruitmentStepResponse`
  (`dto/RecruitmentStepResponse.java:5-11`)는 `sortNo`/`stepGroupName`/
  `competitionRate`/`applicantCount`/`recruitCount`/`occurredAtRaw` 6개만
  정확히 포함하고 `id`/`recrutStepSn`/`minStepSn`/`maxStepSn`/
  `jobPosting`/`createdAt`을 제외한다(엔티티 `RecruitmentStep.java`의
  전체 필드와 대조 확인). `AttachmentResponse`
  (`dto/AttachmentResponse.java:5-9`)도 `sortNo`/`fileName`/`fileType`/
  `url` 4개만 포함하고 `id`/`recrutAtchFileNo`/`jobPosting`/`createdAt`을
  제외한다. Task 명세 Scope 2·3항과 정확히 일치.
- **정렬 기준** — `RecruitmentStepRepository.java:9`,
  `AttachmentRepository.java:9`의 Spring Data 파생 메서드명
  (`findByJobPostingIdOrderBySortNoAscRecrutStepSnAsc`,
  `findByJobPostingIdOrderBySortNoAscRecrutAtchFileNoAsc`)이 Task 명세
  Scope 4항이 요구한 `sortNo ASC` + 동일 `sortNo` 내 자연키 ASC를
  파생 쿼리 문법으로 정확히 반영한다(Repository 쿼리 자체가 정렬
  기준이 되므로 Service/Controller에서 별도 정렬 로직 불필요 — 실제로도
  없음).
- **Out of Scope 준수**:
  - migration 파일 3개(`V2`/`V3`/`V4__*.sql`) 모두 `git diff`에 등장하지
    않음 — 새 migration 없음 확인.
  - `Counter.builder(...)` 호출부(`JobPostingService.java:34-38`)가
    diff 전후로 동일 — 새 metric 추가 없음.
  - `JobPostingService.search(...)`/`JobPostingController` 목록
    엔드포인트/`JobPostingRepository.java`/`JobPostingListResponse.java`
    모두 diff에 없음 — 목록 API 무변경 확인.
  - `findByJobPostingId`(정렬 없음, 기존 COLLECT 테스트가 사용) 삭제/수정
    없이 그대로 유지, 새 정렬 메서드만 추가.
  - `EntityGraph`/fetch join 등 도입 없음 — `JobPostingService.findById`는
    Task 명세 예시와 동일하게 3개의 독립 쿼리(`repository.findById`,
    `recruitmentStepRepository.find...`, `attachmentRepository.find...`)
    구조를 그대로 사용한다.

## 스타일/설계 일관성

- `JobPostingDetailResponse`는 Task 명세 권고대로 `JobPostingResponse`를
  상속/재사용하지 않고 record 필드를 전부 나열하는 동일한 스타일을
  따른다 — 기존 코드베이스의 Lombok/MapStruct 미사용 컨벤션과 일치하고
  과도한 추상화(별도 mapper, base interface 등)를 추가하지 않았다.
- `JobPostingService`는 새 Query Service 계층을 만들지 않고 기존
  `findById`를 확장하는 방식을 그대로 따랐다(Scope 5항, 명세 예시와
  거의 동일한 코드).
- Controller는 여전히 Service 하나만 호출하고(`JobPostingController
  .java:38`), 여러 Repository를 직접 다루지 않는다.
- 새로 추가된 두 테스트가 삽입 순서와 반대로 정렬을 검증하도록 설계돼
  있어("정렬이 실제로 동작하지 않으면 실패하는" 테스트) 단순히 저장
  순서를 그대로 반환해도 우연히 통과하는 약한 테스트가 아니다 — 이 점을
  긍정적으로 평가.

## 테스트 결과

- 별도 재실행 없음(호출 Claude가 이미 확인 완료 사실을 전달받아 신뢰):
  `cd backend && ./gradlew test` → 66/66 통과 (BUILD SUCCESSFUL, JUnit XML
  tests=66 failures=0 errors=0). 신규 테스트 2건이 이 안에 포함되어
  있음을 diff 상 테스트 클래스 구조로 확인.
- 추가로 코드 리딩만으로 재확인한 사항(재실행 아님): `AlioCollectorServiceTest`
  / `AlioDetailEnrichmentServiceTest` / `AlioCollectorConcurrencyTest` 3개
  파일 모두 `git diff --stat` 결과 변경 라인 0 — 회귀 위험 최소화 확인.
- 호출 Claude의 수동 검증(로컬 실행, dev DB 실공고 id=182, 목록/404 확인)
  결과도 diff 상 코드 구조와 일치한다 — 별도 모순 없음.

## Findings

- 블로킹 이슈 없음.
- 사소한 관찰(비차단): 신규 테스트 2건 모두 `save()` 헬퍼로
  `source="ALIO"`를 사용하는데, 두 번째 테스트
  (`getsRecruitmentStepsAndAttachmentsWithPublicFieldsInStableOrder`)는
  실제로는 `RecruitmentStep`/`Attachment`를 테스트 안에서 직접
  `recruitmentStepRepository.saveAllAndFlush(...)`로 저장하므로 ALIO
  enrichment 파이프라인을 거치지 않는다 — 이는 Task 명세 Test Plan에
  명시된 방식("Repository로 직접 저장")과 정확히 일치하므로 문제
  아님. 참고로만 남김.
- 자기소개서/근거 기반 검증 원칙과는 무관한 범위(단순 조회 API)이므로
  해당 원칙 위반 가능성 자체가 없음.

## 다음 액션

- **PASS.** 추가 수정 요청 없음. `.ai/tasks/JOB-003.md`의 status를
  `in_progress` → `done`으로 갱신하고 `.ai/metrics/metrics.jsonl`에
  최종 상태(라운드 1 PASS, test 66/66)를 기록할 것을 호출 Claude에게
  권장.
