---
task_id: AGENT-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-23T20:00:00+09:00
verdict: NEEDS_REVISION
---

## 검토 범위

- Task 명세: `.ai/tasks/AGENT-002.md` (Scope §1~14, Out of Scope, Acceptance
  Criteria, Technical Notes, Test Plan 전체 정독)
- 설계 근거: `docs/DECISIONS.md` ADR-0030 (전체 정독) + 참고 선례 ADR-0029/
  AGENT-001(`.ai/reviews/AGENT-001-review-1.md`)
- Codex thread: `01a02e24-139e-7041-bf36-0928b64383f1` (round 1, 컴파일
  오류 1건 + `blankKeyFailsWithoutProviderCall` 순서 버그 1건을 같은
  thread에서 수정 완료된 상태)
- `git status --porcelain`으로 실제 변경 파일 집합 확인: 신규 17개
  production 파일(`applicationdraft/` 전체) + 신규 4개 test 파일, 기존
  파일 수정은 `backend/src/main/resources/application.yml`/
  `docs/METRICS.md`/`docs/DECISIONS.md`(Claude 작성)/
  `.ai/metrics/metrics.jsonl`뿐이며 `agent/`/`match/`/`career/` 디렉터리는
  0바이트도 수정되지 않음을 확인.
- production 파일 17개 전부(`ApplicationDraftController`/`Service`/DTO
  7종/`llm/` 5종/`llm/dto/` 4종)와 test 파일 4개 전부(`ApplicationDraftServiceTest`
  8개, `ApplicationDraftControllerTest` 2개, `ApplicationDraftPromptBuilderTest`
  2개, `AnthropicApplicationDraftClientTest` 2개 — 총 14개 테스트)를 직접
  Read.
- `cd backend && ./gradlew test --max-workers=1 --rerun` 직접 실행(전체
  및 `applicationdraft` 패키지 필터).

## Acceptance Criteria 체크

핵심 설계 계약(ADR-0030 결정 1/4/5)은 코드 레벨에서 정확히 구현되어
있음을 직접 라인 단위로 확인했다.

- [x] **PKB id 유효 집합이 승인 PKB 전체(`findAll()`)이고 AGENT-001
      추천 후보로 제한되지 않음** — `ApplicationDraftService.java:47`
      `experiences.findAll()`/`certifications.findAll()`/
      `educations.findAll()`/`awards.findAll()`을 그대로 `ids()`로 변환해
      `validateAndDedupIds()`의 valid set으로 사용(L48, L53). `strategy`
      (AGENT-001 응답)의 `recommendedExperiences` 등은 오직
      `ApplicationDraftPromptBuilder.strategy()`(프롬프트 참고 자료)에서만
      읽히고 id 검증 로직 어디에서도 참조되지 않음을 grep/직접 코드
      추적으로 확인. `ApplicationDraftServiceTest.
      outsideAgentRecommendationsButApprovedFullPkbIsAcceptedAndIdsAreDeduplicated`
      (L29)가 `strategy()`에 `recommendedExperiences=List.of()`(빈 배열)를
      주고 `primaryExperienceId=9`(승인 PKB에는 있지만 추천 목록엔 없는
      id, `QuestionIntent.COLLABORATION`)를 반환해도 502가 아니라 정상
      반영됨을 검증 — ADR-0030 결정 1을 실제로 강제하는 테스트. 다만
      AC 원문이 요구한 "AGENT-001이 AI/RAG 위주로 추천하도록 구성"
      시나리오(비어있지 않은 recommendedExperiences 목록에 id=9가 없는
      경우)보다는 약한 형태(recommendedExperiences 자체를 빈 배열로 둠)
      다 — 결과적으로 계약은 검증되지만 테스트 시나리오가 스펙 문구보다
      단순화됨(§Findings 3 참고, 코드 자체는 올바름).
- [x] **questionId 축(dedup 아님)과 PKB id 축(dedup)이 다르게 처리됨**
      — `validateQuestions()`(L69)는 `result.putIfAbsent(...)!=null`이면
      즉시 `DUPLICATE_QUESTION_RESULT` 예외(dedup 없음). `dedup()`(L78)은
      `LinkedHashSet`으로 실제 dedup하고 WARN 로그만 남김. 두 axis가 서로
      다른 메서드로 완전히 분리되어 구현됨.
      `duplicateQuestionResultIsNotDeduplicated` 테스트로 questionId 축
      확인, 위 outsideAgentRecommendations 테스트로 PKB id 축(supporting
      `[9,9]`→`[9]`) 확인.
