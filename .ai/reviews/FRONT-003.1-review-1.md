---
task_id: FRONT-003.1
review_round: 1
reviewer: claude
reviewed_at: 2026-08-26T00:00:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

- [x] `CareerEditor.tsx`의 4개 폼(경험/자격증/학력/수상) 각각 controlled 전환 —
      충족. `frontend/src/components/CareerEditor.tsx`의 `ExperienceForm`
      (diff 57행), `CertificationForm`(58행), `EducationForm`(59행),
      `AwardForm`(60행) 전부 `useState({...})` + `change(name,value)` 공용
      핸들러로 전환됨. `grep -n defaultValue` 결과 대상 3개 파일 전부 0건.
      GPA/만점처럼 `number|null` 필드도 `item?.gpa?.toString()??""`로
      안전하게 문자열화(0 같은 falsy 값도 optional chaining이 null/undefined만
      단락시키므로 정상 처리). `<form action={...}>` 패턴, `name` 속성은
      그대로 유지되어 FormData 수집 경로는 변경 없음. 오케스트레이터의
      학력 폼 실측(GPA>만점 검증 실패 시 필드 전체 유지)과 코드 정합.
- [x] `StageEditor.tsx` 추가/수정 폼 2곳, `ApplicationEditor.tsx` 메모/지원일
      폼 2곳 controlled 전환 — 충족. `StageEditor.tsx:27-28`(수정 폼),
      `StageEditor.tsx:38-39`(추가 폼) 모두 `values`+`change`로 전환.
      `ApplicationEditor.tsx`는 `memo`/`appliedAt` 개별 `useState`로 전환
      (diff 10행). 오케스트레이터의 메모 2300자 서버 400 실측(필드 유지)과
      코드 정합.
- [x] 정상 저장 후 폼 닫힘 + 목록 최신화, 회귀 없음 — 충족(오케스트레이터
      실측: 학력 폼 정상 저장 → 폼 닫힘 → 목록에 최신 값 반영 확인).
      단, 아래 Findings 1번 참고(리오픈 시 동기화 관련 잠재 이슈,
      이번 AC 시나리오 자체는 위반하지 않음).
- [x] `/jobs/875` 콘솔 key 중복 경고 0건 — 충족(오케스트레이터
      `read_console_messages` 실측 확인). 코드상으로도
      `frontend/src/app/(app)/jobs/[id]/page.tsx`의 attachment/
      recruitmentSteps map 모두 index를 포함한 조합 key로 전환됨을 확인.
- [ ] **attachment key 유일성 근거를 diff에 주석/커밋 설명으로 남긴다 —
      미충족.** `jobs/[id]/page.tsx`는 여전히 완전히 한 줄로 압축된 파일이고,
      diff 어디에도 주석이 추가되지 않았다(`grep -c '//' "frontend/src/app/(app)/jobs/[id]/page.tsx"`
      = 0). 현재 이 변경은 아직 커밋되지 않은 working tree diff이므로
      커밋 설명도 존재하지 않는다(`git log` 최신 커밋은 `63e2320`이며 이
      diff와 무관). Task 명세가 "diff에 주석 또는 커밋 설명"을 명시적으로
      요구했는데 둘 다 없다.
- [x] attachment key 조합 정확도 — 충족.
      `` `${a.sortNo??"x"}-${a.fileName}-${a.url}-${i}` `` (jobs/[id]/page.tsx,
      diff 44행)로 명세와 정확히 일치. index만 단독 사용하지 않고 조합의
      일부로만 포함.
- [x] recruitmentSteps key 조합 정확도 — 충족.
      `` `${s.sortNo}-${s.stepGroupName}-${i}` ``로 명세와 정확히 일치
      (정렬된 배열에 `.map((s,i)=>...)`로 index 포함, `stepGroupName`은
      `??"x"` 없이 그대로 사용 — 명세가 optional 아니라고 명시한 부분과 일치).
- [x] `lib/actions/*.ts` 무수정 — 충족. 리뷰 대상 diff는 4개 파일
      (`CareerEditor.tsx`, `StageEditor.tsx`, `ApplicationEditor.tsx`,
      `jobs/[id]/page.tsx`)로만 정확히 scoped되어 있고, `git status --short`
      전체 결과에도 `lib/actions/` 하위 변경이 없음.
