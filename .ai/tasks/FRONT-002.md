---
task_id: FRONT-002
title: CareerOps 쓰기(mutation) 인프라 + Application 생성/상태변경/메모수정/삭제
phase: plan
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a03bb8-9999-7e51-bd7f-d4205fb21bac
---

## Context

**선행 Task: APPLICATION-003 (필수, §7에 필요)** — `GET
/api/applications`에 `jobPostingId` optional 필터를 추가하는 backend
Task(`.ai/tasks/APPLICATION-003-job-posting-id-filter.md`, 사용자 승인
완료)가 먼저 `status:passed`로 완료되어야 아래 §7(Application 생성 UX)을
설계된 그대로(사전 확인 방식) 구현할 수 있다. APPLICATION-003이 아직
완료되지 않은 상태에서 이 Task에 먼저 착수해야 한다면, §7만 낙관적 시도
+ 409 fallback으로 잠정 구현하고 APPLICATION-003 완료 후 별도 후속
수정으로 전환한다 — 그 경우 이 사실을 결과 보고에 명시한다(임의로
조용히 낙관적 방식을 최종본으로 남기지 않는다).

FRONT-001로 조회 전용 CareerOps UI(Dashboard/Jobs/Applications/Career/
Notifications, Server Component 전용 fetch + fixture fallback,
`API_BASE_URL` 서버 전용 환경변수)가 완성됐다. ADR-0036 결정 6은 쓰기
기능이 필요해지는 시점에 "CORS 허용 vs 프록시" 중 하나를 다시 결정해야
한다고 명시적으로 미뤄뒀다 — 이번 Task가 그 결정을 내리는 지점이다
(`docs/DECISIONS.md` ADR-0039, **확정**).

FRONT-002 전체 목표는 "조회만 가능한 UI"를 "실제로 취업 준비 상태를
관리할 수 있는 제품"으로 만드는 것이다. 이 Task(FRONT-002 본체)는 그
중 (1) 쓰기 인프라(Server Action 패턴, API layer 확장, 최소 form
primitive, 데모 모드 정책)를 처음으로 구축하고, (2) 가장 핵심 흐름인
`JobApplication` 생성/상태·메모·지원일 수정/삭제를 실제로 연결한다.
`ApplicationStage` CRUD(FRONT-002.1)와 Career 4개 섹션 CRUD
(FRONT-002.2)는 이 Task가 만드는 인프라를 그대로 재사용하는 후속
Task이므로, 이 Task를 먼저 완료해야 한다.

**절대 제한(AGENTS.md/이번 Phase 지시)**: Anthropic/Kakao 실제 API 호출
0건. `recommend`/`semantic-match`/`agent-analysis`/`application-draft`/
notification send/automation trigger를 호출하는 코드를 만들지 않는다
(FRONT-001의 금지 목록과 동일, 이번 Task로 새로 추가되지 않음).

## Scope

### 1. Mutation architecture (ADR-0039 확정)

- `frontend/src/lib/actions/applications.ts`에 `"use server"` 지시문을
  파일 최상단에 두고, 아래 Server Action들을 export한다:
  - `createApplication(input: {jobPostingId: string; memo?: string; appliedAt?: string})`
    → `JobApplicationCreateRequest`(`jobPostingId`(Long, NOT NULL),
    `status`(생략 시 backend가 `INTERESTED` 기본값 적용 — 이 Action은
    항상 `status`를 생략해 backend 기본값에 위임한다), `memo`(nullable,
    max 2000자), `appliedAt`(nullable, `LocalDate`))로 매핑해
    `POST /api/applications` 호출.
  - `updateApplicationStatus(id: string, status: ApplicationStatus)`,
    `updateApplicationMemo(id: string, memo: string)`,
    `updateApplicationAppliedAt(id: string, appliedAt: string | null)`
    3개로 분리하거나, 하나의 `updateApplication(id, patch)`로 통합—
    구현 재량이나 반드시 `JobApplicationUpdateRequest`의 부분 수정
    시맨틱(요청 필드가 없거나 null이면 무변경)을 그대로 반영한다.
    `PATCH /api/applications/{id}` 호출.
  - `deleteApplication(id: string, opts: {redirectTo?: string})` →
    `DELETE /api/applications/{id}`. 성공 시 `opts.redirectTo`가 있으면
    Server Action 안에서 `redirect()`(Next.js) 호출, 없으면 결과만
    반환.
  - 모든 함수는 공용 `ActionResult<T>` 타입(`lib/actions/types.ts`,
    아래 §5)을 반환한다 — throw 하지 않는다(폼/버튼 컴포넌트가 항상
    구조화된 값으로 성공/실패/데모모드를 구분할 수 있어야 함).
