---
task_id: FRONT-002.1
title: ApplicationStage CRUD — /applications/[id] 전형 타임라인 생성/수정/삭제
phase: plan
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a03bcd-b74c-74d1-9099-76fbad696dc3
---

## Context

**선행 Task: FRONT-002 (필수)** — Server Action 인프라(`lib/actions/`,
`lib/api/*.ts` mutation 헬퍼, `components/form.tsx` primitive,
`ActionResult` 타입, 데모 모드 정책, `components/ConfirmDialog.tsx`
기반 삭제 확인)와 `JobApplication` 생성/상태변경/삭제를 FRONT-002가
먼저 구축한다. 이 Task는 그 인프라를 그대로 재사용해
`/applications/[id]`에 이미 읽기 전용으로 표시되고 있는 전형 타임라인
(`ApplicationStage`)에 생성/수정/삭제를 연결한다.

**선행 조건**: FRONT-002가 `status:passed`로 완료된 뒤 시작한다
(`lib/actions/types.ts`의 `ActionResult`, `components/form.tsx`의
Field/FieldError/TextInput/Textarea/Select/FormActions/SubmitButton,
`components/ConfirmDialog.tsx`, `lib/api/client.ts`의
`postJson`/`patchJson`/`deleteRequest`/`ApiError`를 그대로 가져다 쓴다 —
새로 만들지 않는다). FRONT-002 → FRONT-002.1 → FRONT-002.2는 병렬이
아니라 **순차** 진행이 기본값이다(같은 인프라를 뒤 Task들이 재사용하기
때문).

## Scope

### 1. `lib/actions/application-stages.ts` (`"use server"`)

- `createStage(applicationId: string, input: {stageType: StageType; label?: string; scheduledAt?: string; memo?: string})`
  → `ApplicationStageCreateRequest`로 매핑해
  `POST /api/applications/{applicationId}/stages` 호출. **`sortOrder`는
  절대 폼에서 입력받지 않고 항상 생략한다** — backend가 생략 시
  `findTopByJobApplicationIdOrderBySortOrderDesc().sortOrder + 1`(없으면
  1)로 자동 할당한다(코드 확인, `ApplicationStageService.create()`).
  이렇게 하면 `UNIQUE(job_application_id, sort_order)` 409 충돌이
  생성 경로에서는 발생하지 않는다. `result`도 생략해 backend 기본값
  `PENDING`에 위임한다.
- `updateStage(applicationId: string, stageId: string, input: {label?: string; scheduledAt?: string | null; result?: StageResult; memo?: string})`
  → `ApplicationStageUpdateRequest`(코드 확인:
  `label/sortOrder/scheduledAt/result/memo` 전부 optional, null/미포함
  시 무변경). **`stageType`은 수정 대상에 포함하지 않는다**(backend
  `ApplicationStageUpdateRequest`에 애초에 `stageType` 필드가 없음 —
  생성 시점에만 결정되고 이후 변경 불가, 코드 확인 완료). **`sortOrder`도
  이 Task의 수정 폼에는 노출하지 않는다**(재정렬/순서 변경 UI는 Out of
  Scope — 아래 근거).
  `PATCH /api/applications/{applicationId}/stages/{stageId}` 호출.
- `deleteStage(applicationId: string, stageId: string)` →
  `DELETE /api/applications/{applicationId}/stages/{stageId}`, 204.
- 셋 다 `ActionResult<T>` 반환, 데모 모드 정책(FRONT-002 §6)을 동일하게
  따른다, 성공 시 `revalidatePath(`/applications/${applicationId}`)`
  호출.

### 2. `lib/api/applications.ts`에 stage mutation 헬퍼 추가

`createStageRequest`/`updateStageRequest`/`deleteStageRequest` 등
얇은 wrapper(FRONT-002 §2와 동일 패턴, 네이밍 재량).

### 3. UX — `/applications/[id]` 전형 타임라인 확장

현재 `stages`는 `sortOrder ASC` 고정 정렬 `<ol className={styles.timeline}>`로
읽기 전용 렌더링되어 있다(코드 확인:
`frontend/src/app/(app)/applications/[id]/page.tsx`). 이 Task는 같은
`.timeline` 시각 스타일(rail dot 등, ADR-0038 톤)을 유지한 채 아래를
추가한다:

