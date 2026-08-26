---
task_id: FRONT-003.1
title: FRONT-002 UX polish — Server Action form 필드 reset 방지 + attachment 목록 key 중복 제거
phase: plan
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a03c16-08e1-7d91-be39-c83eef668706
---

## Context

FRONT-002/FRONT-002.1/FRONT-002.2 리뷰에서 발견되고 `.ai/tasks/FRONT-002.md`
"Follow-up / FRONT-003 후보"에 기록됐던 두 UX 결함 중, 사용자가 이번
Phase에서 우선 수정을 명시한 2건만 다룬다. 나머지 후보
(`appliedAt` null 복원, Stage drag/reorder)는 이번에도 Out of Scope로
유지한다(backend 제약/범위 확대 회피). bullets/tags 반복 입력 UI는
이 Task범위가 아니며, FRONT-003(AI Insight UI) 진행 중 실제 visual QA로
필요성만 재평가한다(별도 언급, 이 Task에서 구현하지 않음).

**반드시 먼저 읽을 것**: `docs/DECISIONS.md` ADR-0040 결정 6/7(이 Task
전체의 설계 근거), `.ai/reviews/FRONT-002.2-review-1.md`(원 가설이 처음
기록된 곳).

이 Task는 FRONT-003(AI Insight UI)과 독립적으로 병렬 진행 가능하다
(다른 컴포넌트를 주로 건드림 — 단 `jobs/[id]/page.tsx`의 attachment
목록 `key` 한 줄은 FRONT-003이 추가하는 4단계 컨테이너와 같은 파일 안에
있으므로, 두 Task 결과를 merge할 때 diff 충돌 여부를 확인한다).

## 조사로 확인한 사실 (추측 없음)

### 1. Form 필드 reset 원인

`frontend/src/components/CareerEditor.tsx`의 4개 폼
(`ExperienceForm`/`CertificationForm`/`EducationForm`/`AwardForm`)은
전부 `<form action={async d=>onSubmit(parse...(d))}>` 패턴이고, 각 입력은
`defaultValue={item?.field}`로 **uncontrolled**하게 렌더링된다.
`frontend/src/app/(app)/applications/[id]/StageEditor.tsx`의 추가/수정
폼, `ApplicationEditor.tsx`의 메모/지원일 폼도 동일 패턴이다.

