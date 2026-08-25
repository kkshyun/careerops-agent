---
task_id: FRONT-000
review_round: 1
reviewer: claude
reviewed_at: 2026-08-25T23:50:00+09:00
verdict: NEEDS_REVISION
---

## Round 2 (2026-08-26T00:15:00+09:00, reviewer: claude)

Codex가 같은 thread에서 `frontend/README.md`에 "Vercel 배포" 섹션을 추가.
직접 파일 열람으로 확인 — Framework Preset/Root Directory/Build
Command/Output Directory/Install Command/Environment Variables
(`NEXT_PUBLIC_API_BASE_URL`) 6개 항목 모두 정확히 기재됨
(`frontend/README.md:18-28`). Codex 재보고 기준 `npm run build`/
`npm run lint` 재실행도 통과. README만 변경되었고 다른 파일 diff는 없음
(`git status --short`로 확인, `frontend/`는 여전히 untracked 신규
디렉토리 전체, `backend/`는 여전히 무수정).

**최종 판정: PASS.** Acceptance Criteria 10개 전항목 충족.

## Acceptance Criteria 체크

- [x] `frontend/`에 Next.js(App Router) + TypeScript 프로젝트, `package.json`의
      `scripts`에 `dev`/`build`/`start`/`lint` 존재 — 충족.
      `frontend/package.json:5-10`에 4개 script 모두 존재. `next.config.ts`,
      `tsconfig.json`, `eslint.config.mjs`, `next-env.d.ts` 모두 정상적인
      Next 16 App Router 구조를 갖춤 (`tsconfig.json`에 `.next/types` include,
      `paths: {"@/*": ["./src/*"]}` alias, `eslint.config.mjs`가 flat config +
      `eslint-config-next/core-web-vitals`/`typescript` 사용 — Next 16 표준
      패턴과 일치).
- [x] `npm install && npm run build`가 에러 없이 성공 — 충족. 리뷰어가 직접
      `frontend/`에서 `rm -rf .next && npm run build` 재실행, `Compiled
      successfully`, `/`와 `/_not-found` 정적 페이지 생성 확인.
- [x] `npm run lint`가 에러 없이 통과 — 충족. 리뷰어가 직접 재실행, 에러/경고
      없이 종료(설정 완화 흔적 없음, `eslint-config-next/core-web-vitals` +
      `/typescript` 그대로 사용).
- [x] `npm run dev`로 `/`에 3개 필수 문구 노출 — 충족. 리뷰어가 직접
      `next dev --webpack`으로 3099 포트에서 실행 후 `curl`로 "CareerOps",
      "채용 공고를 모으고, 내 경험과 맞는 공고를 추천해주는 서비스", "서비스
      준비 중" 3개 모두 HTML에서 확인(`frontend/src/app/page.tsx:6-10`).
- [x] 기본 브라우저 스타일이 아닌 중앙 정렬 최소 레이아웃 — 충족.
      `frontend/src/app/globals.css:25-40`에서 `.landing`이
      `display:grid; place-items:center; min-height:100vh`로 중앙 정렬,
      `.hero`에 카드형 배경/그림자/radius 적용. 순수 CSS만 사용, 디자인
      시스템/컴포넌트 라이브러리 도입 없음.
- [x] `.env.local.example` 존재 + `.env.local`은 git에 포함 안 됨 — 충족.
      `frontend/.env.local.example:1`에 `NEXT_PUBLIC_API_BASE_URL=` 존재.
      루트 `.gitignore` diff(`.gitignore:3-5`)에서 `.env.*`는 무시하되
      `!.env.local.example`로 예외 처리한 것을 확인. `git check-ignore -v`로
      `frontend/.env.local`은 무시됨, `frontend/.env.local.example`은
      무시되지 않음(빈 결과, 즉 트래킹 가능)을 직접 검증.
- [x] `backend/` 무수정 — 충족. `git status --porcelain -- backend`,
      `git diff --stat -- backend` 모두 빈 출력.
