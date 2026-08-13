---
task_id: CO-XXXX
review_round: 1
reviewer: claude
reviewed_at: <ISO 8601>
verdict: PASS | FAIL | NEEDS_REVISION
---

## Acceptance Criteria 체크

- [ ] 기준 1 — 충족 / 미충족 (근거)
- [ ] 기준 2 — 충족 / 미충족 (근거)

## 테스트 결과

test_count / test_pass_count, 실행 방법, 실패 시 원인.

## Findings

- (있다면) 버그, Acceptance Criteria 위반, 원칙 위반(불필요한 추상화,
  근거 없는 자기소개서 생성 로직 등) 등을 항목별로.

## 다음 액션

- PASS: 완료 처리, metrics.jsonl에 최종 상태 기록.
- FAIL / NEEDS_REVISION: 같은 Codex thread에 무엇을 요청했는지 요약.