- 각 Action은 성공 시 관련 경로에 `revalidatePath()`를 호출한다(§6
  목록 참고). Server Component는 `cache:"no-store"`로 이미 항상
  네트워크를 재요청하지만(FRONT-001 `client.ts`), Next.js Router
  Cache(클라이언트 세션 캐시)를 명시적으로 무효화하기 위해
  `revalidatePath`를 생략하지 않는다.

### 2. API layer 확장 (`lib/api/*.ts` 재사용, 새 계층 없음)

- `frontend/src/lib/api/client.ts`에 다음을 추가한다(기존 `getJson`
  패턴 재사용, Repository/Adapter/UseCase 같은 backend식 계층 신설
  금지):
  - `export class ApiError extends Error { status:number; body:unknown }`
    — non-2xx 응답에서 throw. 응답 body는 `response.json()`을
    시도하되 파싱 실패(빈 body 등)를 흡수하고 `undefined`로 둔다
    (아래 §4 근거 — backend에 커스텀 에러 body 포맷이 보장되지
    않음).
  - `postJson<T>(path, body)`, `patchJson<T>(path, body)`,
    `deleteRequest(path)`(204 No Content 처리, 반환값 없음) — 셋 다
    `apiBaseUrl` 미설정 시 호출자가 절대 부르지 않아야 하므로(§4 데모
    모드는 Action에서 분기) 여기서는 단순히 `apiBaseUrl`이 없으면
    `Error`를 throw해 오용을 즉시 드러낸다(fixture 대상 쓰기 자체가
    없어야 하므로 이 경로가 실제로 실행되면 버그).
- `frontend/src/lib/api/applications.ts`에 `createApplicationRequest`/
  `updateApplicationRequest`/`deleteApplicationRequest`처럼 위 헬퍼를
  감싼 얇은 함수를 추가(HTTP 세부사항을 `lib/actions/`에서 감춤).
  네이밍은 재량.

### 3. Form primitive (`frontend/src/components/form.tsx` 신설, 거대
   프레임워크 금지)

최소 컴포넌트만 만든다:
- `Field({label, htmlFor, error, children})` — label + children + 에러
  텍스트 한 줄.
- `FieldError({children})` — `--danger` 톤 텍스트.
- `TextInput`/`Textarea`/`Select` — 네이티브 `<input>`/`<textarea>`/
  `<select>`를 감싸되 스타일만 `Form.module.css`(신설, 기존
  `App.module.css`의 `.filter input,.filter select` 톤을 재사용/
  일반화)로 통일. 새 date picker/select 라이브러리 도입 금지 — 날짜는
  `<input type="date">`/`<input type="datetime-local">`, enum은
  `<select>`로 충분.
- `FormActions({children})` — 버튼 정렬 wrapper.
- `SubmitButton({children})` — `react-dom`의 `useFormStatus()`로
  `pending` 상태를 읽어 `disabled` + 로딩 텍스트(`"저장 중…"` 등)
  처리. `<form action={serverAction}>` 내부에서만 동작(React 19
  표준 패턴).
- 새 npm dependency 추가 금지(목표 0개 — 아래 §20 재확인).

### 4. 에러 처리 정책 (코드로 확인한 사실 기반)

- `backend/src/main/java` 전체에 `@ControllerAdvice`/
  `@ExceptionHandler`가 `application`/`career` 패키지에는 없음(코드
  확인 완료 — agent/applicationdraft/match/notification/recommend
  패키지에만 존재, 이번 Task 대상 domain과 무관). 즉 `application`/
  `career` 도메인의 400/404/409 응답 **body의 정확한 스키마는
  테스트로 보장되지 않는다**(컨트롤러 테스트들은 `status()`만
  검증하고 body 필드는 검증하지 않음 — 코드 확인 완료). Server
  Action은 **HTTP status code만 신뢰**하고, body는 있으면 best-effort로
  `message`/`detail`/`error` 필드 중 존재하는 것을 노출하되, 없으면
  상태코드별 고정 한국어 메시지로 대체한다:
  - 400 → "입력값을 확인해주세요."
  - 404 → "대상을 찾을 수 없습니다. 새로고침 후 다시 시도해주세요."
  - 409 → (Application 생성 시) "이미 이 채용공고에 지원 등록이
    되어 있습니다." / (Stage 등, FRONT-002.1) "이미 사용 중인
    순서입니다."
  - 그 외(500 등) → "요청을 처리하지 못했습니다. 잠시 후 다시
    시도해주세요."

### 5. 공용 타입 (`frontend/src/lib/actions/types.ts`)

```ts
export type ActionResult<T = undefined> =
  | { ok: true; data: T }
  | { ok: false; kind: "demo"; message: string }
  | { ok: false; kind: "error"; message: string; status?: number };
```
과도한 Result/Either 프레임워크 도입 금지 — 이 타입 하나로 전부
표현한다.

