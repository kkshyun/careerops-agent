---
task_id: FRONT-002.2
review_round: 1
reviewer: claude
reviewed_at: 2026-08-26T12:00:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `npm run build`/`npm run lint`가 에러 없이 통과한다. — 독립 재실행 확인.
  `npm run build` → `Compiled successfully`, `npm run lint` → 에러 없음
  (경고 0건).
- [x] `package.json` dependencies에 변화가 없다. — `git diff frontend/package.json`
  재확인, `dependencies`/`devDependencies` 블록 변경 없음. `scripts.test`에
  `src/lib/actions/result.test.ts src/lib/career-validation.test.ts`만 추가.
- [x] 로컬 backend 기동 상태에서 4개 리소스 생성→조회→수정→삭제 E2E,
  cleanup 완료 — 오케스트레이터가 실제 브라우저+backend+DB로 4건(Experience
  id 9, Certification id 14, Education id 5, Award id 4) 전부 수행,
  `GET .../id`로 필드(특히 Experience의 bullets/tags 파싱 결과, Education의
  gpa/gpaScale) 반영 확인, 삭제 후 404로 cleanup 확인. 기존 실 PKB 데이터
  (FinSight 등) 미변경. 코드로도 `lib/actions/career.ts`가
  `revalidatePath("/career")`를 성공 시마다 호출함을 확인(`career.ts:4`).
- [x] Education `gpa`만 입력하고 `gpaScale` 비우면 검증 동작 — 클라이언트
  경로는 오케스트레이터가 브라우저로 확인(`GPA와 만점을 모두
  입력해주세요.` 메시지, `frontend/src/lib/career-validation.ts:2`
  `validateGpaPair`). **서버 400 경로는 이번 리뷰에서 내가 직접 curl로
  클라이언트를 완전히 우회해 재확인**:
  `POST /api/career/educations {"institution":"...","gpa":3.5}` →
  `HTTP 400`(생성 안 됨). `lib/actions/result.ts`의 `errorMessage(400)`이
  "입력값을 확인해주세요."로 매핑하는 것도 코드 확인(재사용, 신규 아님).
  두 경로 모두 실제로 트리거되어 AC의 "최소 하나는 실제로 확인" 요건을
  충족.
- [x] 날짜 순서 검증(Experience/Certification/Education) — 오케스트레이터가
  Experience/Certification을 브라우저로 확인. **Certification 서버 400
  경로도 내가 curl로 직접 재확인**: `POST /api/career/certifications
  {"name":"...","acquiredDate":"2026-08-20","expirationDate":"2026-08-01"}`
  → `HTTP 400`. `validateDateOrder`(`career-validation.ts:1`)가 Experience/
  Certification/Education 3곳 모두에서 동일하게 재사용됨을 코드로 확인
  (`lib/actions/career.ts:5-7`, `components/CareerEditor.tsx:8,15,16`).
- [x] fixture 모드에서 4개 탭 모두 데모 안내, 데이터 변경 없음 — 오케스트레이터가
  Experience 탭에서 실측(삭제 시도 → 안내 노출, 항목 유지). 코드 확인
  결과 4개 리소스가 전부 `mutate()`/`remove()`(`career.ts:4`)라는 동일
  함수를 거치고, 그 함수가 `!apiBaseUrl` 체크로 바로 `demo()`를 반환하므로
  4개 리소스 간 동작이 대칭적임을 코드로 뒷받침. 1회 실측 + 대칭적 코드
  구조로 충족 판단.
- [x] `CareerExperience` 생성/수정에 `sourceType` 선택 UI 없음 — `ExperienceInput`
  타입(`lib/api/career.ts:9`), `parseExperience`(`CareerEditor.tsx:5`),
  `createExperienceRequest`/`updateExperienceRequest`(`lib/api/career.ts:14`)
  어디에도 `sourceType` 필드 없음. grep 재확인(`sourceType` 0건 in
  `career.ts`/`career.ts`(actions)/`CareerEditor.tsx`).
- [x] backend 코드가 한 글자도 수정되지 않는다 — `git diff --stat -- backend/`
  결과 `JobApplication*` 관련 5개 파일이 걸리지만, 이는 FRONT-002.1(이미
  PASS된 별개 Task)이 남긴 uncommitted 변경으로 Career 도메인과 무관하고
  이번 리뷰 스코프 밖으로 지정됨. FRONT-002.2가 다룬 `career` 패키지
  backend 코드는 diff에 전혀 등장하지 않음 — 이번 Task 기준으로는 충족.
- [x] 금지 endpoint 호출 코드 0건 — `recommendations|generate|evidence|kakao|notify`
  grep 0건(`career.ts`/`career.ts`(actions)/`CareerEditor.tsx`). 오케스트레이터의
  6개 endpoint grep과 결과 일치.
- [x] 4개 리소스 삭제 모두 `ConfirmDialog` 재사용, `window.confirm()` 없음 —
  `CareerEditor.tsx:15`의 `ItemShell`에서 `<ConfirmDialog ref={dialog}
  title="삭제하시겠습니까?" onConfirm={remove}/>` 1곳만 렌더링되고 4개
  `kind` 전부가 같은 `ItemShell`을 거침(과장된 경고 문구 없음, §5 근거
  충족). `window\.(confirm|alert|prompt)` grep 0건 재확인.