- [x] **글자수 repair가 provider 실패=전체 실패 원칙의 유일한 예외** —
      `applyRepair()`(L79-81)의 `try` 블록이 `client.repair(...)`와
      `validRepair(...)` 검증을 모두 감싸고, `catch(RuntimeException ex)`
      한 곳에서 폴백(원본 draft 유지) + `repairCounters.get("failed")`.
      전체 요청은 그대로 200을 반환(호출부 `calculate()`에서 이 예외를
      전파하지 않음). `repairProviderFailureFallsBackWithLimitExceeded`
      테스트로 확인. plan()/AGENT-001 실패는 여전히
      `ApplicationDraftException`/`ResponseStatusException`이 밖으로
      전파되어 502(비대칭 확인).
- [x] `agent`/`match`/`career` 패키지 무변경 — `git status --porcelain`에
      해당 디렉터리 변경 없음 확인.
- [x] `docs/METRICS.md`에 5개 지표 행 추가, 문구까지 Task 명세와 완전히
      일치(`git diff` 확인).
- [x] `application.yml`에 `careerops.ai.application-draft.connect-timeout-seconds`
      (10)/`request-timeout-seconds`(150) 추가, `careerops.ai.api-key`/
      `model` 재사용(diff 확인, `AnthropicApplicationDraftClient` 생성자가
      기존 키만 주입받음, 신규 키 없음).
- [x] `./gradlew test --max-workers=1 --rerun`(전체) → 247개 중
      `MultipartUploadLimitIntegrationTest` 1건만 실패, 격리 실행
      (`--tests "*MultipartUploadLimitIntegrationTest*"`)하면 PASS —
      AGENT-001 리뷰에서 이미 진단된 기존 DB 커넥션 풀 경합 flake로
      이번 Task와 무관함을 직접 재현해 확인. `agent`/`match`/`career`
      회귀 없음.
- [ ] **로그에 request `question` 원문/응답 `draft` 전체/
      `CareerExperience.detail`/`summary` 원문이 남지 않는다** —
      **미검증.** `AnthropicApplicationDraftClientTest.java`에는
      `ListAppender` 기반 로깅 캡처 테스트가 전혀 없다(`blankKeyFailsWithoutProviderCall`,
      `blankKeyFailsBeforeRepairPromptIsBuilt` 2개뿐). Task 명세가 명시적으로
      요구한 "`AnthropicSemanticJobMatchClientTest`/
      `AnthropicAgentAnalysisClientTest`의
      `missingKeyAndSensitiveInputAreNotLogged` 패턴을
      `AnthropicApplicationDraftClient`에도 재현"이 전혀 이행되지 않음
      (`grep -rn "ListAppender" src/test/java/com/careerops/backend/applicationdraft/`
      결과 0건, `agent/llm/AnthropicAgentAnalysisClientTest.java`에는
      존재함을 대조 확인). `ApplicationDraftService`가 INFO 로그에
      `characterMetric`/개수만 남기는 것(`ApplicationDraftService.java:60`)은
      코드상 안전해 보이지만, **명시적으로 요구된 자동 검증이 없어
      회귀를 잡아줄 안전판이 없다.**
- [ ] 아래 checkbox 항목들은 코드 검토상 로직은 정확해 보이나(§Findings
      참고) **자동 테스트로 검증되지 않음**:
  - PKB 4종 전부 0건 → 409(AGENT-001의 409 전파), draft LLM 미호출
  - 문항 4개 → 200, 정확히 4개 결과 + 순서 무관 questionId 매칭
  - LLM 응답에서 request questionId 중 하나가 누락 → 502
    (`MISSING_QUESTION_RESULT`)
  - 승인 PKB에 없는 `certificationIds`/`educationIds`/`awardIds` 값 →
    각각 502(`primaryExperienceId`만 테스트됨, 3개 카테고리 미검증)
  - `maxLength` 초과 → repair 후에도 여전히 초과 → 재시도 없이
    `limitExceeded=true`(현재는 "repair 성공"과 "repair provider 실패"
    2개 케이스만 있고, "repair는 성공했지만 결과가 여전히 초과"라는
    세 번째 분기가 없음)
  - `maxLength`가 없는 문항은 `draft` 길이와 무관하게 항상
    `limitExceeded=false`이고 repair 대상에서 제외
  - `AgentAnalysisService`(fake)가 실패를 던지면 `ApplicationDraftClient.plan()`이
    전혀 호출되지 않음(fake 호출 카운트 검증) — Task 명세가 Test Plan
    항목 25로 명시했으나 대응하는 테스트 없음
