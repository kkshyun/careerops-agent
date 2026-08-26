---
task_id: FRONT-002.1
review_round: 1
reviewer: claude
reviewed_at: 2026-08-26T00:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `npm run build`/`npm run lint`가 에러 없이 통과한다 — 오케스트레이터가
      직접 실행해 확인(성공/에러 없음). 리뷰어가 재실행하지 않음(중복
      불필요, 코드 자체에 타입 에러 소지 없음을 정독으로 확인).
- [x] `package.json` dependencies에 변화가 없다 — 오케스트레이터
      `git diff frontend/package.json` 확인 결과 변화 없음. 이번 diff에도
      `package.json` 변경 없음(대상 파일 목록에 없음).
- [x] 로컬 backend E2E 시나리오(생성/연속 생성/수정/삭제/cleanup) —
      오케스트레이터가 실제 backend+DB+브라우저로 전체 수행, `sort_order`
      1/2 자동 증가를 DB 직접 조회로 확인, cleanup까지 완료 보고. 코드
      상으로도 `createStage`가 `sortOrder`를 타입에 아예 갖지 않아(아래
      §2) 일관됨.
- [x] fixture 모드 데모 안내 — `application-stages.ts`의 `demo()`
      helper가 `createStage`/`updateStage`/`deleteStage` 셋 다 동일하게
      `if(!apiBaseUrl)return demo()`로 게이트(`application-stages.ts:13,18,23`).
      오케스트레이터는 생성 시도만 브라우저로 직접 확인했지만, 세 액션이
      완전히 동일한 분기 구조라 수정/삭제도 코드상 보장됨.
- [x] "등록된 전형 단계가 없습니다" 빈 상태 문구 유지 — `StageEditor.tsx:37`에
      기존 FRONT-001 문구 그대로 보존(`<p className={styles.muted}>등록된
      전형 단계가 없습니다.</p>`), 오케스트레이터가 `/applications/3`(빈
      상태)로 회귀 없음도 확인.
- [x] `stageType` 수정 UI 컨트롤 없음 — `StageItem`의 인라인 수정 폼
      (`StageEditor.tsx:29`)에 `stageType` select 없음(생성 폼에만
      존재, `StageEditor.tsx:37`). `updateStage`/`StageUpdateRequest`
      타입 시그니처 자체에 `stageType` 필드가 없음(런타임 무시가 아니라
      타입 정의부터 배제 — `application-stages.ts:17`,
      `lib/api/applications.ts`의 `StageUpdateRequest` 정의). grep
      `stageType` 결과도 `application-stages.ts`에서 `createStage`
      시그니처(1곳)에만 나타남.
- [x] Stage 삭제가 `ConfirmDialog` 재사용, `window.confirm()` 0건 —
      `StageEditor.tsx:6`에서 `@/components/ConfirmDialog` import(새
      컴포넌트 생성 안 함), grep 결과 `window.confirm/alert/prompt` 0건.
- [x] backend 코드 미수정 — working tree에 backend 파일 diff가 존재하나
      (`JobApplicationController/Repository/Service`에 `jobPostingId`
      쿼리 파라미터 추가), 이는 FRONT-002(이미 별도 리뷰 PASS, out of
      scope로 명시됨)가 만든 변경이며 FRONT-002.1의 diff 대상 파일
      목록(`application-stages.ts`, `lib/api/applications.ts`,
      `lib/types.ts`, `StageEditor.*`, `page.tsx`)과 무관하다. FRONT-002.1
      자체가 만든 코드는 backend를 전혀 건드리지 않는다.
- [x] 금지 endpoint 0건 — 오케스트레이터 grep 확인, 이번 라운드에서도
      `application-stages.ts`/`lib/api/applications.ts`에 정의된 endpoint는
      `POST/PATCH/DELETE /api/applications/{id}/stages[/{stageId}]`
      뿐으로 명세와 일치.

## 재확인 항목 (검토자 직접 확인)

