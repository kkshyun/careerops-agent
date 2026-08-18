---
task_id: PKB-004
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T18:40:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

1. `POST` `title`만 → 201, 나머지 필드 null — **충족**.
   `AwardControllerTest.createsMinimalAndFullAward` (`AwardControllerTest.java:26-31`):
   `{"title":"대상"}`만 보내고 `issuer`/`awardedDate`/`description`이 전부
   `isEmpty()`임을 확인.
2. `POST` 전체 필드(issuer/awardedDate/description) 포함 → 201, 응답이 요청과
   일치 — **충족**. 같은 테스트 `L33-40`: issuer/awardedDate/description
   3개 필드 모두 요청값과 응답값을 `jsonPath`로 1:1 대조.
3. `POST` `title` 누락 → 400, row 미생성 — **충족**.
   `rejectsMissingTitleWithoutCreatingRows` `L44-49`: `{}` 요청 → 400,
   `repository.count()`를 요청 전후로 비교해 row 미생성 확인.
4. `GET` pagination 동작, `awardedDate DESC NULLS LAST` 정렬 실동작(날짜
   없는 항목이 마지막) — **충족**.
   `listsWithPaginationClampDefaultSizeAndFixedOrdering` `L52-68`:
   파라미터 없이 호출 → `$.size==20`(default), 3건(최근/과거/날짜없음)
   저장 후 `content[0..2]`가 "최근"→"과거"→"날짜 없음" 순서임을 직접
   assert(날짜 없는 항목이 실제로 마지막에 옴을 확인). `size=1000` →
   `$.size==100`(clamp 실동작), `page=1&size=2` → 두 번째 페이지에
   "날짜 없음" 1건만 남음을 확인. `AwardRepositoryTest.
   ordersByAwardedDateDescendingWithNullsLast`(`L30-38`)로 repository
   레벨에서도 동일 순서 재확인(entity 레벨 이중 검증).
   `AwardService.findAll()`(`AwardService.java:22-26`)의
   `Math.min(pageable.getPageSize(), 100)` 구현과 일치. `AwardController.
   findAll()`은 `Pageable pageable`만 받고 정렬/필터 파라미터를 추가로
   노출하지 않으며, `AwardService.findAll()`도 `pageable.getSort()`를
   버리고 `PageRequest.of(pageable.getPageNumber(), size)`(sort 미지정)만
   repository에 전달 — 클라이언트 지정 정렬/필터가 없음(Out of Scope
   준수, 아래 Findings 참고).
5. `GET /{id}` 존재/미존재 200/404 — **충족**.
   `getsExistingAwardAndReturnsNotFoundForMissingOne` `L71-77`.
6. `PATCH` 일부 필드만 변경 시 나머지 필드 유지 — **충족**.
   `patchesOnlyProvidedFieldsAndReturnsNotFoundForMissingOne` `L80-89`:
   `title`/`issuer`/`awardedDate`/`description` 4개 필드를 모두 채운
   entity를 생성한 뒤 `description`만 PATCH하고, 응답에서 `title`/
   `issuer`/`awardedDate` 3개 필드가 기존값 그대로 유지됨을 `jsonPath`로
   명시적으로 확인(PKB-002 review 1에서 지적됐던 "날짜 필드 유지 미검증"
   경미 사항이 이번엔 재발하지 않음). `AwardService.update()`
   (`AwardService.java:30-37`)의 `if (request.X() != null)` 패턴은
   `CareerExperienceService`/`CertificationService`와 동일 구조.
7. `PATCH` 존재하지 않는 id → 404 — **충족**. 같은 테스트 `L90-92`.
8. `DELETE` 204 → 단건조회 404, 존재하지 않는 id → 404 — **충족**.
   `deletesExistingAwardAndReturnsNotFoundAfterward` `L96-104`.
9. 기존 JobPosting/COLLECT/JobApplication/ApplicationStage/
   CareerExperience/Certification/Education 전체 테스트 회귀 없음 —
   **충족**. `git status --short`로 `job`/`collector`/`application`
   패키지, `CareerExperience*`/`Certification*`/`Education*` 파일이
   전혀 수정되지 않고 신규 Award 관련 파일 11개만 추가됨을 확인(아래
   "패키지 격리 확인" 참고). 전체 스위트 재실행 결과 회귀 없음.
10. `cd backend && ./gradlew test` 전체 실패 0건 — **충족**. 아래 "테스트
    결과" 참고.

## 테스트 결과

reviewer가 직접 재실행(Claude가 사전에 125/125를 보고했으나, 독립적으로
재확인함).

- 사전조건: `docker compose ps` → `careerops-agent-postgres-1`/
  `careerops-agent-redis-1` 둘 다 `healthy`(이미 기동 중).
