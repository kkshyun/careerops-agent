---
task_id: JOB-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-15T21:20:00+09:00
verdict: FAIL
---

## Acceptance Criteria 체크

- [ ] **필터 없이 목록 조회** — 미충족. `GET /api/jobs` (파라미터 없음)은
      `JobPostingService.search(null,null,null,null,pageable)`을 거쳐
      `JobPostingRepository.search(...)`를 호출하는데, 이 JPQL 자체가
      PostgreSQL에서 SQL 준비(prepare) 단계부터 실패한다
      (`JobPostingControllerTest.usesDefaultPaginationAndReturnsSecondPage`,
      `JobPostingRepositoryTest.paginatesSearchResults` 실패).
- [ ] **status 필터** — 미충족. `search("OPEN", null, null, null)` 호출도
      (다른 3개 필터가 null이므로) 같은 원인으로 실패
      (`JobPostingRepositoryTest.searchesByEachFilterAndCombinesFiltersWithAnd`
      의 `search("OPEN", null, null, null)` 단언 포함, 해당 테스트 전체
      실패).
- [ ] **careerLevel 부분 일치** — 미충족. 같은 테스트(위)에서 검증하는
      부분이나 테스트 자체가 실패.
- [ ] **companyName 부분 일치** — 미충족. 위와 동일.
- [ ] **jobCategory 부분 일치** — 미충족. 위와 동일.
- [ ] **필터 조합(AND)** — 미충족. 위와 동일
      (`search("OPEN", "신입", null, null)` 케이스도 같은 테스트 안에서
      실패).
- [ ] **정렬 검증** — 미충족.
      `JobPostingRepositoryTest.sortsByApplicationEndAtAscendingWithNullLast`
      는 `search(null, null, null, null)`을 호출해 실패. JPQL의
      `ORDER BY j.applicationEndAt ASC NULLS LAST` 절 자체는 문법상
      문제가 없어 보이나(`JobPostingRepository.java:22`), 쿼리 전체가
      prepare 단계에서 죽기 때문에 검증 불가.
- [ ] **Pagination** — 미충족.
      `JobPostingRepositoryTest.paginatesSearchResults`,
      `JobPostingControllerTest.usesDefaultPaginationAndReturnsSecondPage`
      둘 다 실패.
- [ ] **size 상한** — 미충족.
      `JobPostingControllerTest.clampsRequestedPageSizeToOneHundred` 실패.
      `JobPostingService.java:60-64`의 `Math.min(pageable.getPageSize(),
      100)` 로직 자체는 코드 리뷰상 올바르나, 하위 쿼리가 죽어 검증
      불가.
- [x] / 부분 충족 **응답 필드** — `JobPostingListResponse.java:15-22`의
      `from()`이 `content`/`totalElements`/`totalPages`/`page`/`size` 5개
      필드를 `Page<JobPosting>`에서 정확히 매핑하고, `content` 항목은
      기존 `JobPostingResponse::from`을 그대로 재사용해 필드 누락이 없다.
      실제로 4개 필터를 **모두 non-null 문자열로 채운** 유일한 케이스인
      `JobPostingControllerTest.getsFilteredJobsWithListResponseFieldsAndFixedOrder`
      는 통과했고, 이 테스트가 `content`/`totalElements`/`totalPages`/
      `page`/`size` 필드 구성과 정렬 순서를 함께 검증하므로 구조 자체는
      맞다는 근거가 있다. 다만 이 케이스만으로는 AC를 "충족"으로 보기
      어렵다 — 실사용에서는 필터를 하나도 안 주거나 일부만 주는 경우가
      대다수이고 그 경우 전부 실패하기 때문.
- [x] **기존 엔드포인트 회귀 없음** — 충족.
      `JobPostingControllerTest`의 `createsJobPostingAndIncrementsMetric`,
      `rejectsBlankRequiredFieldWithoutSaving`, `getsExistingJobPosting`,
      `returnsNotFoundForUnknownId`, `JobPostingRepositoryTest`의
      `savesAndFindsJobPostingById`, `returnsEmptyForUnknownId`,
      `findsBySourceAndExternalId` 모두 실패 목록에 없음 — `POST
      /api/jobs`, `GET /api/jobs/{id}` 기존 계약은 이번 변경으로 깨지지
      않았다.
