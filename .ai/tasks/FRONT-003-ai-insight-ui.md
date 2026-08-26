---
task_id: FRONT-003
title: AI Insight UI — MATCH-002/AGENT-001/AGENT-002 데모 분석 표시 (MATCH-001 실시간 유지)
phase: plan
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a03c2e-736c-7fa0-b474-778ed7926e3b
---

## Context

CareerOps의 마지막 제품 개발 Phase(FRONT-003)다. Backend에는 이미 실제
Anthropic 호출로 검증된 MATCH-002(semantic 매칭)/AGENT-001(지원 전략
분석)/AGENT-002(자기소개서 초안)가 존재하지만(ADR-0028/0029/0030), 이
Phase는 **Anthropic 실제 호출 0건**이 절대 제약이다. 이 Task는 그 3개
기능의 실제 결과 형태를 "실제 backend response DTO와 최대한 일치하는
fixture"로 만들어 `/jobs/[id]`에 단계적 workflow(기본 적합도 → AI 심층
분석 → 지원 전략 → 자기소개서 초안)로 표현한다. FRONT-001이 이미 만든
MATCH-001(`GET /api/jobs/{id}/match`, deterministic, Anthropic 미사용)
실시간 흐름(`MatchPanel.tsx`/`jobs/[id]/actions.ts`)은 그대로 유지하고
확장한다.

**반드시 먼저 읽을 것**: `docs/DECISIONS.md` ADR-0040(이 Task의 설계
근거 전체 — demo job 선정 이유, fixture 정책, 레이아웃 재사용 근거).

## 절대 제한 (AGENTS.md / 이번 Phase 지시, FRONT-001/FRONT-002와 동일)

- `frontend/src/` 어디에도 다음 6개 backend endpoint를 실제로
  호출(`fetch`/`getJson`/`postJson` 등)하는 코드가 없어야 한다:
  `POST /api/jobs/{id}/semantic-match`, `POST /api/jobs/{id}/agent-analysis`,
  `POST /api/jobs/{id}/application-draft`, `POST /api/jobs/recommendations`,
  `POST /api/notifications/job-recommendations`(prepare), `POST
  /api/notifications/job-recommendations/{id}/send`.
- 이번 Task가 새로 만드는 MATCH-002/AGENT-001/AGENT-002 fixture 함수는
  **`apiBaseUrl`(`API_BASE_URL` 환경변수) 유무를 확인하는 조건문 자체를
  갖지 않는다** — 항상 fixture만 반환한다(ADR-0040 결정 1). `GET
  /api/jobs/{id}/match`(MATCH-001)만 기존처럼 `apiBaseUrl`이 있으면 실제
  호출, 없으면 fixture로 분기하는 기존 동작을 그대로 유지한다(변경
  없음).

## 실제 조사한 Backend DTO (추측 없음, 파일 경로 포함)

### MATCH-001 (기존, 변경 없음 — 재확인만)

- `GET /api/jobs/{jobId}/match`
  (`backend/src/main/java/com/careerops/backend/match/JobMatchController.java`)
- Response `JobMatchResponse`(`match/dto/JobMatchResponse.java`):
  `jobPostingId`(Long), `overallScore`(double, relevance only), `recommendedExperiences`/
  `recommendedCertifications`/`recommendedEducations`/`recommendedAwards`
  (`List<MatchEvidence>`), `unmatchedJobCategories`(`List<String>`),
  `careerLevel`(String), `educationRequirement`(String), `computedAt`(Instant).
- `MatchEvidence`(`match/dto/MatchEvidence.java`): `type`(String),
  `id`(Long), `title`(String), `score`(double), `matchedFields`(`List<String>`).
- frontend 기존 타입 `lib/types.ts`의 `JobMatch`/`MatchEvidence`가 이미
  이 구조를 반영하고 있다(변경 불필요).

### MATCH-002 (신규 fixture 대상)

- `POST /api/jobs/{jobId}/semantic-match`
  (`backend/src/main/java/com/careerops/backend/match/SemanticJobMatchController.java`)