1. **ActionResult/데모모드/revalidatePath 패턴 일치** — `application-stages.ts`가
   `lib/actions/applications.ts`(FRONT-002)와 완전히 동일한 구조
   (`"use server"`, `apiBaseUrl` 체크, `try/catch` + `actionError`,
   성공 시 `revalidatePath`)를 따른다. 세 함수 모두
   `revalidatePath(\`/applications/${applicationId}\`)` 호출 확인
   (`application-stages.ts:14,19,24`).
2. **`updateStage` 타입에 `stageType`/`sortOrder` 부재** — 위 AC 항목에서
   확인한 대로, 런타임 필터링이 아니라 TypeScript 타입 정의 자체에
   두 필드가 없음. `createStage` 쪽도 `sortOrder`를 아예 받지 않음
   (`application-stages.ts:12`, `lib/api/applications.ts`의
   `StageCreateRequest`).
3. **컴포넌트 재사용** — `StageEditor.tsx`가 `ConfirmDialog`, `form.tsx`
   (`Field/FormActions/Select/SubmitButton/Textarea/TextInput`),
   `ActionNotice`, `Badge`(`components/ui`)를 모두 import로 가져다 쓰고
   새 파일을 만들지 않음(`git status`에 이 컴포넌트들 관련 신규 파일
   없음).
4. **`lib/api/applications.ts` wrapper가 기존 client 재사용** —
   `createStageRequest`/`updateStageRequest`/`deleteStageRequest`가
   `client.ts`의 `postJson`/`patchJson`/`deleteRequest`를 그대로 호출
   (새 fetch 로직 없음).
5. **`lib/types.ts` 확장 충돌 없음** — 기존 `Stage` 타입의 inline union을
   `StageType`/`StageResult`로 이름만 추출해 재사용, 리터럴 값 동일
   (순서/철자 변화 없음), `ApplicationDetail = Application & {stages:
   Stage[]}` 등 기존 타입과 충돌 없음.
6. **CSS 톤 유지** — `StageEditor.module.css`는 `.timeline`/`.mono` 등
   기존 rail 스타일(App.module.css, 변경 없음)은 건드리지 않고
   `.stageHeader/.itemActions/.form/.addSection`만 추가, 기존
   `var(--border)`/`var(--background)` 토큰(globals.css) 재사용(신규
   스타일 시스템 도입 없음).

## 테스트 결과

- `npm run build` / `npm run lint` — 오케스트레이터 실행, 통과(에러 0건).
- `npm test` — 오케스트레이터 실행, 6/6 통과. 이번 Task가 새 단위
  테스트를 추가하지는 않았으나(Task Acceptance Criteria에 단위 테스트
  요구 없음), 기존 테스트 회귀 없음 확인됨.
- E2E(로컬 backend + DB + 브라우저) — 오케스트레이터가 생성 2건 연속
  (409 없음, sort_order 1/2 DB 확인) → 수정(`result=PASSED`, 새로고침 후
  유지) → 삭제(`ConfirmDialog` 확인) → cleanup(테스트 Application
  cascade 삭제, 기존 데이터 미영향) 전 과정 수행.
- fixture 모드 — 생성 시도 시 데모 배너 확인, 새로고침 없이도 fixture
  데이터 불변 확인.

## Findings

- 없음. 위반 사항이나 버그를 발견하지 못했다.
- (참고, 판정에 영향 없음) `/jobs/[id]` 첨부파일 key 중복 경고는
  FRONT-001부터 있던 pre-existing 이슈로 이번 Task와 무관.
- (참고, 판정에 영향 없음) working tree에 backend `jobPostingId` 쿼리
  파라미터 추가 diff가 있으나 이는 FRONT-002 범위(이미 리뷰 PASS)이고
  FRONT-002.1이 만든 변경이 아니므로 이번 판정에서 제외했다.

## 다음 액션

- PASS. Task 상태를 `passed`로 갱신하고 `.ai/metrics/metrics.jsonl`에
  기록할 것을 호출자(Claude)에게 안내.
