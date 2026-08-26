---
task_id: FRONT-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-26T00:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

(Task 명세 `.ai/tasks/FRONT-002.md` §Acceptance Criteria, 순서대로)

- [x] `npm run build`(`next build --webpack`)/`npm run lint` 에러 없이 통과 — 오케스트레이터가 직접 실행해 확인(빌드/린트 성공). 리뷰어는 소스 레벨로 타입/구조 문제 없음을 재확인.
- [x] `package.json` dependencies 신규 항목 없음 — `git diff -- frontend/package.json` 확인 결과 `test` 스크립트만 확장(`src/lib/actions/result.test.ts` 추가), `dependencies` 블록 변경 없음.
- [x] 로컬 backend E2E 전체 시나리오(생성→사전확인 UI 전환→상태변경(SUBMITTED)→OFFERED confirm→메모 저장→지원일 저장→삭제→cleanup) — 오케스트레이터가 실제 로컬 backend + 브라우저로 전 과정 수동 수행, DB로 삭제까지 확인, 기존 실 데이터 미훼손 확인(신규 공고 875로만 테스트).
- [x] `ConfirmDialog` 5개 동작(초기 포커스 취소 버튼, Tab 트랩, Escape 닫힘, backdrop 클릭 닫힘, 포커스 복귀) — 오케스트레이터가 5가지 모두 수동 확인 완료. 리뷰어가 `ConfirmDialog.tsx`/`.module.css` 코드를 직접 읽고 설계(§9)와 1:1로 일치함을 확인:
  - `forwardRef<ConfirmDialogHandle,...>`, `open()`에서 `restoreFocusRef.current=document.activeElement` 후 `showModal()` (`ConfirmDialog.tsx:13`)
  - 취소 버튼 `autoFocus` (`ConfirmDialog.tsx:15`)
  - backdrop: `onClick={event=>{if(event.target===dialogRef.current)...close()}}` (`ConfirmDialog.tsx:15`)
  - `onClose={()=>restoreFocusRef.current?.focus()}` (`ConfirmDialog.tsx:15`)
  - `cancel` 이벤트 `preventDefault()` 없음(Escape 네이티브 동작에 위임) — 코드에 없음, 의도대로.
  - `.confirm{border:1px solid var(--danger);background:var(--danger);color:white}` 만 danger, `.cancel`은 `--border`/`--accent` 톤(`ConfirmDialog.module.css:1`) — 확인 버튼만 danger 강조.
  - `isPending` 동안 두 버튼 모두 `disabled`, 확인 버튼 라벨 `"처리 중…"`.
- [x] `window.confirm/alert/prompt` 0건 — `grep -rn "window\.(confirm|alert|prompt)" src/` 결과 0건.
- [x] 데모 모드에서 mutation 시도 시 fixture 불변 + 중립 톤 안내 — `lib/fixtures/data.ts` diff 없음(git status/diff 확인, 전혀 수정 안 됨), 각 Server Action이 `apiBaseUrl` 없으면 `demo()` 즉시 반환(`lib/actions/applications.ts:11,14,19,24,29,34`), `ActionNotice.module.css`가 기본(demo 포함) `--warning` 보더/`--text-secondary` 톤, `kind==="error"`일 때만 `--danger`로 전환 — 에러와 시각적으로 구분됨. 오케스트레이터가 fixture 모드 수동 확인도 완료(`app-01` 상태 불변 + 배너 확인).
- [x] 금지 6개 endpoint(semantic-match/agent-analysis/application-draft/jobs recommendations POST/notifications job-recommendations POST prepare/send POST) 0건 — grep 결과 유일하게 걸리는 것은 `lib/api/notifications.ts`의 기존 GET `/api/notifications/job-recommendations` 목록 조회(FRONT-001부터 존재, 금지 대상인 POST prepare/send와 다름, 이번 diff로 신규 추가되지 않음).
- [x] client 컴포넌트의 backend URL 직접 fetch 0건 — `grep -rln '"use client"' src/ | xargs grep -ln "localhost:8080\|API_BASE_URL"` 결과 0건.
- [x] 새 modal/toast/date-picker 라이브러리 미추가 — `<dialog>` 네이티브 엘리먼트만 사용, `<input type="date">` 네이티브 사용, package.json 변화 없음.
- [x] `/`, `/dashboard`, `/jobs`(목록), `/career`, `/notifications` 미변경 — `git status --short frontend/src/app/` 결과 변경/신규 파일이 `applications/[id]/*`, `jobs/[id]/ApplicationCreateButton.tsx`뿐임을 확인. 목록 페이지 `/applications`(page.tsx)도 미변경(Out of Scope "목록 인라인 상태변경 없음"과 일치).
- [x] `backend/` 코드 한 글자도 미수정 — `git diff -- frontend/`로 범위를 좁혀 확인했고, `backend/` 하위 변경분은 별도 Task(APPLICATION-003)의 working tree 변경으로 이번 FRONT-002 diff(frontend/만)와 경로가 겹치지 않음(frontend 쪽 diff에 backend 파일 없음).
- [x] `ActionResult` 판별/상태코드→메시지 매핑 순수 함수 `node:test` 최소 1~2케이스 — `frontend/src/lib/actions/result.test.ts`에 `describe`/`it` 2개 블록(각각 다중 assert)로 `errorMessage`/`actionError` 검증. `npm test` 전체 6/6 통과(오케스트레이터 확인).

