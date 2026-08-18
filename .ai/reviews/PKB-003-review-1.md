---
task_id: PKB-003
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T20:05:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

1. `POST` `institution`만 → 201, 나머지 필드 null — **충족**.
   `EducationControllerTest.createsMinimalAndFullEducation` L28-34: `{"institution":"한국대학교"}`만
   보내고 `major`/`degree`/`status`/`startDate`/`endDate`/`gpa`/`gpaScale`/`description`
   전부 `isEmpty()`임을 확인.
2. `POST` 전체 필드(major/degree/status/startDate/endDate/gpa/gpaScale/description) → 201,
   응답이 요청과 일치 — **충족**. 같은 테스트 L36-48, 8개 필드 모두 `jsonPath`로 1:1 대조.
3. `POST` `institution` 누락 → 400, row 미생성 — **충족**.
   `rejectsInvalidCreateRequestsWithoutCreatingRows` L54-66: `invalidRequests[0]="{}"` 포함
   6개 케이스를 순회하며 각각 400 확인 후 `repository.count()`가 `before`와 동일함을 확인.
4. `POST` `endDate < startDate` → 400, row 미생성 — **충족**. 같은 테스트 `invalidRequests[1]`
   (`startDate=2025-03-01`, `endDate=2025-02-28`).
5. `POST` `gpa`만 있고 `gpaScale` 없음(또는 반대) → 400, row 미생성 — **충족**. 같은 테스트
   `invalidRequests[2]`(`gpa`만), `invalidRequests[3]`(`gpaScale`만).
6. `POST` `gpa > gpaScale` → 400, row 미생성 — **충족**. 같은 테스트 `invalidRequests[4]`
   (`gpa=4.6`, `gpaScale=4.5`).
7. `POST` 정상 `gpa`/`gpaScale` 조합(3.8/4.5) → 201, 응답에 정확히 반환(스케일/precision
   유실 없음) — **충족**. `acceptsValidGpaWithoutPrecisionLoss` L70-79: MockMvc 응답
   `jsonPath("$.gpa").value(3.8)`/`gpaScale`.value(4.5) 확인에 더해, DB에서 재조회한
   엔티티를 `assertThat(saved.getGpa()).isEqualByComparingTo("3.80")`으로 별도 검증 —
   HTTP 레이어와 영속화 레이어 양쪽에서 정밀도 유실이 없음을 이중 확인. `EducationRepositoryTest.
   savesAndFindsEducationWithGpaAndNullableFields`(L20-34)에서도 repository 레벨로 동일하게
   재확인.
8. `POST` `degree`에 enum에 없는 문자열 → 400 — **충족**. 같은 테스트 `invalidRequests[5]`
   (`"degree":"DIPLOMA"`) — Jackson 역직렬화 실패로 Spring 기본 400 처리, 명세와 일치.
9. `GET` pagination + `startDate DESC NULLS LAST` 정렬 실동작 — **충족**.
   `listsWithPaginationClampDefaultSizeAndFixedOrdering` L82-97: 기본 `size=20`(L88),
   `size=1000` → 100 clamp(L92-93), `page=1&size=2` 슬라이스(L94-97), 정렬 순서
   "최근→과거→날짜없음"(L89-91) 확인. `EducationService.findAll()`(`EducationService.java:27-31`)의
   `Math.min(pageable.getPageSize(), 100)`과 정확히 대응. `EducationRepositoryTest.
   ordersByStartDateDescendingWithNullsLast`(L37-45)로 repository 레벨도 재확인.
10. `GET /{id}` 존재/미존재 200/404 — **충족**.
    `getsExistingEducationAndReturnsNotFoundForMissingOne` L101-107.
11. `PATCH` 일부 필드(`description`)만 변경 시 나머지(`gpa`/`gpaScale` 포함) 유지 —
    **충족, PKB-002 대비 커버리지 개선**. `patchesOnlyProvidedFieldsAndValidatesMergedValues`
    L115-120: `institution`/`major`/`gpa`/`gpaScale`/`description`을 모두 `jsonPath`로
    명시 확인. PKB-002 리뷰에서 지적됐던 "date 필드 유지 미검증" 유형의 갭이 이번엔
    `gpa`/`gpaScale`에 대해서는 명시적으로 채워짐.