- Response `SemanticJobMatchResponse`(`match/dto/SemanticJobMatchResponse.java`):
  ```java
  record SemanticJobMatchResponse(
      Long jobPostingId,
      double deterministicScore,
      double semanticScore,
      List<SemanticMatchEvidence> experienceMatches,
      List<SemanticMatchEvidence> certificationMatches,
      List<SemanticMatchEvidence> educationMatches,
      List<SemanticMatchEvidence> awardMatches,
      List<String> gaps,
      Instant computedAt)
  ```
- `SemanticMatchEvidence`(`match/dto/SemanticMatchEvidence.java`):
  `type`(String), `id`(Long), `title`(String), `score`(double),
  `evidence`(`List<EvidenceSource>`), `reason`(String, 자연어 최대 200자).
- `EvidenceSource`(`match/dto/EvidenceSource.java`) enum 14값: `JOB_TITLE`,
  `JOB_CATEGORY`, `CAREER_LEVEL`, `EDUCATION_REQUIREMENT`, `EXPERIENCE_TAG`,
  `EXPERIENCE_TITLE`, `EXPERIENCE_SUMMARY`, `EXPERIENCE_DETAIL`,
  `CERTIFICATION_NAME`, `CERTIFICATION_DESCRIPTION`, `EDUCATION_MAJOR`,
  `EDUCATION_DESCRIPTION`, `AWARD_TITLE`, `AWARD_DESCRIPTION`.
- `semanticScore`가 이 API의 대표 관련도이며, **"합격 가능성"이 아니다**
  (DTO 주석 원문). `deterministicScore`는 MATCH-001과 같은 계산이지만
  독립 재계산이라 미세하게 다를 수 있다(ADR-0028).

### AGENT-001 (신규 fixture 대상)

- `POST /api/jobs/{jobId}/agent-analysis`
  (`backend/src/main/java/com/careerops/backend/agent/AgentAnalysisController.java`)
- Response `AgentAnalysisResponse`(`agent/dto/AgentAnalysisResponse.java`):
  ```java
  record AgentAnalysisResponse(
      Long jobPostingId, String roleSummary, List<String> keyThemes,
      List<String> knownRequirements, String positioningHeadline,
      String positioningSummary,
      List<ExperienceRecommendation> recommendedExperiences,
      List<PkbRecommendation> recommendedCertifications,
      List<PkbRecommendation> recommendedEducations,
      List<PkbRecommendation> recommendedAwards,
      String primaryMessage, List<String> secondaryMessages,
      List<String> avoidOrBeCareful, List<String> gaps, Instant computedAt)
  ```
- `ExperienceRecommendation`(`agent/dto/ExperienceRecommendation.java`):
  `id`(Long), `title`(String, DB 조회 실제 제목), `priority`(int, 1..N),
  `semanticMatchScore`(double, MATCH-002 원본 score — Agent LLM이 생성한
  값이 아님), `reason`(String, 최대 300자), `emphasisPoints`(`List<String>`,
  최대 5개·각 150자), `evidence`(`List<AgentEvidenceSource>`).
- `PkbRecommendation`(`agent/dto/PkbRecommendation.java`): `id`(Long),
  `title`(String), `semanticMatchScore`(double), `reason`(String, 최대
  300자), `evidence`(`List<AgentEvidenceSource>`).
- `AgentEvidenceSource`(`agent/dto/AgentEvidenceSource.java`) enum 15값 —
  `EvidenceSource`와 동일 14개 + `EXPERIENCE_BULLET`(MATCH-002 enum과
  별도 정의, ADR-0029 결정 5 — 두 enum은 의도적으로 분리된 서로 다른
  타입).
- 항상 최대: 경험 5개, 자격/학력/수상 각 3개(ADR-0026 top-N을 이어받음).
- PKB(경험/자격/학력/수상) 4종이 전부 0건이면 409(fixture에는 해당하지
  않음 — demo job은 항상 채워진 PKB를 전제).

### AGENT-002 (신규 fixture 대상)

- `POST /api/jobs/{jobId}/application-draft`
  (`backend/src/main/java/com/careerops/backend/applicationdraft/ApplicationDraftController.java`)
