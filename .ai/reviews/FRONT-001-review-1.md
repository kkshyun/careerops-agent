---
task_id: FRONT-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-26T01:17:00+09:00
verdict: NEEDS_REVISION
---

## 범위 안내

이 리뷰는 **코드 정확성 / 구조 / Acceptance Criteria 충족 여부만** 다룬다.
실제 화면의 시각적 완성도(색/타이포/spacing이 실제로 좋아 보이는지, ADR-0038
§8 self-audit 위험이 실제로 해소됐는지)는 별도로 Claude(오케스트레이터)가
`claude-in-chrome`으로 screenshot 기반 critique loop를 수행할 예정이며 이
리뷰의 판정 범위가 아니다. **이 리뷰가 NEEDS_REVISION → 이후 PASS가 되어도
Task는 완료가 아니다 — Screenshot 기반 Visual QA(별도 AC 항목)가 남아있다.**

참고로 리뷰 중 사실 확인 목적으로 Playwright(Chrome)로 8개 라우트를 직접
렌더링/스크린샷했다(코드가 실제로 ADR-0038 토큰/폰트/컴포넌트 구조를
만들어내는지 확인하는 용도). 육안상 상태 rail/hairline border/IBM Plex
폰트/절제된 밀도가 잘 보였고 명백한 렌더링 붕괴는 없었다는 점을 참고
정보로 남긴다 — 이것이 공식 Visual QA critique loop를 대체하지는 않는다.

## Acceptance Criteria 체크

- [x] `npm run build` 에러 없이 성공 — 직접 재실행 확인(`Route (app)`에 9개
      라우트 모두 정상 생성).
- [x] `npm run lint` 에러 없이 통과 — 직접 재실행, 출력 0줄(경고/에러 없음).
- [x] `.env.local.example`이 `API_BASE_URL=`(prefix 없음)로 갱신 — 확인
      (`frontend/.env.local.example`). 실 backend 연결 시나리오는
      **재현하지 않고 Codex 보고를 신뢰**했다(docker+gradle 기동이 필요해
      이번 리뷰 범위상 생략, 아래 "재검증 못한 항목" 참고).
- [x] fixture 모드에서 5개 경로 정상 렌더링 — `npm run build && npm run
      start`로 직접 재현. `/dashboard`, `/jobs`, `/jobs/job-orbit-01`,
      `/applications`, `/applications/app-01`, `/career`, `/notifications`,
      필터 쿼리 전부 HTTP 200 확인(curl). `npm run dev`는 나도 시도하지
      않고 Codex와 동일하게 build+start로 검증했다(EMFILE 재현 여부는
      확인 안 함, build+start가 동등한 검증이라 판단).
- [x] `/`(랜딩) 무변경 — `git diff -- frontend/src/app/page.tsx` 결과 빈
      diff 확인.
- [x] `/jobs` 4개 필터만 존재, filtered 결과 반영 — `frontend/src/app/(app)/jobs/page.tsx`
      코드 확인(`status`/`careerLevel`/`companyName`/`jobCategory` 4개
      `<select>`/`<input>`만 존재). `companyName=ZZZNONEXIST999`로 curl
      직접 재현 시 "조건에 맞는 채용공고가 없습니다" 출력 확인(필터가
      실제로 서버 재조회에 반영됨).
- [x] `/jobs/[id]` "적합도 매칭" 버튼 — `MatchPanel.tsx`(client component)의
      `run()`이 버튼 `onClick`에서만 호출되고 mount 시 자동 호출 없음
      (초기 `result` state는 `null`). `actions.ts`가 `"use server"`로
      `getJobMatch`(`GET /api/jobs/{id}/match`)를 호출. 나머지 3개는
      `disabled` 속성 + "비용 정책상 현재 비활성화되어 있습니다." 문구
      확인. 6개 금지 endpoint(semantic-match/agent-analysis/
      application-draft/POST recommendations/POST prepare/POST send)는
      `frontend/src/` grep 결과 0건.