React 19는 `action` prop에 함수가 전달된 `<form>`이 그 액션(함수)의
실행을 마치면(성공/실패 무관, 심지어 `CareerEditor`의 `submit`처럼
클라이언트 측 검증 실패로 실제 backend 호출 없이 조기 반환해도) **해당
form 안의 uncontrolled 필드를 자동으로 reset**한다 — 이는 controlled
input에는 영향이 없는, React가 문서화한 표준 동작이다(WebFetch로
React 팀 공식 설명 확인: "React 19 will automatically reset a form with
uncontrolled components after submission... This has no impact on
controlled form inputs... You can opt out by using an onSubmit or by
using controlled inputs"). `FRONT-002.2-review-1.md`가 이미 같은 가설을
코드 근거로 기록해뒀다(`CareerEditor.tsx:15-16`, `submit`이 검증 실패
시 Server Action 호출 전에 조기 반환하는데도 필드가 reset된다는 관찰).

### 2. attachment key 중복 원인

`frontend/src/app/(app)/jobs/[id]/page.tsx`는
`{j.attachments.map(a=><li key={a.sortNo}>...)}`로 렌더링한다.
`AttachmentResponse`(`backend/src/main/java/com/careerops/backend/job/dto/AttachmentResponse.java`)는
`sortNo`/`fileName`/`fileType`/`url` 4필드만 노출하고(JOB-003 결정 —
ALIO 자연키 `recrutAtchFileNo`나 엔티티 PK는 노출하지 않음), DB
유일성 제약(`uk_attachments_recrut_atch_file_no`)은 `recrutAtchFileNo`
(전역 UNIQUE)에만 걸려 있다 — `sortNo`(Integer, nullable)는
ALIO가 제공하는 "표시 순서" 힌트일 뿐 유일성이 전혀 보장되지 않는다.

**결정적 증거**: `backend/src/test/java/com/careerops/backend/job/JobPostingControllerTest.java`의
`getsRecruitmentStepsAndAttachmentsWithPublicFieldsInStableOrder`
테스트가 다음과 같이 **동일 `sortNo=1`을 가진 서로 다른 두
`Attachment`**를 의도적으로 fixture로 고정해 정렬 tie-break 동작을
검증한다(112~118행):
```java
attachmentRepository.saveAllAndFlush(java.util.List.of(
    new Attachment(saved, 8002L, 1, "두번째.pdf", "PDF", "https://example.com/second.pdf"),
    new Attachment(saved, 8001L, 1, "첫번째.hwp", "HWP", "https://example.com/first.hwp")
));
```
즉 동일 `sortNo`는 "버그로 인한 이례적 데이터"가 아니라 이 API 설계가
스스로 정상 상태로 인정하고 테스트로 고정해 둔 상태다. 오늘 시점
로컬 dev DB 전수 조회 결과 `attachments` 테이블에는 우연히 중복
`sortNo`가 0건이지만(데이터 스냅샷일 뿐, 언제든 재발 가능), 같은
설계를 공유하는 `recruitment_steps` 테이블에는 실제로 5,403개
`JobPosting`에서 동일 `(job_posting_id, sort_no)` 중복이 실측됐다 —
두 목록 모두 구조적으로 같은 위험을 가진다. 이 Task는 사용자가 지정한
attachment 목록만 고친다.

## Scope

### A. Form 필드 reset 방지 — controlled input 전환

**대상 파일**: `frontend/src/components/CareerEditor.tsx`(4개 폼 전부),
`frontend/src/app/(app)/applications/[id]/StageEditor.tsx`(추가/수정
폼 2곳), `frontend/src/app/(app)/applications/[id]/ApplicationEditor.tsx`
(메모/지원일 폼 2곳).

- 각 폼의 모든 필드를 `useState`로 관리하는 controlled input으로
  전환한다 — `defaultValue`를 제거하고 `value`+`onChange`로 교체한다.
  초기값은 기존과 동일하게 `item`(수정 폼) 또는 빈 값(추가 폼)에서
  가져온다. 폼이 조건부 렌더링(`editing`/`adding` 토글)으로 매번
  마운트/언마운트되는 기존 구조를 유지하면 성공적인 저장 후 폼이
  닫혔다가 다시 열릴 때 최신 `item` 값으로 자연스럽게 재초기화된다
  (별도 리셋 로직 불필요) — 이 마운트/언마운트 흐름 자체는 바꾸지
  않는다.
- `<form action={...}>` 패턴과 `SubmitButton`(`components/form.tsx`)의
  `useFormStatus()` pending 표시는 **그대로 유지**한다(ADR-0040 결정
  6 — controlled input은 action prop과 함께 써도 자동 reset 대상에서
  제외된다). `onSubmit`+`preventDefault`로 전환하지 않는다.
- `components/form.tsx`(`Field`/`TextInput`/`Textarea`/`Select`/
  `FormActions`/`SubmitButton`)는 이미 모든 props를 그대로 전달하는
  얇은 wrapper이므로 **수정할 필요가 없다** — `value`/`onChange`를
  그냥 추가로 넘기면 된다.
- 각 필드 상태를 개별 `useState`로 나열하지 않고, 폼 하나당 단일
  `useState<Record<string, string>>`(또는 폼 전용 타입)과 공용
  `handleChange(name, value)` 핸들러로 묶는 것을 권장한다(재량 — 목표는
  최소 diff와 가독성 유지이지 특정 패턴 강제가 아니다).
- 검증 실패 시 필드 값이 사라지지 않는다는 것 외에, 기존 동작(제출
  성공 시 폼 닫힘 + `router.refresh()`, `ActionResult` 기반 에러
  메시지 표시, `ConfirmDialog` 삭제 흐름)은 **전혀 변경하지 않는다**.

### B. attachment 목록 key 중복 제거

- `frontend/src/app/(app)/jobs/[id]/page.tsx`의
  `{j.attachments.map(a=><li key={a.sortNo}>...)}`를
  `sortNo`+`fileName`+`url` 조합(+ 배열 index를 마지막 안전망으로만
  추가)으로 바꾼다 — 예:
  `key={`${a.sortNo ?? "x"}-${a.fileName}-${a.url}-${i}`}`
  (index 단독 사용 금지, 조합의 일부로만 포함). `fileName`/`url`은
  같은 `sortNo`를 가진 서로 다른 첨부파일이라면 사실상 항상 달라지므로
  (같은 파일명+URL의 완전 중복 데이터가 있다면 그것은 실제로 같은
  파일이라 React key 관점에서 동일 취급해도 안전하다) 실질적 유일성을
  얻는다.
- **(범위 확장, 사용자 승인 완료)** `recruitmentSteps` 목록도 같은
  방식으로 수정한다. 실제로 로컬 backend(`jobPostingId=875`)로 확인한
  결과, 브라우저 콘솔 key 중복 경고의 실제 원인은 attachment가 아니라
  **recruitmentSteps**였다(전형 단계 3개가 전부 `sortNo=0`으로 동일).
  `frontend/src/app/(app)/jobs/[id]/page.tsx`의
  `{[...j.recruitmentSteps].sort(...).map(s=><li key={s.sortNo}>...)}`를
  attachment와 동일한 조합 패턴으로 바꾼다:
  `key={`${s.sortNo}-${s.stepGroupName}-${i}`}`(정렬된 배열의 index
  포함, `stepGroupName`은 optional이 아니므로 `??"x"` 불필요). FRONT-003
  §22("console major warning 0")를 충족하려면 이 목록도 함께 고쳐야
  한다는 사실이 실제 QA로 확인되어 사용자가 범위 확장을 승인했다.

## Out of Scope

- `appliedAt`을 명시적으로 `null`로 되돌리는 기능(backend API 제약,
  FRONT-002가 이미 Out of Scope로 남김 — 유지).
- `ApplicationStage` drag & drop 재정렬(FRONT-002 Out of Scope 유지).
- bullets/tags 반복 입력 UI 개선(이 Task 범위 아님 — FRONT-003 visual
  QA에서 별도로 필요성만 재평가).
- `recruitmentSteps` 목록의 동일한 key 위험(위 §B 참고, 범위 밖).
- backend 코드 변경, 새 npm dependency(react-hook-form 등 폼
  라이브러리 도입 금지 — controlled input 전환은 React 표준 기능만
  사용).
- MATCH-002/AGENT-001/AGENT-002 관련 작업(FRONT-003 담당).

## Acceptance Criteria

- [ ] `CareerEditor.tsx`의 4개 폼(경험/자격증/학력/수상) 각각에서: 값을
      입력하고 클라이언트 검증이 실패하도록 유도(예: 경험/자격증/학력
      폼은 종료일 < 시작일, 학력 폼은 GPA > 만점) → 제출 → 에러 메시지가
      보이고 방금 입력했던 **모든** 필드 값(여러 필드를 채운 상태 포함)이
      그대로 남아있다(수동 확인, 4개 폼 각 1회 이상).
- [ ] `StageEditor.tsx` 추가/수정 폼, `ApplicationEditor.tsx` 메모/지원일
      폼에서도 서버가 에러를 반환하는 입력(예: 메모 2000자 초과 —
      FRONT-002 AC가 이미 이 케이스로 서버 400 경로를 검증한 바 있음)
      제출 시 값이 유지된다(수동 확인 최소 1건씩).
- [ ] 정상 입력으로 성공적으로 저장한 뒤에는 폼이 기존과 동일하게
      닫히고 목록이 최신 값으로 갱신된다(회귀 없음, 수동 확인).
- [ ] `/jobs/875`(로컬 backend, attachment 3건 + recruitmentSteps 3건
      전부 sortNo=0으로 동일 — 실제 key 중복 재현 케이스)를 열었을 때
      브라우저 콘솔에 "두 자식이 동일한 key를 가짐(Encountered two
      children with the same key)" 경고가 attachment/recruitmentSteps
      어느 목록에서도 발생하지 않는다(수동 확인, `read_console_messages`
      결과 0건).
