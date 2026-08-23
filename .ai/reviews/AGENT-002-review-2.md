---
task_id: AGENT-002
review_round: 2
reviewer: claude
reviewed_at: 2026-08-23T21:10:00+09:00
verdict: PASS
---

## 검토 범위

- `.ai/reviews/AGENT-002-review-1.md`(NEEDS_REVISION, 핵심 설계 계약은
  코드 레벨에서 정확히 구현, 다만 자동 테스트 커버리지 — 특히 로깅
  미검증 테스트와 7개 Acceptance Criteria 대응 테스트 — 누락)를 먼저
  정독.
- `git status --porcelain` / `git diff --stat`으로 이번 라운드에서 실제
  변경된 파일 집합 재확인: 트래킹된 파일 중에는
  `.ai/metrics/metrics.jsonl`/`backend/src/main/resources/application.yml`/
  `docs/DECISIONS.md`/`docs/METRICS.md`만 modified 상태(1차 리뷰 시점과
  동일, Codex 이번 라운드 변경분 아님)이고, `applicationdraft/` 관련
  파일은 전부 untracked 상태(신규 패키지, 아직 커밋되지 않음)라 순수
  `git diff`로는 production 변경 여부를 직접 비교할 수 없었음.
- 대신 `ApplicationDraftService.java`(가장 복잡한 production 파일)의
  전체 내용을 다시 Read해 1차 리뷰가 라인 단위로 인용한 로직
  (`experiences.findAll()`(L47), `validateQuestions()`(L69, `putIfAbsent`
  기반 dedup 없는 questionId 검증), `applyRepair()`(L79-81, try 블록이
  `client.repair()`+`validRepair()`를 모두 감싸고 단일 `catch(RuntimeException)`
  으로 폴백))가 **줄 번호까지 정확히 동일**함을 확인 — 1차 리뷰 이후
  production 코드가 손대지지 않았다는 강한 정황 증거. `AnthropicApplicationDraftClient.java`
  전체도 다시 Read해 `requireApiKey()`가 `plan()`/`repair()`/`call()`
  최상단에서 호출되는 1차 수정 상태 그대로임을 확인. Codex 보고("production
  코드는 전혀 수정하지 않았다")와 정합.
- 신규/변경된 테스트 파일 2개(`ApplicationDraftServiceTest.java` 54줄
  전체, `AnthropicApplicationDraftClientTest.java` 31줄 전체)를 전문
  Read해 각 테스트의 assertion까지 직접 확인(아래 §Acceptance Criteria
  체크 참고).
- `cd backend && ./gradlew test --max-workers=1 --rerun` 직접 실행(Docker
  Compose Postgres/Redis 기동된 상태), 실패 테스트 격리 재실행, 테스트
  결과 XML(`build/test-results/test/TEST-com.careerops.backend.applicationdraft*.xml`)
  직접 확인.

## Acceptance Criteria 체크 — 1차 NEEDS_REVISION 사유였던 항목만 재검증

(1차 리뷰에서 이미 PASS 판정한 나머지 항목 — ADR-0030 결정 1/4/5 코드
구현, `agent`/`match`/`career` 무변경, `application.yml`/`docs/METRICS.md`
— 은 production 코드가 이번 라운드에 변경되지 않았으므로 재검증하지
않고 1차 결과를 그대로 신뢰함.)

- [x] **로그에 `question` 원문/`draft` 전체/`CareerExperience.detail`/
      `summary` 원문이 남지 않는다** —
      `AnthropicApplicationDraftClientTest.missingKeyAndSensitiveInputsAreNotLogged`
      (`AnthropicApplicationDraftClientTest.java:20-30`)가 `ListAppender`를
      root logger에 부착하고, `SENSITIVE-DRAFT-QUESTION`/
      `SENSITIVE-FULL-DRAFT`/`SENSITIVE-PKB-SUMMARY`/`SENSITIVE-PKB-DETAIL`을
      담은 `CareerExperience`/`QuestionRequest`/`RawQuestionDraft`로
      `client.plan(...)`과 `client.repair(...)`를 모두 blank key 상태로
      호출한 뒤, 캡처된 로그 전체(`getFormattedMessage()` concat)에 4개
      민감값이 전혀 없음을 `doesNotContain(...)`으로 검증. `agent/llm/AnthropicAgentAnalysisClientTest.missingKeyAndSensitiveInputAreNotLogged`
      (AGENT-001에서 이미 리뷰 통과한 레퍼런스 패턴)와 구조가 동일 —
      두 테스트 모두 blank key로 즉시 실패시켜 실제 프롬프트 조립 이전에
      끝나는 구조라 "강한" 회귀 검증은 아니지만, 이 프로젝트의 기존
      확립된 컨벤션과 정확히 동일한 강도이며 Task 명세 §12가 명시적으로
      요구한 "`missingKeyAndSensitiveInputAreNotLogged` 패턴을 재현"을
      문자 그대로 충족한다. `AnthropicApplicationDraftClient.java`를
      직접 재확인한 결과 `plan()`/`repair()`/`call()` 어디에도 이
      클래스 자체에서 로그를 남기는 코드가 없고(`requireApiKey()`가
      항상 프롬프트 조립보다 먼저 실행됨, L29/L32/L35), `ApplicationDraftService`의
      유일한 로그 3곳(L60 성공/L63 실패/L78 dedup 경고, L81 repair
      실패)도 전부 id/개수/enum만 남겨 원문을 노출하지 않음을 재확인 —
      코드와 테스트가 일치.
- [x] **PKB empty 409 전파 + `client.plan()` 미호출** —
      `pkbEmptyConflictIsPropagatedAndPlanIsNotCalled`
      (`ApplicationDraftServiceTest.java:38`)가 `analysis.analyze(1L)`이
      `ResponseStatusException(CONFLICT)`을 던지도록 stub한 뒤,
      `service.draft(...)`가 던진 예외의 status가 정확히 `CONFLICT`임을
      확인하고 `verify(client, never()).plan(...)`(9-arg matcher, 실제
      인터페이스 시그니처와 일치)로 미호출을 강제.
- [x] **`AgentAnalysisException` 실패 시 `client.plan()` 미호출** —
      `agentAnalysisFailureStopsBeforePlan`(L39)가 `AgentAnalysisException`을
      stub하고, 던져진 `ApplicationDraftException.reason()`이
      `AGENT_ANALYSIS_FAILED`임과 `verify(client, never()).plan(...)`을
      함께 확인.
- [x] **문항 4개, 순서 무관 `questionId` 매칭** —
      `fourQuestionsAreMatchedByIdDespiteDifferentProviderOrder`(L40)가
      지원동기/직무역량/문제해결/협업 4문항을 request로 보내고, LLM이
      `team, problem, support, job` 순서(요청 순서와 다름)로 결과를
      반환해도 응답 `questions`가 정확히 request 순서(`support, job,
      problem, team`)와 그에 대응하는 draft 텍스트(`d1, d2, d3, d4`)로
      매칭됨을 `containsExactly`로 검증 — index 매칭이 아니라 `questionId`
      맵 매칭임을 직접 증명하는 테스트.
- [x] **`MISSING_QUESTION_RESULT`** —
      `missingQuestionResultFails`(L41)가 request 2문항(`q1`,`q2`) 중
      `q1`만 LLM이 반환하도록 stub하고, `reason()`이
      `MISSING_QUESTION_RESULT`인 `ApplicationDraftException`이 던져짐을
      확인.
- [x] **cert/education/award unknown id 3종 각각 502** —
      `unknownCertificationIdFails`/`unknownEducationIdFails`/
      `unknownAwardIdFails`(L42-44)가 공용 헬퍼 `assertUnknownPkbIds`를
      통해 각각 `certificationIds=[81]`/`educationIds=[82]`/
      `awardIds=[83]`(모두 PKB에 존재하지 않는 id, `primaryExperienceId`는
      알려진 9로 고정)로 3개 카테고리를 독립적으로 검증하고 각각
      `UNKNOWN_CANDIDATE_ID`를 확인 — 1차 리뷰가 "`primaryExperienceId`만
      테스트되고 3개 카테고리 미검증"이라 지적한 gap이 정확히 메워짐.
- [x] **repair 후에도 초과 → 재시도 없이 `limitExceeded=true`** —
      `repairThatStillExceedsLimitDoesNotRetry`(L45)가 `maxLength=3`,
      1차 draft `"123456"`(6자), repair 응답 `"12345"`(5자, 여전히
      초과)를 stub하고, 최종 `draft=="12345"`(repair 결과가 반영됨),
      `limitExceeded=true`, `verify(client, times(1)).repair(...)`(재시도
      없이 정확히 1회만 호출)를 확인 — 기존 "repair 성공"/"repair
      provider 실패" 2개 케이스와 구분되는 세 번째 분기를 정확히 겨냥.
- [x] **`maxLength` 미지정 문항은 항상 `limitExceeded=false`, repair
      제외** — `questionWithoutMaxLengthIsNeverRepairedOrExceeded`(L46)가
      `maxLength=null`에 10,000자 draft를 stub하고, `limitExceeded==false`,
      `characterCount==10000`, `verify(client, never()).repair(...)`를
      확인.
- [x] `[Non-blocking, 선택 항목]` "AGENT-001 추천 후보 밖 id 허용" 테스트
      개선 — `outsideAgentRecommendationsButApprovedFullPkbIsAcceptedAndIdsAreDeduplicated`
      (L32)가 이제 `strategyWithDifferentRecommendation()`(L53, 추천
      목록에 `id=77 "AI/RAG 경험"`을 담은 비어있지 않은
      `ExperienceRecommendation`)을 사용해, "추천 목록이 완전히 비어
      있어서 우연히 통과"가 아니라 "추천 목록에 다른 경험이 있는데도
      id=9(추천 밖 협업 경험)가 승인 PKB에 있다는 이유만으로 허용됨"을
      더 명확히 드러냄 — AC 원문("AI/RAG 위주로 추천하도록 구성한 상태")
      의도에 더 가까워짐.

## 테스트 결과

- `cd backend && ./gradlew test --max-workers=1 --rerun` → **257 tests
  completed, 1 failed**(`MultipartUploadLimitIntegrationTest`).
  `--tests "*MultipartUploadLimitIntegrationTest*"` 격리 재실행 →
  **BUILD SUCCESSFUL**(PASS) — 1차 리뷰에서 이미 이 Task와 무관한 기존
  DB 커넥션 풀 경합 flake로 진단된 것과 동일 현상 재확인. FAIL 사유로
  삼지 않음(지시사항 §3).
- `applicationdraft` 패키지 전용(`build/test-results/test/TEST-com.careerops.backend.applicationdraft*.xml`
  직접 확인):
  - `ApplicationDraftControllerTest`: 2/2
  - `ApplicationDraftServiceTest`: **17/17**(1차 8개 + 이번 라운드 신규
    9개)
  - `AnthropicApplicationDraftClientTest`: **3/3**(1차 2개 + 이번 라운드
    신규 로깅 테스트 1개)
  - `ApplicationDraftPromptBuilderTest`: 2/2
  - **합계 24/24 PASS**, 전부 실패 0/에러 0(`tests="N" ... failures="0"
    errors="0"`).
- 신규 10개 테스트가 1차 리뷰 Findings 1·2가 지적한 gap(로깅 미검증,
  PKB empty 409/AGENT-001 실패 미호출/문항 4개 순서 무관/
  MISSING_QUESTION_RESULT/cert·edu·award unknown id 3종/repair-후에도-초과/
  maxLength-null) **7개 항목 전부**에 1:1로 대응함을 각 테스트의
  assertion까지 직접 읽고 확인(위 §Acceptance Criteria 체크 참고).

## Findings

- 원칙 위반 없음: 신규 production dependency 없음(`build.gradle` diff
  없음), Secret/API key 커밋 없음(테스트는 blank/빈 문자열 key만 사용),
  자기소개서 텍스트의 사실 검증 자체는 스펙대로 validation 대상이
  아니며(ADR-0029/ADR-0030 근거 명시) 이번 변경은 테스트 추가뿐이라
  해당 원칙과 무관.
- production 코드 변경 없음을 `git diff`로 직접 비교하지는 못했다(신규
  패키지가 아직 git에 커밋되지 않아 untracked 상태이므로 baseline
  diff 불가). 대신 1차 리뷰가 인용한 정확한 줄 번호·로직이 현재 파일과
  그대로 일치함을 직접 대조해 우회 검증했다 — 완벽한 대체는 아니지만
  실질적으로 production 로직에 손이 가지 않았다는 강한 근거다. 다음
  라운드부터는 이런 대조를 쉽게 하기 위해, PASS 판정 직후 Claude가
  `applicationdraft` 관련 파일을 커밋해 두는 것을 권장한다(이 리뷰의
  판정 자체에는 영향 없음, 향후 리뷰 편의를 위한 참고).
- 로깅 테스트(및 AGENT-001의 동일 레퍼런스 패턴)가 blank key 즉시 실패
  경로만 exercise하는 구조적 한계는 1차 리뷰 이후에도 동일하게 남아
  있다(§Acceptance Criteria 체크 첫 항목 참고). 다만 이는 이 Task만의
  문제가 아니라 프로젝트 전체가 이미 채택한 컨벤션이고, Task 명세가
  "그 패턴을 재현하라"고 명시적으로 요구했으므로 이번 라운드의 blocking
  사유로 삼지 않는다.

## 다음 액션

**PASS.** 1차 NEEDS_REVISION의 두 blocking 사유(로깅 미검증 테스트 부재,
7개 Acceptance Criteria 대응 테스트 부재)가 신규 테스트 10개로 정확히
해소되었고, 각 테스트의 assertion을 직접 읽어 실제로 해당 조건을
검증함을 확인했다. Non-blocking 선택 항목(추천 후보 밖 id 테스트 개선)도
반영됨. production 코드는 이번 라운드에 변경되지 않았고(1차 리뷰의 코드
정확성 판정을 그대로 승계), `./gradlew test` 전체 257개 중
`MultipartUploadLimitIntegrationTest`(무관 기존 flake) 1건만 실패,
`applicationdraft` 24/24 PASS.

호출한 Claude에게: 이 Task는 자동 테스트 라운드 기준 완료 처리 가능.
남은 것은 Task 명세 Acceptance Criteria 마지막 항목(`[수동]` 실제 dev DB
+ 실제 Anthropic API E2E, jobId=7552)뿐이며 이는 리뷰 범위 밖(Claude가
별도 수행)이다. `.ai/metrics/metrics.jsonl`에 최종 상태 기록을 권장한다.