## 테스트 결과

- `npm test` (node:test) → **8/8 pass** (`format.test.ts` 4 + `result.test.ts`
  2 + `career-validation.test.ts` 2). `career-validation.test.ts`가
  `validateDateOrder`/`validateGpaPair`의 정상/역전/부분입력/경계값
  케이스를 커버함을 코드로 확인(`0/0`, `4.6>4.5`, `gpa`만 입력 등).
- `npm run build` → 성공(webpack, TypeScript 통과).
- `npm run lint` → 에러 없음.
- 실 backend 대상 curl 2건(GPA 단독 입력, 날짜 역전) 직접 실행 → 둘 다
  `HTTP 400`, 리소스 미생성 확인(위 참고).
- 오케스트레이터의 브라우저 E2E(4리소스 생성/수정/삭제/cleanup, fixture
  삭제 안내)는 별도로 수행됨 — 이번 리뷰에서 재현하지 않고 보고 내용과
  코드 정합성만 대조.

## Findings

낮은 심각도, PASS 판정에 영향 없음. 후속 개선 후보로 기록할 가치가 있다고
판단한 항목만 남긴다.

1. **클라이언트 검증 실패 시 폼 필드 초기화 (재현 확인됨, 비차단)**
   `CareerEditor.tsx`의 각 `*Form`은 `<form action={async d=>onSubmit(...)}>`
   패턴을 쓰고(`ExperienceForm` 등, `CareerEditor.tsx:10`), `onSubmit`(=
   `submit`, `CareerEditor.tsx:15-16`)이 클라이언트 검증에 걸리면
   `setResult(errorResult(validation));return` 으로 Server Action 호출 전에
   조기 반환한다 — 즉 필드 리셋의 원인은 이 컴포넌트의 로직이 아니라
   React 19가 `action` prop에 함수를 넘긴 `<form>`을 액션 디스패치 완료
   후(성공/실패 무관) 기본적으로 리셋하는 표준 동작으로 보인다(코드
   근거로 확인 가능한 범위에서는 이 컴포넌트가 별도로 reset을 호출하는
   부분이 없음). Task AC는 "검증 동작 여부"만 요구하고 "필드 값 보존"은
   요구하지 않으므로 AC 위반은 아니다. 다만 Experience 폼처럼 필드가
   많고 `bullets`/`tags`처럼 재입력 비용이 큰 textarea가 있는 폼에서는
   실사용 시 불편할 수 있어, 후속 Task(폼 상태를 `useState`로 controlled
   전환하거나 에러 시 `defaultValue`를 마지막 입력값으로 유지하는 방식
   검토)로 기록할 가치가 있다.
2. **Server Action 내부의 이중 클라이언트 검증 (설계상 의도적, 비결함)**
   `lib/actions/career.ts`의 `mutate()`(`career.ts:4`)는 `validation`
   파라미터가 있으면 실제 backend 호출 전에 `invalid(message)`를 즉시
   반환한다(`createExperience`/`createCertification`/`createEducation`
   모두 `validateDateOrder`/`validateGpaPair`를 다시 계산해 넘김,
   `career.ts:5-7`). `CareerEditor.tsx`의 브라우저 컴포넌트가 이미 동일
   순수 함수로 선검증하므로, 정상적인 UI 플로우에서는 이 두 번째 체크가
   항상 무효(pass-through)이고, 실제 backend의 400 응답 경로는 이 앱의
   UI를 통해서는 사실상 도달 불가능하다(내가 이번 리뷰에서 curl로 직접
   확인한 것도 이 때문 — 브라우저로는 재현이 어려움). 이는 "불필요한
   서버 왕복을 줄인다"는 Task §4 의도에 부합하는 합리적 선택이라 결함으로
   보지 않지만, 향후 backend 규칙이 바뀌었을 때 이 프론트 중복 로직이
   조용히 어긋날 수 있다는 점은 인지해둘 가치가 있다(문서화 정도로 충분,
   코드 변경 요청 아님).
3. `frontend/src/lib/types.ts`의 `Stage`/`StageResult` 관련 diff는 이번
   FRONT-002.2 변경 파일 목록에 포함돼 있었으나 실제로는 FRONT-002.1이
   만든 `StageEditor.tsx`/`application-stages.ts`/`api/applications.ts`
   에서만 쓰이고 `career.ts`/`CareerEditor.tsx`에서는 전혀 참조되지 않음
   (grep 확인). FRONT-002.2 산출물과는 무관한 uncommitted 잔여분으로
   보이며, 이번 판정에는 영향 없음.

## 다음 액션

- **PASS.** Acceptance Criteria 11개 전부 충족(코드 확인 + 오케스트레이터
  브라우저 E2E + 이번 리뷰의 독립 재실행/재현 조합). 완료 처리하고
  `.ai/metrics/metrics.jsonl`에 최종 상태 기록 권장.
- 위 Findings 1, 2는 blocking이 아니므로 이번 라운드에서 Codex에게 수정
  요청하지 않는다. 필요하면 별도 후속 Task(예: "career 폼 UX 개선")로
  분리해 백로그에 남기는 것을 권장.
