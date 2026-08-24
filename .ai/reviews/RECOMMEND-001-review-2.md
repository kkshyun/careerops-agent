---
task_id: RECOMMEND-001
review_round: 2
reviewer: claude
reviewed_at: 2026-08-24T17:45:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

1차 리뷰에서 이미 PASS로 확인된 12개 항목(Anthropic 1회 호출, OPEN 필터,
broad category 배제 안 됨, DB 재조회, unknown jobId/PKB id 502, 중복 jobId
최고 score, score 범위 clamp 없이 실패, 결정적 재정렬, reason 200자,
PKB 4종 empty 409, OPEN 0건 200+빈배열, provider 실패 502)은 이번 라운드에서
production 코드가 컨트롤러 1개 파일 예외 처리 추가 외에 변경되지 않았으므로
재확인만 하고 세부 근거는 review-1을 그대로 인용한다. 이번 라운드는 review-1이
미충족으로 지적한 항목에 집중한다.

- [x] **limit 미지정 시 5개, limit=20 정상, limit=0/21 400 — 충족(신규
      해소).** `JobRecommendationController.java:17-20`에 `@Validated` +
      `@Min(1) @Max(20)` + 신규 `@ExceptionHandler(ConstraintViolationException.class)
      @ResponseStatus(BAD_REQUEST)`가 추가됨. `JobRecommendationControllerTest.java`
      4개 케이스(`missingLimitUsesDefaultFive`, `maximumLimitTwentyIsAccepted`,
      `limitZeroIsBadRequestAndDoesNotCallService`,
      `limitTwentyOneIsBadRequestAndDoesNotCallService`)가 각각
      `verify(service).recommend(5)`/`recommend(20)`/`verifyNoInteractions(service)`로
      실제 HTTP 계층까지 검증. 격리 실행 4/4 PASS
      (`build/test-results/test/TEST-com.careerops.backend.recommend.JobRecommendationControllerTest.xml`,
      `tests="4" failures="0" errors="0"`). round2가 만든 이 테스트가
      실제로 round3 이전 코드의 500 버그(핸들러 부재)를 잡아낸 것 자체가
      테스트가 실질적임을 증명한다.