## 코드 레벨 상세 검증 (오케스트레이터 요청 10개 항목)

1. `ConfirmDialog.tsx` — 위 AC 체크 항목에서 라인별로 검증, §9 설계와 완전히 일치.
2. `ActionResult<T>` — `lib/actions/types.ts`가 §5 타입 정의와 문자 그대로 동일. `applications.ts`의 5개 함수(`createApplication`/`updateApplicationStatus`/`updateApplicationMemo`/`updateApplicationAppliedAt`/`deleteApplication`) 모두 내부에서 `try/catch`로 감싸 `actionError()`를 반환하거나 데모 분기로 조기 반환 — 어디에도 `throw`가 바깥으로 새어나가지 않음(단, `deleteApplication`의 `redirect()` 호출은 Next.js 내부 특수 예외이며 try/catch 바깥에 배치되어 있어 올바름, `applications.ts:37`).
3. 에러 처리 정책 — `result.ts`의 `errorMessage(status, conflictMessage)`가 400/404/409(+conflictMessage)/그외에 대해 §4의 고정 한국어 문구와 문자 그대로 일치. **관찰(경미, 비차단)**: §4는 "body가 있으면 message/detail/error 필드 중 존재하는 것을 best-effort로 노출"하라는 조항도 포함하는데, 현재 구현은 `ApiError.body`를 캡처만 하고(`client.ts:7,12`) `errorMessage`/`actionError`에서 실제로 파싱/노출하지 않는다. 다만 Technical Notes가 "에러 body 스키마 보장 안 됨"이라 명시하고, AC의 실제 테스트 문구("400 → 입력값을 확인해주세요")도 고정 메시지와 정확히 일치하므로, 이 구현이 실질적으로 더 안전한 선택이며 AC를 위반하지 않는다. 판정에 영향 없음, 후속 참고 사항으로만 기록.
4. 데모 모드 정책 — `lib/fixtures/data.ts` diff/status 확인 결과 **완전히 미수정**(git status에 해당 경로 없음). `API_BASE_URL` 미설정 시 5개 Server Action 모두 네트워크/fixture 배열 접근 없이 즉시 `{ok:false,kind:"demo",message:DEMO_MESSAGE}` 반환. `DEMO_MESSAGE` 문구가 명세 §6과 동일.
5. `client.ts`의 `postJson`/`patchJson`/`deleteRequest` — 셋 다(`requestJson` 경유 포함) `apiBaseUrl` 없으면 `throw new Error("API_BASE_URL is not configured")`로 즉시 오용을 드러냄(`client.ts:17,36`), §2와 일치.
6. `getApplications()`의 `jobPostingId` 필터 — fixture 모드에서 `if(q.jobPostingId)list=list.filter(a=>a.jobPostingId===q.jobPostingId)`(`applications.ts:2`)로 §7이 지시한 것과 동일한 패턴(기존 `status` 필터와 나란히).
7. `revalidatePath` — 상태/메모/지원일 3개 update 함수 모두 `revalidatePath(/applications/{id})` + `revalidatePath("/applications")` 호출, `deleteApplication`은 `revalidatePath("/applications")`만 호출 — 리뷰 요청에 적힌 경로와 정확히 일치. `createApplication`은 revalidatePath를 호출하지 않지만, 생성 후 클라이언트에서 `router.push(/applications/{id})`로 완전 네비게이션하므로 캐시 무효화가 필요 없는 설계이며 Task 요청 목록에도 create는 포함되어 있지 않음(§1은 "성공 시 관련 경로에 revalidatePath" 원칙만 언급, create의 정확한 대상 경로는 지정하지 않음 — 실사용(§7 AC 시나리오)에서도 문제 없이 동작 확인됨).
8. `backend/` 미수정 — `frontend/`로 범위를 좁힌 `git diff`/`git status`에 `backend/` 경로가 전혀 등장하지 않음. `backend/` 자체의 변경분은 이미 완료·리뷰된 별도 Task(APPLICATION-003)의 working tree 변경으로 이번 리뷰 대상 아님.
9. `package.json` diff — dependencies 블록 변경 없음, `test` 스크립트에 `src/lib/actions/result.test.ts` 추가된 것이 유일한 변경.
10. Acceptance Criteria 전체 대조 — 위 표 참고, 전부 충족.