### 6. 데모(fixture) 모드 정책

- `API_BASE_URL`이 없으면(Vercel 기본 배포 상태) 모든 Server Action은
  **네트워크 호출도, fixture 배열 수정도 하지 않고** 즉시
  `{ok:false, kind:"demo", message:"데모 데이터에서는 저장되지
  않습니다. 로컬에서 API_BASE_URL을 설정한 실제 Backend에 연결하면
  저장할 수 있습니다."}`를 반환한다.
- Form/버튼 컴포넌트는 `kind==="demo"`일 때 에러(빨간색) 톤이 아니라
  중립/안내 톤(`--text-secondary` 배경 or `--warning` 보더 정도)의
  배너로 표시한다 — "실패"가 아니라 "이 환경의 의도된 제약"이라는
  것을 시각적으로 구분한다.
- fixture 데이터(`lib/fixtures/data.ts`)는 이 Task에서 절대 수정하지
  않는다(런타임 mutable 상태로 바꾸지 않음 — "가짜로 저장된 것처럼"
  보이게 만들지 않는다, 이번 Phase의 최우선 지시).

### 7. Application 생성 UX (`/jobs/[id]`)

- **Uniqueness는 실제로 DB `UNIQUE(job_posting_id)` + 애플리케이션
  사전 `existsByJobPostingId` 체크로 강제되고, 위반 시 API가 409를
  반환한다(코드 확인 완료 — `JobApplicationService.create()`,
  `V5__create_job_applications_table.sql`).** APPLICATION-003이
  `GET /api/applications`에 `jobPostingId` optional 필터를 추가했으므로
  (선행 Task, 위 Context 참고), 이 Task는 **사전 확인(precheck) 방식**으로
  설계한다 — 낙관적 시도 후 409를 받아 처리하는 방식은 쓰지 않는다.
