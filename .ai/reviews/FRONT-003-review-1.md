---
task_id: FRONT-003
review_round: 1
reviewer: claude (reviewer subagent)
reviewed_at: 2026-08-26T00:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `/jobs/7501`(실backend): 1단계 실시간 계산 유지, 2~4단계 "데모 분석" 라벨 —
  `frontend/src/app/(app)/jobs/[id]/AiInsightWorkflow.tsx:14,20` `Stage` 컴포넌트가
  `demo=false`(기본 적합도)/`demo` 기본값 true(2~4단계)로 라벨을 분기. `MatchPanel.tsx`는
  1글자도 수정되지 않음(`git diff --stat`이 orchestrator 확인대로 empty). 오케스트레이터가
  실제 backend + 브라우저로 재확인(overallScore 0.00, unmatchedJobCategories 일치).
- [x] fixture 모드 `/jobs/job-orbit-01` 4단계 전체 콘텐츠 — `AiInsightWorkflowServer.tsx`가
  `apiBaseUrl` 유무에 따라 `AI_INSIGHT_FIXTURE_DEMO_JOB`(`job-orbit-01`)을 주입하고
  `hasAiInsightDemo`가 이를 포함하는 것을 코드로 확인. 오케스트레이터 스크린샷으로 재확인.
- [x] 비-demo job은 안내 문구+링크만, 버튼/폼 미렌더링 — `AiInsightWorkflow.tsx:20`
  `{demo?<><Toggle .../>...</>:<DemoNotice demoJob={demoJob}/>}` 구조로 `demo=false`일 때
  `Toggle`/결과 컴포넌트 자체가 트리에 없음(폼 없음). `/jobs/68` 실측으로 재확인됨.
- [x] 자기소개서 초안 2문항 + characterCount/maxLength/draft + 복사 버튼 "복사됨" 전환 —
  `applicationDraftFixture`에 q1/q2 2문항(`ai-insight.ts:23-26`), `DraftCard`
  (`AiInsightWorkflow.tsx:18`)가 `characterCount`/`maxLength`/`draft` 모두 렌더링,
  `copy()`가 `navigator.clipboard.writeText` 후 `setCopied(true)`+2초 `setTimeout`으로
  라벨 복원. 실제 클릭은 자동화 환경 한계로 오케스트레이터가 직접 못 했으나 코드 검토로
  로직 정확함을 확인함(아래 Findings 참고, 사소한 개선 여지 있음).
- [x] `missingCompanyContext=true` 문항 caveat 노출 — q1이 `missingCompanyContext:true`이고
  `DraftCard`가 `{result.missingCompanyContext&&<p ...>공고에 회사 소개 정보가 없어 이
  부분은 일반적인 문장으로 채웠습니다.</p>}`를 렌더링(`AiInsightWorkflow.tsx:18`). 오케스트레이터
  실측으로 재확인.
- [x] evidence enum → 한국어 라벨, 원문 enum 미노출 — `format.ts:8-9`에
  `evidenceSourceLabel`(14개)/`agentEvidenceSourceLabel`(15개, `EXPERIENCE_BULLET` 포함)
  완전 매핑 확인(직접 카운트). `EvidenceTags`(`AiInsightWorkflow.tsx:12`)가 항상 이 맵을
  통해서만 렌더링하며, 컴포넌트 파일 자체에는 raw enum 리터럴이 전혀 없음(grep 확인).
- [x] 금지 6개 endpoint 호출 0건 — `grep -rn "semantic-match|agent-analysis|application-draft|
  jobs/recommendations|notifications/job-recommendations"`로 독립 재실행, 유일한 매치는
  기존 `lib/api/notifications.ts`의 `GET /api/notifications/job-recommendations`(알림 목록
  조회, 이 Task 대상 파일 아님, prepare/send POST와 무관)뿐.
- [x] fixture 함수(`getSemanticMatchFixture`/`getAgentAnalysisFixture`/
  `getApplicationDraftFixture`)에 `apiBaseUrl` 분기 없음 — `ai-insight.ts:28-30`, 인자 없이
  고정 상수만 반환. `grep apiBaseUrl frontend/src/lib/fixtures/ai-insight.ts` 0건.
- [x] "합격률"/"합격 가능성" 미노출 — 독립 grep 0건.
- [x] `/applications/[id]` "공고 인사이트 보기" 링크 → `/jobs/{jobPostingId}` — diff 확인
  (`applications/[id]/page.tsx`), 오케스트레이터 클릭 테스트로 재확인.
- [x] `npm run build`/`npm run lint` 통과 — 독립 재실행, 둘 다 에러 없이 통과.
- [x] `package.json` dependencies 무변경 — 독립 diff 확인, `test` 스크립트 한 줄만 확장.
- [x] `/`,`/dashboard`,`/jobs`,`/career`,`/notifications`,`/applications`(목록) 무변경 —
  독립 `git diff --stat` 확인, 전부 empty.
- [x] `backend/` 무변경 — 독립 `git diff --stat -- backend/` 확인, empty.
- [x] `node:test`로 `characterCount===draft.length` 검증 — `ai-insight.test.ts`에 3개
  테스트(그 중 1개가 정확히 이 요구사항), 독립 실행 결과 11/11 전체 통과(신규 3건 포함).

## 테스트 결과

- `npm run test` (node:test, 4개 스위트) — **test_count=11, test_pass_count=11, fail=0**
  (`ai-insight.test.ts` 3건 포함, 독립 재실행으로 확인, orchestrator 보고와 일치).