12. `PATCH` 기존 `gpaScale`이 있는 상태에서 `gpa`만 새 값으로 PATCH할 때 병합된 조합이
    `gpa > gpaScale`이면 400 — **충족, 명세 예시와 정확히 일치**. 같은 테스트 L121-123:
    초기 `gpa=3.80`/`gpaScale=4.50`으로 생성 → `{"gpa":4.60}`만 PATCH(gpaScale 생략) →
    병합된 `gpaScale=4.50`과 비교해 `4.60 > 4.50`이므로 400. `EducationService.update()`
    (`EducationService.java:39-42`)의 `gpa`/`gpaScale` 병합 로직과 완전히 대응. 같은 테스트
    L124-126에서 `startDate`/`endDate` 병합 검증(`{"endDate":"2021-02-28"}`만 PATCH,
    기존 `startDate=2021-03-01`과 병합해 역전 감지)까지 추가로 커버 — 명세에 명시된 AC는
    아니지만 Technical Notes의 "날짜도 동일 패턴 적용" 요구를 테스트로 실증.
13. `PATCH` 존재하지 않는 id → 404 — **충족**. 같은 테스트 L127-129.
14. `DELETE` 204 → 단건조회 404, 존재하지 않는 id → 404 — **충족**.
    `deletesExistingEducationAndReturnsNotFoundAfterward` L133-141.
15. 기존 JobPosting/COLLECT/JobApplication/ApplicationStage/CareerExperience/Certification
    전체 테스트 회귀 없음 — **충족**. `git status --short`로 `job`/`collector`/`application`
    패키지, `CareerExperience*` 파일 무변경 확인(트래킹되지 않음 = 미수정). `career` 패키지
    내 신규 파일은 Education/Certification/Award 관련뿐이며 서로 겹치지 않음. 전체 스위트
    재실행 결과 회귀 없음(아래 "테스트 결과" 참고).
16. `cd backend && ./gradlew test` 전체 실패 0건 — **충족**. 아래 "테스트 결과" 참고.

## 테스트 결과

reviewer가 직접 `build/test-results/test/*.xml`을 파싱해 재확인(Claude가 사전에
125/125을 보고했으나, 독립적으로 재검증함 — 별도 재실행은 하지 않고 오케스트레이터가
방금 실행한 결과물의 XML을 직접 검증).

- `TEST-com.careerops.backend.career.EducationControllerTest.xml`: `tests="7"`,
  `failures="0"`, `errors="0"`.
- `TEST-com.careerops.backend.career.EducationRepositoryTest.xml`: `tests="2"`,
  `failures="0"`, `errors="0"`.
- 전체 `build/test-results/test/TEST-*.xml` 합산(Python 스크립트로 `tests`/`failures`/
  `errors` attribute 파싱): **test_count = 125, 실패/에러 합계 = 0**.
- test_pass_count = 125/125. Education 신규 기여분은 9건(Controller 7 + Repository 2)이며,
  오케스트레이터가 보고한 "100 baseline + Certification 8 + Education 9 + Award 8 = 125"와
  정확히 일치.
- migration 번호 충돌 없음: `ls src/main/resources/db/migration/` 확인 결과 V8(Certification)/
  V9(Education)/V10(Award) 각각 유일.

## Findings

- **[원칙 확인]** `EducationService`의 `create()`/`update()`/`delete()`(`EducationService.java`
  전체) 어디에도 `@Transactional`이 없음 — import 자체가 없음. Task 명세와 ADR-0020의
  "단일 row CRUD이므로 `@Transactional`을 붙이지 않는다" 요구를 정확히 준수.
- **[GPA merge 검증 상세 확인 — 이번 리뷰 중점 항목]** `EducationService.update()`
  (`EducationService.java:37-42`)가 `startDate`/`endDate`/`gpa`/`gpaScale` 4개 필드
  모두 "요청값 없으면 기존 entity 값 사용"으로 병합한 뒤 `validateDates`/`validateGpa`를
  호출하고, 그 다음에야 개별 필드를 실제로 갱신(L43-51)하는 순서로 구현되어 있어 병합
  검증이 필드 갱신보다 항상 먼저 일어남(검증 실패 시 어떤 필드도 변경되지 않음이 코드
  구조상 보장됨). 테스트(`patchesOnlyProvidedFieldsAndValidatesMergedValues`)가 이
  로직의 세 가지 경로(1) 무관 필드만 PATCH 시 gpa/gpaScale 유지, 2) gpa만 PATCH해
  병합 후 위반 시 400, 3) endDate만 PATCH해 병합 후 위반 시 400)를 모두 명시적으로
  검증함. Certification(PKB-002)의 동일 패턴보다 오히려 더 촘촘한 커버리지.