- **Stage 추가**: 타임라인 하단(또는 상단)에 "전형 단계 추가" 폼(접혀
  있다가 버튼으로 펼치는 방식 — `useState` client 토글, `ExperienceCard`의
  펼치기 패턴 재사용). 필드: `stageType`(`<select>`, 실제 6개 값
  `DOCUMENT`(서류)/`CODING_TEST`(코딩테스트)/`WRITTEN`(필기)/
  `INTERVIEW`(면접)/`FINAL`(최종)/`OTHER`(기타), 기존
  `stageTypeLabel` 매핑 재사용, 필수), `label`(텍스트, 선택,
  max 100자), `scheduledAt`(`<input type="datetime-local">`, 선택),
  `memo`(`<textarea>`, 선택, max 1000자).
- **Stage 수정**: 각 타임라인 항목에 "수정" 토글(client `useState`)로
  같은 필드 구성(단 `stageType` 제외, §1 근거)의 인라인 폼으로
  전환. `result`(`<select>`, 실제 4개 값
  `PENDING`(대기)/`PASSED`(합격)/`FAILED`(불합격)/`CANCELLED`(취소),
  기존 `stageResultLabel` 매핑 재사용)도 이 폼에 포함.
- **Stage 삭제**: 각 항목에 삭제 버튼. FRONT-002가 만든 공용
  `components/ConfirmDialog.tsx`를 그대로 import해 재사용한다(새
  confirm 컴포넌트를 이 Task에서 만들지 않는다 — 컴포넌트 설계/props는
  `.ai/tasks/FRONT-002.md` §9 참고). 삭제 버튼 `onClick`에서
  `confirmRef.current?.open()`을 호출하고, `title`은 "이 전형 단계를
  삭제할까요?" 같은 문맥에 맞는 문구만 새로 정한다.
- 위 세 동작 모두 성공 시 같은 페이지가 새 데이터로 갱신되어야
  한다(`revalidatePath` + Next.js가 이미 no-store로 재조회하므로 별도
  client 상태 동기화 불필요).

## Out of Scope

- **Stage 재정렬(드래그 앤 드롭 또는 순서 이동 버튼)** — backend가
  `sortOrder` 변경 API 자체는 제공하지만(`PATCH`의 `sortOrder` 필드),
  두 stage의 순서를 "맞바꾸는" 것은 프론트가 두 번의 PATCH를
  원자적이지 않게 순차 호출해야 하고 중간에 실패하면
  `UNIQUE(job_application_id, sort_order)` 위반으로 어중간한 상태가
  남을 수 있다. 이번 Task는 생성 시 항상 맨 뒤에 추가되는 것만
  지원하고, 재정렬은 UX/에러 복구 설계가 더 필요한 별도 Task 후보로
  남긴다.
- `stageType` 변경 — backend API가 애초에 지원하지 않음(위 §1).
- `JobApplication` 자체의 상태/메모/삭제 — FRONT-002 범위(이미 완료).
- 새 backend endpoint, 새 npm dependency, 새 modal/toast 라이브러리.

## Acceptance Criteria

- [ ] `npm run build`/`npm run lint`가 에러 없이 통과한다.
- [ ] `package.json` dependencies에 변화가 없다(diff 확인).
- [ ] 로컬 backend 기동 상태에서, "FRONT-002.1 E2E TEST"라는 식별
      가능한 라벨/메모를 붙인 테스트용 `JobApplication` 1건(또는
      FRONT-002 E2E로 만든 지원 재사용)에 대해:
      - Stage 생성(`stageType=INTERVIEW`, `label="1차 면접"`) →
        `POST .../stages`가 201을 받고 타임라인 맨 뒤에 즉시(같은
        새로고침 후) 나타난다. `sortOrder`는 UI에서 입력받지 않았음에도
        backend가 자동 할당한 값이 응답에 포함된다.
      - 같은 지원에 두 번째 Stage를 연속 생성해도 `sortOrder` 충돌
        (409)이 발생하지 않는다(자동 증가 검증).
      - 방금 만든 Stage를 수정(`result=PASSED`, `memo` 추가)하면
        새로고침 후에도 유지된다.
      - Stage 삭제 → `ConfirmDialog` 오픈 → 확인 후 `DELETE`가 204를
        받고 타임라인에서 사라진다.
      - 위 테스트로 만든 Stage/Application을 전부 cleanup한다(기존
        실제 데이터 미변경, 결과 보고에 정리 완료를 명시).
- [ ] fixture 모드에서 Stage 추가/수정/삭제 버튼이 데이터 변경 없이
      "데모 데이터에서는 저장되지 않습니다" 안내를 보여준다.
- [ ] `/applications/[id]`의 "전형 단계가 없습니다" 빈 상태 문구(기존
      FRONT-001 구현)는 그대로 유지되며, Stage를 하나도 추가하지 않은
      지원 상세는 이 Task 이후에도 동일하게 보인다(회귀 없음).