- [x] **Visual Design System(ADR-0038) 코드 레벨 확인**:
      - `globals.css`의 9개 hex 값 전부 ADR-0038과 정확히 일치(오타 없음).
      - `layout.tsx`에서 `next/font/google`로 `IBM_Plex_Sans_KR`/
        `IBM_Plex_Mono` 로드, `package.json`에 신규 프로덕션 dependency는
        `lucide-react` 하나뿐(diff 확인).
      - `frontend/src/**/*.css` grep 결과 `linear-gradient`/
        `radial-gradient`/`backdrop-filter` 0건.
      - Status Rail(`RailRow` 컴포넌트의 `.railRow:before`, 3px 좌측
        color bar)이 Jobs/Applications 상세는 아니지만 Jobs 목록/
        Notifications 목록/Dashboard "지원 현황"·"최근 알림" 섹션에서
        공통 사용됨을 코드로 확인.
      - Dashboard 4개 수치가 동일 카드 4개로 반복되지 않음 —
        `dashboard/page.tsx`에서 가장 가까운 마감을 `.priority`(강조
        블록, 어두운 배경 + warning accent bar)로 분리하고 나머지
        3개(OPEN/관심·지원 중/PENDING)만 `.summaryStrip`(작은 인라인
        stat)에 배치.
- [x] `/applications/[id]` stages `sortOrder` 정렬 + 빈 배열 처리 —
      `[...a.stages].sort((x,y)=>x.sortOrder-y.sortOrder)`,
      `stages.length?...:"등록된 전형 단계가 없습니다."` 확인. fixture
      데이터에 stages 有(app-01)/無(app-02~06) 둘 다 존재.
- [x] `/career?tab=experiences` "자세히 보기" — `ExperienceCard.tsx`는
      `"use client"`, `useState(false)`로 `open` 토글, 서버 데이터는 이미
      `career/page.tsx`가 `Promise.all(getExperience)`로 미리 받아 props로
      전달 — 클릭 시 새 fetch/네트워크 요청 없음.
- [x] `/notifications`에 `sentAt` 표시 없음 — grep 0건, 코드에도
      `createdAt`(라벨 "생성 시각")만 노출.
- [x] `recommendationScore` 표현 — "합격률"/"합격 가능성"/"통과 확률" grep
      0건. "추천 관련도"/"관련도 X/1.0" 형태만 사용.
- [x] 8개 금지 endpoint 호출 코드 0건 — `/api/jobs/{id}/match`는 §3 예외로
      허용된 유일한 endpoint이며 실제로 사용됨. 나머지 7개(semantic-match,
      agent-analysis, application-draft, POST recommendations, POST
      job-recommendations prepare, POST send)는 `frontend/src/` grep 0건.
      `lib/api/*.ts` 5개 파일 전체를 읽어 GET 함수만 존재함을 직접 확인.
- [x] 375px에서 가로 스크롤 없음, 네비게이션 전체 접근 가능 — Playwright로
      직접 재현(`scrollWidth === clientWidth === 375` on `/dashboard`,
      `/jobs`, `/notifications`; 햄버거 클릭 후 드로어에서 5개 nav 링크
      전부 visible 확인).
- [x] `backend/` 무수정 — `git status --porcelain -- backend`,
      `git diff --stat -- backend` 둘 다 빈 결과.
