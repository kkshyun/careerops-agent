---
name: codex-implement
description: Task 명세(.ai/tasks/CO-XXXX-*.md)를 Codex MCP(mcp__codex__codex, mcp__codex__codex-reply)에 전달해 실제 구현을 위임하고, reviewer subagent와 함께 리뷰-수정 루프를 돌리고, .ai/metrics/metrics.jsonl에 진행 상황을 기록하는 절차. "Codex에게 구현시켜", "이 Task 구현해줘", "CO-XXXX 구현" 같은 요청이나, 애플리케이션 코드 구현이 필요한 모든 순간에 사용한다.
---

# codex-implement

이 Skill은 "명세는 Claude, 구현은 Codex" 원칙을 실제로 수행하는 절차다.
애플리케이션 코드를 여기서 직접 작성하지 않는다 — Codex MCP tool을 호출한다.

## 0. 전제 조건 확인

- Task 명세(`.ai/tasks/CO-XXXX-*.md`)가 이미 있어야 한다. 없으면 먼저
  `architect` subagent로 만든다.
- Codex MCP 연결을 확인한다 (`claude mcp list`에서 `codex` = Connected 여부,
  또는 `mcp__codex__codex` tool 호출이 성공하는지). **연결이 안 되어 있으면
  임의로 Claude가 직접 구현하지 말고, 연결 상태와 문제를 사용자에게 먼저
  보고하고 멈춘다.**

## 1. 첫 구현 요청 (새 Task, `codex_thread_id`가 null인 경우)

1. Task 명세 전체를 읽는다.
2. `mcp__codex__codex`를 호출한다:
   - `prompt`: 아래 "Codex 프롬프트 구성" 참고
   - `cwd`: 저장소 루트
   - `sandbox`: `workspace-write` (파일 수정 필요, 네트워크/승인 필요한
     명령은 기본적으로 지양하게 프롬프트에 명시)
   - `approval-policy`: `on-request`
3. 응답에서 thread id를 받아 Task 명세 frontmatter의 `codex_thread_id`에
   기록한다.
4. `.ai/metrics/metrics.jsonl`에 `phase: "implement"` 줄을 append한다
   (`codex_invocation_count`는 1부터 시작).

## 2. 후속 요청 (같은 Task, 이미 thread가 있는 경우 — 수정 요청 등)

- **절대 새 thread를 만들지 않는다.** `mcp__codex__codex-reply`에
  Task 명세의 `codex_thread_id`와 새 `prompt`를 넘긴다.
- Task 명세의 `codex_invocation_count`를 1 증가시키고 metrics에 반영한다.

## 2.5. Codex에게 시키지 않는 것

- **`.ai/metrics/metrics.jsonl` 기록은 항상 Claude(오케스트레이터)가 리뷰 결과를
  반영해서 남긴다.** Codex 프롬프트에서 이 파일을 직접 수정하라고 지시하지
  않는다. Codex가 자기 결과를 스스로 "passed"로 self-report해 append하면
  리뷰 게이트를 우회하는 셈이 된다 (CORE-001 1차 리뷰에서 실제 발생한 문제 —
  `.ai/reviews/CORE-001-review-1.md` 참고). Codex의 결과는 어디까지나
  텍스트 보고로만 받고, metrics 반영은 이 Skill의 "5. Metrics 기록 규칙"에
  따라 별도로 한다.

## 3. Codex 프롬프트 구성

Codex는 이 저장소의 `AGENTS.md`를 읽을 수 있으므로 전체 원칙을 다시
설명할 필요는 없다. 프롬프트에는 다음만 명시한다:

- 이번 요청이 첫 구현인지 수정 요청인지
- 대상 Task ID와 명세 파일 경로 (Codex가 직접 읽게 하거나, 핵심 Scope /
  Acceptance Criteria / Out of Scope / Test Plan을 프롬프트에 인용)
- "구현, 테스트 코드 작성, 로컬 테스트 실행까지 하고, 변경한 파일 목록과
  테스트 실행 결과(통과/실패 개수)를 요약해서 보고하라"는 명시적 지시
- 수정 요청인 경우: reviewer의 FAIL/NEEDS_REVISION 사유를 그대로 인용

## 4. 결과 수신 후

1. Codex 응답(변경 파일 목록, 테스트 결과 요약)을 Task 명세의
   "Codex Thread 기록" 표에 한 줄 추가한다.
2. `reviewer` subagent를 호출한다. 넘겨줄 것: Task 명세 경로, 리뷰 라운드
   번호, (필요하면) 리뷰 대상 diff 확인 방법(`git diff`).
3. reviewer의 판정에 따라:
   - **PASS**: Task 상태를 `passed`로, metrics에 `phase: "done"`,
     `status: "passed"` 줄을 append. 사용자에게 요약 보고.
   - **FAIL / NEEDS_REVISION**: reviewer가 만든 수정 요청을 그대로 "2. 후속
     요청"으로 같은 Codex thread에 보낸다. 리뷰 라운드가 반복되면
     (reviewer 기준 3라운드 이상) 자동 반복을 멈추고 사용자에게 Task 명세
     자체를 점검해야 하는지 물어본다.

## 5. Metrics 기록 규칙

- 각 phase 전환(`plan`→`implement`→`review`→`verify`/`done`)마다
  `.ai/metrics/metrics.jsonl`에 한 줄을 append한다. 스키마는
  `docs/METRICS.md`.
- `codex_invocation_count`는 해당 Task에서 `mcp__codex__codex` +
  `mcp__codex__codex-reply` 호출 누적 횟수다.
- `review_round_count`는 reviewer subagent 호출 누적 횟수다.
- `first_review_pass`는 review_round_count가 1일 때 PASS면 true, 아니면
  false. 2라운드 이상 진행됐다면 항상 false로 고정된다(이후 줄에서도).
- `implementation_blocker_count`/`implementation_revision_count`(JOB-001부터):
  formal review(reviewer subagent 호출)가 시작되기 **전**, 구현 단계에서
  Codex가 명시적으로 "막혔다"고 보고한 횟수와 그 사이 발생한 `codex-reply`
  호출 횟수를 각각 센다. review가 시작된 뒤의 수정 왕복은 이 두 필드가
  아니라 `review_round_count`로 카운트한다(스키마 정의는 `docs/METRICS.md`
  참고). 구현 단계에서 blocker/revision이 아예 없었다면 둘 다 0으로
  명시적으로 기록한다(null로 남기지 않는다).
- `human_revision_required`는 사용자가 직접 코드를 고치거나 명세를
  바꿔야 했다면 true.

## 원칙 재확인 (요약, 상세는 AGENTS.md)

- Claude는 이 Skill 실행 중에도 애플리케이션 코드를 직접 작성하지 않는다.
- 같은 Task의 수정 요청은 항상 같은 Codex thread를 재사용한다.
- Codex MCP 연결 문제는 조용히 우회하지 않는다.