- [ ] 코드 리뷰: 새 attachment key 조합이 index 없이도 실질적으로
      유일함을 보장하는 근거가 diff에 주석 또는 커밋 설명으로 남는다.
- [ ] 이번 변경이 기존 Server Action 호출부(`updateExperience`/
      `createStage`/`updateApplicationMemo` 등, `lib/actions/*.ts`)를
      전혀 수정하지 않는다(diff 확인 — 이 Task는 `frontend/src/components/`,
      `frontend/src/app/(app)/applications/[id]/`,
      `frontend/src/app/(app)/jobs/[id]/page.tsx`만 건드린다).
- [ ] `npm run build`(`next build --webpack`)/`npm run lint`가
      `frontend/`에서 에러 없이 통과한다.
- [ ] `package.json`의 `dependencies`에 신규 항목이 추가되지 않는다
      (diff 확인).
- [ ] backend(`backend/` 하위) 코드가 한 글자도 수정되지 않는다(diff
      확인).

## Technical Notes

- **반드시 참고**: `docs/DECISIONS.md` ADR-0040 결정 6/7,
  `.ai/reviews/FRONT-002.2-review-1.md`(원 가설 최초 기록).
- React 19 공식 동작 확인 출처(WebFetch로 조사): 자동 reset은
  uncontrolled 필드에만 적용되고 controlled input에는 영향이 없다 —
  이 사실이 이번 Task의 유일한 기술적 근거다. 다른 우회(예: 매 실패마다
  `key`를 바꿔 강제 remount)는 오히려 사용자가 입력한 값을 잃게 만들어
  원래 목적과 반대로 작동하므로 채택하지 않는다(ADR-0040 기각 대안
  참고).
