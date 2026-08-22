---
task_id: AGENT-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-22T23:00:00+09:00
verdict: PASS
---

## 검토 범위

- Task 명세: `.ai/tasks/AGENT-001.md`
- 설계 근거: `docs/DECISIONS.md` ADR-0029 (전체 정독)
- Codex thread: `01a029a7-71af-7370-b38f-77486328b5d1` (round 1, `codex_invocation_count=3` — 컴파일 오류 1건 + 테스트 버그 2건 수정 후 확정)
- `git status --porcelain` / `git diff --stat`로 실제 변경 파일 집합을 직접 확인 (신규 13 production + 4 test 파일, 기존 파일 수정은 `ExperienceBulletRepository.java`(+1줄)/`application.yml`(+3줄)/`docs/METRICS.md`(+8줄)/`.ai/metrics/metrics.jsonl`(+1줄, Claude가 기록)뿐이며 `match/`, `match/semantic/` 디렉터리는 0바이트도 수정되지 않음을 확인).
- 모든 production 파일(`AgentAnalysisService`/`Controller`/`PromptBuilder`/`AnthropicAgentAnalysisClient`/`AgentAnalysisException`/`AgentAnalysisClient`/DTO 7종)과 테스트 4개 파일 전체를 직접 Read.
- `cd backend && ./gradlew test --max-workers=1` 직접 실행(전체 및 `agent` 패키지만 필터링).

## Acceptance Criteria 체크 (`.ai/tasks/AGENT-001.md` 21개 항목)

