---
task_id: FRONT-003.1
review_round: 2
reviewer: claude
reviewed_at: 2026-08-26T00:00:00+09:00
verdict: PASS
---

## 범위

이번 라운드는 1차 리뷰(`FRONT-003.1-review-1.md`, NEEDS_REVISION)의 수정
요청 2건만 재검증한다. `git diff --stat -- "frontend/src/app/(app)/jobs/[id]/page.tsx" "frontend/src/app/(app)/applications/[id]/StageEditor.tsx"`
결과 이번 라운드가 실제로 건드린 파일은 이 2개뿐임을 확인했다(각각
+3/-2, +8/-4). 1차 리뷰에서 이미 PASS 판정한 `CareerEditor.tsx`,
`ApplicationEditor.tsx`는 이번 diff에 포함되지 않아 재검증하지 않았다
(전체 working tree diff에는 여전히 나타나지만 이번 라운드에서 수정되지
않았으므로 1차 리뷰 결론이 그대로 유효).

## Acceptance Criteria 체크 (1차 NEEDS_REVISION 사유였던 2건)

- [x] **코드 리뷰: attachment key 조합이 index 없이도 실질적으로 유일함을
      보장하는 근거가 diff에 주석으로 남는다 — 충족.**
      `frontend/src/app/(app)/jobs/[id]/page.tsx`에 `export default async
      function JobDetail` 바로 위 한 줄로
      `// ALIO sortNo is only a display-order hint; combining response
      identity fields keeps distinct steps/files unique, with index as a
      final fallback.`이 추가됨. Task 명세 "조사로 확인한 사실 2"의 핵심
      근거(sortNo는 ALIO가 주는 표시 순서 힌트일 뿐 DB 유일성 제약이 없고,
      identity 필드 조합이 실질적 유일성을 준다는 점, index는 최종
      안전망일 뿐이라는 점)를 정확히 담고 있다. 파일의 기존 압축
      (minified) 스타일을 깨지 않고 별도 한 줄로만 추가되어 함수 본문
      한 줄 압축은 그대로 유지된다(Technical Notes의 "관련 없는 줄까지
      스타일만 바꾸는 diff는 피한다" 경고와도 부합).
- [x] **(권고) `StageEditor.tsx` 폼 state를 마운트/언마운트 경계 안으로
      이동 — 충족, CareerEditor.tsx와 동등한 구조로 확인.**
      `StageEditor.tsx:22`에 `StageEditForm({stage,onSubmit})`,
      `StageEditor.tsx:24`에 `StageAddForm({onSubmit})`을 신설했고, 각각
      내부에서 `useState({label:stage.label??"", ...})` /
      `useState({stageType:"",label:"",...})`로 초기화한다. 호출부는
      `StageEditor.tsx:33`의 `{editing&&<StageEditForm stage={stage}
      onSubmit={submit}/>}`, `StageEditor.tsx:41`의 `{adding&&<StageAddForm
      onSubmit={submit}/>}`로, 토글이 꺼지면 컴포넌트 자체가 언마운트되고
      다시 켜지면 새로 마운트되며 그 시점의 최신 `stage` prop(또는 추가
      폼은 빈 값)으로 `useState`가 재초기화된다. `CareerEditor.tsx:15`의
      `ItemShell`이 `{editing&&body}`(body = `<ExperienceForm item={item}
      .../>` 등, 각 Form 컴포넌트 내부에 `useState` 보유)로 동일하게
      토글-마운트하는 패턴과 구조적으로 동등함을 코드로 직접 대조 확인했다.
      1차 리뷰 Findings 1번이 지적한 문제(State가 `StageItem`/
      `StageEditor` 최상위에 있어 폼 토글로는 재초기화되지 않던 것)가
      완전히 해소됐다 — draft 보존/stale 데이터 재현 시나리오 둘 다 더
      이상 성립하지 않는다.

## `<form action>` + `useFormStatus` 패턴 유지 확인

- `grep -n defaultValue "StageEditor.tsx"` 결과 0건 — 리팩터 이후에도
  uncontrolled 필드가 재도입되지 않았다.
- `<form action={onSubmit} ...>` 패턴이 `StageEditForm`/`StageAddForm`
  양쪽에 그대로 유지되고(`grep -c "action={onSubmit}\|action={submit}"` = 2),
  각 `<form>` 안에 `<FormActions><SubmitButton>...</SubmitButton></FormActions>`가
  중첩되어 있어 `useFormStatus()`(`components/form.tsx:3,12`)가 올바른
  form context를 계속 참조한다. `components/form.tsx` 자체는 diff에
  포함되지 않음(무변경).

## 테스트 결과

- `npm run build`(`next build --webpack`): 성공, TypeScript/정적 페이지
  생성 에러 0건(리뷰어 직접 재실행).
- `npm run lint`(`eslint .`): 에러 0건(리뷰어 직접 재실행).
- `npm test`(`node --test src/lib/format.test.ts
  src/lib/actions/result.test.ts src/lib/career-validation.test.ts`):
  test_count=8, test_pass_count=8, 전부 통과(리뷰어 직접 재실행). 1차
  리뷰와 동일하게, 이 3개 파일은 이번 diff와 무관한 순수 로직 테스트이고
  React 컴포넌트용 자동 테스트 프레임워크는 이 저장소에 없다(기존 사실,
  재확인만 함).
- `git diff --stat -- backend/ frontend/src/lib/actions/
  frontend/package.json`: 출력 없음 — 세 경로 모두 여전히 무변경 확인.

## Findings

없음. 1차 리뷰의 미충족 항목(필수 1건, 권고 1건) 모두 요청한 형태 그대로
해소됐고, 부작용이나 새로운 회귀도 발견하지 못했다.

## 다음 액션

**PASS.** FRONT-003.1의 남은 유일한 미충족 AC(주석)와 권고 사항
(StageEditor 리팩터)이 모두 해소되었고, 1차 리뷰에서 이미 PASS였던 나머지
8개 AC는 이번 라운드에서 변경되지 않은 파일들이므로 그대로 유효하다.
FRONT-003.1을 최종 PASS로 종결해도 된다. `metrics.jsonl`에 최종 상태 기록
필요(오케스트레이터 처리).