- [수동, 이번 라운드 범위 아님] 실제 dev DB + Anthropic API E2E(jobId=7552)
  — 리뷰 통과 후 Claude가 별도 수행 예정이므로 이번 라운드에서 판정 제외.

## 테스트 결과

- `cd backend && ./gradlew test --max-workers=1 --rerun` → **247 tests
  completed, 1 failed**(`MultipartUploadLimitIntegrationTest`, 기존 flake).
  `--tests "*MultipartUploadLimitIntegrationTest*"` 격리 실행 시 PASS —
  이번 Task와 무관함을 재확인.
- `applicationdraft` 패키지 전용: `ApplicationDraftControllerTest` 2/2,
  `ApplicationDraftServiceTest` 8/8, `AnthropicApplicationDraftClientTest`
  2/2, `ApplicationDraftPromptBuilderTest` 2/2 — **총 14/14 PASS**
  (`build/test-results/test/TEST-com.careerops.backend.applicationdraft*.xml`
  직접 확인). 다만 이 14개는 Task 명세 Test Plan이 명시한 최소 26개
  항목(`applicationdraft` 관련, 1~28 중 회귀 항목 제외) 대비 커버리지가
  낮다(§Findings, §Acceptance Criteria 체크 참고).
- 자동 테스트에서 실제 Anthropic API 호출 코드/하드코딩 키 없음(테스트
  코드에 `messages().create` 실호출 없음, blank key 즉시 실패 경로만
  테스트).

## Findings

1. **[Blocking] 로깅 미검증 테스트 완전 부재** — Task 명세 §12/Test Plan
   항목 28이 명시적으로 요구한 `ListAppender` 기반
   "question 원문/draft 전체/PKB 원문 미노출" 테스트가
   `AnthropicApplicationDraftClientTest.java`에 전혀 없다. 이 프로젝트는
   민감정보 미로깅을 AGENT-001/MATCH-002에서 이미 확립한 패턴으로 매번
   재검증해왔고(`AnthropicAgentAnalysisClientTest`,
   `AnthropicSemanticJobMatchClientTest` 모두 존재), 이번만 빠진 것은
   회귀 안전판이 없는 상태로 프로덕션에 나가는 것과 같다. 코드 자체는
   위험해 보이지 않지만("검토상 안전"과 "테스트로 강제됨"은 다르다),
   AGENTS.md의 "Secret/API Key는 절대 로그에 남기지 않는다"류 원칙과
   동일선상의 민감정보 로깅 방지 원칙이 자동 검증 없이 방치되는 것은
   이 리뷰 지시사항이 "핵심 리스크"로 특정한 항목이기도 하다.
2. **[Blocking] Acceptance Criteria 체크박스 다수가 자동 테스트로
   커버되지 않음** — 위 체크리스트에 나열한 7개 항목(PKB empty 409,
   문항 4개/순서 무관 매칭, MISSING_QUESTION_RESULT, cert/edu/award
   unknown id 3종, repair-후에도-초과, maxLength-null 항상 false,
   AGENT-001 실패 시 draft LLM 미호출)이 `ApplicationDraftServiceTest`/
   `ApplicationDraftControllerTest` 어디에도 대응하는 테스트가 없다.
   프로덕션 코드를 직접 읽은 결과 로직 자체는 이 계약들을 지키는 것으로
   보이나(예: `known()`/`dedup()`이 4개 카테고리에 동일하게 적용되므로
   cert/edu/award도 동작할 것으로 추정됨), Task 명세가 "각 항목을
   1:1에 가깝게 커버한다"고 명시적으로 요구했고 이는 향후 리팩터링 시
   회귀를 잡아줄 안전판이 currently 없다는 뜻이다. 특히
   "AGENT-001 실패 시 draft LLM 미호출"은 AGENT-001 리뷰에서도 핵심으로
   다뤄졌던 "provider 실패=전체 실패, silent fallback 없음" 원칙의 직접
   증거이므로 테스트 부재가 더 아쉽다.