- [x] 존재하지 않는 jobId → 404, 두 LLM 미호출 — `AgentAnalysisService.calculate()` L52-53(`jobs.findById` 없으면 즉시 `ResponseStatusException(NOT_FOUND)`), `AgentAnalysisControllerTest.missingJobCallsNeitherLlm`(fake 호출 카운트 0 확인)로 검증.
- [x] PKB 4종 전부 0건 → 409, 두 LLM 미호출 — L54-55에서 `semanticService.match()` 호출 **이전**에 count 체크. `emptyPkbReturnsConflictAndCallsNeitherLlm` 테스트로 확인.
- [x] semantic match 성공 시 200 — 컨트롤러 테스트 다수가 정상 200 케이스를 거침.
- [x] LLM 배열 순서가 priority에 그대로 반영 — `AgentAnalysisServiceTest.categoryCapsKeepOriginalArrayOrderInsteadOfSortingBySemanticScore`가 **오름차순 score(0.1~0.6)를 가진 6개 경험을 LLM이 id 오름차순(=score 오름차순)으로 반환**했을 때 캡 적용 후 `[1,2,3,4,5]`(가장 낮은 score 5개, score 내림차순이면 `[6,5,4,3,2]`가 나와야 함)가 그대로 유지됨을 확인 — score 재정렬이 아니라는 것을 실제로 반증 불가능한 방식(대조군)으로 검증한 강한 테스트. (참고: `AgentAnalysisControllerTest.preservesAgentOrderAssignsPriorityAndSemanticScoresAndPassesGaps`는 우연히 LLM 순서와 score 내림차순이 일치해 그 자체로는 "순서 vs score재정렬"을 구분하지 못하지만, 위 ServiceTest가 이를 명확히 보완함.)
- [x] 후보 풀 밖 경험 미노출 — `candidatePoolOnlyIsReloadedAndBulletsAreSorted`에서 `agent.experiences`가 selected만 포함하고 excluded는 아예 프롬프트 입력 후보에도 없음을 확인(후보 풀 제한이 출력 검증 이전, 입력 구성 단계에서부터 이뤄짐).
- [x] 카테고리별 unknown id(경험/자격/학력/수상 각 최소 1건) → 502, 노출 없음 — `unknownCandidateInEveryCategoryFailsWholeResponse` 4개 케이스 모두 `502`+`recommendedExperiences` 필드 자체 부재(빈 body) 확인.
- [x] 상한 초과 시 앞쪽 N개(경험5/자격·학력·수상3) 유지, 재정렬 없음 — `AgentAnalysisServiceTest`(경험6→5, 자격/학력/수상 각 4→3) 확인.
- [x] 중복 id → 첫 등장만 유지, priority 1..N 재부여 — `duplicateKeepsFirstAndReassignsGaplessPriority` 확인.
- [x] Raw record에 score/relevanceScore/priority/gaps/type 없음 — 소스 직접 읽음(`RawAgentAnalysisResult`/`RawExperienceRecommendation`/`RawPkbRecommendation`, 모두 스펙과 필드명·순서까지 완전히 일치). `AgentAnalysisContractTest.structuredOutputHasNoForbiddenFields`가 `RecordComponent` 리플렉션으로 5개 금지어를 실제로 검사.
- [x] `semanticMatchScore`가 MATCH-002 원본 score와 일치 — `AgentAnalysisService.scores()`(L104)가 `SemanticMatchEvidence::score`에서만 값을 가져오고 LLM 출력에는 해당 필드 자체가 없음(컴파일 타임 보장). 테스트로도 수치 일치 확인(`.91`/`.11`/`.37` 등).
- [x] MATCH-002 reason 미포함 — `grep -rn "\.reason()" agent/`로 직접 확인한 결과 `SemanticMatchEvidence.reason()` 호출이 `agent` 패키지 어디에도 없음(발견된 `.reason()` 호출은 전부 `AgentAnalysisException.reason()` 또는 Raw*.reason() — Agent 자신의 출력 필드). `AgentAnalysisContractTest.promptIncludesOriginalContextAndBulletsButNeverSemanticReason`이 `UNIQUE_REASON_MARKER` 부재를 실제로 검증.
- [x] `gaps` pass-through — `RawAgentAnalysisResult`에 gaps 필드 없음, `AgentAnalysisService.convert()` L89에서 `safe(match.gaps())`로 MATCH-002 gaps를 그대로 복사. 테스트로 fake gaps 값 일치 확인.
- [x] truncate/개수 상한 정책(300자/150자/5개/3개) — `truncatesTextAndListLimitsWithoutFailure` 테스트로 전항목 확인, 실패 아님(200 유지)도 함께 확인.
- [x] agent LLM timeout/malformed → 502 — `semanticFailureStopsAgentAndAgentFailuresReturnBadGateway`.
- [x] semantic match 실패 시 agent LLM 미호출 → 502 — 동일 테스트, `agent.calls` 0 확인.
- [x] ImportCandidate PENDING/REJECTED 미노출 — 아키텍처상 `career_*` 테이블에는 애초에 승인된 레코드만 존재(`ImportCandidateStatus`는 `pkbimport` 패키지에만 존재, `career`/`match`/`agent`는 이를 참조하지 않음을 grep으로 확인). MATCH-002가 이미 검증한 것과 동일한 저장소를 그대로 재사용하므로 회귀 없음.
- [x] `ExperienceBulletRepository.findByCareerExperienceIdIn` — `bulletRepositoryFindsAcrossMultipleExperienceIds` 테스트로 확인(2개 id 조회, 제3자 id 제외 확인).
- [x] `docs/METRICS.md` 3행 추가 — diff로 정확한 표 행(Prometheus명/Micrometer명/타입/태그/의미/계측위치) 명세와 1:1 일치 확인.
- [x] `application.yml` agent timeout 2개 키(10/60), 기존 api-key/model 재사용 — diff 확인, `AnthropicAgentAnalysisClient` 생성자가 `careerops.ai.api-key`/`careerops.ai.model`을 그대로 주입받음(신규 키 없음).
- [x] `[자동]` match 회귀 + `git diff --stat`로 `match/` 무변경 — `git status --porcelain` 결과 0건, `./gradlew test --tests "com.careerops.backend.match.*"` 직접 실행 통과.
- [x] `./gradlew test` 전체 — 아래 테스트 결과 참고.
- [ ] `[수동]` Case A~D — 실제 dev DB + 실제 Anthropic API 필요, 이번 라운드에서 실행 불가(자동 리뷰 범위 밖). Codex thread 기록에도 아직 수행 안 됨으로 표기돼 있음 — Task 상태를 `passed`로 완전히 닫기 전에 Claude/사용자가 별도로 수행해야 함.