- [x] `npm run build`/`npm run lint` 통과 — 충족(독립 재실행 확인:
      `next build --webpack` 성공, `eslint .` 에러 0건 — 오케스트레이터
      보고와 별개로 리뷰어가 직접 재실행함).
- [x] `package.json` dependencies 무변경 — 충족(`git status --short`에
      `package.json` 자체가 목록에 없음, 즉 diff 없음).
- [x] backend 무수정 — 충족(`git status --short`에 `backend/` 관련 항목
      없음).

## 테스트 결과

- `npm run build` (webpack): 성공, TypeScript/정적 페이지 생성 에러 0건
  (리뷰어가 직접 재실행, 오케스트레이터 결과와 일치).
- `npm run lint`: 에러 0건(리뷰어가 직접 재실행).
- `npm test`(`node --test src/lib/format.test.ts src/lib/actions/result.test.ts
  src/lib/career-validation.test.ts`): test_count=8, test_pass_count=8,
  전부 통과. 단 이 3개 파일은 이번 diff와 무관한 순수 로직 유닛 테스트이고
  (`format.test.ts`/`career-validation.test.ts`/`result.test.ts`), 이 저장소에는
  React 컴포넌트 대상 자동 테스트 프레임워크 자체가 없다(package.json
  scripts에 컴포넌트 테스트 러너 없음, `*.test.*`/`*.spec.*` 검색 결과도 이
  3개뿐). 따라서 이번 Task의 핵심 변경(controlled input 전환, key 조합)에
  대한 자동 테스트는 존재하지 않으며, Task의 Test Plan 자체도 브라우저
  수동 QA를 요구하고 있어 이는 Task 설계상 의도된 것으로 보인다(신규
  실패 아님, 명시적 보고).

## Findings

1. **(경미, blocking 아님) `StageEditor.tsx`의 `values` state 위치가
   CareerEditor.tsx와 다른 패턴이라 "재오픈 시 최신 값으로 자연 재초기화"
   기대가 부분적으로 깨질 수 있다.** `StageEditor.tsx:27`의
   `useState({label:stage.label...})`은 `StageItem` 컴포넌트 최상단에
   있는데, `StageItem` 자체는 `editing` 토글에 따라 마운트/언마운트되지
   않는다(오직 내부 `<form>` JSX만 `{editing&&<form>...}`으로 토글됨,
   `StageEditor.tsx:31`). `StageItem`은 부모 `.map`에서 `key={stage.id}`로
   고정되므로(`StageEditor.tsx:41`) `router.refresh()`로 새 `stage` prop이
   내려와도 이미 마운트된 `StageItem`의 `values` state는 재초기화되지
   않는다. 마찬가지로 `StageEditor.tsx:38`의 추가 폼 `values`도
   `StageEditor` 컴포넌트 최상단에 있어 `adding` 토글로는 재초기화되지
   않는다. 이는 Task 명세 Scope A가 명시한 전제("폼이 조건부 렌더링으로
   매번 마운트/언마운트되는 기존 구조를 유지하면... 최신 item 값으로
   자연스럽게 재초기화된다")가 `CareerEditor.tsx`(폼 필드가 완전히 별도
   컴포넌트로 분리되어 `{editing&&body}`처럼 통째로 마운트/언마운트됨)에는
   그대로 성립하지만 `StageEditor.tsx`(state가 토글되지 않는 부모 레벨에
   있음)에는 그대로 성립하지 않는다는 뜻이다. 실질적 영향: (a) 수정 폼을
   열고 값을 바꾼 뒤 저장하지 않고 "수정 취소" 후 다시 "수정"을 누르면
   직전에 입력했던(저장되지 않은) 값이 초기 `stage` 값 대신 그대로
   남는다(원래 의도된 재초기화 대신 draft 보존), (b) 이 stage가 다른
   경로로 서버에서 갱신된 뒤 `router.refresh()`가 일어나도 로컬 `values`가
   새 `stage` prop과 동기화되지 않아, 수정 폼을 다시 열면 오래된 값이
   보일 수 있다. 이번 Task의 AC 체크리스트 문구 자체는(검증 실패 시 필드
   유지, 저장 성공 시 폼 닫힘+목록 갱신) 위반하지 않으므로 AC 판정 자체는
   충족으로 두었으나, Task 설계 근거와 명확히 어긋나는 실제 동작 차이이므로
   기록한다.