3. **[Non-blocking, 참고] "AGENT-001 추천 후보 밖 id 허용" 테스트가
   AC 원문보다 단순화됨** — `outsideAgentRecommendationsButApprovedFullPkbIsAcceptedAndIdsAreDeduplicated`
   테스트는 `recommendedExperiences=List.of()`(완전히 빈 배열)로
   전략을 구성한다. AC 원문은 "AGENT-001이 AI/RAG 관련 경험만 추천하도록
   구성한 상태에서"라고 되어 있어, 비어있지 않은 추천 목록에 id=9가
   빠져 있는 시나리오를 기대한 것으로 읽힌다. 코드 자체(§Acceptance
   Criteria 체크의 첫 항목)는 `strategy`의 추천 목록을 아예 참조하지
   않으므로 이 차이가 실제 버그를 놓칠 가능성은 낮지만, 테스트 의도가
   더 명확하게 드러나려면 비어있지 않은 recommendedExperiences를 구성해
   대조군을 만드는 편이 낫다.
4. 그 외 발견된 원칙 위반 없음 — 신규 production dependency 없음,
   API key/secret 커밋 없음, 과도한 추상화 없음(AGENT-001/`match/semantic`
   패턴을 그대로 미러링), 자기소개서 텍스트 자체의 사실 검증은 스펙대로
   validation 대상이 아니며 이는 ADR-0029/ADR-0030에 근거가 명시되어
   있어 원칙 위반이 아니다.

## 다음 액션

**NEEDS_REVISION.** 핵심 설계 계약(ADR-0030 결정 1/4/5)은 코드 레벨에서
정확히 구현되어 있고 발견된 버그는 없다. 다만 Task 명세가 명시적으로
요구한 자동 테스트 커버리지(특히 로깅 미검증 테스트)가 상당 부분
누락되어 있어, 같은 Codex thread(`01a02e24-139e-7041-bf36-0928b64383f1`)에
아래를 그대로 요청한다.

1. `AnthropicApplicationDraftClientTest`에 `ListAppender` 기반 로깅
   검증 테스트를 추가한다. `agent/llm/AnthropicAgentAnalysisClientTest`의
   `missingKeyAndSensitiveInputAreNotLogged` 패턴을 그대로 재현하되,
   대상은 `question` 원문, `draft` 전체, `CareerExperience.detail`/
   `summary` 원문이다. `ApplicationDraftService`(또는 필요하면 `ApplicationDraftPromptBuilder`)
   레벨에서도 실제 로그 출력에 이 값들이 없는지 캡처해 확인한다.
2. `ApplicationDraftServiceTest`에 아래 케이스를 추가한다:
   - `AgentAnalysisService.analyze()`가 (실제 예외든 `ResponseStatusException(CONFLICT)`든)
     PKB empty 409를 던지는 경우 → 그대로 409 전파, `client.plan()`
     미호출(`verify(client, never()).plan(...)`).
   - `AgentAnalysisService.analyze()`가 `AgentAnalysisException`을 던지는
     경우 → `client.plan()`이 전혀 호출되지 않고 502
     (`verify(client, never()).plan(...)`).
   - 문항 4개(지원동기/직무역량/문제해결/협업)로 정상 호출 → 응답
     `questions` 정확히 4개, request 순서와 다른 순서로 LLM이 결과를
     반환해도 `questionId` 기준으로 정확히 매칭됨.
   - LLM 응답에서 request questionId 중 하나가 누락된 경우 →
     `MISSING_QUESTION_RESULT`로 502.
   - 승인 PKB에 없는 `certificationIds`/`educationIds`/`awardIds` 값을
     반환하는 경우 각각 `UNKNOWN_CANDIDATE_ID`로 502(현재는
     `primaryExperienceId` 카테고리만 테스트됨).
   - `maxLength` 초과 → repair가 성공적으로 응답은 하지만 결과 draft가
     여전히 `maxLength`를 초과하는 경우 → 재시도 없이
     `limitExceeded=true`로 200.
   - `maxLength`가 없는 문항은 draft가 길어도 `limitExceeded=false`이고,
     `client.repair(...)`가 해당 문항을 포함하지 않고 호출됨(또는
     repair 자체가 호출되지 않음)을 확인.
3. (선택) `outsideAgentRecommendationsButApprovedFullPkbIsAcceptedAndIdsAreDeduplicated`
   테스트에서 `recommendedExperiences`를 완전히 빈 배열이 아니라 id=9를
   포함하지 않는 다른 경험 추천 목록으로 구성해, AC 원문의 시나리오
   ("AI/RAG 위주 추천 중에도 후보 밖 협업 경험 허용")를 더 명확히
   반영한다(blocking 아님, 코드 계약은 이미 검증됨).

위 테스트가 추가되고 전체 `./gradlew test`가 통과하면 재검토 요청.
1라운드이므로 아직 명세 자체의 모호성 문제는 없다고 판단된다 — 문제는
Codex의 테스트 커버리지 실행 누락이지 Task 명세의 불명확성이 아니다.
