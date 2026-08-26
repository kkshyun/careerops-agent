---
task_id: FRONT-002.2
title: Career(경험/자격증/학력/수상) 4개 섹션 CRUD — /career
phase: plan
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-26T00:00:00+09:00
codex_thread_id: 01a03bdc-0903-7310-8d76-8a171e47c673
---

## Context

**선행 Task: FRONT-002 (필수)** — Server Action 인프라와 form
primitive(`lib/actions/types.ts`, `components/form.tsx`,
`lib/api/client.ts`의 `postJson`/`patchJson`/`deleteRequest`, 데모 모드
정책, `components/ConfirmDialog.tsx` 기반 삭제 확인)를 FRONT-002가
먼저 구축한다. 이 Task는 그 인프라를 재사용해 `/career`(경험/자격증/
학력/수상 4개 탭)에 생성/수정/삭제를 연결한다.

**선행 조건**: FRONT-002가 `status:passed`로 완료된 뒤 시작한다.
FRONT-002.1(ApplicationStage)과는 서로 다른 페이지(`/applications/[id]`
vs `/career`)를 다루므로 데이터 의존성은 없으나, 같은 인프라를
재사용하는 동일 계보의 Task이고 순서는 **FRONT-002 → FRONT-002.1 →
FRONT-002.2 순차 진행이 기본값**이다(병렬 진행하지 않는다).

PKB(Personal Knowledge Base)는 자기소개서 파이프라인(AGENT-001/002)의
근거(Evidence) 원천이다 — `AGENTS.md`의 "AI가 사용자가 하지 않은
경험/수치를 만들어내지 못하게 막는다"는 제약은 이 데이터의 **입력**을
사용자가 직접 통제해야 한다는 뜻이기도 하다. 이 Task로 처음 만드는
CRUD가 그 입력 경로가 된다.

## Scope

### 1. `lib/actions/career.ts` (`"use server"`)

4개 리소스 각각에 `create*`/`update*`/`delete*` 3개씩, 총 12개 Server
Action(또는 리소스 타입을 파라미터로 받는 소수의 제네릭 함수로 묶는
것도 허용 — 단, 4개 리소스의 필드 shape가 서로 달라 과도한 추상화가
오히려 가독성을 해칠 수 있으므로 **리소스별 함수 4×3 = 12개를
기본안으로 제안**한다. Codex가 공통화가 실제로 더 단순하다고 판단하면
재량으로 조정 가능, 단 Repository/Adapter 같은 새 계층은 만들지
않는다):

- **CareerExperience**: `createExperience`, `updateExperience`,
  `deleteExperience`. `bullets`/`tags`는 아래 §3에서 별도 논의.
- **Certification**: `createCertification`, `updateCertification`,
  `deleteCertification`.
- **Education**: `createEducation`, `updateEducation`,
  `deleteEducation`. `gpa`/`gpaScale` 쌍 검증(§4).
- **Award**: `createAward`, `updateAward`, `deleteAward`.

모두 `ActionResult<T>` 반환, 데모 모드 정책 동일 적용, 성공 시
`revalidatePath("/career")` 호출(4개 탭이 같은 경로의 `?tab=` query만
다르므로 경로 하나로 충분 — `revalidatePath`는 query string과 무관하게
해당 path segment를 무효화한다).

### 2. `lib/api/career.ts`에 mutation 헬퍼 추가

`createExperienceRequest`/... 등 12개(또는 그 이하로 공통화한) 얇은
wrapper. FRONT-002 §2와 동일 패턴.

### 3. UX — `/career` 4개 탭 공통 구조

- 각 탭 상단에 "추가" 버튼(펼침/접힘 토글, `ExperienceCard` 패턴
  재사용) → 해당 리소스 생성 폼.
- 각 카드(기존 `.document`/`ExperienceCard` 스타일 유지)에 "수정"
  토글 → 인라인 편집 폼(생성 폼과 필드 구성 동일, 초기값만 채워짐).
- 각 카드에 "삭제" 버튼 → `ConfirmDialog`(FRONT-002 §9 재사용) →
  확인 시 목록에서 즉시 제거(재조회로 반영).
- 4개 탭 모두 같은 `.documentList`/`.document`/`Form.module.css` 톤을
  유지한다 — 탭마다 다른 디자인 언어를 쓰지 않는다.

