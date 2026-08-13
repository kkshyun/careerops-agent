# METRICS.md — 지표 정의

측정 시스템 자체를 과도하게 만들지 않는다. 지금은 JSONL append만으로 충분하다.

## 개발 프로세스 지표 (지금부터 기록)

파일: `.ai/metrics/metrics.jsonl` — 한 줄에 JSON 객체 하나, append-only.
같은 `task_id`에 대해 상태가 바뀔 때마다(예: 계획 완료, 구현 완료, 리뷰 라운드
종료, 최종 완료) 새 줄을 추가한다. 즉 한 task의 이력은 여러 줄로 남고,
가장 마지막 줄이 최신 상태다. 과거 줄을 수정/삭제하지 않는다.

### 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `task_id` | string | 예: `CO-0001` |
| `phase` | string | `plan` \| `implement` \| `review` \| `verify` \| `done` |
| `planned_by` | string | 보통 `claude` |
| `implemented_by` | string | 보통 `codex`, Claude 직접 구현 시 `claude` |
| `started_at` | string (ISO 8601) | 이 phase 시작 시각 |
| `completed_at` | string (ISO 8601) \| null | 이 phase 종료 시각, 진행 중이면 null |
| `duration` | number \| null | 초 단위, `completed_at` - `started_at` |
| `codex_invocation_count` | number | 해당 task에서 Codex를 호출한 누적 횟수 (`codex` + `codex-reply` 합산) |
| `review_round_count` | number | 누적 리뷰 라운드 수 |
| `first_review_pass` | boolean \| null | 1차 리뷰에서 바로 통과했는지. 아직 리뷰 전이면 null |
| `test_count` | number \| null | 실행된 테스트 수 |
| `test_pass_count` | number \| null | 통과한 테스트 수 |
| `human_revision_required` | boolean | 사람(사용자)이 직접 수정 개입했는지 |
| `status` | string | `in_progress` \| `blocked` \| `passed` \| `failed` \| `abandoned` |

### 예시

```jsonl
{"task_id":"CO-0001","phase":"plan","planned_by":"claude","implemented_by":null,"started_at":"2026-08-13T10:00:00+09:00","completed_at":"2026-08-13T10:20:00+09:00","duration":1200,"codex_invocation_count":0,"review_round_count":0,"first_review_pass":null,"test_count":null,"test_pass_count":null,"human_revision_required":false,"status":"in_progress"}
{"task_id":"CO-0001","phase":"implement","planned_by":"claude","implemented_by":"codex","started_at":"2026-08-13T10:20:00+09:00","completed_at":"2026-08-13T10:45:00+09:00","duration":1500,"codex_invocation_count":1,"review_round_count":0,"first_review_pass":null,"test_count":6,"test_pass_count":6,"human_revision_required":false,"status":"in_progress"}
{"task_id":"CO-0001","phase":"review","planned_by":"claude","implemented_by":"codex","started_at":"2026-08-13T10:45:00+09:00","completed_at":"2026-08-13T10:50:00+09:00","duration":300,"codex_invocation_count":1,"review_round_count":1,"first_review_pass":true,"test_count":6,"test_pass_count":6,"human_revision_required":false,"status":"passed"}
```

누가/언제 기록하는지는 `codex-implement` Skill과 `reviewer` subagent 절차에
포함되어 있다 (여기서는 스키마만 정의).

## 제품 지표 (Phase 2+ 예정, 아직 미구현)

CareerOps 제품 자체가 갖춰야 할 최소 지표. 지금은 정의만 해두고, 해당 기능을
구현하는 Task에서 함께 계측을 배선한다.

- 채용공고 수집 성공률
- 중복 공고 제거율
- 추천 Top-K 적합도
- 카카오톡 알림 성공률
- 경험 Retrieval 적합도
- 자기소개서 unsupported claim 비율
- 자기소개서 작성 시간 감소율
- LLM 호출량 및 (가능하면) 비용
- Agent 실행 성공률

이 지표들의 구체적 정의(계산식, 수집 방식)는 관련 기능을 설계하는 Task에서
`architect` subagent가 확정하고 이 문서에 추가한다.
