---
task_id: FRONT-000
title: 프론트엔드 프로젝트 초기 세팅 + 최소 랜딩 페이지
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-25T00:00:00+09:00
codex_thread_id: 01a0393a-bf55-7190-811b-0ac74b4cabc4
---

## Context

CareerOps는 지금까지 backend(Spring Boot)만 존재하고 frontend가 없다.
이번 Task는 두 가지 목적을 동시에 달성한다.

1. 이후 CareerOps 실제 프론트엔드로 계속 확장해 나갈 최소 기반을 만든다.
2. Vercel에 배포해 공개 HTTPS 도메인을 확보한다 — 이 도메인은 Kakao
   Developers 앱의 대표 도메인으로 등록할 예정이다(KAKAO-001 이후 카카오
   로그인/알림 기능과 연결되는 전제 조건).

`docs/ARCHITECTURE.md`에 예정 프론트엔드 스택으로 이미 명시된 **Next.js +
TypeScript**를 그대로 따른다(사용자와 확인 완료 — Vite 대안은 기각).

## Scope

- repository root 아래 `frontend/`에 Next.js(App Router) + TypeScript
  프로젝트 생성.
- 최소 랜딩 페이지 1개(`/`):
  - "CareerOps" 타이틀
  - 부제: "채용 공고를 모으고, 내 경험과 맞는 공고를 추천해주는 서비스"
  - "서비스 준비 중" 문구
  - 기본 HTML처럼 보이지 않는 깔끔한 최소 레이아웃(중앙 정렬, 여백, 기본
    폰트/색상 정도 — 디자인 시스템/컴포넌트 라이브러리 도입 없이 순수
    CSS 또는 CSS Module로 충분)
- 이후 backend REST API를 연결할 수 있도록 backend base URL을 환경변수로
  관리하는 구조만 마련(`NEXT_PUBLIC_API_BASE_URL`). 이번 단계에서 실제
  API를 호출하는 코드는 작성하지 않는다 — 환경변수를 읽어 쓸 수 있는
  자리만 만들어 둔다(예: 값이 있으면 어딘가 로그/주석 수준으로 참조,
  또는 단순히 `.env.local.example`에 키만 정의).
- `frontend/README.md`(또는 루트 `README.md` 보강)에 로컬 개발 실행 방법
  (`npm install`, `npm run dev`) 기록.
- `.gitignore`는 루트에 이미 Next.js 항목(`node_modules/`, `.next/`,
  `out/`)이 있으므로 grep으로 확인만 하고, 부족하면 보강한다.

## Out of Scope

- 실제 UI 전체 구현(라우팅, 페이지 여러 개, 상태관리, 디자인 시스템 도입).
- 실제 backend API 호출 코드.
- Kakao SDK/로그인 연동.
- Vercel CLI를 통한 실제 배포 실행(Claude/Codex가 대신 배포하지 않는다 —
  사용자가 Vercel 웹 UI에서 직접 연결).
- CI 파이프라인 구성.

## Acceptance Criteria

- [ ] `frontend/` 디렉토리에 Next.js(App Router) + TypeScript 프로젝트가
      생성되어 있고, `frontend/package.json`의 `scripts`에 `dev`/`build`/
      `start`/`lint`가 있다.
- [ ] `npm install && npm run build`가 `frontend/` 디렉토리에서 에러 없이
      성공한다.
- [ ] `npm run lint`가 에러 없이 통과한다(경고는 허용 가능하나, 기본
      Next.js ESLint 설정을 임의로 완화하지 않는다).
- [ ] `npm run dev`로 로컬 실행 시 `/`에 다음 텍스트가 모두 보인다:
      "CareerOps", "채용 공고를 모으고, 내 경험과 맞는 공고를 추천해주는
      서비스"(줄바꿈 허용), "서비스 준비 중".
- [ ] 랜딩 페이지가 기본 브라우저 스타일 그대로가 아니라 중앙 정렬된
      최소 레이아웃(폰트/여백/색상 정도)을 갖춘다.
- [ ] `NEXT_PUBLIC_API_BASE_URL` 환경변수를 위한 `.env.local.example`
      (또는 동등한 예시 파일)이 `frontend/`에 존재하고, 실제 값이 담긴
      `.env.local`은 git에 포함되지 않는다(루트 `.gitignore`가 이미
      `node_modules/`, `.next/`, `out/`을 덮고 있으므로, `.env*` 패턴이
      `frontend/` 하위에도 적용되는지 확인 — 안 되면 보강).