2. Codex가 `docs/DECISIONS.md`/`.ai/tasks/FRONT-002.md`를 수정하지
   않았는지 diff 범위를 넘어 확인했는데, 두 파일은 working tree에 이미
   수정된 상태로 있으나(architect 산물, 리뷰 지시에서 제외 대상으로 명시됨)
   Codex의 diff(4개 파일)에는 포함되어 있지 않다 — Codex가 범위를 벗어난
   수정을 하지 않았음을 확인.
3. `frontend/next-env.d.ts`가 working tree에 수정 상태로 있으나 이는
   `next build`/`next dev` 실행 시 자동 재생성되는 파일이고 diff 대상
   4개 파일에 포함되지 않아 이번 리뷰 범위 밖으로 판단(비고).

## 다음 액션

**NEEDS_REVISION** — Acceptance Criteria 10개 중 9개는 코드 검토와 재실행한
빌드/린트/유닛테스트로 충족을 확인했다. 다만 "새 attachment key 조합이
index 없이도 실질적으로 유일함을 보장하는 근거를 diff에 주석 또는 커밋
설명으로 남긴다"는 AC가 문자 그대로 미충족이므로(코드에 주석 없음, 아직
커밋 전이라 커밋 메시지도 없음) 다음 라운드에서 아래를 같은 Codex thread에
요청할 것을 권고한다.

### Codex에게 보낼 수정 요청

1. `frontend/src/app/(app)/jobs/[id]/page.tsx`에서 attachment/
   recruitmentSteps 목록 `key` 조합 바로 위(또는 파일이 한 줄로 압축된
   스타일이므로 return 문 시작 전 별도 줄)에 왜 `sortNo`+`fileName`+`url`
   (+index)/`sortNo`+`stepGroupName`(+index) 조합이 index 없이도 실질적으로
   유일한지 설명하는 짧은 주석을 추가해달라. 근거는 이미 Task 명세
   §"조사로 확인한 사실 2"에 있다 — `sortNo`는 ALIO가 주는 표시 순서
   힌트일 뿐 DB 유일성 제약이 없고(`uk_attachments_recrut_atch_file_no`는
   `recrutAtchFileNo`에만 걸림), 동일 `sortNo`를 가진 서로 다른 두
   attachment/step은 실제로 backend 테스트 fixture와 실측 DB 데이터로
   확인된 정상 상태다 — 라는 취지를 1~2줄로 남기면 된다. 파일의 기존
   압축 스타일을 깨지 않도록 파일 최상단 import 줄 아래에 한 줄
   `// ...` 형태로 추가하는 것을 제안한다(파일 전체 재포맷 금지, 이
   Task Technical Notes 이미 경고한 사항).
2. (선택, blocking 아님이지만 권고) `StageEditor.tsx`의 `values` state를
   `StageItem`/`StageEditor` 최상위 대신, 폼이 실제로 마운트/언마운트되는
   경계 안쪽(예: `CareerEditor.tsx`처럼 수정/추가 폼을 별도 하위 컴포넌트로
   분리해 `{editing&&<StageEditForm .../>}`/`{adding&&<StageAddForm .../>}`
   형태로 만들고 그 컴포넌트 내부에서 `useState` 초기화)으로 옮겨서, Task
   Scope A가 명시한 "폼을 닫았다 다시 열면 최신 값으로 자연 재초기화"
   전제가 `StageEditor.tsx`에서도 `CareerEditor.tsx`와 동일하게 성립하도록
   맞춰달라. AC 문구를 문자 그대로 위반하지는 않아 blocking으로 요구하진
   않지만, 다음 라운드에 함께 고치면 두 파일의 패턴 일관성과 잠재적 stale
   데이터 이슈를 동시에 해소할 수 있다.

수정 후에는 attachment key 주석 추가 여부(필수)와, 처리한다면 StageEditor
리팩터링 후에도 `npm run build`/`npm run lint`/`npm test`가 통과하는지만
재확인하면 된다 — 나머지 9개 AC는 이번 라운드에서 이미 충족을 확인했으므로
재검증 범위를 좁혀도 된다.