- `npm run build` (`next build --webpack`) — 독립 재실행, 에러 없이 성공(7개 route 정상 생성).
- `npm run lint` (`eslint .`) — 독립 재실행, 에러 없이 통과.
- Backend 실제 호출/브라우저 수동 확인은 오케스트레이터가 실제 로컬 backend +
  claude-in-chrome으로 이미 수행(위 각 기준에 인용). 클립보드 "복사" 버튼의 실제 클릭
  동작만 브라우저 자동화 환경 제약(`navigator.clipboard.writeText`가 OS 포커스 없이
  항상 reject)으로 직접 재현되지 않았고, 이번 리뷰에서도 코드 검토로만 정확성을 확인함.

## Findings

원칙 위반이나 Acceptance Criteria 미충족 항목은 없음. 아래 2건은 **비차단(non-blocking)
개선 제안**이며, 이번 라운드의 PASS 판정에는 영향을 주지 않는다:

1. **`DraftCard`의 클립보드 복사에 `try/catch`가 없음**
   (`AiInsightWorkflow.tsx:18`, `copy=async()=>{await navigator.clipboard.writeText(result.draft);
   setCopied(true);...}`). Clipboard 권한이 거부되거나 API가 없는 환경에서 `writeText`가
   reject되면 `setCopied(true)`가 실행되지 않으므로 버튼에 잘못된 상태("복사됨")가 표시되지는
   않지만(요구사항 충족), catch가 없어 unhandled promise rejection이 콘솔에 남고 Next.js
   dev 모드에서는 에러 오버레이로 노출될 수 있다. `try{...}catch{}`로 감싸 조용히
   실패하도록 명시하는 편이 더 안전하다. **블로킹 아님** — 다음 라운드나 후속 정리 작업에서
   반영해도 무방.
2. **`page.module.css`의 `.insights .insights{...}` 중첩 셀렉터로 `MatchPanel`의 중복
   헤더/`disabledTools` 그리드를 숨김** (`page.module.css` 4번째 규칙,
   `AiInsightWorkflow.tsx`가 `MatchPanel`을 `Stage` 안에 그대로 삽입하면서 `.insights`
   클래스가 중첩됨). `MatchPanel.tsx` 자체의 "Semantic 매칭/지원 전략 분석/자기소개서
   초안 — 비용 정책상 현재 비활성화되어 있습니다" 3개 항목 grid는 이제 이 Task가 만든
   2~4단계로 대체되어 내용상 stale하지만, 코드로 제거되지 않고 CSS로만 숨겨져 DOM에는
   그대로 남는다(`display:none`이라 접근성 트리·시각적으로는 문제없음을 확인했으나,
   view-source나 CSS 미적용 상황에서는 모순된 문구가 노출될 수 있음). Task 명세의
   제약("1단계의 실제 동작(`requestMatch` Server Action, `apiBaseUrl` 분기)은 한 글자도
   바꾸지 않는다")은 로직만 보호하고 있어, `MatchPanel`에 `embedded`류 prop을 추가해
   중복 헤더/`disabledTools`를 직접 렌더링하지 않도록 정리할 여지가 있었다. **블로킹
   아님** — 기능·시각적으로 정확히 동작함을 코드와 orchestrator 스크린샷으로 확인했으므로
   이번 라운드는 PASS로 처리하되, 후속 cleanup 후보로 기록.

자기소개서 관련 근거 기반 검증 원칙: `applicationDraftFixture`의 q2가 `agentAnalysisFixture
.recommendedExperiences`(우선순위 1,2 = exp-async/exp-rag)에 없는 `exp-club`을
`primaryExperienceId`로 사용해, ADR-0030 "AGENT-002는 AGENT-001 후보 풀에 갇히지
않는다" 원칙을 fixture로도 정확히 재현하고 있음(`ai-insight.ts:25`) — 임의 생성 경험이나
수치 없이 실제 dev DB PKB 구조(비동기 처리 분리, 상태 머신, 재시도, 분산 락 등)만
비식별화해 재사용한 것으로 확인.

## 다음 액션

- **PASS**. Acceptance Criteria 15개 전부 충족, 테스트 11/11 통과, backend/다른 라우트/
  package.json 무변경, 금지 endpoint·금지 표현 0건 모두 독립 재확인 완료. FRONT-003을
  완료 처리하고 `.ai/metrics/metrics.jsonl`에 최종 상태를 기록해도 된다.
- 위 Findings의 2건은 blocking이 아니므로 즉시 같은 Codex thread에 보낼 필요는 없다.
  다만 후속 정리를 원하면 다음 요청을 그대로 전달 가능:
  1. `AiInsightWorkflow.tsx`의 `DraftCard.copy()`에 `try/catch`를 추가해 clipboard 거부/
     미지원 환경에서 조용히 실패하도록(현재 상태 유지, 에러만 흡수) 만들어 달라.
  2. `MatchPanel`이 `AiInsightWorkflow`의 1단계 안에 내장될 때만 중복 "AI 인사이트" 헤더와
     stale한 `disabledTools`(Semantic 매칭/지원 전략 분석/자기소개서 초안 "비용 정책상
     비활성" 안내) 블록을 렌더링하지 않도록, `MatchPanel`에 `embedded?:boolean` 같은
     prop을 추가하고(`requestMatch`/`apiBaseUrl` 로직은 그대로 유지) `page.module.css`의
     `.insights .insights` CSS 오버라이드 규칙을 제거해 달라.