## 발견 사항(비차단, 후속 참고용)

- **`/jobs/[id]` React key 중복 경고**(`key={a.sortNo}`, `page.tsx`) — 오케스트레이터가 사전 확인한 대로 FRONT-001부터 존재하던 pre-existing 코드이며 이번 diff가 해당 줄을 건드리지 않았음(`git diff` 확인). 이번 Task 범위 밖, 판정에 영향 없음. 다음 Task 후보로 기록할 가치 있음.
- **§4 "body 기반 best-effort 메시지" 미구현** — 위 항목 3 참고. Technical Notes의 근거(에러 body 스키마 미보장)와 AC의 실제 테스트 문구를 볼 때 현재 구현(고정 상태코드 메시지만)이 더 안전한 선택으로 판단되며, 비차단 관찰 사항으로만 기록.
- **`ConfirmDialog`의 상태변경(OFFERED/REJECTED/WITHDRAWN) confirm에서, `updateApplicationStatus`가 실패(`ok:false`)해도 `applyStatus`가 throw하지 않고 정상 resolve하므로 다이얼로그가 자동으로 닫힌다** — `deleteApplication`을 감싸는 `remove()`는 실패 시 명시적으로 `throw`해 다이얼로그를 유지시키는 반면(§9 예시와 정확히 일치), 상태변경 쪽은 실패해도 조용히 닫히고 `ActionNotice`로만 에러가 표시된다. §9는 이 throw-on-failure 패턴을 delete 예시로만 명시했고 status 변경까지 강제하지는 않아 스펙 위반은 아니며(구현 재량), AC도 confirm 실패 시나리오는 요구하지 않는다. 다만 일관성 측면에서 상태변경 실패 시에도 다이얼로그를 유지하도록 `applyStatus` 실패 시 reject하게 바꾸면 UX가 더 일관돼 보인다는 점을 개선 후보로 남긴다(비차단).

## 테스트 결과

- `npm test`(node:test): 6/6 통과 — `format.test.ts` 4개(기존) + `actions/result.test.ts` 2개(신규, 이번 Task). 오케스트레이터가 직접 실행해 확인, 리뷰어가 테스트 소스(`result.test.ts`)를 읽고 검증 대상이 §4/§5 정책과 일치함을 확인.
- `npm run build`(`next build --webpack`)/`npm run lint`: 오케스트레이터가 직접 실행해 성공 확인. 리뷰어는 소스 레벨 재검증(타입 사용, import 경로, 컴포넌트 시그니처)으로 추가 문제 없음을 확인.
- 실 로컬 backend E2E: 오케스트레이터가 브라우저(claude-in-chrome)로 전체 시나리오(생성/사전확인 UI 전환/SUBMITTED 변경/OFFERED confirm 5항목/메모/지원일/삭제/cleanup/fixture 데모 모드)를 수동 수행, 결과 모두 명세와 일치. 리뷰어는 이를 코드 레벨로 교차검증(§9 설계 1:1 대응 등)해 신뢰성을 보강.

## 다음 액션

- **PASS.** 모든 Acceptance Criteria 충족, 테스트 통과(자동 6/6 + 수동 E2E 전체), 원칙 위반(Secret 노출, fixture 가짜 저장, 불필요한 추상화/신규 dependency, 근거 없는 자기소개서 생성 로직 등) 없음.
- 위 "발견 사항" 3건은 모두 비차단이며 이번 라운드에서 Codex에 수정 요청을 보낼 필요 없음. 원한다면 후속 Task(FRONT-002.1/FRONT-002.2 착수 전 또는 별도 정리 Task)에서 다음을 고려:
  1. `/jobs/[id]` 첨부파일 목록의 `key={a.sortNo}` 중복 수정(FRONT-001부터의 pre-existing 이슈).
  2. 상태변경 confirm 실패 시에도 다이얼로그를 유지하도록 `applyStatus` 실패 경로에서 reject하게 조정(선택 사항).
- `.ai/metrics/metrics.jsonl`에 review round 1 PASS로 기록 권장(오케스트레이터가 진행).