- Request `ApplicationDraftRequest`(`applicationdraft/dto/ApplicationDraftRequest.java`):
  `questions: List<QuestionRequest>`(1~10개). `QuestionRequest`
  (`applicationdraft/dto/QuestionRequest.java`): `questionId`(String,
  not blank), `question`(String, not blank), `maxLength`(Integer, nullable,
  양수). **fixture는 이 request를 실제로 보내지 않는다** — request/response
  쌍을 고정 fixture 세트로만 하드코딩한다.
- Response `ApplicationDraftResponse`(`applicationdraft/dto/ApplicationDraftResponse.java`):
  ```java
  record ApplicationDraftResponse(Long jobPostingId,
      List<QuestionDraftResult> questions, OverallStrategy overallStrategy,
      Instant computedAt)
  ```
- `QuestionDraftResult`(`applicationdraft/dto/QuestionDraftResult.java`):
  `questionId`(String), `primaryIntent`(`QuestionIntent`),
  `secondaryIntents`(`List<QuestionIntent>`), `primaryExperienceId`(Long),
  `supportingExperienceIds`(`List<Long>`), `certificationIds`/
  `educationIds`/`awardIds`(`List<Long>`), `coreMessage`(String),
  `outline`(`List<String>`), `draft`(String, 실제 초안 본문),
  `characterCount`(int, `draft.length()` 기준 — CareerOps 자체 기준,
  공백 포함/제외나 바이트 기준이 아님), `maxLength`(Integer, nullable),
  `limitExceeded`(boolean), `missingCompanyContext`(boolean),
  `warnings`(`List<String>`).
- `QuestionIntent`(`applicationdraft/dto/QuestionIntent.java`) enum 9값:
  `SUPPORT_MOTIVATION`, `JOB_COMPETENCY`, `PROBLEM_SOLVING`,
  `COLLABORATION`, `CONFLICT`, `GROWTH`, `VALUES`, `AI_TECH`, `OTHER`.
- `OverallStrategy`(`applicationdraft/dto/OverallStrategy.java`):
  `primaryPositioning`(String), `experienceDistribution`
  (`List<ExperienceDistributionEntry>`), `warnings`(`List<String>`).
- `ExperienceDistributionEntry`(`applicationdraft/dto/ExperienceDistributionEntry.java`):
  `experienceId`(Long), `title`(String), `usedInQuestionIds`(`List<String>`).
- `missingCompanyContext`는 `JobPosting`에 회사 사업/문화/인재상을
  서술하는 필드가 전혀 없다는 사실(ADR-0030)을 구조적으로 드러내는
  필드다 — fixture도 이 한계를 숨기지 않고 실제로 `true`인 문항을
  최소 1개 포함해야 한다(아래 §4).

## Scope

### 1. 대표 demo job 확정 (ADR-0040 결정 2)

- **실제 backend 모드**: `JobPosting.id = 7501`(한전KDN "AI기반
  무정전활선작업 무인화 로봇플랫폼 개발 및 실증" 연구과제, ALIO
  external_id 303887, OPEN, careerLevel=신입+경력, jobCategory=정보통신).
  오늘 실측(`GET /api/jobs/7501/match`) 결과 `overallScore=0.0`,
  `unmatchedJobCategories=["정보통신"]` — 이 값은 실시간 계산이므로
  재수집/PKB 변경에 따라 미래에 달라질 수 있다(고정 fixture 아님).
- **Fixture-only(backend 없음, Vercel 배포) 모드**: 기존
  `frontend/src/lib/fixtures/data.ts`의 `job-orbit-01`
  ("데이터 플랫폼 신입 엔지니어")에 동일한 MATCH-002/AGENT-001/AGENT-002
  fixture 콘텐츠를 매핑한다.
- `frontend/src/lib/fixtures/ai-insight.ts`(신규)에
  `export const AI_INSIGHT_DEMO_JOB_IDS = new Set(["7501", "job-orbit-01"])`
  또는 동등한 판별 함수를 두고, `/jobs/[id]`는 이 집합에 포함된 id일
  때만 2~4단계에 실제 콘텐츠를 보여준다.
