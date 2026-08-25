---
task_id: FRONT-001
title: CareerOps 서비스형 UI — Dashboard/Jobs/Applications/Career/Notifications
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a039a5-04db-7080-8344-a833060ef364
---

## Context

FRONT-000으로 Next.js(App Router)+TypeScript 뼈대와 최소 랜딩 페이지(`/`)가
Vercel에 배포되어 있다. 지금까지 backend(Spring Boot)는 채용공고 수집/조회
(JOB-*), 지원 관리(APPLICATION-*), PKB(PKB-*), 매칭/지원전략/자기소개서
(MATCH-*/AGENT-*), 다건 추천(RECOMMEND-*), 알림 준비/Kakao 발송(NOTIFY-001/
KAKAO-001), 자동화(AUTOMATION-001)까지 구현을 완료했지만 이를 실제로
확인할 수 있는 화면이 없다.

이번 Task의 목표는 `docs/PROJECT.md`의 제품 목표(1~4번: 채용공고 수집,
적합도 판단, 알림, PKB 관리)를 사용자가 실제로 눈으로 보고 탐색할 수 있는
최소 서비스형 UI를 만드는 것이다. **AI를 실제로 실행하는 것이 아니라
정보 구조와 UX를 먼저 완성하는 단계**이며, Anthropic/Kakao 실제 API 호출,
backend에 대한 쓰기(mutation)는 이번 Task에 포함하지 않는다(FRONT-002
후보).