- [ ] **회귀 없음 (`./gradlew test` 전체 통과)** — 미충족. 아래 테스트
      결과 참고.

## 테스트 결과

- test_count: 41
- test_pass_count: 36 (failures: 5, errors: 0)
- 실행 방법: 저장소 루트 `docker compose ps`로 PostgreSQL(healthy) 확인 →
  `set -a && source .env && set +a` → `cd backend && ./gradlew test
  --console=plain` 직접 실행(신선 실행, up-to-date 캐시 아님 —
  `> Task :test` 자체가 새로 수행됨). Codex가 보고한 "41 tests completed,
  5 failed"와 정확히 일치. `build/test-results/test/TEST-*.xml`의
  `tests=`/`failures=` 합산으로도 41/5 재확인
  (`JobPostingControllerTest`: 7 tests / 2 failures,
  `JobPostingRepositoryTest`: 6 tests / 3 failures).
- 실패 5건 전부 `JobPostingRepository.search(...)` JPQL을 실행 경로에
  포함한 테스트다:
  - `JobPostingRepositoryTest.searchesByEachFilterAndCombinesFiltersWithAnd`
  - `JobPostingRepositoryTest.sortsByApplicationEndAtAscendingWithNullLast`
  - `JobPostingRepositoryTest.paginatesSearchResults`
  - `JobPostingControllerTest.usesDefaultPaginationAndReturnsSecondPage`
  - `JobPostingControllerTest.clampsRequestedPageSizeToOneHundred`
- 실패 원인 SQL(`build/test-results/test/TEST-com.careerops.backend.job.JobPostingRepositoryTest.xml`):
  ```
  ERROR: operator does not exist: character varying ~~ bytea
    Hint: No operator matches the given name and argument types.
    Position: 404
  ```
  이 오류는 생성된 SQL
  `... and (? is null or jp1_0.career_level like ('%'||?||'%') escape '') ...`
  중 `career_level` LIKE 절(4개 필터 중 JPQL에서 첫 번째로 등장하는
  `CONCAT` 기반 절)에서 발생한다 — 파라미터 문자열을 `?`→`$n`으로 치환해
  위치를 계산해도(스크립트로 직접 계산) `Position: 404`가 정확히
  `jp1_0.career_level like ('%'||$4...` 구간과 일치한다.

## Findings — 근본 원인 분석

`JobPostingRepository.java:19-21`은 Task 명세 Technical Notes의 "참고
형태"를 그대로 옮겨 각 nullable 필터 파라미터(`:careerLevel`,
`:companyName`, `:jobCategory`)를 **같은 이름의 파라미터를 두 곳에서
재사용**한다: `(:careerLevel IS NULL OR ...)`의 NULL 체크용 바인딩 1개와,
`CONCAT('%', :careerLevel, '%')`(SQL로는 `'%'||?||'%'`) 안의 값 바인딩
1개, 총 2개의 `?` 슬롯으로 컴파일된다.

실제 테스트의 성공/실패 패턴이 원인을 명확히 가리킨다:

| 테스트 | 4개 필터 값 | 결과 |
|---|---|---|
| `getsFilteredJobsWithListResponseFieldsAndFixedOrder` | 4개 전부 **non-null** 문자열 | **PASS** |
| `searchesByEachFilterAndCombinesFiltersWithAnd` | 매 호출마다 1개 이상 **null** | FAIL |
| `sortsByApplicationEndAtAscendingWithNullLast` | 4개 전부 null | FAIL |
| `paginatesSearchResults` | 4개 전부 null | FAIL |
| `usesDefaultPaginationAndReturnsSecondPage` | 4개 전부 null | FAIL |
| `clampsRequestedPageSizeToOneHundred` | 4개 전부 null | FAIL |

즉 `search(...)`를 호출하는 6개 테스트 중, **4개 필터를 전부 non-null
문자열로 채운 단 1개 테스트만 통과**하고 null 값이 하나라도 섞이면
전부 실패한다. `status = :status`(단순 동등 비교, `CONCAT` 미사용)는
값이 null이어도 실패에 관여하지 않는다 — 에러 위치(Position 404)가
가리키는 지점은 언제나 `career_level`의 `CONCAT`/`||` 기반 LIKE절이다.