- [ ] backend production code(`backend/` 하위)는 전혀 수정하지 않는다.
- [ ] 실제 Anthropic API, Kakao API, 기타 유료/외부 네트워크 호출이 빌드/
      런타임 코드에 없다(정적 랜딩 페이지만).
- [ ] Vercel 배포에 필요한 설정값(Framework Preset/Root Directory/Build
      Command/Output Directory/Environment Variables)을 결과 보고에 정리한다
      (Next.js 기본값 기준으로 정리하되, `frontend/`가 루트가 아니므로
      Root Directory 지정이 필요함을 명시).
- [ ] 테스트: 이번 Task는 페이지 렌더링 확인이 핵심이므로 별도 단위 테스트
      프레임워크 도입은 하지 않는다. 대신 `npm run build`(프로덕션 빌드
      성공)와 `npm run lint`를 최소 검증으로 삼는다.

## Technical Notes

- Next.js 프로젝트 생성 시 `create-next-app`을 사용해도 되나, 대화형
  프롬프트 없이 비대화형 플래그(`--yes` 또는 명시적 플래그 조합)로 실행할
  것 — TypeScript: yes, ESLint: yes, Tailwind: 이번 단계에서는 불필요
  (사용자가 "불필요한 디자인 시스템/라이브러리는 아직 추가하지 않음"이라고
  명시) → Tailwind 제외, App Router: yes, `src/` 디렉토리 사용 여부는
  Codex 재량(일관성 있게 하나만 선택), import alias는 기본값(`@/*`) 사용.
- 이 머신에는 Node.js/npm이 설치되어 있지 않을 수 있다(사전 조사 시
  미설치 확인됨). Codex 실행 환경에 Node/npm이 있는지 먼저 확인하고,
  없다면 임의로 우회하지 말고 Claude(오케스트레이터)에게 보고한다.
- 새 production dependency 추가 시(Next.js/React/TypeScript 자체 제외)
  이유를 결과 보고에 남긴다. 원칙적으로 이번 단계에서는 `create-next-app`
  기본 의존성 외 추가 라이브러리를 넣지 않는다.
- `docs/ARCHITECTURE.md`의 "Frontend" 섹션은 이미 Next.js/TypeScript로
  기재되어 있어 이번 Task로 인한 변경이 없다(구현 완료 후에도 ARCHITECTURE.md
  수정은 Claude가 별도로, 필요 시에만 한다 — Codex는 `docs/`를 수정하지
  않는다).

## Test Plan

- `frontend/`에서 `npm install`
- `npm run build` (프로덕션 빌드 성공 확인)
- `npm run lint` (통과 확인)
- `npm run dev` 후 `curl localhost:3000` 또는 브라우저 확인으로 필수 텍스트
  노출 확인, 실행 결과를 텍스트로 보고(스크린샷 불필요)

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | Next.js+TS frontend 초기 생성, 랜딩 페이지, env 변수 자리 마련 | 중단: Codex 실행 환경에도 Node.js/npm 미설치. 파일 변경 없음. Tech Lead(사용자)의 Node/npm 설치 방침 결정 대기 |
| 2 (새 thread, 사유: network_access 샌드박스 설정은 thread 생성 시에만 지정 가능해 기존 thread에서 이어갈 수 없었음) | Node/npm 설치 완료 후 재요청, network_access=true 샌드박스로 frontend 구현 | 완료 보고: frontend/ Next.js 16.3.2+TS+App Router 생성, 랜딩 페이지, NEXT_PUBLIC_API_BASE_URL 예시, README, .gitignore 보강. install/build/lint/dev 전부 통과, curl로 필수 문구 3개 확인. 단, 공식 create-next-app이 샌드박스에서 macOS 사용자 설정 디렉터리 접근 차단으로 실패해 Codex가 동등 구조를 수동 스캐폴딩함 |
| 3 (같은 thread `01a0393a-...`) | reviewer NEEDS_REVISION: Vercel 배포 설정값(Codex가 채팅 응답으로는 보고했으나 저장소 산출물 어디에도 기록 안 됨)을 frontend/README.md에 추가 요청 | frontend/README.md에 "Vercel 배포" 섹션 추가 완료, build/lint 재통과. reviewer round 2에서 PASS 확정 (.ai/reviews/FRONT-000-review-1.md) |

사용자 승인 후 추가 사항: 사용자가 `frontend/.nvmrc` 추가를 요청했으나 이번
Task 대화에는 해당 합의 기록이 없어(착오로 추정) 사용자에게 재확인 후 진행.
설정 파일 성격이라 Codex 위임 없이 Claude가 직접 `frontend/.nvmrc`(24.19.0,
검증에 사용한 버전과 동일) 생성. `npm run lint`/`npm run build` 재검증 통과.