설계 근거는 `docs/DECISIONS.md`의 **ADR-0036**(데이터 페칭 전략 — Server
Component 전용 fetch + fixture fallback, CORS/backend 변경 없음),
**ADR-0037**(스타일링 방식 — Plain CSS Modules + CSS 변수 design token,
Tailwind/UI 프레임워크 미도입), **ADR-0038**(Visual Design System — 실제
팔레트 hex/타이포그래피/밀도/형태/signature element/"generic AI
dashboard" 회피 원칙)을 **반드시 먼저 읽고 정확히 그대로 따른다.**
ADR-0038은 사용자가 명시적으로 요구한 "AI가 만든 흔한 대시보드처럼
보이지 않는 실제 서비스 수준 UI"를 위한 구체적 디자인 브리프이며, 이번
Task의 시각적 완성도는 기능 구현만큼 중요한 Acceptance Criterion이다.

## Scope

### 1. AppShell (라우트 그룹으로 분리, `/`는 변경 없음)

- `frontend/src/app/(app)/layout.tsx` 신설 — `/dashboard`, `/jobs`,
  `/applications`, `/career`, `/notifications`에만 적용되는 레이아웃.
  기존 `/`(랜딩)는 route group 밖에 그대로 둬서 이번 Task로 전혀 변경되지
  않는다.
- Desktop(대략 1024px 이상): 좌측 Sidebar(네비게이션: Dashboard/채용공고/
  지원현황/커리어/알림) + 상단 Header(현재 페이지 타이틀 정도, 로그인/
  프로필 없음 — 단일 사용자 MVP) + 메인 콘텐츠 영역.
- Mobile(375px 기준 검증, 대략 768px 미만): Sidebar는 기본 숨김, 상단에
  햄버거 버튼으로 여닫는 오버레이/드로어 네비게이션으로 대체. 375px
  뷰포트에서 텍스트 잘림/가로 스크롤 발생 없이 모든 네비게이션 항목에
  접근 가능해야 한다.
- 네비게이션 토글은 `"use client"` 컴포넌트 1개(예: `NavToggle`)로 국한하고
  나머지 Shell 구조는 Server Component로 유지한다.

### 2. `/dashboard` (서비스 진입점)

Server Component에서 아래 기존 GET들을 병렬로 호출해 조합한다(신규 backend
API 없음, ADR-0036 결정 없이도 이미 원칙):

- Summary cards 4개:
  - OPEN 채용공고 수: `GET /api/jobs?status=OPEN&size=1` → `totalElements`
  - 관심·지원 중: `ApplicationStatus` 값별로 `GET /api/applications?status=X&size=1`을
    병렬 호출해 `totalElements`를 합산(예: INTERESTED+PLANNED+SUBMITTED를
    "진행 중"으로 합산, OFFERED/REJECTED/WITHDRAWN은 "종료"로 구분 — 정확한
    묶음은 Technical Notes 참고)
  - 다가오는 마감: `GET /api/jobs?status=OPEN&size=5`(기본 정렬
    `applicationEndAt ASC NULLS LAST`을 그대로 사용, 별도 클라이언트
    정렬 불필요)
  - PENDING 알림 수: `GET /api/notifications/job-recommendations?status=PENDING&size=1`
    → `totalElements`
- 추천 공고 카드 3~5개: **RECOMMEND-001(`POST /api/jobs/recommendations`)을
  호출하지 않는다.** 대신 `GET /api/notifications/job-recommendations?status=PENDING&size=20`으로
  이미 계산·저장된 알림을 가져와 `recommendationScore` 내림차순으로
  frontend에서 재정렬한 뒤 상위 3~5개만 카드로 표시한다(이미 계산된 결과의
  재정렬일 뿐, 새 LLM 호출이 전혀 없다).
- 지원 현황 요약: `GET /api/applications?size=5`(고정 정렬 `updatedAt DESC`)로
  최근 업데이트된 지원 5건을 리스트로 표시.
- 최근 알림: `GET /api/notifications/job-recommendations?size=5`(고정 정렬
  `createdAt DESC`)로 최근 알림 5건 표시(상태 배지 포함).
- 각 섹션에 해당 목록 페이지로 가는 링크("전체 보기" 등)를 둔다.

### 3. `/jobs`, `/jobs/[id]`

- `/jobs`: `GET /api/jobs`가 실제 지원하는 4개 optional 필터만 노출 —
  `status`(정확 일치), `careerLevel`, `companyName`(부분 일치),
  `jobCategory`(부분 일치). 필터 폼은 네이티브 `<form method="get">`(또는
  `Link` 조합)로 구현해 URL(searchParams) 갱신 → Server Component 재조회
  방식을 따른다(ADR-0036 결정 2). 키워드 통합검색, 지역/고용형태/학력
  필터, 클라이언트 정렬 등 **backend가 지원하지 않는 필터는 만들지
  않는다**(`docs/ROADMAP.md` Phase 3 이후 후보 참고 — JOB-002가 의도적으로
  제외한 항목).
- 목록 테이블/카드에 회사명/공고명/직무(jobCategory)/경력구분/필요학력/
  마감일/상태(status)를 표시. `applicationEndAt`이 오늘부터 7일 이내인
  행은 마감임박 시각 표시(배지 등, `--warning` 토큰)로 구분한다. 행 클릭 시
  `/jobs/[id]`로 이동.
- pagination은 `page`/`size`(API 최대 100, UI 기본 20) 기반 Prev/Next
  링크로 구현.
- `/jobs/[id]`: `JobPostingDetailResponse`의 실제 필드(companyName/title/
  employmentType/careerLevel/educationRequirement/status/institutionCode/
  jobCategory/location/applicationStartAt/applicationEndAt/source/
  sourceUrl/externalId/createdAt/recruitmentSteps[]/attachments[])를
  표시한다. `recruitmentSteps`/`attachments`가 빈 배열이면 "등록된 전형
  단계/첨부파일이 없습니다"로 표시(데이터 없음을 있는 척 만들지 않음).
  `sourceUrl`은 외부 링크(`target="_blank" rel="noreferrer"`)로 "원문
  보기" 버튼 제공.
- **AI 인사이트 영역 — `/match`만 실제 활성화, 나머지 3개는 자리만
  (사용자 결정, architect 최초 제안이었던 "4개 전부 비활성화"를
  뒤집음)**:
  - **"적합도 매칭"(`GET /api/jobs/{jobId}/match`, MATCH-001)은 실제로
    호출하는 버튼으로 구현한다.** 이 endpoint는 Anthropic을 호출하지
    않는 순수 deterministic 계산(PKB × 공고 카테고리 매칭)이므로 이번
    Task의 "Anthropic 실제 호출 금지" 제약과 무관하다. 버튼 클릭(자동
    mount 호출 금지 — 명시적 사용자 action 필요) 시 `JobMatchResponse`
    (`overallScore`, `recommendedExperiences/Certifications/Educations/
    Awards`(각 `MatchEvidence[]`: `type, id, title, score,
    matchedFields[]`), `unmatchedJobCategories`, `careerLevel`,
    `educationRequirement`, `computedAt`)를 받아 화면에 표시한다.
    `overallScore`도 §7 규칙과 동일하게 "추천 관련도"류 표현만 쓰고
    "합격 가능성"으로 표현하지 않는다.
  - "Semantic 매칭"(`/semantic-match`), "지원 전략 분석"
    (`/agent-analysis`), "자기소개서 초안"(`/application-draft`) 3개는
    자리만 두고 `disabled` + "비용 정책상 현재 비활성화되어
    있습니다"류의 명확한 문구를 노출한다(숨기지 않음).
  - 4개를 시각적으로 완전히 동일한 스타일로 늘어놓지 않는다 —
    실제로 작동하는 `/match`와 비활성 3개가 한눈에 구분되어야 한다
    (예: `/match`만 `--accent` 활성 버튼, 나머지 3개는 `--text-secondary`
    톤의 비활성 상태 — ADR-0038의 색 토큰 규칙을 따른다).

### 4. `/applications`, `/applications/[id]`

- `/applications`: table/list 형태(Kanban 미도입 — 상태 6종을 컬럼별로
  나누는 것보다 정렬된 리스트가 이 데이터 규모에 더 단순하고, backend가
  Kanban에 필요한 per-status 정렬/드래그 상태 변경 API를 제공하지 않음).
  `ApplicationStatus`(INTERESTED/PLANNED/SUBMITTED/OFFERED/REJECTED/
  WITHDRAWN) summary(상태별 개수, `GET /api/applications?status=X&size=1`
  totalElements 병렬 호출) + `status` 필터만 지원(backend가 제공하는
  유일한 필터). 고정 정렬 `updatedAt DESC`를 그대로 사용.
  companyName/title/status/appliedAt/applicationEndAt/jobPostingStatus
  컬럼 표시.
- `/applications/[id]`: `JobApplicationDetailResponse` 표시(memo 포함) +
  `stages`를 `sortOrder` 순 수직 타임라인으로 렌더링. `StageType`(DOCUMENT/
  CODING_TEST/WRITTEN/INTERVIEW/FINAL/OTHER)은 한국어 라벨로 매핑(예:
  서류/코딩테스트/필기/면접/최종/기타), `StageResult`(PENDING/PASSED/
  FAILED/CANCELLED)는 배지로(대기=`--text-secondary`, 합격=`--success`,
  불합격=`--danger`, 취소=`--text-secondary` 취소선). `scheduledAt`이
  null이면 "일정 미정". `stages`가 빈 배열이면 "등록된 전형 단계가
  없습니다". backend에 없는 단계를 UI가 추측해서 만들지 않는다.

### 5. `/career`

- 4개 섹션을 탭(또는 세그먼트)으로 전환: 경험(CareerExperience)/자격증
  (Certification)/학력(Education)/수상(Award). 탭 전환은 `?tab=experiences`
  등 URL searchParams 기반 `Link`로 구현(client fetch 아님).
- 경험 탭: `GET /api/career/experiences`(type/keyword 필터, pagination)로
  목록을 가져온 뒤, 목록에는 없는 `detail`/`bullets`/`tags`가 필요하므로
  Server Component가 반환된 각 id에 대해 `GET /api/career/experiences/{id}`를
  `Promise.all`로 병렬 조회해 카드에 필요한 전체 데이터를 미리 확보한다
  (ADR-0036 영향 절 근거 — PKB 규모가 작아 N+1 비용 문제 없음). 카드
  기본 상태는 summary(type/title/organization/role/기간/summary)만
  보이고, "자세히 보기"(client-side `useState` 토글, 새 fetch 없음)로
  detail/bullets/tags를 펼친다.
- 자격증/학력/수상 탭: 각 `GET /api/career/certifications|educations|awards`
  (pagination만, 필터 없음 — backend가 제공하지 않음)로 조회. 이 3개는
  list 응답 자체가 이미 전체 필드를 포함하므로 별도 detail fetch가
  필요 없다. Education은 `gpa`/`gpaScale`이 둘 다 있을 때만 "GPA
  X/Y"로 표시(하나만 있는 경우는 없음 — backend 검증으로 보장됨).

### 6. `/notifications`

- `NotificationStatus`(PENDING/SENDING/SENT/FAILED) summary(상태별 개수) +
  `status` 필터 + 목록(고정 정렬 `createdAt DESC`). 각 행에
  companyName/title/applicationEndAt/`recommendationScore`/`reason`/
  `status`/`createdAt` 표시.
- **읽기 전용** — `POST .../send`, `POST` prepare(`/api/notifications/job-recommendations`)
  둘 다 호출하는 코드/버튼을 만들지 않는다.
- **중요(코드 조사로 확인한 사실)**: `JobRecommendationNotificationResponse`는
  entity에 `sentAt` 컬럼이 있음에도 **API 응답에 `sentAt`을 포함하지
  않는다**(코드 확인 완료, `notification/dto/JobRecommendationNotificationResponse.java`).
  따라서 화면에 "발송 시각"을 표시하지 않는다 — API가 주지 않는 값을
  프론트가 추측/생성하지 않는다(AGENTS.md 근거 기반 원칙). `createdAt`
  (알림 생성 시각)만 표시한다.

### 7. `recommendationScore` 표현

모든 화면에서 `recommendationScore`(double, [0,1])는 **"추천 관련도"**로
표기한다. "합격률"/"합격 가능성"/"통과 확률" 등 확률적 표현을 절대 쓰지
않는다(`docs/DECISIONS.md` ADR-0031 결정 4/ADR-0026/ADR-0028과 동일 원칙).
숫자 그대로(예: 0.82) 또는 막대바 등 상대 비교 시각화는 허용하되, "%"로
표기해 확률처럼 보이게 만들지 않는다(예: "82%" 대신 "0.82" 또는 "관련도
0.82/1.0"). 카드/리스트에 짧은 툴팁/캡션으로 "지원자 PKB와의 상대적
연관도이며 합격 가능성과 무관합니다" 같은 문구를 추가하는 것을 권장한다.

## Out of Scope

- Anthropic/Kakao 실제 API 호출 — `lib/api/` 어디에도
  `/semantic-match`/`/agent-analysis`/`/application-draft`/
  `POST /api/jobs/recommendations`/`POST /api/notifications/job-recommendations`
  (prepare)/`POST .../{id}/send`를 호출하는 함수를 **작성하지 않는다**
  (구현 자체가 없다는 것이 곧 차단 장치 — AC에서 검증). `GET
  /api/jobs/{jobId}/match`는 예외적으로 허용(위 Scope 3 참고 — Anthropic
  미사용 deterministic 계산).
- Backend에 대한 모든 쓰기(POST/PATCH/DELETE) — 지원 등록/수정, 채용공고
  수동 등록, PKB 생성/수정, 알림 발송 전부 FRONT-002.
- 인증/회원가입, multi-user, 복잡한 상태관리 라이브러리(Redux/Zustand),
  chart 라이브러리, 대규모 테스트 프레임워크, mobile native app.
- Backend production code/schema/CORS 변경. 이번 Task 구현 중 CORS가
  필요하다고 판단되면 임의로 backend를 수정하지 말고 즉시 Claude에게
  보고한다(ADR-0036에 따르면 이번 Task 범위에서는 필요 없어야 정상).
- `NOTIFY-002`, 새 backend GET API 신설(모든 화면은 기존 GET만 조합).
- Dark mode(디자인 토큰 구조는 두되 실제 테마 전환 UI는 없음).

## Acceptance Criteria

- [ ] `npm run build`가 `frontend/`에서 에러 없이 성공한다(`next build --webpack`
      그대로 유지).
- [ ] `npm run lint`가 에러 없이 통과한다.
- [ ] `frontend/.env.local.example`이 `API_BASE_URL=`(server-only, `NEXT_PUBLIC_`
      접두어 없음)로 갱신되어 있다. `API_BASE_URL`을 로컬 backend
      (`http://localhost:8080`)로 설정하고 backend를 기동한 상태에서
      `npm run dev` 후 `/dashboard`/`/jobs`/`/applications`/`/career`/
      `/notifications`가 실제 DB 데이터를 표시한다(수동 확인, 최소
      스크린샷 없이 텍스트로 결과 보고).
- [ ] `API_BASE_URL`을 설정하지 않은 상태(`npm run dev` 또는 `npm run build && npm run start`)에서
      같은 5개 경로가 fixture 데이터로 정상 렌더링된다(에러/빈 화면 없음).
- [ ] `/`(랜딩 페이지)는 이번 Task로 내용이 전혀 변하지 않는다(diff 확인).
- [ ] `/jobs`에 `status`/`careerLevel`/`companyName`/`jobCategory` 4개
      필터를 URL query string으로 적용하면 fixture 데이터 기준으로 필터
      결과가 반영된 목록이 렌더링된다. 4개 외 필터 UI(키워드 통합검색,
      지역, 고용형태, 학력 등)가 존재하지 않는다.
- [ ] `/jobs/[id]`에서 "적합도 매칭" 버튼은 클릭 시(페이지 mount 시
      자동 호출 아님) 실제 `GET /api/jobs/{id}/match`를 호출해 결과를
      표시하고, 나머지 3개(Semantic 매칭/지원 전략 분석/자기소개서
      초안)는 `disabled` + 비활성화 사유 문구가 보인다. 4개 endpoint
      중 Semantic/agent-analysis/application-draft/추천(POST)/prepare
      (POST)/send(POST) 6개는 `frontend/src/` 어디에도 호출 코드가
      없다(grep 검증).
- [ ] **Visual Design System(ADR-0038) 준수**:
      - `frontend/src/app/globals.css`(또는 동등 토큰 정의 파일)에
        ADR-0038의 hex 값 그대로 CSS 변수가 정의되어 있다
        (`--background:#F6F7F9`/`--surface:#FFFFFF`/`--border:#E3E6EC`/
        `--text-primary:#16192B`/`--text-secondary:#5B6072`/
        `--accent:#24417A`/`--success:#1F8A5D`/`--warning:#B7791F`/
        `--danger:#B4322A`).
      - `next/font/google`로 IBM Plex Sans KR(+필요 시 IBM Plex Mono)이
        로드되고, 신규 npm dependency 없이(폰트는 Next.js 내장 기능)
        적용된다. `lucide-react`가 `package.json`에 추가된 유일한 신규
        production dependency다(그 외 신규 dependency는 없다).
      - `frontend/src/**/*.css`(또는 `*.module.css`) 어디에도
        `linear-gradient`/`radial-gradient` 배경, `backdrop-filter`
        (glassmorphism)가 없다(grep 검증 가능한 형태로 결과 보고에
        남긴다).
      - Jobs/Applications/Notifications 목록과 Dashboard의 "최근 알림"/
        "지원 현황" 섹션 행에 status rail(3px 좌측 색상 바)이 실제로
        적용되어 있다.
      - Dashboard의 4개 summary 수치가 동일한 크기/형태의 카드 4개로
        기계적으로 반복되지 않는다(가장 가까운 마감 1건이 시각적으로
        더 강조되고, 나머지는 보조적인 stat strip 형태) — 코드 리뷰로
        확인.
- [ ] **Screenshot 기반 Visual QA**: `npm run dev`로 실행한 로컬 환경에서
      아래 8개 화면의 실제 브라우저 렌더링 screenshot을 최소 2 라운드
      (render → screenshot → self-critique → refine → 재확인) 확보한다:
      `/dashboard`(desktop), `/jobs`(desktop), `/jobs/[fixture-id]`
      (desktop), `/applications`(desktop), `/career`(desktop),
      `/notifications`(desktop), `/dashboard`(375px), `/jobs`(375px).
      Screenshot 도구를 이번 실행 환경에서 전혀 쓸 수 없다면, "쓸 수
      없다"는 사실과 이유를 명시하고 로컬 URL을 안내하는 것으로
      대체하되 **"visual QA를 완료했다"고 거짓 보고하지 않는다**(이번
      Task의 핵심 원칙 — 사용자가 명시적으로 강조함).
- [ ] `/applications/[id]`에서 `stages` 배열이 있는 fixture 데이터는
      `sortOrder` 순 타임라인으로, `stages`가 빈 배열인 fixture 데이터는
      "등록된 전형 단계가 없습니다" 문구로 렌더링된다.
- [ ] `/career?tab=experiences`에서 카드 기본 상태에는 `detail`/`bullets`/
      `tags`가 보이지 않고, "자세히 보기" 클릭(페이지 새로고침/새 네트워크
      요청 없이) 후에만 보인다.
- [ ] `/notifications` 목록/카드 어디에도 "발송 시각"(`sentAt`) 표시가
      없다(API가 해당 필드를 반환하지 않으므로).
- [ ] `recommendationScore`가 표시되는 모든 위치(Dashboard 추천 카드,
      `/notifications` 목록)에서 "합격률"/"합격 가능성"/"통과 확률" 등의
      표현이 코드 어디에도 없고, 대신 "추천 관련도" 또는 동등한 비확률적
      표현을 쓴다(문자열 grep으로 검증 가능).
- [ ] `frontend/src/` 전체에서 `/api/jobs/{id}/match`, `/semantic-match`,
      `/agent-analysis`, `/application-draft`, `/api/jobs/recommendations`
      (POST), `/api/notifications/job-recommendations`(POST, prepare),
      `/send`(POST) 8개 endpoint를 호출하는 코드가 0건이다(grep으로 검증
      가능한 형태로 결과 보고에 남긴다).
- [ ] 375px 뷰포트(브라우저 devtools 또는 동등 방법)에서 AppShell/Dashboard/
      Jobs 목록/Notifications 목록이 가로 스크롤 없이 렌더링되고, 네비게이션
      항목 전체에 접근 가능하다(수동 확인 결과를 텍스트로 보고).
- [ ] backend(`backend/` 하위) 코드가 한 글자도 수정되지 않는다(diff 확인).
- [ ] 테스트: 대규모 프레임워크 도입 없이, `recommendationScore` 표기
      변환/날짜 포맷/상태 라벨 매핑처럼 순수 함수가 있다면 Node.js 내장
      `node:test`(신규 dependency 없음)로 최소 1~2개 케이스를 검증한다
      (필수는 아니며, 해당 순수 함수가 실제로 만들어졌을 때만 적용 — 없다면
      생략하고 그 사실을 보고).

## Technical Notes

- **반드시 먼저 읽을 것**: `docs/DECISIONS.md` ADR-0036(데이터 페칭),
  ADR-0037(스타일링). 이번 Task 설계의 전제다.
- **API 응답 필드(코드로 직접 확인, 추측 없음)** — Codex가 다시 backend를
  뒤질 필요 없이 아래를 그대로 신뢰해도 된다:
  - `GET /api/jobs`(query: `status`/`careerLevel`/`companyName`/
    `jobCategory`/`page`/`size`) → `{content: JobPostingResponse[],
    totalElements, totalPages, page, size}`.
    `JobPostingResponse`: `id, companyName, title, employmentType,
    careerLevel, educationRequirement, status, institutionCode,
    jobCategory, location, applicationStartAt(LocalDate),
    applicationEndAt(LocalDate), source, sourceUrl, externalId,
    createdAt(Instant)`.
  - `GET /api/jobs/{id}` → `JobPostingDetailResponse`(위 필드 전체 +
    `recruitmentSteps: {sortNo, stepGroupName, competitionRate,
    applicantCount, recruitCount, occurredAtRaw}[]`, `attachments:
    {sortNo, fileName, fileType, url}[]`).
  - `GET /api/applications`(query: `status`/`page`/`size`) →
    `{content: JobApplicationResponse[], totalElements, totalPages,
    page, size}`. `JobApplicationResponse`: `id,
    status(INTERESTED|PLANNED|SUBMITTED|OFFERED|REJECTED|WITHDRAWN),
    memo, appliedAt(LocalDate), createdAt, updatedAt, jobPostingId,
    companyName, title, applicationEndAt, jobPostingStatus`. 고정 정렬
    `updatedAt DESC`(backend JPQL 하드코딩, `sort` query param을 보내도
    무시됨 — UI가 이를 신뢰).
  - `GET /api/applications/{id}` → `JobApplicationDetailResponse`(위
    필드 + `stages: ApplicationStageResponse[]`).
  - `GET /api/applications/{applicationId}/stages` →
    `ApplicationStageResponse[]`: `id,
    stageType(DOCUMENT|CODING_TEST|WRITTEN|INTERVIEW|FINAL|OTHER),
    label, sortOrder, scheduledAt(LocalDateTime|null),
    result(PENDING|PASSED|FAILED|CANCELLED), memo, createdAt,
    updatedAt`(detail 응답에 이미 포함되므로 이 endpoint를 별도로 부를
    필요는 없음).
  - `GET /api/career/experiences`(query: `type`/`keyword`/`page`/`size`) →
    `{content: CareerExperienceResponse[], ...pagination}`.
    `CareerExperienceResponse`(목록용, detail 없음): `id,
    type(PROJECT|ACTIVITY|WORK|RESEARCH|OTHER), title, organization,
    role, startDate, endDate, summary, createdAt, updatedAt`.
  - `GET /api/career/experiences/{id}` → `CareerExperienceDetailResponse`(위
    + `detail(string), bullets:
    {bulletType(CONTEXT|ACTION|RESULT|OTHER), content, sortOrder}[],
    tags: string[]`).
  - `GET /api/career/certifications`(query: `page`/`size`, 필터 없음) →
    `{content: CertificationResponse[], ...pagination}`.
    `CertificationResponse`: `id, name, issuer, acquiredDate,
    expirationDate, credentialId, description, createdAt, updatedAt`
    (이미 전체 필드, 별도 detail fetch 불필요).
  - `GET /api/career/educations`(query: `page`/`size`) →
    `EducationResponse`: `id, institution, major,
    degree(HIGH_SCHOOL|ASSOCIATE|BACHELOR|MASTER|DOCTORATE|OTHER),
    status(ENROLLED|ON_LEAVE|GRADUATED|EXPECTED_GRADUATION|WITHDRAWN),
    startDate, endDate, gpa(number|null), gpaScale(number|null),
    description, createdAt, updatedAt`.
  - `GET /api/career/awards`(query: `page`/`size`) → `AwardResponse`:
    `id, title, issuer, awardedDate, description, createdAt, updatedAt`.
  - `GET /api/notifications/job-recommendations`(query:
    `status(PENDING|SENDING|SENT|FAILED)`/`page`/`size`) →
    `{content: JobRecommendationNotificationResponse[], ...pagination}`.
    `JobRecommendationNotificationResponse`: `id, jobId, companyName,
    title, applicationEndAt, recommendationScore(number 0~1), reason,
    status, createdAt` — **`sentAt` 없음**(위 Scope 6 참고). 고정 정렬
    `createdAt DESC`(하드코딩, `sort` param 무시됨).
  - 모든 list 응답 pagination 필드는 동일 패턴:
    `{content, totalElements, totalPages, page, size}`.
- **CORS/backend 사실 확인**: backend 전체(`backend/src/main/java`)에
  `CorsConfig`/`@CrossOrigin`/`addCorsMappings` 0건, Spring Security
  dependency도 없음(인증 없음 + CORS 헤더도 없음). 즉 브라우저 직접
  fetch는 막히지만 서버 프로세스 간 호출은 아무 제약이 없다 — ADR-0036의
  Server Component 전용 fetch 결정이 이 사실에 근거한다.
- **`lib/api/` 구조 제안**(과도한 추상화 지양): `frontend/src/lib/api/`
  아래 `jobs.ts`/`applications.ts`/`career.ts`/`notifications.ts` +
  공통 `client.ts`(base URL 판단, JSON fetch 헬퍼) 정도. 각 파일은 위
  Technical Notes에 나열된 GET만 함수로 노출한다 — 나열되지 않은
  endpoint(AI/Kakao 관련)는 함수 자체를 만들지 않는다(Out of Scope의
  "차단 장치").
- **fixture 데이터**: `frontend/src/lib/fixtures/`에 위 DTO 타입과 정확히
  일치하는 TypeScript 객체로 작성. 회사명/공고명/PKB 내용은 전부 가상의
  값을 쓴다(실제 ALIO 수집 데이터를 그대로 베끼거나 사용자의 실제 PKB
  원문을 절대 사용하지 않는다). 각 리소스마다 최소 5~10건 정도로,
  Dashboard 집계/필터/pagination/타임라인/빈 배열(전형 단계 없음 등)
  케이스가 최소 1건씩은 존재하도록 구성한다.
- **날짜 포맷**: 별도 라이브러리 없이 `Intl.DateTimeFormat`/
  `toLocaleDateString('ko-KR')` 사용.
- **아이콘**: `lucide-react`를 신규 dependency로 추가한다(사용자
  승인 완료). Sidebar 네비게이션 5개 항목과 소수의 기능적 어포던스
  (외부 링크, 펼치기 chevron 등)에만 사용 — 데이터 행/뱃지마다
  아이콘을 붙이지 않는다(ADR-0038 AI-slop audit 위험 3). 그 외 신규
  production dependency는 추가하지 않는다.
- **폰트**: `next/font/google`로 IBM Plex Sans KR(본문/타이틀)과 IBM
  Plex Mono(날짜/D-day/recommendationScore/ID 등 data-face)를 로드한다.
  둘 다 Google Fonts에 있어 `next/font/google`이 빌드 타임에 자체
  호스팅하므로 `package.json`에 신규 항목이 생기지 않는다(Next.js 내장
  기능). ADR-0038 §3 참고.
- **Visual QA 도구**: 이 개발 환경에 headless 브라우저(Playwright 등)가
  기본 설치되어 있지 않다. `claude-in-chrome` skill(사용자 Chrome
  확장 기반)이 사용 가능하면 이를 이용해 실제 렌더링 screenshot을
  확보한다. 그마저 불가능하면 AC의 "Screenshot 기반 Visual QA" 항목이
  요구하는 대로 불가능하다는 사실을 명시하고 로컬 URL만 안내한다 —
  screenshot 없이 "확인했다"고 보고하지 않는다.
- **Development Metrics**: 이번 Task는 `.ai/metrics/metrics.jsonl`에
  일반 Task 진행(plan/implement/review/verify) 기록만 남기면 되고,
  frontend 자체에 새 Product Metric(Micrometer 등)을 추가할 필요는
  없다 — backend를 건드리지 않으므로 계측 대상 자체가 없다.

## Test Plan

- `frontend/`에서 `npm install`(신규 dependency 추가 시) → `npm run build` →
  `npm run lint`.
- fixture 모드(`API_BASE_URL` 미설정) `npm run dev`로 5개 경로 전부 수동
  확인(빈 화면/에러 없음, AC 목록의 필터/타임라인/expand/AI 비활성화 항목
  확인).
- 가능하면 로컬 backend(`docker compose up -d` + `./gradlew bootRun`)를
  실제로 띄우고 `API_BASE_URL=http://localhost:8080`으로 5개 경로가 실제
  DB 데이터를 렌더링하는지 확인(backend 실행이 불가능한 환경이면 그 사실과
  이유를 결과 보고에 명시하고 fixture 모드 검증만으로 대체).
- 375px 뷰포트 수동 확인(브라우저 devtools 반응형 모드 또는
  `claude-in-chrome` screenshot).
- grep 기반 검증: 금지된 6개 endpoint 문자열(§3의 `/match` 제외 6개)이
  `frontend/src/` 어디에도 없음, "합격률"/"합격 가능성"/"통과 확률"
  문자열이 없음, `linear-gradient`/`radial-gradient`/`backdrop-filter`가
  CSS 어디에도 없음.
- **Screenshot critique loop(최소 2라운드) — Codex 구현 완료 후 Claude가
  수행**: Codex의 sandbox 실행 환경에는 브라우저/screenshot 도구가 없을
  가능성이 높으므로, 이 단계는 Codex의 "구현 후 검증"이 아니라 Codex
  구현 완료 보고를 받은 뒤 **Claude(오케스트레이터)가 `claude-in-chrome`
  skill로 직접** 수행한다: render → screenshot → ADR-0038 §8과 사용자가
  준 critique 체크리스트(hierarchy가 첫눈에 보이는가/카드 과다/spacing이
  기계적인가/한국어 줄바꿈/badge 과화려 등) 기준으로 critique → 문제
  발견 시 **같은 Codex thread에 구체적 수정 요청**(코드는 Claude가 직접
  고치지 않는다) → 재구현 → 재-screenshot. `claude-in-chrome`을 못 쓰는
  환경이면 그 사실을 사용자에게 명시하고 로컬 URL 안내로 대체하되
  "완료했다"고 보고하지 않는다. Codex는 자기 구현 결과 보고에 "이
  화면들을 시각적으로 이렇게 구성했다"는 설명만 포함하면 되고, 실제
  screenshot 확보/critique는 Claude의 리뷰 단계 책임이다.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | AppShell+5개 화면+Visual Design System(ADR-0038) 구현, `/match` 실제 활성화, fixture/실 API 분기 | 완료 보고: build/lint 통과, fixture 7경로+실 backend 5경로 HTTP 200, grep 검증(금지 endpoint/확률 표현/gradient 0건) 통과. `npm run dev`는 환경 EMFILE 제한으로 `npm run build && npm run start`로 대체 검증. **Codex가 metrics.jsonl에 스스로 status:passed를 기록해 Claude가 되돌림(codex-implement 원칙 위반, 리뷰 전이므로 무효)**. Screenshot visual QA는 지시대로 Codex가 수행하지 않음 — Claude가 별도 진행 |
| 2 | reviewer round 1 NEEDS_REVISION 2건(순수 함수 `node:test` 누락, `error.tsx` 부재) 수정 요청 | format.test.ts(4 케이스) + error.tsx 추가, 테스트 작성 중 `isClosingSoon` 7일 경계 실버그 발견·수정. npm test 4/4, build/lint 통과, 금지 패턴 재검사 0건 |

reviewer round 2: PASS(코드 레벨, `.ai/reviews/FRONT-001-review-1.md` Round 2 섹션). Claude의
desktop screenshot visual QA(claude-in-chrome, ADR-0038 준수 확인)와 합쳐 Task 종결.
375px 모바일은 claude-in-chrome의 `resize_window`가 이 환경에서 실제 viewport를
바꾸지 못해(`window.innerWidth` 확인 결과 항상 1512px 고정) Claude가 직접 screenshot을
찍지 못함 — 대신 round 1 reviewer가 Playwright로 375px 가로 스크롤 없음/드로어 네비
5개 노출을 확인했고, Claude도 CSS `@media` 브레이크포인트(767/850/700/600/500px)를
코드로 직접 읽어 구조적으로 확인함. 이 한계를 사용자에게 명시적으로 보고함.