- `/jobs/[id]`는 Server Component이므로, 기존 `getJob(id)` 호출과
  함께 `getApplications({jobPostingId: id, size: 1})`(또는 동등한
  `lib/api/applications.ts` 함수, size를 1로 제한해 존재 여부만
  확인 — 목록 전체를 가져오지 않는다)을 병렬로 호출해 이미 지원
  등록된 `JobApplication`이 있는지 페이지 렌더 시점에 미리 안다.
  - `getApplications()`(`frontend/src/lib/api/applications.ts`)의
    `q` 파라미터 타입에 `jobPostingId?: string`을 추가한다. backend
    모드는 `queryString(q)`가 이미 임의 키를 그대로 쿼리스트링으로
    직렬화하므로 별도 분기 없이 동작한다(코드 확인:
    `queryString()`은 값이 있는 모든 key를 그대로 넣음). fixture
    모드는 `applications` 배열이 이미 `jobPostingId` 필드를 갖고
    있으므로(코드 확인: `lib/fixtures/data.ts`) `q.jobPostingId`가
    있으면 동일하게 `list.filter(a=>a.jobPostingId===q.jobPostingId)`
    조건을 추가한다(기존 `status` 필터와 같은 패턴).
  - **이미 지원 등록이 있는 경우**: "지원 등록" 버튼 대신 "지원 관리
    중" 상태 배지(또는 안내 텍스트) + 그 `JobApplication.id`로 바로
    이동하는 "지원 상세 보기" 링크(`/applications/{id}`,
    `styles.buttonSecondary`)를 보여준다. 새 생성 폼/버튼은 렌더링하지
    않는다(중복 지원 시도 자체를 UI에서 원천적으로 막음 — 더 이상
    409 응답을 받을 경로가 없다).
  - **지원 등록이 없는 경우**: 기존과 동일하게 "지원 등록" 버튼
    (`<form action={createApplication.bind(null, jobId)}>` 또는 client
    wrapper + `useActionState`)을 보여준다. 클릭 시 메모/지원일 없이
    (또는 간단한 선택 입력) 바로 생성 요청. 응답이 `ok:true`면 생성된
    `applicationId`로 `/applications/{id}`로 이동(Server Action 내부
    `redirect()` 또는 클라이언트에서 `router.push`)한다.
  - **경합(race) 시나리오**: 사전 확인과 실제 생성 요청 사이에(예:
    같은 화면을 두 탭에서 열어두고 동시에 클릭) 다른 요청이 먼저
    성공해 여전히 409가 돌아올 수 있다 — 이 경우까지 UI에서 완전히
    막을 수는 없으므로, 409 응답을 받으면 §4의 기존 고정 문구("이미
    이 채용공고에 지원 등록이 되어 있습니다")를 그대로 안내로
    보여주고 페이지를 새로고침하도록 안내한다(정상 흐름에서는 거의
    발생하지 않는 예외 경로이므로 별도 정교한 UX를 만들지 않는다).
- MatchPanel과 마찬가지로 버튼들은 `styles.button`(`--accent`)/
  `styles.buttonSecondary` 스타일을 재사용해 페이지 톤을 유지한다.

### 8. Application 상태/메모/지원일 수정 UX (`/applications/[id]`)

- **실제 `ApplicationStatus` enum 6개 값**(`INTERESTED`, `PLANNED`,
  `SUBMITTED`, `OFFERED`, `REJECTED`, `WITHDRAWN`, 코드 확인 완료
  `com.careerops.backend.application.ApplicationStatus`)은 선형적인
  단일 축이 아니라 관심→준비→제출과 최종 결과(OFFERED/REJECTED/
  WITHDRAWN)가 섞인 상태값이라, segmented control(가로 나열)보다
  **`<select>` 기반 상태 변경 폼**을 쓴다(6개면 segmented control도
  가능한 개수지만, "최종 합격/불합격/철회"를 "관심/지원예정/제출"과
  같은 시각적 무게로 나열하면 오조작 위험이 커진다 — 기존
  `applicationLabel` 한국어 매핑을 그대로 재사용).
  - `<select>` 변경 즉시 자동 제출(onChange → `useTransition`으로
    `updateApplicationStatus` 호출, MatchPanel과 동일한 패턴)하거나,
    별도 "변경" 버튼을 둘지는 구현 재량이나 **되돌릴 수 없는 되돌림
    방지를 위해 OFFERED/REJECTED/WITHDRAWN으로 변경할 때만** §9 confirm
    을 거치게 한다(관심/지원예정/제출 간 이동은 confirm 불필요 —
    자주 바뀌는 값이라 매번 확인창을 띄우면 UX 저해).
- 메모(`memo`, max 2000자)는 `<textarea>` + "저장" 버튼(자동저장
  아님, 명시적 제출)로 별도 폼.
- 지원일(`appliedAt`, `LocalDate`, nullable)은 `<input type="date">` +
  "저장" 버튼. 비우고 저장하면 `null`로 갱신할 수 있어야 한다(단,
  `JobApplicationUpdateRequest`는 "필드가 없거나 null이면 무변경"
  시맨틱이므로, "명시적으로 지운다"를 표현하려면 backend가 구분할 수
  있는 값이 필요하다 — 코드 확인 결과 `appliedAt` 필드가 요청 JSON에
  `null`로 명시되어 와도 Jackson은 `request.appliedAt() != null` 조건에서
  걸러져 "무변경"으로 처리된다(코드 확인:
  `JobApplicationService.update()` `if (request.appliedAt() != null)`).
  **즉 현재 backend API로는 appliedAt을 한 번 설정한 뒤 다시 null로
  되돌릴 방법이 없다** — 이 사실을 UI에 정직하게 반영해, "지원일
  지우기" 기능은 제공하지 않고 날짜 재입력(변경)만 지원한다(Out of
  Scope에도 명시).

### 9. 삭제(destructive action) confirm — 공용 `ConfirmDialog` 컴포넌트

새 modal 라이브러리를 도입하지 않고, HTML5 표준 `<dialog>` element
(모든 대상 브라우저에 이미 내장, 신규 dependency 아님) 기반의 최소
공용 컴포넌트를 만든다. 이 컴포넌트는 **destructive action 전용**으로만
쓴다 — 일반 확인/알림 용도로 확장하지 않는다.

- **파일**: `frontend/src/components/ConfirmDialog.tsx`(`"use client"`)
  + `frontend/src/components/ConfirmDialog.module.css`. 이 Task가 만든
  뒤 FRONT-002.1(Stage 삭제)과 FRONT-002.2(Career 4개 리소스 삭제)가
  그대로 import해 재사용한다 — 각 Task가 자체 confirm 컴포넌트를 새로
  만들지 않는다.
- **API(ref 기반, imperative open)**:
  ```ts
  export type ConfirmDialogHandle = { open: () => void };

  type ConfirmDialogProps = {
    title: string;
    description?: string;
    confirmLabel?: string;   // 기본 "삭제"
    cancelLabel?: string;    // 기본 "취소"
    onConfirm: () => void | Promise<void>; // 성공 시 다이얼로그가 자동으로 닫힘
  };
  ```
  `forwardRef`로 `ConfirmDialogHandle`을 노출한다 — 컴포넌트 자신은
  트리거 버튼을 렌더링하지 않는다. 호출부(Application 삭제 버튼, Stage
  삭제 아이콘 버튼, Career 카드 삭제 버튼 등 이미 각기 다른 마크업/
  스타일을 쓰는 기존 버튼)가 자신의 버튼 `onClick`에서
  `confirmRef.current?.open()`을 호출하고, 같은 컴포넌트 트리 안에
  `<ConfirmDialog ref={confirmRef} .../>` 하나를 함께 렌더링한다. 이
  분리로 트리거 버튼의 스타일/문구는 호출부 재량으로 남기고(평소엔
  secondary 톤 버튼을 그대로 쓰고, 트리거 버튼 자체를 danger 톤으로
  강조하지 않는다는 원칙 유지 — destructive 강조는 최종 확인
  단계에만 집중시킨다), `ConfirmDialog` 내부의 **최종 확인 버튼만**
  `--danger` 톤으로 강조한다.
- **내부 동작**:
  - `dialogRef = useRef<HTMLDialogElement>(null)`,
    `restoreFocusRef = useRef<HTMLElement | null>(null)`.
  - `open()`: `restoreFocusRef.current = document.activeElement as
    HTMLElement`(트리거 버튼 기록) 후 `dialogRef.current?.showModal()`
    호출. `showModal()`(단순 `open` 속성이 아님)을 써야 브라우저가
    top-layer 모달로 렌더링하며 포커스 트랩을 네이티브로 제공한다.
  - **포커스 트랩**: 별도 JS 구현 없이 `showModal()`의 브라우저 기본
    동작에 의존한다(모든 대상 브라우저 — Chrome/Edge/Safari/Firefox
    최신 버전 — 가 `<dialog>` top-layer 안에서 Tab 포커스를 자동으로
    가둔다). **초기 포커스**는 취소 버튼에 `autoFocus`를 지정해
    다이얼로그가 열리자마자 안전한(비파괴적) 액션으로 포커스가
    가게 한다(실수로 Enter를 눌러도 삭제가 바로 실행되지 않도록 —
    위와 같은 "기본은 안전한 선택, 위험한 선택은 의도적 동작이
    필요"라는 원칙).
  - **Escape로 닫기**: 별도 구현 없이 `<dialog>`의 네이티브 기본
    동작(`cancel` → `close` 이벤트)에 의존한다 — `cancel` 이벤트에
    `preventDefault()`를 호출하지 않는다.
  - **backdrop 클릭으로 닫기**: `<dialog>` 엘리먼트 자체의 `onClick`
    핸들러에서 `event.target === dialogRef.current`(다이얼로그 콘텐츠가
    아니라 다이얼로그 요소 자체를 클릭 = backdrop 영역)일 때만
    `dialogRef.current?.close()`를 호출한다.
  - **닫힐 때 포커스 복귀**: `<dialog>`의 `onClose`(네이티브 `close`
    이벤트, Escape/backdrop/취소/확인 모든 닫힘 경로가 공통으로
    거침)에서 `restoreFocusRef.current?.focus()`를 호출한다.
  - **확인 버튼**: `type="button"`, `onClick`에서 로컬
    `isPending`(`useState`) `true` 설정 → `await onConfirm()` → 성공
    시 `dialogRef.current?.close()`(→ 위 `onClose`가 포커스를 트리거로
    되돌림). `onConfirm`이 실패(reject)해도(예: 네트워크 오류로 throw)
    다이얼로그는 닫지 않고 버튼을 다시 활성화한다 — 단, 이 컴포넌트가
    호출하는 Server Action들은 §5 정책상 throw하지 않고 항상
    `ActionResult`를 반환하므로, 실제로는 호출부가
    `onConfirm={async () => { const result = await
    deleteApplication(...); if (result.ok) router-level 처리 }}`처럼
    감싸는 형태가 된다 — `ConfirmDialog` 자체는 `ActionResult`의
    존재를 모른다(호출부 책임으로 유지, 컴포넌트를 도메인에 결합하지
    않음).
  - `isPending` 동안 확인/취소 버튼 모두 `disabled`, 확인 버튼 라벨은
    `"처리 중…"`으로 바뀐다(`SubmitButton`의 pending 표기와 톤 통일).
- **스타일(`ConfirmDialog.module.css`)**: 새 팔레트를 만들지 않고
  `globals.css`의 기존 토큰만 재사용한다 — 다이얼로그 박스는
  `--surface` 배경 + `--border` 테두리(기존 `.panel`과 동일 톤),
  `::backdrop` 의사요소는 반투명 어두운 배경. 취소 버튼은 기존
  `.buttonSecondary`와 같은 톤(`--border` 테두리, `--accent` 텍스트),
  확인 버튼만 `--danger`로 강조(`border-color:var(--danger)
  background:var(--danger) color:white` — `.button`의 accent 조합을
  danger로 치환한 것과 동일한 시각 언어).
- **사용처(이 Task 범위)**: `/applications/[id]`의 "지원 삭제" 버튼,
  §8에서 OFFERED/REJECTED/WITHDRAWN으로의 상태 변경. 상태 변경
  확인에도 동일 컴포넌트를 재사용하되 `confirmLabel`을 "변경"처럼
  문맥에 맞게 바꾼다(props로 이미 지원).
- 삭제 성공 시 `/applications`로 이동(Server Action 내부 `redirect()`).

## Out of Scope

- **`ApplicationStage` CRUD** — FRONT-002.1.
- **Career(경험/자격증/학력/수상) CRUD** — FRONT-002.2.
- `GET /api/applications`에 `jobPostingId` 필터를 추가하는 backend
  변경 — **APPLICATION-003으로 이미 승인/분리된 별도 Task**(위 Context
  참고). 이 Task는 backend를 건드리지 않고 APPLICATION-003이 제공하는
  필터를 소비만 한다.
- `appliedAt`을 명시적으로 `null`로 되돌리는 기능 — 위 §8 근거로
  현재 backend API가 지원하지 않음. 필요하면 backend
  `JobApplicationUpdateRequest`에 "clear" 시그널(예: sentinel 값 또는
  별도 endpoint)을 추가하는 backend Task 후보로 남긴다.
- `JobApplication` 목록(`/applications`)에서의 인라인 빠른 상태변경
  (한 줄에서 바로 select) — 이 Task는 상세 페이지(`/applications/[id]`)
  에서만 상태변경을 지원한다(목록 인라인은 후속 후보, 이번 Task는
  범위를 상세 페이지로 좁혀 완결성을 높인다).
- 인증/멀티유저, backend production code 변경, 새 UI/toast/modal
  라이브러리, Tailwind.
- Backend CORS 추가 — ADR-0039로 명시적으로 기각(Server Action이
  대체).

## Acceptance Criteria

- [ ] `npm run build`(`next build --webpack`)와 `npm run lint`가
      `frontend/`에서 에러 없이 통과한다.
- [ ] `package.json`의 `dependencies`에 신규 항목이 추가되지 않는다
      (diff 확인 — 목표 0개, `lucide-react`/`next`/`react`/`react-dom`
      그대로).
- [ ] 로컬 backend(`API_BASE_URL=http://localhost:8080`, APPLICATION-003
      적용된 상태)가 켜진 상태에서:
      - `/jobs/{id}`(아직 지원 등록 안 한 공고)를 열면 "지원 등록"
        버튼이 보이고, 클릭 시 `POST /api/applications`가 성공(201)해
        `/applications/{id}`로 이동해 방금 만든 지원 내역이 보인다.
      - 방금 지원 등록한 같은 공고의 `/jobs/{id}`를 다시 열면(새
        요청/새로고침) "지원 등록" 버튼 대신 "지원 관리 중" 상태와
        해당 `/applications/{id}`로 가는 링크가 보이고, 새로운 생성
        폼/버튼은 렌더링되지 않는다(사전 확인이 실제로 동작함 — 더
        이상 중복 생성을 시도할 UI 경로 자체가 없음).
      - `/applications/{id}`에서 상태를 `SUBMITTED`로 바꾸면(confirm
        불필요 범위) 새로고침 후에도 `SUBMITTED`로 유지된다.
      - `/applications/{id}`에서 상태를 `OFFERED`로 바꾸면
        `ConfirmDialog`가 뜨고, 취소(취소 버튼/Escape/backdrop 클릭
        중 최소 1가지씩 각각 1회 이상) 시 상태가 바뀌지 않고 다시
        열어 확인 시 반영된다(수동 확인).
      - 메모를 수정해 저장하면 새로고침 후에도 유지된다(2000자 초과
        입력 시 서버 400을 받아 "입력값을 확인해주세요" 류 메시지가
        보인다 — 클라이언트 `maxLength` 속성으로 사전에 막아도 되지만
        서버 오류 처리 경로도 최소 1회 확인).
      - 지원일을 입력해 저장하면 새로고침 후에도 유지된다.
      - "지원 삭제" 클릭 → `ConfirmDialog` 오픈 → 확인 시 `DELETE
        /api/applications/{id}`가 204를 받고 `/applications`로
        이동하며 목록에서 해당 항목이 사라진다.
      - 위 흐름 전체를 명세의 "실제 로컬 Backend E2E 시나리오"대로
        수행한 뒤 테스트로 생성한 데이터를 전부 정리(cleanup)한다 —
        기존에 있던 실제 지원 데이터는 건드리지 않는다(수동 확인 결과를
        결과 보고에 남긴다).
- [ ] `ConfirmDialog` 동작(코드/수동 확인): 열릴 때 취소 버튼으로
      초기 포커스가 이동한다, Tab 키로 다이얼로그 밖 요소로 포커스가
      나가지 않는다, Escape로 닫힌다, backdrop(다이얼로그 콘텐츠
      바깥) 클릭으로 닫힌다, 닫힌 뒤 포커스가 트리거 버튼으로
      복귀한다 — 이 5가지를 `/applications/[id]`의 삭제 confirm에서
      최소 1회씩 수동 확인한다.
- [ ] `window.confirm()`/`window.alert()`/`window.prompt()` 호출이
      `frontend/src/` 어디에도 없다(grep 확인 — `ConfirmDialog`로
      완전히 대체).
- [ ] `API_BASE_URL`을 설정하지 않은 상태(fixture 모드)에서 "지원
      등록"/상태 변경/메모 저장/삭제 버튼을 눌러도 fixture 데이터가
      변하지 않고(새로고침 전후 동일), 매번 "데모 데이터에서는
      저장되지 않습니다" 안내가 보인다(에러 톤이 아님, 시각적으로
      구분).
- [ ] `frontend/src/` 어디에도 `/semantic-match`, `/agent-analysis`,
      `/application-draft`, `POST /api/jobs/recommendations`,
      `POST /api/notifications/job-recommendations`(prepare),
      `/send`(POST) 6개 endpoint를 호출하는 코드가 없다(FRONT-001과
      동일 grep 검증 유지).
- [ ] `frontend/src/` 어디에도 브라우저(Client Component)가
      `http://localhost:8080` 또는 `API_BASE_URL`을 직접 `fetch`하는
      코드가 없다(모든 backend 호출은 Server Action/Server Component
      경유 — grep으로 client 파일(`"use client"` 포함 파일)에 backend
      URL 패턴이 없음을 확인).
- [ ] 새 modal/toast/date-picker 라이브러리가 추가되지 않았다(package.json
      diff로 확인).
- [ ] `/`(랜딩), `/dashboard`, `/jobs`, `/career`, `/notifications`
      기존 화면은 이번 Task로 내용이 변하지 않는다(diff 확인 — 이번
      Task는 `/jobs/[id]`와 `/applications`, `/applications/[id]`만
      건드린다).
- [ ] backend(`backend/` 하위) 코드가 한 글자도 수정되지 않는다(diff
      확인).
- [ ] 테스트: `ActionResult` 판별/상태코드→메시지 매핑처럼 순수 함수가
      있다면 `node:test`로 최소 1~2개 케이스 추가(신규 dependency
      없음). Server Action 자체(네트워크 I/O 포함)는 자동 테스트
      대상에서 제외하고 위 수동 E2E로 대체한다(FRONT-001의 테스트
      정책과 동일한 실용적 기준).

## Technical Notes

- **반드시 먼저 읽을 것**: `docs/DECISIONS.md` ADR-0036(FRONT-001 데이터
  페칭, 특히 결정 6), ADR-0039(**확정**, 쓰기 아키텍처), ADR-0037/0038
  (스타일 유지). 그리고 `.ai/tasks/APPLICATION-003-job-posting-id-filter.md`
  (§7이 전제하는 backend 필터 계약).
- **실제 조사한 API 계약(추측 없음, 코드 파일 경로 포함)**:
  - `POST /api/applications`
    (`backend/.../application/JobApplicationController.java`) —
    Request `JobApplicationCreateRequest{jobPostingId: Long(NotNull),
    status: ApplicationStatus?, memo: String?(@Size max=2000),
    appliedAt: LocalDate?}`. 201 + `JobApplicationResponse{id, status,
    memo, appliedAt, createdAt, updatedAt, jobPostingId, companyName,
    title, applicationEndAt, jobPostingStatus}`. 404(jobPosting 없음),
    409(이미 존재 — `existsByJobPostingId` 사전체크 + DB
    `UNIQUE(job_posting_id)` catch 이중 방어, 코드 확인).
  - `PATCH /api/applications/{id}` — Request
    `JobApplicationUpdateRequest{status: ApplicationStatus?, memo:
    String?(max 2000), appliedAt: LocalDate?}`, 세 필드 모두 optional,
    null/미포함 시 무변경(부분 수정). 200 + `JobApplicationResponse`.
    404.
  - `DELETE /api/applications/{id}` — 204. 404.
  - `ApplicationStatus` enum 6값(코드 원문 그대로):
    `INTERESTED, PLANNED, SUBMITTED, OFFERED, REJECTED, WITHDRAWN`.
  - `JobApplicationResponse`/`JobApplicationDetailResponse` 필드는
    FRONT-001 Task Technical Notes에 이미 정리되어 있음(그대로 신뢰
    가능, 이번 조사로 재확인 완료).
- **에러 body 포맷 미보장**: `application` 패키지 컨트롤러 테스트
  (`JobApplicationControllerTest`, `ApplicationStageControllerTest`)는
  `status().isConflict()`/`isNotFound()`/`isBadRequest()`만 검증하고
  응답 body 필드는 전혀 검증하지 않는다(코드 확인). 커스텀
  `@ControllerAdvice`도 이 패키지엔 없다. 즉 Spring Boot 4.1 기본
  에러 응답(정확한 스키마는 이 조사에서 실행 검증하지 못함 — 로컬
  backend가 이번 조사 시점에 기동되어 있지 않아 실제 HTTP 응답을
  직접 관찰하지 못했다)에 의존하지 말고 §4처럼 상태코드 우선 정책을
  쓴다. Codex 구현 중 실제로 backend를 띄워 에러 body를 관찰하게 되면
  그 사실을 결과 보고에 남겨달라(다음 Task들이 참고할 수 있도록).
- **삭제된 `JobApplication`이 참조하던 `JobPosting`에는 영향 없음** —
  FK는 `NO ACTION`(APPLICATION-001, ADR-0016)이라 `JobApplication`
  삭제는 `JobPosting`/`RecruitmentStep`/`Attachment`에 아무 영향이
  없다(단방향 삭제, cascade 없음 — 코드/migration 확인 완료).
- **Metrics**: 이번 Task는 `docs/METRICS.md`의 개발 프로세스 지표
  (`.ai/metrics/metrics.jsonl`, plan/implement/review/verify)만
  기록하면 된다. backend를 건드리지 않으므로 새 Product Metric
  (Micrometer)은 없다. 다만 이번 Task부터 "실제 쓰기가 가능한 제품"이
  되므로, `docs/METRICS.md`에 이미 정의된 제품 지표 중 쓰기 관련
  항목(있다면)이 이제부터 실측 가능해진다는 점만 인지하고 있을 것 —
  이번 Task 범위에서 새 지표를 추가하지는 않는다.
- **`window.confirm()` 대신 `<dialog>` 기반 `ConfirmDialog`를 택한
  이유**: `window.confirm()`은 스타일을 전혀 제어할 수 없어
  ADR-0038의 시각적 완성도 기준과 어긋나고, 브라우저별 문구/버튼
  배치가 제각각이라 제품 톤을 해친다는 사용자 피드백에 따라
  `<dialog>` 기반 공용 컴포넌트로 전환한다. `<dialog>`는 별도
  라이브러리 없이 포커스 트랩/top-layer 렌더링을 브라우저가 이미
  구현해 제공하므로(§9), `window.confirm()` 대비 구현 비용 증가는
  크지 않다.

## Test Plan

- `frontend/`에서 `npm run build` → `npm run lint`.
- fixture 모드(`API_BASE_URL` 미설정) `npm run dev`로 §6 데모 정책
  확인(모든 mutation 버튼이 안내 메시지만 보여주고 데이터 불변).
- 로컬 backend(`docker compose up -d` + `./gradlew bootRun`, **APPLICATION-003
  적용된 버전**)를 실제로 띄우고 `API_BASE_URL=http://localhost:8080`으로
  Acceptance Criteria의 E2E 시나리오(생성→같은 공고 재방문 시 사전 확인
  UI 전환 확인→상태변경(ConfirmDialog 포함)→새로고침 유지→메모/지원일
  수정→삭제(ConfirmDialog)→cleanup)를 실제로 수행한다. 이번 Task는 backend를
  실행하지 않고는 핵심 AC를 검증할 수 없으므로, backend 기동이
  불가능한 환경이면 그 사실과 이유를 결과 보고에 명시하고 fixture
  모드 검증 + 코드 리뷰로 대체한다(그 경우 status:passed로 자동
  전환하지 않고 사용자에게 명시적으로 보고).
- grep 기반 검증: 금지된 6개 endpoint 문자열 0건, client 파일에서
  backend URL 직접 fetch 0건, package.json dependencies 변화 없음.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | Server Action 인프라(ActionResult/ApiError/mutation helper), 최소 form primitive, 공용 ConfirmDialog, `/jobs/[id]` 사전확인 UX, `/applications/[id]` 상태/메모/지원일 수정+삭제 구현 | 16개 파일 신규/수정. Codex 샌드박스에서 Docker 소켓 접근 승인 거절 + Next 서버 포트 바인딩 EPERM으로 실제 backend E2E 미수행(코드 자체는 npm test 6/6, build/lint 통과 자체 보고). Claude가 직접 실제 backend+claude-in-chrome으로 전체 E2E(생성→사전확인 전환→상태변경→새로고침 유지→메모/지원일 저장→OFFERED confirm 5항목 전부 수동 확인→삭제→DB cleanup 확인) 및 fixture 데모 모드(불변+안내 배너) 수행. reviewer round1 PASS, 수정 요청 없음(비차단 발견 2건만 기록: 에러 body best-effort 노출 미구현이지만 더 안전한 선택, 상태변경 실패 시 ConfirmDialog가 delete와 다르게 throw 없이 닫힘 — 둘 다 AC 위반 아님). |