이는 JPQL의 `CONCAT('%', :param, '%')`이 PostgreSQL SQL로 `'%'||?||'%'`
(문자열 연결 연산자 `||`)로 변환되기 때문이다. `||` 연산자는 PostgreSQL
카탈로그에 `text||text`, `bytea||bytea` 등 여러 후보가 등록되어 있고,
피연산자 타입이 리터럴('%', 두 개)과 바인드 파라미터(`?`) 조합으로
모호할 때, 파라미터에 바인딩되는 값이 **null**인 경우 Hibernate/pgjdbc가
그 슬롯에 구체적인 `VARCHAR` JDBC 타입을 실어 보내지 못하고(값이
있으면 `setString(...)`으로 명확히 VARCHAR 타입이 전달되어 위 표의
유일한 PASS 케이스처럼 정상 동작), PostgreSQL이 `||` 연산자 오버로드를
모호하게 해석하다 `bytea` 쪽을 선택한다. 이후 그 결과를
`jp1_0.career_level`(character varying 컬럼)과 `LIKE`(`~~`)로 비교하려는
순간 `character varying ~~ bytea` 오류가 난다. `status = :status`는
단순 동등 비교라 파라미터가 null이어도 컬럼 타입(`character varying`)
쪽으로 문제없이 타입이 정해지므로 영향을 받지 않는다.

결론: **문제는 `JobPostingRepository.java`의 JPQL `@Query`에서
`CONCAT(...)`(→ `||`)를 사용해 LIKE 패턴을 만드는 방식이, 해당 파라미터가
null일 수 있는 이번 유스케이스(선택적 필터)와 결합했을 때 PostgreSQL의
연산자 타입 추론을 깨뜨리는 것**이다. Task 명세에 이 방식이 "참고 형태"로
제시되어 있었지만 실제로는 동작하지 않음이 이번 실행으로 확인됐다.

## 다음 액션

**FAIL.** 같은 Codex thread(01a00553-bf5d-77b0-a6f2-366490b1208a)에 아래
내용을 그대로 전달해 수정을 요청할 것.

### Codex에게 보낼 수정 요청

