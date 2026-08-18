---
task_id: FIX-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T19:05:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `GET /api/career/experiences` (파라미터 없음) → 200, 빈 필터로 전체 목록 반환.
  충족. `CareerExperienceSearchNonTransactionalTest.listsAllExperiencesWithoutParameters()`
  (backend/src/test/java/com/careerops/backend/career/CareerExperienceSearchNonTransactionalTest.java:38-50)
  가 `@Transactional` 없이 실제 요청 경로로 200과 content 배열을 검증한다.

- [x] `GET /api/career/experiences?type=PROJECT` (keyword 없음) → 200.
  충족. `filtersByTypeWithoutKeyword()` (동일 파일:52-58).

- [x] `GET /api/career/experiences?keyword=spring` (type 없음) → 200, 대소문자
  무시 매치.
  충족. `filtersByKeywordWithoutType()` (동일 파일:60-66), lowercase "spring"으로
  "Spring Boot 프로젝트" 매치 확인.

- [x] `GET /api/career/experiences?type=PROJECT&keyword=spring` → 200, 두 조건
  모두 만족.
  충족. `filtersByTypeAndKeyword()` (동일 파일:68-76).

- [x] keyword 대소문자 무시 확인 (keyword=SPRING 대문자로 조회).
  충족. `filtersByKeywordIgnoringCase()` (동일 파일:78-84), keyword="SPRING"으로
  "Spring Boot 프로젝트" 매치.

- [x] 위 5개 케이스 중 최소 하나는 `@Transactional`로 감싸지 않은 테스트여야
  한다.
  충족(핵심 요구사항, 직접 확인 완료). `CareerExperienceSearchNonTransactionalTest`
  클래스 선언(동일 파일:18-20 `@SpringBootTest` + `@AutoConfigureMockMvc`)과 6개
  `@Test` 메서드 어디에도 `@Transactional`/`@Rollback` 어노테이션이 없음을 grep과
  전체 파일 재독으로 확인. `@BeforeEach`/`@AfterEach`에서 직접 저장한 ID를
  추적해 수동 cleanup(`repository.deleteAllById(createdIds)` + `flush()`)하는
  방식으로 격리하며, rollback에 의존하지 않는다. Task 명세가 요구한
  "Option 1"(별도 non-transactional `@SpringBootTest` 클래스, 권장안)을 정확히
  따랐다.

- [x] pagination/정렬 회귀 없음: size clamp(`size=1000` → 100) 유지.
  충족. 기존 `CareerExperienceControllerTest.listsWithCombinedFiltersAsFlatResponses`
  는 diff 없이(`git diff --stat` 결과 없음) 그대로 남아있어 기존 size clamp
  검증이 유지되고, 신규 파일에도 `clampsRequestedPageSizeToOneHundred()`
  (동일 파일:86-91)가 non-transactional 경로로 동일 시나리오를 추가 커버한다.

- [x] 기존 목록/detail 응답 필드 구성 회귀 없음.
  충족. 신규 테스트가 `bullets`/`tags` 미포함을 재확인하고, 기존
  `CareerExperienceControllerTest`(detail API 테스트 포함)가 diff 없이 전부
  통과(131/131, 아래 참고)했다.

- [x] `cd backend && ./gradlew test` 전체 실패 0건.
  충족, 독립 재검증 완료(아래 테스트 결과 참고).

## Out of Scope 준수 확인

- Repository/Service 변경은 명세된 diff와 정확히 일치한다.
  `CareerExperienceRepository.java`: `:keyword`(CONCAT 인자) → `:keywordPattern`
  (LIKE 우변 직접 바인딩)만 변경, 그 외 쿼리/필터/정렬 로직 무변경.
  `CareerExperienceService.java`: `findAll()` 호출부에 `keywordPattern(keyword)`
  헬퍼 추가만 있고, `findById`/`detail`/그 외 메서드 무변경(`git diff` 확인).
  새 필터/API contract 변경/일반 리팩터링 없음.
- Certification/Education/Award 관련 파일: `git status --porcelain -uall |
  grep -iE 'certification|education|award'` 결과 매치 없음 — 전혀 건드리지
  않음.
- `keyword` 빈 문자열/공백 trim 로직 미도입: `keywordPattern()`은 null 체크만
  하고 trim을 추가하지 않았음(diff 확인) — Out of Scope 명시 사항과 일치.
- import: `Locale`은 기존 `import java.util.*;`(CareerExperienceService.java:11)
  로 이미 커버되어 신규 import 불필요 — 명세 그대로.

## 테스트 결과

- `cd backend && ./gradlew test --rerun` 직접 실행: `BUILD SUCCESSFUL`.
- JUnit XML(`build/test-results/test/*.xml`, 25개 클래스 파일) 직접 파싱으로
  독립 재검증: **test_count=131, test_pass_count=131, failures=0, errors=0,
  skipped=0**. Claude가 이전에 보고한 131/131과 일치.
- `CareerExperienceSearchNonTransactionalTest` 6개 테스트 모두 개별 testcase
  PASS 확인(`TEST-com.careerops.backend.career.CareerExperienceSearchNonTransactionalTest.xml`).
- `backend/build.gradle:36-43`의 `tasks.named('test')` 블록이
  `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/careerops_test`를
  테스트 JVM 환경변수로 강제함을 재확인(ADR-0010) — dev DB(`careerops`)는
  이번 테스트 실행으로 영향받지 않음.

## Findings

- 없음. 과도한 추상화나 불필요한 패턴 없음(`keywordPattern()` 헬퍼 하나만
  추가, JOB-002 선례와 동일한 최소 패턴). 새 production dependency 없음.
  Secret/API Key 커밋 없음(`git status`에 `.env` 등 미포함). 자기소개서 관련
  코드 아님(근거 기반 검증 원칙 해당 없음).
- 사소하지만 언급할 점(수정 요청 아님, 참고용): 6개 신규 테스트가 모두 한
  파일(`CareerExperienceSearchNonTransactionalTest`)에 있어 non-transactional
  cleanup 로직(수동 ID 추적)이 매 테스트마다 재사용되는데, 이는 명세가 권장한
  Option 1과 정확히 일치하므로 문제 아님.

## 다음 액션

- **PASS.** Acceptance Criteria 9개 항목 전부 충족, `@Transactional` 부재
  핵심 요구사항 직접 확인, 전체 테스트 131/131 독립 재검증 완료, Out of
  Scope 위반 없음. 추가 수정 요청 없음.
- `.ai/metrics/metrics.jsonl`에 review/done phase 기록 추가 필요(호출한
  Claude가 처리).
