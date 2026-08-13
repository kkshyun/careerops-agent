# .ai/ — Task 작업 기록

Claude(Planner/Reviewer)와 Codex(Developer)의 작업물이 쌓이는 곳.

- `tasks/` — Task 명세 (Claude 작성). 파일명: `CO-XXXX-<slug>.md`.
  템플릿: `tasks/_TEMPLATE.md`.
- `reviews/` — 리뷰 기록 (Claude 작성). 파일명: `CO-XXXX-review-N.md`
  (N = 리뷰 라운드, 1부터). 템플릿: `reviews/_TEMPLATE.md`.
- `metrics/` — 개발 프로세스 지표 로그. `metrics/metrics.jsonl`. 스키마는
  [`docs/METRICS.md`](../docs/METRICS.md).

## Task ID

형식: `CO-XXXX` (4자리, 0001부터 순증가). 다음 번호는 `tasks/`에서 가장 큰
번호 + 1. Task ID는 재사용하지 않는다 (작업이 중단/폐기돼도 번호를 비운다).

## 생명주기

계획(`tasks/CO-XXXX-*.md` 작성) → 구현(Codex, `codex-implement` Skill 사용) →
리뷰(`reviewer` subagent, `reviews/CO-XXXX-review-N.md` 작성) → 검증(테스트
결과 확인) → 완료. 각 단계 전환마다 `metrics/metrics.jsonl`에 한 줄을
append한다.

상세 역할 정의는 저장소 루트의 [`AGENTS.md`](../AGENTS.md),
[`CLAUDE.md`](../CLAUDE.md) 참고.