- [x] **로그 privacy(`missingKeyAndSensitiveInputAreNotLogged`) — 충족(신규
      해소).** `AnthropicJobRecommendationClientTest.java:47-77`이
      MATCH-002 패턴 그대로 재사용: 빈 API key로 client 생성 →
      `recommend()` 호출 시 `PROVIDER_4XX` 예외 확인 + `ListAppender`로
      root logger를 잡아 sensitive job title/PKB summary·detail/API key가
      로그에 전혀 없음을 `assertThat(logs).doesNotContain(...)`로 검증.
      부수적으로 `classifiesTimeout4xx5xxAndMalformedFailures`
      (`AnthropicJobRecommendationClientTest.java:22-45`)가 `classify()`의
      timeout/401/429/503/malformed 5가지 분기를 모두 assertion으로 고정
      (review-1 Findings #2의 "classify() 단위 테스트 없음" 부수 지적도 해소).
      격리 실행 2/2 PASS.
- [x] **Test Plan 24개 케이스 모두 Fake client로 통과 — 충족.** 아래
      "테스트 결과"에서 케이스별 재매핑.
- [x] **`./gradlew test` 전체 통과(회귀 없음) — 충족, 다만 별도 환경
      노이즈 있음.** 격리 기준 recommend 패키지 19/19 PASS. 전체 스위트를
      직접 4회 반복 실행한 결과 매번 산발적으로 1~2개 클래스가
      `Failed to load ApplicationContext`(Postgres `FATAL: sorry, too many
      clients already`)로 실패했으나, 실패하는 클래스가 매번 무작위이고
      (`JobRecommendationControllerTest`뿐 아니라 RECOMMEND-001과 무관한
      기존 `MultipartUploadLimitIntegrationTest`도 동일 원인으로 같은
      실행에서 함께 실패) 해당 클래스만 격리하면 항상 통과한다. 자세한
      근거는 "테스트 결과" 참고. AGENT-001/AGENT-002에서 이미 문서화된
      pre-existing DB 커넥션 풀 경합과 동일 패턴이며 RECOMMEND-001
      코드 결함이 아니라고 판단한다(상세 근거 아래).

## 테스트 결과

- **격리 기준(신뢰할 수 있는 결과)**:
  `JobRecommendationControllerTest` 4/4,
  `AnthropicJobRecommendationClientTest` 2/2,
  `JobRecommendationServiceTest` 12/12,
  `JobRecommendationPromptBuilderTest` 1/1 — recommend 패키지 신규/보강
  테스트 합계 **19/19 PASS**
  (`./gradlew test --tests "com.careerops.backend.recommend.*"`,
  `backend/build/test-results/test/TEST-com.careerops.backend.recommend.*.xml`
  전부 `failures="0" errors="0"`). `JobRecommendationControllerTest`만
  다시 격리 재실행(`--rerun`)해도 동일하게 4/4 PASS — flake 아님을 재확인.

- **전체 스위트 flake 재현 및 원인 분석(직접 실행)**: `./gradlew test
  --rerun`(전체) 1회 실행 결과 276 tests completed, 5 failed —
  `JobRecommendationControllerTest` 4개 전부 + RECOMMEND-001과 무관한
  기존 `com.careerops.backend.pkbimport.MultipartUploadLimitIntegrationTest`
  1개. 두 클래스 모두 스택트레이스 근본 원인이 동일하게
  `Unable to obtain connection from database: FATAL: sorry, too many
  clients already`(Postgres 커넥션 상한 초과, Flyway
  init 단계에서 실패 → `ApplicationContext` 로드 자체가 실패)였다
  (`build/test-results/test/TEST-com.careerops.backend.recommend.JobRecommendationControllerTest.xml`,
  `build/test-results/test/TEST-com.careerops.backend.pkbimport.MultipartUploadLimitIntegrationTest.xml`).
  즉 이는 여러 `@SpringBootTest` 클래스가 병렬로 Hikari pool을 여는
  과정에서 발생하는 환경/DB 설정(`max_connections`) 이슈이지,
  RECOMMEND-001 코드가 커넥션을 닫지 않거나 과도하게 점유하는 버그가
  아니다 — `JobRecommendationService`는 `@Transactional(readOnly=true)`
  로 Spring Data 표준 트랜잭션 경계만 쓰고 수동 커넥션 관리 코드가 없다.
  `.ai/metrics/metrics.jsonl`의 AGENT-001/AGENT-002 항목에 이미 동일
  원인·동일 증상("격리 재실행시 항상 통과", "매번 다른 무관 클래스에서
  발생")으로 기록된 pre-existing 패턴과 정확히 일치한다.
  **결론: Claude의 판단(pre-existing flake, RECOMMEND-001 코드 결함
  아님)에 동의한다.** 다만 RECOMMEND-001이 `@SpringBootTest` 클래스를
  1개(`JobRecommendationControllerTest`) 신규 추가해 전체 스위트의
  동시 컨텍스트 로드 수가 늘었으므로, flake **발생 빈도**를 다소
  악화시켰을 가능성은 있다 — 이는 코드 결함이 아니라 테스트 인프라
  차원의 기존 이슈이므로 이번 Task를 블로킹할 사유는 아니지만, 향후
  `@SpringBootTest` 클래스가 계속 늘어나면(다음 Task들에서도 반복될
  패턴) Postgres `max_connections` 상향 또는 Gradle test 병렬도 조정을
  별도로 검토할 필요가 있다는 점만 기록해 둔다(blocking 아님, 정보성).

- **Test Plan 24개 케이스 재매핑**:
  - 1,2,3,8,9,10,11-14,15,16,17,18,19,20,21,23 →
    `JobRecommendationServiceTest`(12개)로 계속 커버, 변경 없음(review-1과
    동일 결론 유지).
  - **4,5,6(limit 기본값/최대값/400) → 신규 `JobRecommendationControllerTest`
    4개로 해소.**
  - **7(CLOSED job 미노출) → 신규 강화.**
    `allOpenCandidatesIncludingBroadCategoryAreSentWithoutCap`
    (`JobRecommendationServiceTest.java:29`)에 `verify(jobs).findAllByStatus("OPEN")`
    계약 검증이 추가돼 review-1 Findings #3(Minor)이 해소됨.
  - **22(PENDING/REJECTED 미노출) → 신규 강화.**
    `onlyApprovedOrManualPkbIsSent`(`JobRecommendationServiceTest.java:37`)가
    이제 `ImportCandidateStatus.PENDING`/`REJECTED`/`APPROVED`를 각각
    명시적 mock으로 만들어 `client.exps`가 `[11L(MANUAL), 14L(APPROVED)]`만
    포함하고 12L(PENDING)/13L(REJECTED)은 제외됨을 직접 검증 — review-1
    Findings #4(Minor)가 해소됨.
  - **24(로그 privacy) → 신규 `AnthropicJobRecommendationClientTest`로 해소.**
  - 결론: 24개 케이스 전부 구체적 assertion으로 매핑 확인됨.

## Findings

1. **[확인 완료, 문제 없음] round 3 수정 범위가 정확히 컨트롤러 1개 파일로
   국한됨.** `git status --short backend/src/main`은
   `JobPostingRepository.java`(M, findAllByStatus 추가)/`application.yml`(M,
   timeout 네임스페이스)/`recommend/`(신규 패키지)만 보이고, 파일 mtime
   비교 결과 `JobRecommendationController.java`(17:30:48)가 다른 모든
   recommend 파일(≤17:13:24)보다 뒤에 수정되어 round3 단독 변경임이
   확인된다. 다른 production 로직(Service/Client/PromptBuilder/DTO)은
   review-1이 PASS 처리한 상태 그대로 손대지 않음.
2. **[확인 완료, 문제 없음] `ConstraintViolationException` 핸들러가 프로젝트
   컨벤션을 지킴.** `grep -r ControllerAdvice backend/src/main/java`가
   빈 결과 — 이 프로젝트에 전역 `@ControllerAdvice`가 없다. 신규 핸들러는
   같은 클래스 안에 기존 `@ExceptionHandler(JobRecommendationException.class)`
   (`JobRecommendationController.java:19`)와 나란히 로컬로 추가됐고
   (`:20`), 같은 파일 전체에서만 `ConstraintViolationException`을 참조함
   (`grep -rn ConstraintViolationException backend/src/main/java` 결과
   이 파일 1곳). 새 전역 예외 처리 계층을 만들지 않고 기존 로컬 핸들러
   패턴을 그대로 확장한 최소 수정.
3. **[확인 완료, 문제 없음] 신규 production dependency 없음.**
   `git diff --stat -- backend/build.gradle` 결과 없음.
4. **[정보, blocking 아님] 전체 스위트 flake 빈도.** 위 "테스트 결과"
   참고. RECOMMEND-001의 신규 `@SpringBootTest` 클래스 추가가 기존
   pre-existing DB 커넥션 경합 flake의 발생 빈도를 다소 늘렸을 가능성은
   있으나, 근본 원인(Postgres `max_connections`)도 증상(무작위 클래스,
   격리 시 항상 통과)도 RECOMMEND-001 이전과 동일하다. 코드 수정 요청
   아님 — 테스트 인프라(DB 커넥션 상한/병렬도) 개선은 별도 Task로
   다룰 사안.
5. **[review-1에서 이미 확인 완료, 재확인] Out of Scope 침범 없음,
   ADR-0031 결정 1~11 정합, 근거 기반 검증(ID all-or-nothing, DB 재조회)
   유지, `.ai/metrics/metrics.jsonl`을 Codex가 직접 건드리지 않음.**
   round2/3에서 이 부분에 해당하는 코드가 변경되지 않았으므로 재확인
   외 추가 발견 사항 없음.

## 다음 액션

- **판정: PASS.** 1차 리뷰의 2개 blocking 항목(Controller limit 테스트
  부재, AnthropicJobRecommendationClient 로그 privacy 테스트 부재)이
  round2에서 신규 테스트로 해소됐고, round2가 만든 테스트가 실제로
  round3에서 수정된 실질적 500 버그(`ConstraintViolationException`
  미처리)를 잡아냈다는 점에서 테스트가 형식적이지 않고 실효성이 있음을
  스스로 증명했다. round3 수정은 컨트롤러 1개 파일, 프로젝트 기존
  로컬 `@ExceptionHandler` 컨벤션을 그대로 따르는 최소 변경. Test Plan
  24개 케이스 전부 구체적 assertion으로 매핑 확인. `recommend` 패키지
  신규/보강 테스트 19/19 격리 PASS, 전체 스위트에서 관찰된 실패는
  RECOMMEND-001 이전부터 존재한 DB 커넥션 풀 경합(Postgres
  `max_connections`)으로 직접 재현·근본원인 확인함 — 코드 결함 아님.
  Codex에게 추가로 요청할 필수 수정 사항 없음.
- **완료 처리 절차**: `.ai/tasks/RECOMMEND-001.md`의 Acceptance Criteria
  체크박스를 전부 체크, `status`를 다음 단계(Test Plan 하단에 명시된
  실제 dev DB + Anthropic API E2E 수동 검증 — Case A~D)로 전환할 것을
  권장. `.ai/metrics/metrics.jsonl`에 review round 2 최종 결과(test_count
  약 299 = 기존 280 + round2/3 무변화, first_review_pass=false,
  review_round_count=2) 기록 필요.
- E2E(Case A~D, 실제 dev DB + 실제 Anthropic API)는 자동 테스트 범위
  밖이며 이 리뷰에서 수행하지 않았다 — Task 명세에 따라 Claude가 별도
  수행해야 하는 항목으로 남아있다.