## 테스트 결과

- `cd backend && ./gradlew test --tests "com.careerops.backend.agent.*" --max-workers=1` → **14/14 PASS** (`AgentAnalysisContractTest` 2, `AgentAnalysisControllerTest` 9, `AgentAnalysisServiceTest` 1, `AnthropicAgentAnalysisClientTest` 2).
- `cd backend && ./gradlew test --max-workers=1` (전체) → **233 tests completed, 1 failed**. 실패 항목은 `com.careerops.backend.pkbimport.MultipartUploadLimitIntegrationTest` 뿐이며, 해당 테스트만 격리 실행(`--tests` 필터)하면 **PASS** — Claude가 사전에 보고한 "AGENT-001과 무관한 기존 flake"라는 진단을 직접 재현해 확인함. `agent` 패키지 회귀는 전무.
- `cd backend && ./gradlew test --tests "com.careerops.backend.match.*" --max-workers=1` → PASS (MATCH-001/MATCH-002 회귀 없음).
- 자동 테스트에서 실제 Anthropic API를 호출하는 코드/하드코딩된 키 없음을 grep으로 확인(`AnthropicOkHttpClient`/`messages().create` 호출이 테스트 코드에 없음, blank apiKey로 즉시 실패 경로만 테스트).

## Findings

문제로 분류할 사항 없음. 아래는 참고용 관찰(Acceptance Criteria 미달 아님):

1. `AgentAnalysisControllerTest.preservesAgentOrderAssignsPriorityAndSemanticScoresAndPassesGaps` 단독으로는 "LLM 배열 순서 = priority"와 "score 내림차순 정렬"을 구분하지 못한다(우연히 두 가설이 같은 결과를 낸다). 다행히 `AgentAnalysisServiceTest.categoryCapsKeepOriginalArrayOrderInsteadOfSortingBySemanticScore`가 두 가설이 갈리는 반례(오름차순 score를 오름차순 순서로 제공)로 확실히 구분해 검증하므로 전체적으로는 요구사항이 충족됨. 다만 컨트롤러 테스트 이름이 이 구분을 검증하는 것처럼 읽혀 다소 오해 소지가 있다(사소, 수정 불필요).
2. 중복 id 제거 로직(`dedup()`)은 4개 카테고리(경험/자격/학력/수상)에 공유되지만, 자동 테스트는 경험 카테고리에서만 중복 케이스를 명시적으로 검증한다. 구현이 공유 메서드라 위험은 낮지만, 완전한 커버리지를 원한다면 자격/학력/수상 중복 케이스 테스트 추가를 고려할 수 있다(선택 사항, blocking 아님).
3. 코드 스타일(한 줄에 다중 statement)은 기존 `SemanticJobMatchService`(MATCH-002, 이미 3라운드 PASS)와 동일한 압축 스타일을 그대로 따른 것으로, 이 프로젝트의 기존 컨벤션과 일치한다 — 별도 지적 사항 아님.

## 다음 액션

- **PASS.** 21개 Acceptance Criteria 중 자동화 가능한 17개 항목 전부 충족을 코드/테스트로 직접 확인했고, 나머지 4개(`[수동]` Case A~D)는 실제 dev DB + Anthropic API가 필요해 이번 자동 리뷰 범위 밖이다.
- ADR-0029의 7가지 핵심 설계 결정(후보 풀 재사용/score 단일 소유·순서 기반 tie-break/reason 미전달/별도 evidence enum/PKB empty=409/timeout 네임스페이스 분리/provider 실패 시 no partial response) 모두 코드에서 실제로 반영됨을 직접 검증.
- 남은 작업: `[수동]` Case A~D(실제 dev DB + Anthropic API) 수행 후 Task 상태를 `done`으로 전환. `.ai/metrics/metrics.jsonl`의 `review_round_count`/`first_review_pass`/`status`를 이번 리뷰 결과(PASS, round 1)로 갱신 필요(Claude가 기록).