- fixture 본문(경험/자격/전략/초안의 reason/텍스트)은 특정 회사명·공고
  제목을 직접 인용하지 않고 "이 공고"/"지원 직무"처럼 일반화된 표현만
  써서, 서로 다른 두 공고 타이틀(한전KDN vs 데이터 플랫폼) 아래에서도
  자연스럽게 읽히게 작성한다.

### 2. Fixture 데이터 (`frontend/src/lib/fixtures/ai-insight.ts`, 신규)

위 §"실제 조사한 Backend DTO"의 3개 응답 타입과 완전히 동일한 필드
구조로 다음을 하드코딩한다(이 파일이 유일한 소스, 런타임 mutable
상태 아님):

- `semanticMatchFixture: SemanticMatch`(아래 §3에서 정의할 frontend
  타입) — `experienceMatches`/`certificationMatches` 등에 최소
  각 1~3개 항목, `reason`은 매번 구체적 근거로 작성한다(예:
  "외부 API 호출과 AI 생성 작업을 동기 요청 경로에서 분리하고 상태
  머신 기반 재처리 구조를 설계한 경험이 있어, 로봇 제어처럼 지연에
  민감한 비동기 처리가 필요한 이 직무와 연관이 있습니다"). "백엔드
  경험이 있어 적합합니다" 류의 generic 문장 반복 금지(사용자 지시).
  `gaps`에는 최소 1개(예: "이 공고 정보에는 로봇 제어·임베디드
  경험 요구 여부가 명시되어 있지 않아, 실제 지원 시 별도 확인이
  필요합니다" 처럼 정직한 한계 서술).
- `agentAnalysisFixture: AgentAnalysis` — `recommendedExperiences`
  최소 2개(우선순위 1, 2), `recommendedCertifications`/`Educations`/
  `Awards` 각 최소 1개, `avoidOrBeCareful` 최소 1개(예: "로봇 하드웨어
  제어 경험은 없으므로 이를 이미 다뤄본 것처럼 서술하지 않습니다").
- `applicationDraftFixture: ApplicationDraft` — 최소 2개 문항. 예:
  1. `questionId="q1"`, `question="지원 동기와 입사 후 목표를
     서술하시오."`, `maxLength=800`, `primaryIntent=SUPPORT_MOTIVATION`,
     `missingCompanyContext=true`(위 DTO 조사에서 확인한 실제 한계를
     반영 — 회사 소개 문장은 일반적 수준으로만 서술).
  2. `questionId="q2"`, `question="협업 과정에서 어려움을 겪고
     해결했던 경험을 서술하시오."`, `maxLength=600`,
     `primaryIntent=COLLABORATION`, `primaryExperienceId`는 §1과
     다른 경험(직무 relevance는 낮지만 협업 서사에 맞는 경험, 예: 동아리
     활동)을 선택해 ADR-0030이 확립한 "AGENT-002는 AGENT-001 후보 풀에
     갇히지 않는다" 원칙을 fixture에서도 시각적으로 보여준다.
  각 문항 `characterCount`는 `draft` 문자열의 실제 길이와 반드시 일치해야
  한다(하드코딩 실수 방지 — 최종 값은 `draft.length`로 계산해서 넣거나
  일치 여부를 코드 리뷰로 확인).
- 개인정보 없음: 학교명/실명 등 식별 가능한 고유명사를 쓰지 않는다
  (ADR-0040 결정 3, `lib/fixtures/data.ts`의 기존 "가상" 네이밍 컨벤션
  재사용).

### 3. Frontend 타입 (`frontend/src/lib/types.ts`, 확장)

Backend record와 동일한 필드명으로 추가한다(camelCase 그대로,
번역/재해석 없음):
- `EvidenceSourceType`(14값 union, MATCH-002), `AgentEvidenceSourceType`
  (15값 union, AGENT-001) — 두 개 별도 union(문자열 값이 13개 겹치지만
  타입은 분리 유지, backend와 동일하게 독립).
- `SemanticMatchEvidence`, `SemanticMatch`(`SemanticJobMatchResponse`
  대응 — 이름 충돌 방지를 위해 `Semantic` 접두사 사용, 재량).
- `ExperienceRecommendation`, `PkbRecommendation`, `AgentAnalysis`.
- `QuestionIntent`(9값 union), `QuestionDraftResult`, `OverallStrategy`,
  `ExperienceDistributionEntry`, `ApplicationDraft`.

### 4. AI Insight 컴포넌트 (`frontend/src/app/(app)/jobs/[id]/` 확장)

- 기존 `MatchPanel.tsx`(1단계, MATCH-001 실시간)는 그대로 두거나, 이번
  Task가 4단계 컨테이너로 재구성하며 그 안에 흡수해도 된다(재량) — 단
  1단계의 실제 동작(`requestMatch` Server Action, `apiBaseUrl` 분기)은
  **한 글자도 바꾸지 않는다**.
- 신규(또는 확장된) 컴포넌트가 `App.module.css`의 `.timeline`(왼쪽 세로
  rail + 원형 점, `StageEditor`가 이미 쓰는 패턴)을 재사용해 4단계를
  세로로 배치한다(ADR-0040 결정 4). 각 단계:
  1. **기본 적합도** — "실시간 계산" 라벨. 기존 동작 유지.
  2. **AI 심층 분석**(MATCH-002) — "데모 분석" 라벨.
  3. **지원 전략 분석**(AGENT-001) — "데모 분석" 라벨.
  4. **자기소개서 초안**(AGENT-002) — "데모 분석" 라벨.
  라벨에는 `title` 속성(네이티브 tooltip)으로 "데모 환경에서는 저장된
  예시 분석 결과를 보여줍니다" 안내(신규 tooltip 라이브러리 금지).
- 2~4단계는 `AI_INSIGHT_DEMO_JOB_IDS`에 포함된 job에서만 "결과 보기"
  버튼과 콘텐츠를 렌더링한다. 포함되지 않은 job에서는 버튼 대신 조용한
  안내 문구("이 공고에는 준비된 데모 분석이 없습니다. 데모 분석은
  [공고명]에서 확인할 수 있습니다")와 그 demo job으로의 링크만
  보여준다(폼/버튼 자체를 렌더링하지 않음).
- 4개 단계 사이에 순서 잠금을 두지 않는다(ADR-0040 결정 5) — 각 단계는
  독립적으로 "결과 보기"를 누를 수 있고, 이미 눌렀으면 펼쳐진 상태를
  유지한다(단순 `useState<boolean>` 토글, 실제 네트워크 지연을 흉내내는
  로딩 스피너/skeleton 없음 — 클릭 즉시 표시).
- **점수 표기**: MATCH-002의 `semanticScore`는 "AI 적합도" 또는 "의미
  기반 연관도"로 표기한다(사용자 지시 — "합격 가능성/합격률" 표현
  금지). `deterministicScore`도 참고용으로 작게 노출 가능(재량).
- **evidence 표기**: `EvidenceSource`/`AgentEvidenceSource` enum 값을
  사람이 읽는 한국어 라벨로 변환하는 매핑을 `lib/format.ts`에 추가한다
  (예: `JOB_TITLE`→"공고 제목", `EXPERIENCE_TAG`→"경험 태그",
  `EXPERIENCE_BULLET`→"경험 상세 기록" 등 14~15개 전부). enum 원문
  문자열이 UI에 그대로 노출되지 않아야 한다.
- **지원 전략 표기**: `roleSummary`/`positioningHeadline`/
  `positioningSummary`/`primaryMessage`/`secondaryMessages`/
  `avoidOrBeCareful`/`gaps`를 문단/리스트로 표시. `recommendedExperiences`는
  `priority` 순서를 그대로 순번으로 보여준다(재정렬 없음).
- **자기소개서 초안 표기**: 문항별 카드에 `question` 원문, `maxLength`
  대비 `characterCount`(예: "512 / 600자", `limitExceeded=true`면
  `--warning` 톤), `primaryIntent`/`secondaryIntents`를 한국어 라벨로
  표시(`QuestionIntent` 매핑 신규 추가), `draft` 본문, `outline` 목록,
  `missingCompanyContext=true`인 문항에는 "공고에 회사 소개 정보가
  없어 이 부분은 일반적인 문장으로 채웠습니다" 같은 정직한 caveat 노출.
  각 문항에 **복사 버튼**을 두어 `navigator.clipboard.writeText(draft)`
  호출 후 버튼 라벨을 일시적으로(예: 2초) "복사됨"으로 바꾼다(`useState`
  + `setTimeout`, 신규 dependency 없음).
  `overallStrategy.experienceDistribution`을 "이 경험이 어떤 문항에
  쓰였는가" 형태의 짧은 표/목록으로 보여준다.
- Copywriting: "AI가 당신의 성공을 분석했습니다"류 과장 금지, "공고와
  내 경험의 연결점을 정리했습니다"처럼 담백한 한국어(ADR-0038 §8
  "무성의한 톤 회피"와 같은 원칙, 과장도 금지).

### 5. Applications 상세 페이지 링크 (`frontend/src/app/(app)/applications/[id]/page.tsx`)

- AI 기능을 복제하지 않고, `PageHeading`의 `action` slot 또는 정보
  패널에 "공고 인사이트 보기" 링크(`styles.buttonSecondary`)를 추가해
  `/jobs/${a.jobPostingId}`로 이동하게 한다.

## Out of Scope

- MATCH-002/AGENT-001/AGENT-002 실제 API 호출(어떤 환경에서도 금지).
- eligibility/합격 가능성 판단 UI(ADR-0028/0029가 이미 범위 밖으로
  결정, 이번에도 유지).
- 문항을 사용자가 직접 입력해 초안을 새로 생성하는 폼(고정 fixture
  질문 2개만 표시 — 실제 요청을 만들지 않으므로 "임의 질문 입력" UI는
  오해를 만든다).
- `frontend/src/app/(app)/jobs/[id]/page.tsx`의 attachment key 버그,
  form validation 필드 reset — **FRONT-003.1로 분리**(독립 병렬 가능).
- 새 npm dependency, Tailwind, toast/modal 라이브러리.
- backend 코드 변경(읽기만 했음, 수정 없음).

## Acceptance Criteria

- [ ] 로컬 backend(`API_BASE_URL=http://localhost:8080`) 연결 상태에서
      `/jobs/7501`을 열면: "기본 적합도" 단계는 실제
      `GET /api/jobs/7501/match` 결과(실측 기준 `overallScore` 0.00
      근접)를 그대로 보여주고(변경 없음, 수동 확인), "AI 심층 분석"/
      "지원 전략 분석"/"자기소개서 초안" 3단계는 고정된 데모 콘텐츠를
      보여주며 각각 "데모 분석" 라벨이 붙어 있다.
- [ ] `API_BASE_URL`을 설정하지 않은 fixture 모드에서 `/jobs/job-orbit-01`을
      열면 동일하게 4단계 전부가 콘텐츠를 보여준다(1단계는 기존 fixture
      MATCH-001 그대로).
- [ ] 위 두 demo job이 아닌 임의의 다른 `/jobs/{id}`를 열면 2~4단계에
      "이 공고에는 준비된 데모 분석이 없습니다"류 안내와 demo job으로의
      링크만 보이고, "결과 보기" 버튼/폼이 렌더링되지 않는다(수동 확인
      최소 1개 공고).
- [ ] 자기소개서 초안 섹션에 문항 2개 이상이 각각 문항 텍스트/
      `characterCount`·`maxLength`/`draft` 본문과 함께 표시되고, 각
      문항의 "복사" 버튼 클릭 시 클립보드에 `draft` 텍스트가 복사되며
      버튼 라벨이 일시적으로 "복사됨"으로 바뀐다(수동 확인, Clipboard
      API 권한이 필요하면 그 사실을 결과 보고에 남긴다).
- [ ] `missingCompanyContext=true`인 문항에서 그 사실을 알리는 caveat
      텍스트가 실제로 보인다(최소 1개 문항, 수동 확인).
- [ ] AGENT-001/MATCH-002 evidence enum 값이 한국어 라벨로 표시되고,
      `EXPERIENCE_TAG`/`JOB_TITLE` 같은 원문 enum 문자열이 화면 어디에도
      그대로 노출되지 않는다(코드 리뷰 + 수동 확인).
- [ ] `frontend/src/` 전체에서 `/semantic-match`, `/agent-analysis`,
      `/application-draft`, `POST /api/jobs/recommendations`,
      `POST /api/notifications/job-recommendations`(prepare 경로),
      `/send`(POST) 6개 endpoint를 실제로 호출하는 코드가 0건이다(grep
      검증, FRONT-001/FRONT-002 grep 기준과 동일 — 문자열이 코드/주석
      어디에도 fetch 호출부와 결합된 형태로 존재하지 않음).
- [ ] MATCH-002/AGENT-001/AGENT-002를 가져오는 frontend 함수(예:
      `getSemanticMatchFixture`류)의 코드에 `apiBaseUrl` 조건 분기가
      없다(코드 리뷰 확인 — 항상 fixture 반환).
- [ ] "합격률"/"합격 가능성"/이와 동등한 예측 표현이 AI Insight
      영역(및 이 Task가 건드리는 파일 전체) 어디에도 없다(grep 검증).
- [ ] `/applications/[id]`에 "공고 인사이트 보기" 링크가 보이고 클릭 시
      해당 `/jobs/{jobPostingId}`로 이동한다(demo job이 아니어도 링크
      자체는 항상 보임).
- [ ] `npm run build`(`next build --webpack`)/`npm run lint`가
      `frontend/`에서 에러 없이 통과한다.
- [ ] `package.json`의 `dependencies`에 신규 항목이 추가되지 않는다
      (diff 확인).
- [ ] `/`,`/dashboard`,`/jobs`(목록),`/career`,`/notifications`,
      `/applications`(목록)는 이번 Task로 변경되지 않는다(diff 확인 —
      이 Task는 `/jobs/[id]`와 그 하위 컴포넌트, `/applications/[id]`
      (링크 추가)만 건드린다).
- [ ] backend(`backend/` 하위) 코드가 한 글자도 수정되지 않는다(diff
      확인).
- [ ] 테스트: fixture 데이터의 `characterCount`가 실제 `draft.length`와
      일치하는지 검증하는 순수 함수 테스트를 `node:test`로 최소 1개
      추가한다(신규 dependency 없음, 기존 `result.test.ts`/`format.test.ts`
      패턴 재사용).

## Technical Notes

- **반드시 참고**: `docs/DECISIONS.md` ADR-0040(이 Task의 설계 근거),
  ADR-0026/0028/0029/0030(각 API의 원 설계 결정), ADR-0037/0038(스타일링
  방식/시각 언어).
- **`frontend-design` skill 상태**: 이 조사 시점에 이 머신 전역(`~/.claude/skills`
  포함)에서 검색한 결과 설치되어 있지 않다(0건). 새로 설치/적용하지
  말고 기존 `App.module.css`/`UI.module.css`의 토큰과 `.timeline` 패턴을
  그대로 재사용한다.
- **실측 데이터**: 이 Task 명세 작성 시점에 실제로 로컬 backend가
  기동 중이어서 `GET /api/jobs/7501/match`를 직접 호출해 확인했다
  (Anthropic 미사용 endpoint이므로 비용 없음) — `overallScore: 0.0`,
  `unmatchedJobCategories: ["정보통신"]`, `careerLevel: "신입+경력"`,
  `educationRequirement: "학력무관"`. 실제 값이 이번 명세 작성 이후
  달라져 있을 수 있으므로(재수집/스케줄러), Codex 구현 시점에 다시
  실측해 결과 보고에 남긴다.
- **PKB 실측(fixture 소재 근거, 오늘 시점 dev DB, 실명/학교명은 fixture에
  옮기지 말 것)**: `CareerExperience` 6건(코테이토 백엔드 파트 부회장/
  IMSI Lab 인턴/엔코아 풀스택 백엔드 부트캠프 수료/LG Aimers AI&Data
  Science 과정 수료/계층적 다중 에이전트 RAG 연구/FinSight — 외부 AI
  호출과 생성 작업을 사용자 API 요청 경로에서 분리, 6단계 상태 머신,
  최대 5회 재시도+지수 백오프, Redis 분산락, Prometheus/Grafana/Loki/
  Discord 연동), `ExperienceTag`(Java/Spring Boot/REST API/AI/머신러닝/
  RAG/Redis/Prometheus/Grafana/Loki/Discord), `Certification` 10건
  (정보처리기사/SQLD/ADsP/빅데이터분석기사/TOPCIT 등), `Education` 2건
  (고졸/학사, 컴퓨터학부, 졸업예정), `Award` 1건(성적우수장학금).
  fixture는 이 구조(비동기 처리 분리/상태 머신/재시도/분산락/모니터링
  연동 같은 구체적 패턴)만 재사용하고 학교명 등 식별 정보는 옮기지
  않는다(ADR-0040 결정 3).
- **Metrics**: 이 Task는 순수 표시(backend 무변경)이므로 신규 Product
  Metric(Micrometer)이 없다. `docs/METRICS.md`의 개발 프로세스 지표
  (`.ai/metrics/metrics.jsonl`)만 plan/implement/review/verify로
  기록한다.
- FRONT-003.1(폼 reset/attachment key 수정)과 이 Task는 서로 다른
  파일을 주로 건드리므로(이 Task는 `jobs/[id]`의 AI 영역·타입·fixture,
  FRONT-003.1은 `CareerEditor`/`StageEditor`/`ApplicationEditor`/
  `jobs/[id]/page.tsx`의 attachment 목록) 병렬 진행 가능하다 — 단
  둘 다 `jobs/[id]/page.tsx`를 건드릴 수 있으므로(이 Task는 신규 4단계
  컨테이너 삽입, FRONT-003.1은 attachment `key` 한 줄) merge 순서를
  Codex 결과 보고에 남긴다.

## Test Plan

- `frontend/`에서 `npm run build` → `npm run lint`.
- fixture 모드(`API_BASE_URL` 미설정) `npm run dev`로 `/jobs/job-orbit-01`
  4단계 전체와 `/jobs/{다른 fixture job}`의 "데모 분석 없음" 안내를
  browser로 확인, 스크린샷 확보.
- 로컬 backend를 실제로 띄우고(`docker compose up -d` +
  `./gradlew bootRun`) `API_BASE_URL=http://localhost:8080`으로
  `/jobs/7501`을 열어 1단계 실제 계산 + 2~4단계 데모 콘텐츠를 확인한다.
  `/jobs/{demo가 아닌 실제 공고 id}`에서 안내 문구도 함께 확인.
- grep 기반 검증: 금지 6개 endpoint 문자열 결합 호출 0건, "합격률"류
  표현 0건, `package.json` dependencies 변화 없음.
- `node:test`로 fixture `characterCount` 일치 테스트 실행.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | 4단계 AI Insight workflow, fixture 3종, 타입/라벨 매핑, Applications 링크 구현 | 9개 파일 신규/수정. Codex 샌드박스 제약으로 실 backend/브라우저 검증 미수행. Claude 실측 중 버그 2건 발견: (1) backend id가 number인데 demo job Set이 문자열이라 7501조차 데모 없음으로 표시되던 타입 불일치, (2) 비-demo 공고 안내 링크가 환경 무관 job-orbit-01 고정이라 실 backend 모드에서 404. 같은 라운드에 두 버그 모두 codex-reply로 수정 요청. |
| 1(계속) | 버그 2건 수정 요청 | `hasAiInsightDemo`에 `String()` 정규화, `AiInsightWorkflowServer.tsx` 신규 도입해 `apiBaseUrl` 유무로 demo job 분기. Claude가 실제 backend+claude-in-chrome으로 `/jobs/7501` 4단계 전체(매칭실행 0.00/1.0, AI심층분석 0.82/1.0+evidence+gaps, 지원전략, 자기소개서 초안 2문항+caveat+경험배분표), `/jobs/68`(비demo) 안내링크→7501 정상 이동, fixture 모드 `/jobs/job-harbor-02` 회귀없음, `/applications/app-01` 인사이트 링크 전부 재확인. reviewer round1 PASS(11/11 test, 15개 AC 전부 충족). 비차단 개선 제안 2건(copy() try/catch 부재, MatchPanel stale 안내 CSS로만 숨김)은 known limitation으로 기록. |