### 4. 리소스별 필드/검증 (코드 확인, 추측 없음)

- **CareerExperience** (`POST/PATCH /api/career/experiences`):
  - `type`(`<select>`, 실제 5개 값 `PROJECT`/`ACTIVITY`/`WORK`/
    `RESEARCH`/`OTHER`, 생성 시 필수), `title`(필수, max 200),
    `organization`(선택, max 200), `role`(선택, max 200),
    `startDate`/`endDate`(둘 다 선택, `<input type="date">`,
    **`endDate < startDate`면 backend가 400을 반환**하므로 — 코드
    확인: `CareerExperienceService.validateDates()` — 프론트도
    동일 규칙을 폼 제출 전 클라이언트에서 먼저 검증해 불필요한 서버
    왕복을 줄인다(단순 `if` 비교, 별도 라이브러리 없음)), `summary`
    (선택, max 500, `<textarea>`), `detail`(선택, max 4000,
    `<textarea>`).
  - `bullets`(선택, `{bulletType: CONTEXT|ACTION|RESULT|OTHER,
    content}[]`)와 `tags`(선택, `string[]`, 각 max 50자)는 **PATCH 시
    필드가 존재하면 기존 것을 전부 지우고 새로 저장**하는 전량 교체
    시맨틱이다(코드 확인:
    `CareerExperienceService.update()`가 `bulletRepository.deleteByCareerExperienceId()`
    후 재삽입, `tags`도 동일 패턴, **필드 자체를 생략하면(null) 무변경**).
    이번 Task는 bullets/tags를 **줄바꿈으로 구분한 하나의 textarea**
    (한 줄 = bullet 하나, `bulletType`은 첫 단어 접두어(`CONTEXT:`/
    `ACTION:`/`RESULT:` 없으면 `OTHER`) 같은 간단한 파싱 정도로
    최소 구현한다 — 별도 "bullet 추가" 반복 입력 UI(dynamic form
    array)는 과함(Out of Scope). tags는 콤마 구분 텍스트 입력 1개.
  - **`sourceType`은 항상 프론트에서 건드리지 않는다** — Server
    Action은 항상 backend 기본값(`MANUAL`)에 위임하는 2-argument
    `create()` 오버로드를 그대로 타는 요청 body만 보낸다(코드 확인:
    `CareerExperienceController.create()`는 3-argument
    `create(request, sourceType, sourceImportCandidateId)` 오버로드를
    호출하지 않고 `service.create(request)`만 호출 — 즉 API로 들어오는
    모든 생성은 이미 항상 `MANUAL`이다, PKB import 파이프라인(`IMPORT`
    소스)은 `pkbimport` 패키지의 별도 내부 경로를 쓰고 이 공개 API를
    거치지 않는다).
- **Certification** (`POST/PATCH /api/career/certifications`):
  `name`(필수, max 200), `issuer`(선택, max 200), `acquiredDate`/
  `expirationDate`(둘 다 선택, `expirationDate < acquiredDate`면 400
  — 클라이언트 사전 검증 동일 적용), `credentialId`(선택, max 100),
  `description`(선택, max 2000).
- **Education** (`POST/PATCH /api/career/educations`):
  `institution`(필수, max 200), `major`(선택, max 200), `degree`
  (`<select>`, 선택, 실제 6개 값 `HIGH_SCHOOL`/`ASSOCIATE`/`BACHELOR`/
  `MASTER`/`DOCTORATE`/`OTHER`), `status`(`<select>`, 선택, 실제 5개
  값 `ENROLLED`/`ON_LEAVE`/`GRADUATED`/`EXPECTED_GRADUATION`/
  `WITHDRAWN`), `startDate`/`endDate`(선택, 날짜 순서 검증 동일),
  `gpa`/`gpaScale`(둘 다 선택이지만 **하나만 채우면 backend가 400을
  반환**한다 — 코드 확인: `EducationService.validateGpa()`,
  "`(gpa == null) != (gpaScale == null)`이면 400", 추가로 `gpa < 0`
  이거나 `gpaScale <= 0`이거나 `gpa > gpaScale`이면도 400. 프론트는
  두 입력을 시각적으로 한 쌍(같은 줄)으로 묶어 "GPA"/"만점" 라벨을
  붙이고, 클라이언트에서도 동일 규칙(둘 다 비었거나 둘 다 채워짐,
  0 ≤ gpa ≤ gpaScale, gpaScale > 0)을 제출 전 검증한다), `description`
  (선택, max 2000).
- **Award** (`POST/PATCH /api/career/awards`): `title`(필수, max
  200), `issuer`(선택, max 200), `awardedDate`(선택), `description`
  (선택, max 2000). 날짜 순서 검증 대상 필드가 하나뿐이라 해당 없음.

### 5. 삭제 확인

FRONT-002가 만든 공용 `components/ConfirmDialog.tsx`를 그대로
import해 재사용한다(새 confirm 컴포넌트를 이 Task에서 만들지 않는다 —
컴포넌트 설계/props/동작은 `.ai/tasks/FRONT-002.md` §9 참고). 4개
리소스 모두 동일 컴포넌트를 쓰되 `title`/`confirmLabel`만 리소스별로
바꾼다("이 경험을 삭제하시겠습니까? 연결된 자기소개서 근거가 있다면
함께 확인하세요" 같은 확장된 경고 문구는 **이번 Task 범위에서는
추가하지 않는다** — `CareerExperience` 삭제가 다른 도메인(자기소개서
Evidence Sheet 등)에 미치는 영향을 이 Task가 조사하지 않았으므로,
과장된 경고 문구로 사용자를 혼란시키지 않고 "삭제하시겠습니까?"
수준의 일반 문구만 쓴다).

## Out of Scope

- `bullets`/`tags`의 정교한 반복 입력 UI(항목 추가/삭제/순서 변경
  개별 컨트롤) — 이번 Task는 §4의 텍스트 파싱 방식으로 최소 구현.
  정교한 UI가 필요하면 후속 Task.
- PKB Import 파이프라인(`pkbimport` 패키지, 문서 업로드→구조화 추출)
  연동 — 이 Task는 수동 CRUD만 다룬다. 기존 Import 플로우와는 무관.
- `sourceType=IMPORT`로 생성된 항목의 origin 표시(예: "가져온 경험"
  배지) — API 응답에 `sourceType` 필드가 없어(코드 확인:
  `CareerExperienceResponse`/`CareerExperienceDetailResponse`에
  `sourceType` 미노출) 프론트가 구분할 수 없다. 필요하면 backend
  응답 DTO 확장이 선행되어야 하는 별도 Task.
- 새 backend endpoint, 새 npm dependency, 새 modal/toast/rich-text
  editor 라이브러리.
- `CareerExperience`/`Certification`/`Education`/`Award` 삭제가
  자기소개서 파이프라인(AGENT-001/002)에 미치는 영향 안내 — 위 §5
  근거.

## Acceptance Criteria

- [ ] `npm run build`/`npm run lint`가 에러 없이 통과한다.
- [ ] `package.json` dependencies에 변화가 없다.
- [ ] 로컬 backend 기동 상태에서, 4개 리소스 각각에 대해 제목/이름
      필드에 **"FRONT-002.2 E2E TEST"**를 포함시켜 생성한 뒤:
      - 생성 직후 `/career?tab=<해당탭>`에 즉시 나타난다(새로고침
        기준).
      - 상세 필드(예: Experience의 `bullets`/`tags`, Education의
        `gpa`/`gpaScale`)를 포함해 수정하면 반영된다.
      - 삭제하면 목록에서 사라진다.
      - 4건 전부 cleanup 완료(기존 실제 PKB 데이터 미변경, 결과
        보고에 명시).
- [ ] Education 생성/수정 시 `gpa`만 입력하고 `gpaScale`을 비우면
      제출이 클라이언트 단계에서 막히거나(권장) 서버 400을 받아
      "입력값을 확인해주세요" 류 메시지가 보인다(둘 중 최소 하나는
      실제로 확인 — 클라이언트 검증을 구현했다면 서버 400 경로는
      의도적으로 우회 후 실제로 최소 1회 트리거해 서버 검증도
      동작함을 확인).
- [ ] `startDate`/`endDate`가 있는 리소스(Experience/Certification의
      acquired·expiration/Education)에서 종료일을 시작일보다 이르게
      입력하면 동일하게 사전/서버 검증이 동작한다.
- [ ] fixture 모드에서 4개 탭 모두 추가/수정/삭제 버튼이 데이터 변경
      없이 데모 안내를 보여준다.
- [ ] `CareerExperience` 생성/수정 시 `sourceType`을 사용자가 선택할
      수 있는 UI 컨트롤이 존재하지 않는다(항상 `MANUAL` 고정, 코드
      확인).
- [ ] backend 코드가 한 글자도 수정되지 않는다.
- [ ] 금지 endpoint(FRONT-001/FRONT-002와 동일) 호출 코드 0건.
- [ ] 4개 리소스 삭제가 모두 `components/ConfirmDialog.tsx`를
      재사용한다(코드 확인 — 이 Task가 새 confirm 컴포넌트를 만들지
      않음). `frontend/src/` 어디에도 `window.confirm()` 호출이 없다
      (grep 확인).

## Technical Notes

- **API 계약 4종 요약(코드 확인, 추측 없음)** — 본문 §4에 상세 기재.
  공통 패턴: `POST`(201)/`GET`(pagination)/`GET/{id}`(Certification/
  Education/Award는 list와 detail 응답이 동일 shape, Experience만
  list(`CareerExperienceResponse`)와 detail(`...DetailResponse`,
  `bullets`/`tags`/`detail` 포함)이 다름 — FRONT-001이 이미 이 차이를
  반영해 목록 조회 후 각 id에 대해 detail을 병렬로 미리 가져오는
  구조이므로, 수정 폼도 이미 화면에 있는 detail 데이터를 그대로
  초기값으로 쓸 수 있다)/`PATCH/{id}`(부분 수정)/`DELETE/{id}`(204).
- **에러 body 정책**: FRONT-002 §4와 동일. `career` 패키지에도
  `@ControllerAdvice` 없음(코드 확인).
- **날짜/GPA 클라이언트 사전 검증**: `AGENTS.md`의 "과도한 Result
  framework 금지" 원칙에 따라 별도 validation 라이브러리(zod 등)를
  도입하지 않는다. 순수 함수 2~3개(`validateDateOrder`,
  `validateGpaPair`) 정도로 `lib/` 또는 `components/form.tsx`에
  추가하고, 가능하면 이 순수 함수에 대해 FRONT-001/FRONT-002가 쓴
  `node:test` 패턴으로 최소 케이스를 검증한다(신규 dependency 없음).
- 폼 primitive/`ActionResult`/데모 배너/`ConfirmDialog`는 FRONT-002
  산출물을 import한다 — 이 Task에서 새로 만들지 않는다. `ConfirmDialog`의
  props/동작은 `.ai/tasks/FRONT-002.md` §9에 상세 기술되어 있다.

## Test Plan

- `npm run build` → `npm run lint`.
- fixture 모드 수동 확인.
- 로컬 backend 기동 후 4개 리소스 각각 생성→조회→수정→삭제 E2E,
  cleanup 확인. backend 기동 불가 시 사실 명시 후 코드 리뷰로 대체.
- `node:test`로 날짜순서/GPA 쌍 검증 순수 함수 케이스 실행.
- grep 기반 검증: 금지 endpoint 0건, `sourceType` 선택 UI 0건.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | Career 4개 리소스(경험/자격증/학력/수상) CRUD, `kind` 파라미터 공통 컴포넌트, bullets/tags 파싱, 날짜순서/GPA 쌍 클라이언트 검증 | 8개 파일 신규/수정. Docker 소켓 승인 거절로 실 backend E2E 미수행(build/lint/test 8/8 자체 보고). Claude가 실제 backend+claude-in-chrome으로 4개 리소스 각각 생성(날짜/GPA 검증 실패 케이스 먼저 확인 후 정상 생성)→수정→삭제(ConfirmDialog, Experience는 UI, 나머지는 API)→cleanup, fixture 데모 모드 확인. reviewer round1 PASS(curl로 서버 400 우회 경로도 재확인), 수정 요청 없음(비차단 발견 2건: 클라이언트 검증 실패 시 React 19 기본 동작으로 폼 필드 리셋 - 후속 개선 후보, mutate() 내 이중 client validation으로 실제 서버 400 경로 도달 어려움 - 리스크 인지). |