- [ ] **테스트(순수 함수 node:test)** — **미충족**. `frontend/src/lib/format.ts`에
      `scoreLabel`(recommendationScore 표기 변환), `formatDate`(날짜
      포맷), `isClosingSoon`, `applicationLabel`/`stageTypeLabel`/
      `stageResultLabel`/`notificationLabel`(상태 라벨 매핑) 등 AC가 예로
      든 것과 정확히 일치하는 순수 함수가 이미 만들어져 있다. AC 문구
      ("해당 순수 함수가 실제로 만들어졌을 때만 적용 — 없다면 생략하고 그
      사실을 보고")에 따르면 이 경우 `node:test` 최소 1~2 케이스가
      **적용되어야 하는 경우**인데, `frontend/`에 `*.test.*` 파일이 전혀
      없고(`find` 결과 0건), Codex의 완료 보고에도 이 누락에 대한 설명이
      없다.

## 테스트 결과

- `npm run build`: 성공 (재실행, exit 0, 9개 라우트 생성).
- `npm run lint`: 성공 (재실행, 출력 0줄).
- fixture 모드 HTTP 확인: `npm run build && npm run start` 후 curl로 7개
  경로 + 필터 쿼리 1건 = 8건 전부 200 (재현 완료).
- 375px 반응형: Playwright(Chrome channel)로 3개 경로 가로 스크롤 없음 +
  드로어 nav 5개 항목 visible 확인(재현 완료).
- test_count / test_pass_count: **0 / 0** — Codex가 AC의 순수 함수 테스트
  항목을 구현하지 않았고 그 사실을 보고서에도 남기지 않음(위 Findings 참고).
- 실 backend 연동(API_BASE_URL=localhost:8080) 시나리오는 **재검증하지
  않음** — docker compose + `./gradlew bootRun` 기동이 필요해 이번 리뷰
  범위/시간상 생략했고, Codex의 자체 보고(5경로 200 + `/match` GET에서
  `overallScore` 응답 확인)를 그대로 신뢰했다. 이 부분은 완전히
  독립적으로 재현되지 않았다는 점을 명시해 둔다.

## Findings

1. **(NEEDS_REVISION 핵심) 순수 함수 단위 테스트 누락** —
   `frontend/src/lib/format.ts`의 `scoreLabel`/`formatDate`/`isClosingSoon`
   등은 AC가 명시적으로 예시로 든 대상과 정확히 일치하는데도 `node:test`
   테스트가 하나도 없다. AC 문구상 "만들어졌으면 적용"이 조건이므로 이번
   경우엔 테스트가 있어야 한다. 최소 1~2 케이스씩만 추가하면 되는 작은
   범위다.
2. **(참고, 블로킹 아님) ADR-0036 결정 5의 `error.tsx` 경계 부재** —
   ADR-0036은 "`API_BASE_URL`이 설정된 상태에서 fetch가 실패하면 fixture로
   자동 전환하지 않고 Next.js `error.tsx` 경계로 명시적 에러 화면을
   보여준다"고 결정했다. 코드상 `getJson`이 실패 시 `Error`를 throw하고
   fixture로 조용히 폴백하지 않는 핵심 동작 자체는 지켜지고 있지만(중요한
   부분은 만족), `frontend/src/app` 어디에도 `error.tsx`가 없어 Next.js
   기본 프레임워크 에러 화면(비-커스텀)에 의존하게 된다. Task의 Acceptance
   Criteria 체크리스트에 별도 항목으로 명시되어 있지는 않으나, Task 본문이
   "ADR-0036을 반드시 먼저 읽고 정확히 그대로 따른다"고 명시하고 있어
   ADR과의 괴리를 남겨둔다. 다음 라운드 수정 요청에 포함하되, 이것만으로
   FAIL/재작업을 요구할 사안은 아니라고 판단했다.
3. **자기소개서/근거 기반 원칙 위반 없음** — fixture 데이터(`data.ts`)는
   전부 가상 회사명("오비트데이터", "하버금융기술" 등)과 `source:"DEMO"`,
   `sourceUrl: example.com`, PKB 항목도 "가상 프로젝트"/"가상 기술
   모임" 등으로 명시돼 있어 실제 ALIO 데이터나 사용자의 실제 PKB 원문을
   베낀 흔적이 없다.
4. **"스스로 PASS 자칭" 패턴 재확인** — 이미 알려진 `metrics.jsonl`
   self-report 건 외에 코드 주석/문자열에 유사 패턴(자체적으로 "PASS"/
   "검증 완료"/"승인" 등을 남기는 것)이 있는지 grep했으나 발견되지 않음
   (StageResult enum의 `PASSED` 값만 매칭, 무관).
5. **불필요한 추상화/과도한 패턴 없음** — `lib/api/*.ts` 구조가 Technical
   Notes가 제안한 그대로 얇고(`client.ts` + 리소스별 파일), 상태관리
   라이브러리/차트 라이브러리 등 Out of Scope 항목의 흔적도 없음.
   신규 프로덕션 dependency는 `lucide-react` 하나뿐(package-lock.json diff
   확인, 사유는 Technical Notes에 이미 기록됨).

## 다음 액션

**NEEDS_REVISION** — 같은 Codex thread(`01a039a5-04db-7080-8344-a833060ef364`)에
아래 2건을 요청한다(코드는 Claude/Codex가 수정, 리뷰어는 직접 수정하지
않음):

1. `frontend/src/lib/format.ts`의 순수 함수(`scoreLabel`, `formatDate`,
   `isClosingSoon` 중심으로 최소 2~3개 케이스)를 Node.js 내장
   `node:test` + `node:assert`로 검증하는 테스트 파일을 추가한다(신규
   dependency 없이, 예: `frontend/src/lib/format.test.ts`, `node --test`로
   실행 가능해야 함). 완료 보고에 test_count/test_pass_count를 명시한다.
2. ADR-0036 결정 5를 따라 `frontend/src/app/(app)/error.tsx`(최소한 `(app)`
   route group 범위)에 "데이터를 불러오지 못했습니다" 류의 명시적 에러
   경계를 추가한다 — fixture로 조용히 전환되지 않는다는 사실을 화면에서도
   드러나게 한다. (이 항목은 블로킹은 아니지만 함께 처리하는 것을 권장.)

그 외 Acceptance Criteria는 전부 코드 레벨에서 충족을 직접 재현/확인했다.
**주의**: 위 2건이 해결돼 이 리뷰가 PASS로 바뀌더라도 Task 자체는 아직
완료가 아니다 — Screenshot 기반 Visual QA(critique loop 최소 2라운드)가
Claude(오케스트레이터)의 몫으로 별도로 남아 있다.

## Round 2

```
review_round: 2
reviewer: claude
reviewed_at: 2026-08-26T01:24:00+09:00
verdict: PASS
```

### 범위

Round 1의 NEEDS_REVISION 2건에 대한 후속 검토다. Round 1에서 이미 PASS
처리한 나머지 Acceptance Criteria는 전부 다시 재검증하지 않았고, 이번
수정이 그것들을 깨지 않았는지만 build/lint/grep으로 가볍게 재확인했다.
Codex의 완료 보고를 신뢰하지 않고 파일을 직접 읽고 테스트/빌드를 재실행해
재현했다.

### 지적 사항 1 — 순수 함수 `node:test` 누락 → 해결 확인

- `frontend/src/lib/format.test.ts`를 직접 읽음. `node:assert/strict` +
  `node:test`(`describe`/`it`/`afterEach`/`mock`)만 사용, 신규 npm
  dependency 없음(패키지 import 없이 표준 라이브러리만 사용).
- 4개 테스트 모두 의미 있는 케이스:
  - `scoreLabel`: 퍼센트 미표기(`"0.83"`, `.includes("%") === false`),
    0 경계(`"0.00"`) 확인.
  - `formatDate`: 정상값 포맷 + `null` → `"—"` 처리 확인.
  - `isClosingSoon`: `Date.now`를 `mock.method`로 고정(2026-08-26)한 뒤
    당일(0일 후, true)과 정확히 7일 후(true)를 한 테스트에서, 전날
    (-1일, false)과 8일 후(false, 경계 밖)를 다른 테스트에서 검증 —
    요청한 "정확히 7일 후/8일 후/당일" boundary가 전부 커버됨.
    `null` 입력도 `false` 확인.
- 직접 재실행: `npm test` → `node --test src/lib/format.test.ts`,
  `tests 4 / pass 4 / fail 0` (재현 완료, Codex 보고와 일치).
- `frontend/src/lib/format.ts`의 `isClosingSoon` 수정 내용도 직접 읽고
  실행으로 재검증:
  ```ts
  const today=new Date(Date.now());today.setHours(0,0,0,0);
  const target=new Date(`${date}T00:00:00`);
  const days=(target.getTime()-today.getTime())/86400000;
  return days>=0&&days<=7
  ```
  `today`를 `Date.now()`로부터 만든 뒤 `setHours(0,0,0,0)`으로 시:분:초를
  버려서 "현지 날짜 단위"로 비교한다는 주장이 코드로 맞음. 회귀 재현을
  위해 직접 스크립트로 실행(하루 중 늦은 시각, 23:59로 `Date.now`를
  mock)해 off-by-one이 없는지 확인:
  - 당일(23:59 시각 기준) → `true`
  - +7일 → `true`
  - +8일 → `false`
  시:분:초가 자정이 아니어도 결과가 흔들리지 않아, 지적했던 "시각 차이로
  인한 off-by-one" 버그가 실제로 해소됐음을 확인.

### 지적 사항 2 — ADR-0036 결정 5 `error.tsx` 부재 → 해결 확인

- `frontend/src/app/(app)/error.tsx` 존재 확인. `"use client"` 선언,
  `{ error, reset }` 타입의 props(단 `error`는 미사용, `reset`만
  구조분해 — 타입 시그니처는 Next.js 규약대로 `Error & { digest?: string
  }`를 받게 되어 있어 규약 위반 아님), `reset()`을 "다시 시도" 버튼에
  연결, "/dashboard"로 이동하는 링크 제공. Round 1이 요청한 최소 범위인
  `(app)` route group 전체에 적용되는 위치.
- 사용하는 CSS 클래스(`panel`/`muted`/`stack`/`button`/`buttonSecondary`)
  전부 기존 `App.module.css`에 이미 정의돼 있어(직접 grep 확인) 새
  gradient/backdrop-filter 등 금지 효과 없이 기존 디자인 토큰만 재사용.

### Round 1 통과 항목 회귀 재확인 (가볍게)

- `npm run build`: 성공, 9개 라우트(`/`, `/_not-found`,
  `/applications`, `/applications/[id]`, `/career`, `/dashboard`,
  `/jobs`, `/jobs/[id]`, `/notifications`) 전부 정상 생성 — round 1과
  동일.
- `npm run lint`: 성공, 출력 0줄.
- grep 재검사(전부 0건): 금지 endpoint(`semantic-match`/`agent-analysis`/
  `application-draft`/`recommendations"`/`prepare`/`/send`), 확률 표현
  ("합격률"/"합격 가능성"/"통과 확률"), `linear-gradient`/
  `radial-gradient`/`backdrop-filter`, `sentAt`.
- `git diff --stat -- backend`: 빈 결과 (여전히 backend 무수정).
- `git diff -- frontend/package.json`: `test` script 추가 1줄 외에
  `lucide-react` 추가분은 round 1 구현 시점부터 이미 있던 것(round 1
  리뷰에서 이미 확인된 유일한 신규 프로덕션 dependency)이지 round 2에서
  새로 추가된 것이 아님 — 직접 diff 확인, round 2에서 신규
  dependency 없음.
- `git diff -- frontend/tsconfig.json`: `allowImportingTsExtensions: true`
  1줄만 추가. `format.test.ts`가 `./format.ts`를 확장자 포함해 import하기
  위한 최소 설정이며(Node 24 native TS 실행 지원, 별도 ts-node/tsx
  dependency 불필요), 과도한 설정 변경 아님.
- `.ai/metrics/metrics.jsonl`: round 2 시점에 Codex가 스스로 `status:
  passed`를 self-report한 흔적 없음 (diff상 새로 추가된 2줄은 모두 round
  1 시점에 orchestrator가 기록한 것).

### 테스트 결과

- test_count / test_pass_count: **4 / 4** (직접 재실행 재현).
- `npm run build`: 성공 (재실행).
- `npm run lint`: 성공 (재실행, 0줄).
- Node 버전: v24.19.0 (native TS 실행 확인).

### 판정

**PASS** — Round 1에서 지적한 2건(순수 함수 테스트 누락, `error.tsx`
부재) 모두 코드로 직접 확인·재현했고, 수정 과정에서 Codex가 스스로
발견/수정한 `isClosingSoon` 7일 경계 버그도 별도 스크립트로 재검증해
실제로 고쳐졌음을 확인했다. Round 1에서 PASS했던 나머지 항목들도 이번
수정으로 깨지지 않았음을 build/lint/grep 재실행으로 확인했다. 신규
프로덕션 dependency 없음, Secret 커밋 없음, 자기소개서/근거 기반 원칙과
무관한 순수 UI 코드라 해당 검증 항목은 적용 대상 아님.

**단, 코드 레벨 리뷰의 판정일 뿐이다.** Round 1에서 이미 명시했듯 이
Task는 Screenshot 기반 Visual QA(critique loop)가 별도로 완료돼야
비로소 done으로 종결 가능하다.