- [x] 실제 외부 API 호출 코드 없음 — 충족. `frontend/src` 전체에서
      `fetch(`/`axios`/`XMLHttpRequest` grep 결과 0건. `.env.local.example`은
      키만 정의, 참조 코드 없음(Task 명세가 허용하는 "값이 있으면 로그/주석
      수준 참조 또는 단순 예시 파일만" 중 후자를 선택 — 명세 위반 아님).
- [ ] Vercel 배포 설정값(Framework Preset/Root Directory/Build Command/
      Output Directory/Environment Variables)을 결과 보고에 정리 —
      **미충족**. 전달받은 Codex 2라운드 완료 보고 원문에 해당 내용이 없고,
      `frontend/README.md`, `frontend/` 어떤 파일에도 Vercel 관련 설정값
      기록이 없음(`grep -rn "Vercel" --exclude-dir=node_modules
      --exclude-dir=.next frontend .ai` 결과 Task 명세 파일 자체의 문구만
      매칭, Codex가 작성한 산출물에는 0건). 이 항목은 Acceptance Criteria에
      명시된 필수 산출물이므로 누락으로 판정.
- [x] 별도 단위테스트 프레임워크 미도입, `build`+`lint`를 최소 검증으로 사용
      — 충족. `frontend/package.json`에 jest/vitest 등 테스트 러너 의존성
      추가 없음.

## create-next-app 미사용/수동 스캐폴딩 검증

Codex의 "공식 `create-next-app`이 샌드박스에서 macOS 사용자 설정 디렉터리
접근 차단으로 실패해 수동 스캐폴딩했다"는 주장은 산출물 검증 결과 신뢰할 수
있다:

- `frontend/package.json:12-22`의 버전(`next@16.3.2`, `react@19.2.4`,
  `react-dom@19.2.4`, `eslint-config-next@16.3.2`)은 `npm view next
  versions`로 실존 확인(canary가 아닌 정식 릴리스). 환각 버전 아님.
- `frontend/next.config.ts`의 `agentRules: false` 옵션은 낯설어 보였으나
  `frontend/node_modules/next/dist/server/config-shared.d.ts:1574`에서 Next
  16의 실제 `NextConfig` 타입 필드임을 확인(공식 `create-next-app` 16.x
  최신 템플릿이 기본으로 넣는 옵션으로 보임). 환각/오류 아님.
- `tsconfig.json`, `eslint.config.mjs`, `next-env.d.ts` 모두 Next 16 App
  Router 표준 산출물과 구조적으로 일치(`.next/types`, `.next/dev/types`
  include, flat ESLint config, `moduleResolution: bundler` 등).
- `frontend/node_modules/next/package.json`의 실제 설치 버전도 `16.3.2`로
  `package.json`과 일치.

결론: 수동 스캐폴딩이지만 표준 템플릿과 동등한 정상 구조. 이 부분은 문제
없음.

## 테스트 결과

리뷰어가 `frontend/`에서 직접 재실행:
- `npm run build` (`.next` 삭제 후 재실행): 성공, `Compiled successfully`,
  `/`·`/_not-found` 정적 생성.
- `npm run lint`: 성공, 출력 없음(에러/경고 0).
- `npm run dev -- --webpack -p 3099` 후 `curl localhost:3099`: HTTP 200,
  3개 필수 문구 전부 HTML에 포함 확인 후 프로세스 정상 종료(`ps aux`로
  잔여 프로세스 없음 확인).
- 별도 test_count/test_pass_count 없음(Task 명세상 단위테스트 프레임워크
  미도입이 정상이므로 build+lint 통과가 곧 검증 기준).

## Findings

1. **[필수 수정] Vercel 배포 설정값 미보고.** Acceptance Criteria 마지막
   항목(Framework Preset/Root Directory/Build Command/Output
   Directory/Environment Variables 정리)이 완전히 누락됨. `frontend/`가
   레포 루트가 아니므로 Root Directory=`frontend` 지정이 필수라는 점까지
   포함해 결과 보고에 정리가 필요.
2. (경미, blocking 아님) Vercel 설정 정리 내용은 채팅 보고뿐 아니라
   `frontend/README.md`에도 한 문단 남겨두면 이후 실제 배포 시 재검색 없이
   바로 참조 가능 — 강제는 아니지만 권장.
3. 나머지 항목은 전부 코드/파일 직접 열람 + 재실행으로 검증 완료, 이견
   없음. 과도한 추상화나 불필요한 패턴, secret 커밋, 근거 없는 자기소개서
   로직 등 원칙 위반 사항 없음. 신규 production dependency는
   next/react/react-dom/typescript/eslint 계열뿐이며 Task 명세가 허용한
   범위 그대로.

## 다음 액션

NEEDS_REVISION: 같은 Codex thread(`01a0393a-bf55-7190-811b-0ac74b4cabc4`)에
다음을 요청.

> FRONT-000 마지막 Acceptance Criteria가 아직 미충족입니다. Vercel 배포에
> 필요한 설정값을 정리해 다시 보고해 주세요:
> - Framework Preset: Next.js
> - Root Directory: `frontend` (레포 루트가 아니므로 반드시 지정)
> - Build Command: 기본값(`next build`) 사용 여부, 커스텀 필요 없는지 확인
> - Output Directory: Next.js 기본값(App Router는 보통 별도 지정 불필요)
>   사용 여부 확인
> - Environment Variables: `NEXT_PUBLIC_API_BASE_URL` (Vercel 프로젝트
>   설정에서 값 입력 필요, 예시 없음/빈 값으로 두면 됨을 명시)
>
> 코드 변경은 필요 없고, 위 내용을 결과 보고 텍스트로 정리해 주시면 됩니다
> (가능하면 `frontend/README.md`에도 "Vercel 배포" 섹션으로 한 문단 추가
> 권장, 필수는 아님).

그 외 항목은 전부 PASS 수준이므로 위 1건만 보완되면 최종 PASS 예상.