- **[BigDecimal precision 확인]** `@Column(precision = 5, scale = 2)`가 `gpa`/`gpaScale`
  양쪽에 적용됐고(`Education.java:20-21`), migration도 `NUMERIC(5,2)`로 1:1 대응
  (`V9__create_career_educations_table.sql:9-10`). HTTP 응답 레이어(jsonPath 3.8/4.5)와
  DB 재조회 레이어(`isEqualByComparingTo("3.80")`) 양쪽에서 정밀도 유실이 없음을 별도
  테스트로 확인했고, 실제 테스트 실행 결과(0 실패)로 실증됨.
- **[nullable 확인]** `degree`/`status` 컬럼 모두 `@Enumerated(EnumType.STRING)` +
  `nullable = false` 미지정(기본값 nullable) 상태로 구현됨(`Education.java:16-17`),
  migration도 `degree VARCHAR(50)`/`status VARCHAR(50)`로 NOT NULL 제약 없음
  (`V9...sql:5-6`). 사용자 승인 사항인 "status를 NOT NULL로 강제하지 않는다"가 정확히
  반영됨. `EducationRepositoryTest.savesAndFindsEducationWithGpaAndNullableFields`가
  `degree`/`status` 둘 다 null인 채로 저장/조회 가능함을 실증.
- **[Out of Scope 준수 확인]** `Education.java`/migration/DTO 4종 어디에도 `sourceType`/
  `sourceReference`(provenance), filter 파라미터, tag, `JobPosting` FK가 없음.
  `EducationController.findAll()`이 `Pageable pageable` 하나만 받고,
  `EducationService.findAll()`이 `pageable.getSort()`를 사용하지 않고 항상
  `findAllOrderByStartDateDescNullsLast()`(서버 고정 정렬)만 호출 — 클라이언트 지정
  정렬/필터가 전혀 반영되지 않음.
- **[패키지 격리 확인]** `git status --short` 결과 `CareerExperience*` 4개 파일은 전혀
  수정되지 않았고(unchanged, 트래킹 목록에 없음), `job`/`collector`/`application`
  패키지도 무변경. Education 관련 신규 파일 13개(entity/enum 2/repository/service/
  controller/dto 4/migration/test 2)만 추가됨 — 명세의 패키지 구조 요구와 정확히 일치.
  같은 시점에 병렬로 진행 중인 PKB-002(Certification)/PKB-004(Award) 파일도 확인했으나
  이번 리뷰 범위(Education)와 겹치지 않고 서로 참조가 없음을 확인.
- Secret/API Key 커밋 없음. 신규 production/test dependency 없음(`build.gradle` 변경이
  `git status --short`에 없음 — 명세와 일치, `BigDecimal`은 JDK/Hibernate 표준 지원이라
  신규 dependency가 아니라는 명세 서술과도 부합). 자기소개서 관련 로직 없음(이번 Task
  범위 아님) — 근거 기반 검증 원칙 위반 사항 없음.
- **[경미]** `EducationControllerTest`에서 `status`(EducationStatus) enum에 잘못된 값을
  보내는 케이스는 별도로 테스트되지 않음(`degree`만 "DIPLOMA"로 테스트됨). Task 명세의
  AC에는 `degree` invalid enum만 명시되어 있어 Acceptance Criteria 위반은 아니지만,
  `status`도 동일한 Jackson 역직렬화 경로를 타므로 원한다면 다음 라운드에서 보강 가능.
  블로킹 아님.

## 다음 액션

**PASS** — Acceptance Criteria 16개 전항목 충족, 전체 테스트 125/125 통과
(Education 신규 기여 9/9 포함, reviewer가 test-results XML을 직접 파싱해 재확인),
`@Transactional` 미적용/PATCH 병합 cross-field 검증(gpa/gpaScale + startDate/endDate)/
BigDecimal precision 무손실/degree·status nullable/Out of Scope 준수/패키지 격리 모두
확인됨. 이번 리뷰의 핵심 위험 지점이었던 "PATCH 시 gpa/gpaScale 병합 검증"은 오히려
PKB-002보다 더 촘촘하게 테스트되어 있음. 경미 사항 1건(status invalid enum 미테스트)은
블로킹 아님.

- `.ai/metrics/metrics.jsonl`에 PKB-003 `review`/`done` phase 라인을 기록하고 Task를
  완료 처리할 것을 호출자(Claude)에게 권고.
- 위 경미 사항은 필수 수정 요청이 아니므로 별도 Codex round 없이 종료 가능.