- `./gradlew test --tests "com.careerops.backend.career.Award*"` →
  `BUILD SUCCESSFUL`.
- `./gradlew test`(전체 스위트) → `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml`을 직접 파싱해 합산: **test_count = 125,
  실패/에러 = 0**. `TEST-...AwardControllerTest.xml` `tests="6"`,
  `TEST-...AwardRepositoryTest.xml` `tests="2"` — Claude가 보고한
  "기존 117(100 baseline + Certification 8 + Education 9) + Award
  Controller 6 + Award Repository 2 = 125"와 정확히 일치.
- test_pass_count = 125/125.

## Findings

- **[원칙 확인]** `AwardService`의 `create()`/`update()`/`delete()`
  어디에도 `@Transactional`이 없음(`AwardService.java` 전체에
  `org.springframework.transaction.annotation.Transactional` import
  자체가 없음). Task 명세와 ADR-0020의 명시적 요구를 정확히 준수.
- **[Out of Scope 준수 확인]** `Award.java`/`V10__create_career_awards_
  table.sql`/DTO 4종 어디에도 `category` 필드가 없음. `Award`와
  `CareerExperience` 사이에 FK 컬럼이나 참조 필드가 전혀 없음(entity에
  `careerExperienceId` 등 없음, migration에도 FK 제약 없음) — ADR-0020의
  "Award-CareerExperience FK 보류" 결정과 정확히 일치. `sourceType`/
  `sourceReference`(provenance), 제목/발급기관 검색 filter, tag 시스템,
  custom metric도 전혀 추가되지 않음.
- **[migration 1:1 일치 확인]** `V10__create_career_awards_table.sql`이
  명세의 `CREATE TABLE` 문과 컬럼명/타입/nullable까지 정확히 일치:
  `title VARCHAR(200) NOT NULL`만 NOT NULL, `issuer`/`awarded_date`/
  `description`은 nullable, `created_at`/`updated_at`은
  `TIMESTAMP(6) WITH TIME ZONE NOT NULL`. UNIQUE 제약/추가 index/FK가
  전혀 없음(PK만 존재) — "같은 제목 중복 수상이 정당할 수 있다"는
  ADR-0020 판단과 일치. `ls backend/src/main/resources/db/migration/`
  결과 `V9__create_career_educations_table.sql` 다음 번호인 `V10`을
  정확히 채택(PKB-002/003과 번호 충돌 없음).
- **[PATCH 컨벤션 확인]** `AwardCreateRequest.title`은 `@NotBlank
  @Size(max=200)`, `AwardUpdateRequest.title`은 `@Size(max=200)`만
  (`@NotBlank` 제거) — 명세와 정확히 일치. Update 레코드의 나머지
  3필드도 Create와 동일한 `@Size`만 유지하고 optional 취급.
- **[패키지 격리 확인]** `git status --short` 결과 `career` 패키지 내
  기존 `CareerExperience*`/`Certification*`/`Education*` 파일은 이번
  변경에서 전혀 수정되지 않았고(Certification/Education 파일은 이미
  이번 diff 범위 밖의 별도 review(PKB-002/003) 대상으로, `AwardControllerTest
  savesAndFindsAward...` 등 Award 관련 파일만 신규 추가), `job`/
  `collector`/`application` 패키지도 무변경.
- Secret/API Key 커밋 없음. 신규 production/test dependency 없음(명세와
  일치, `build.gradle` 변경 없음 — `git status --short`에 없음). 자기소개서
  관련 로직 없음(이번 Task 범위 아님) — 근거 기반 검증 원칙 위반 사항
  없음.
- 블로킹 사항 없음. PKB-002 review 1에서 지적됐던 두 경미 사항
  ("PATCH 유지 필드 명시적 assert 누락", "count 검증 결합")이 이번
  라운드에서는 재발하지 않고 개선된 형태로 반영됨(AC 6 테스트가
  3필드 유지를 모두 명시적으로 assert).

## 다음 액션

**PASS** — Acceptance Criteria 10개 전항목 충족, 전체 테스트 125/125
통과(reviewer가 독립적으로 `--tests`/전체 스위트 두 번 재실행하고
`build/test-results/test/*.xml`을 직접 파싱해 확인), `@Transactional`
미적용/Out of Scope 준수(`category`/FK/provenance/filter/tag 없음)/
migration 1:1 일치/패키지 격리 모두 확인됨. 경미한 findings 없음.

- `.ai/metrics/metrics.jsonl`에 PKB-004 `review`/`done` phase 라인을
  기록하고 Task를 완료 처리할 것을 호출자(Claude)에게 권고.
- Codex에게 추가로 보낼 수정 요청 없음.