- [ ] `stageType`을 수정하는 UI 컨트롤이 존재하지 않는다(코드 확인 —
      backend가 지원하지 않으므로).
- [ ] Stage 삭제가 `components/ConfirmDialog.tsx`를 재사용한다(코드
      확인 — 이 Task가 새 confirm 컴포넌트를 만들지 않음). `frontend/src/`
      어디에도 `window.confirm()` 호출이 없다(grep 확인).
- [ ] backend 코드가 한 글자도 수정되지 않는다.
- [ ] 금지 endpoint(FRONT-001/FRONT-002와 동일 6개) 호출 코드 0건.

## Technical Notes

- **API 계약(코드 확인, 추측 없음)**:
  - `POST /api/applications/{applicationId}/stages` — Request
    `ApplicationStageCreateRequest{stageType: StageType(NotNull),
    label: String?, sortOrder: Integer?, scheduledAt: LocalDateTime?,
    result: StageResult?, memo: String?}`. 201 +
    `ApplicationStageResponse{id, stageType, label, sortOrder,
    scheduledAt, result, memo, createdAt, updatedAt}`. 404(부모
    application 없음), 409(`sortOrder` 명시 충돌 시에만 — 이 Task는
    항상 생략하므로 실질적으로 발생하지 않음).
  - `PATCH /api/applications/{applicationId}/stages/{id}` — Request
    `ApplicationStageUpdateRequest{label: String?, sortOrder:
    Integer?, scheduledAt: LocalDateTime?, result: StageResult?, memo:
    String?}`(`stageType` 없음). 200 + `ApplicationStageResponse`.
    404.
  - `DELETE /api/applications/{applicationId}/stages/{id}` — 204.
    404.
  - `StageType` enum 6값: `DOCUMENT, CODING_TEST, WRITTEN, INTERVIEW,
    FINAL, OTHER`. `StageResult` enum 4값(기본값 `PENDING`): `PENDING,
    PASSED, FAILED, CANCELLED`.
  - `sortOrder`는 `Integer`, 생성 시 생략하면
    `findTopByJobApplicationIdOrderBySortOrderDesc()+1`(없으면 1),
    `UNIQUE(job_application_id, sort_order)` DB 제약(코드/migration
    확인: `V6__create_application_stages_table.sql`).
  - 부모 `JobApplication` 삭제 시 `ApplicationStage`는 `ON DELETE
    CASCADE`로 함께 삭제된다(ADR-0017, 코드 확인) — 이 Task 구현과
    직접 관련은 없지만 cleanup 시 참고(Application을 지우면 Stage도
    자동 정리됨).
- **에러 body 정책**: FRONT-002 §4와 동일(상태코드 우선, body 스키마
  미보장).
- 폼 primitive/`ActionResult`/데모 모드 배너 스타일/`ConfirmDialog`는
  FRONT-002가 만든 것을 import해서 쓴다 — 이 Task에서 새로 만들지
  않는다(코드 리뷰 포인트). `ConfirmDialog`의 props/동작(포커스 트랩,
  Escape, backdrop, 포커스 복귀)은 `.ai/tasks/FRONT-002.md` §9에 상세
  기술되어 있다 — 이 문서에서 다시 설명하지 않는다.

## Test Plan

- `npm run build` → `npm run lint`.
- fixture 모드 수동 확인(데모 배너, 데이터 불변).
- 로컬 backend 기동 후 위 Acceptance Criteria E2E 시나리오 수행,
  cleanup 확인. backend 기동 불가 시 그 사실을 명시하고 코드 리뷰로
  대체(FRONT-002와 동일 원칙).
- grep 기반 검증: 금지 endpoint 0건, `stageType` PATCH 필드 미노출.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | Stage 생성/수정/삭제 Server Action + `/applications/[id]` 타임라인 확장(추가 폼/인라인 수정/ConfirmDialog 삭제), FRONT-002 인프라 재사용 | 6개 파일 신규/수정. Docker 소켓 승인 거절로 실 backend E2E 미수행(build/lint/test 6/6 자체 보고). Claude가 실제 backend+DB+claude-in-chrome으로 전체 E2E(테스트 Application 생성→Stage 2건 연속 생성 409없음→DB sort_order 1/2 자동증가 확인→수정(PASSED) 유지→삭제 confirm→cleanup) 및 fixture 데모 모드(불변+안내) 수행. reviewer round1 PASS, 수정 요청 없음(stageType/sortOrder가 updateStage 타입 시그니처 자체에 없음을 코드로 확인). |