- `CareerEditor.tsx`/`StageEditor.tsx`/`ApplicationEditor.tsx`는 현재
  한 줄로 압축된(minified) 스타일로 작성되어 있다(기존 코드베이스
  컨벤션 — Codex가 생성한 기존 스타일). 이번 수정도 기존 파일의 코드
  스타일(포맷팅 밀도)을 크게 벗어나지 않게 유지할 것을 권장하되,
  가독성이 심각하게 나빠지면 일반적인 포맷팅으로 바꿔도 무방하다(재량,
  단 diff가 이 Task의 목적과 무관한 전체 재포맷을 유발하지 않도록
  주의 — 관련 없는 줄까지 스타일만 바꾸는 diff는 피한다).
- **Metrics**: 순수 UX 버그 수정이라 신규 Product Metric 없음.
  `.ai/metrics/metrics.jsonl`에 개발 프로세스 지표만 기록.

## Test Plan

- `frontend/`에서 `npm run build` → `npm run lint`.
- browser 수동 QA: `CareerEditor` 4개 폼 + `StageEditor` 2개 폼 +
  `ApplicationEditor` 2개 폼에서 위 Acceptance Criteria 시나리오를
  각각 재현하고 필드 값 유지 여부를 확인.
- 로컬 backend로 첨부파일 2개 이상인 실제 공고(`/jobs/{id}`)를 열어
  브라우저 개발자 도구 콘솔에 key 중복 경고가 없는지 확인.
- grep: `package.json` dependencies 변화 없음, `lib/actions/*.ts` 무변경.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | CareerEditor/StageEditor/ApplicationEditor controlled input 전환, attachment key 조합 수정 | 4개 파일 수정. Claude 실측: 메모 2000자 초과 서버400 후 필드 유지, 학력 GPA검증 실패 후 필드 유지+정상저장 회귀없음, `/jobs/875` 콘솔 key 경고 재확인 결과 attachment 아닌 recruitmentSteps가 원인임을 발견 → 사용자 승인 받아 범위 확장(같은 라운드에 추가 요청). reviewer round1 NEEDS_REVISION: attachment/recruitmentSteps key 유일성 근거 주석 누락(필수), StageEditor state가 마운트/언마운트 경계 밖에 있어 폼 재오픈시 재초기화 안 됨(권고). |
| 2 | 주석 추가 + StageEditor state를 하위 컴포넌트로 이동 | `jobs/[id]/page.tsx`에 유일성 근거 주석 1줄 추가, `StageEditor.tsx`를 `StageEditForm`/`StageAddForm`으로 분리해 state를 마운트 경계 안으로 이동. reviewer round2 PASS(빌드/린트/테스트 직접 재실행 포함), 최종 종결. |