1. `JobPostingRepository.java`의 `search` `@Query`에서 `careerLevel`/
   `companyName`/`jobCategory` 3개 필터의 `LIKE CONCAT('%', :param, '%')`
   부분을 **`CONCAT`(→ SQL `||`) 없이** `LIKE :param` 형태로 바꿔라. 즉:
   ```java
   @Query("""
           SELECT j FROM JobPosting j
           WHERE (:status IS NULL OR j.status = :status)
             AND (:careerLevel IS NULL OR j.careerLevel LIKE :careerLevel)
             AND (:companyName IS NULL OR j.companyName LIKE :companyName)
             AND (:jobCategory IS NULL OR j.jobCategory LIKE :jobCategory)
           ORDER BY j.applicationEndAt ASC NULLS LAST
           """)
   Page<JobPosting> search(
           @Param("status") String status,
           @Param("careerLevel") String careerLevel,
           @Param("companyName") String companyName,
           @Param("jobCategory") String jobCategory,
           Pageable pageable);
   ```
   이제 `:careerLevel`/`:companyName`/`:jobCategory`는 호출하는 쪽에서
   `null` 또는 이미 `%값%` 형태로 감싼 패턴 문자열이어야 한다(레포지토리
   시그니처는 그대로 유지, 계약만 "값을 그대로 넘기지 말고 와일드카드까지
   포함해서 넘겨라"로 바뀜 — 인터페이스/파라미터 이름은 유지해도 됨).
2. `JobPostingService.search(...)`에서 repository를 호출하기 전에
   `careerLevel`/`companyName`/`jobCategory` 3개 값을 `null`이 아닐 때만
   `"%" + value + "%"`로 감싸는 작은 private 헬퍼를 추가하고 그 결과를
   `repository.search(...)`에 넘겨라(`status`는 정확 일치이므로 그대로
   전달). 예:
   ```java
   private static String likePattern(String value) {
       return value == null ? null : "%" + value + "%";
   }
   ```
   기존 size clamp(`Math.min(pageable.getPageSize(), 100)`) 로직은 그대로
   둔다.
3. 왜 이렇게 고쳐야 하는지: 기존 `LIKE CONCAT('%', :param, '%')`는
   PostgreSQL SQL로 `'%'||?||'%'`(문자열 연결 연산자)로 변환되는데, 해당
   파라미터가 null일 수 있는 이번 선택적 필터 유스케이스에서 PostgreSQL이
   `||` 연산자 오버로드를 모호하게 해석해 `bytea`로 잘못 추론하고, 이후
   `career_level LIKE (...)`(`~~ character varying`) 비교에서
   `operator does not exist: character varying ~~ bytea` 에러가 난다.
   실제로 4개 필터를 전부 non-null로 채운 테스트 1건만 통과하고, null이
   하나라도 섞인 나머지 5개 테스트는 전부 이 에러로 실패했다(재현 확인
   완료). `LIKE CONCAT(...)` 대신 패턴 문자열을 Java에서 미리 만들어
   단일 파라미터로 바인딩하면 `||` 연산자 자체가 SQL에서 사라지므로
   이미 정상 동작하는 `status = :status`(단순 동등 비교)와 같은 방식이
   되어 타입 추론 문제가 없어진다.
4. 수정 후 저장소 루트에서 `docker compose up -d`(이미 떠 있으면 생략),
   `.env`를 source한 뒤 `cd backend && ./gradlew test`를 실행해 이번에
   실패한 5개 테스트(`JobPostingRepositoryTest
   .searchesByEachFilterAndCombinesFiltersWithAnd`,
   `.sortsByApplicationEndAtAscendingWithNullLast`,
   `.paginatesSearchResults`, `JobPostingControllerTest
   .usesDefaultPaginationAndReturnsSecondPage`,
   `.clampsRequestedPageSizeToOneHundred`) 포함 전체 41개(또는 그 이상)
   테스트가 0 failure로 통과하는지 직접 실행해 확인하고 결과를 보고하라.
   (Codex sandbox에서 Docker/Gradle 접근이 막혀 있으면 이번에도 실행
   불가 사실과 이유를 그대로 보고하면 된다 — 오케스트레이터가 재실행해
   검증한다.)

### 참고 (Out of Scope 재확인 — 수정 시 지키지 않아도 되는 방향)

- QueryDSL, Specification 등 신규 쿼리 빌딩 기술 도입 금지 — 위 수정안은
  JPQL `@Query`를 그대로 유지하므로 문제 없음.
- `ILIKE`/`LOWER()` 등 대소문자 처리 추가 금지(Task Technical Notes에서
  이미 도입하지 않기로 결정됨) — 위 수정안과 무관, 손대지 말 것.
- LIKE 패턴에 포함될 수 있는 `%`/`_` 등 메타문자 이스케이프 처리는 이번
  수정 범위에 포함하지 않는다(기존 `CONCAT` 방식에도 동일하게 없던
  한계이며 이번 버그와 무관 — 별도 이슈로 필요시 재검토).

### 참고 (비차단 관찰 사항 — 이번 라운드에서 고칠 필요는 없음)

`JobPostingController.java:44`의 `@PageableDefault(size = 20) Pageable
pageable`은 Spring의 기본 `Pageable` 바인딩을 사용하므로, 클라이언트가
문서화되지 않은 `?sort=` 쿼리 파라미터를 보내면 `Pageable`에 `Sort`가
실려 JPQL에 이미 고정된 `ORDER BY j.applicationEndAt ASC NULLS LAST`와
충돌/중복 정렬절이 생길 위험이 Technical Notes에 이미 경고돼 있다. 이번
Task Out of Scope("범용 sort 파라미터 없음")이고 AC에도 해당 테스트가
없어 이번 라운드의 판정에는 반영하지 않았지만, 다음 라운드 리뷰나 실사용
전 한 번은 `?sort=xxx` 호출 시 500이 나는지 최소 수동 확인을 권장한다.
