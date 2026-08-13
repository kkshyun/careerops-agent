# METRICS.md — 지표 정의

측정 시스템 자체를 과도하게 만들지 않는다.

이 문서는 두 종류의 지표를 구분한다:

- **Development Metrics(개발 프로세스 지표)**: Claude/Codex가 Task를
  계획·구현·리뷰·검증하는 "AI 개발팀 워크플로 자체"의 품질/효율을
  측정한다. `.ai/metrics/metrics.jsonl`에 append-only로 기록한다(JOB-001
  이전부터 동일).
- **Product Metrics(제품 지표)**: CareerOps **제품 자체**가 런타임에
  발생시키는 지표다. Micrometer 커스텀 카운터 등으로 계측하고
  `/actuator/prometheus`를 통해 노출한다. JOB-001부터 실제로 구현되기
  시작한다(그 전까지는 계측할 도메인 기능이 없었다).

## Development Metrics — 개발 프로세스 지표 (지금부터 기록)

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

## Product Metrics — 제품 지표

CareerOps 제품 자체가 런타임에 발생시키는 지표. Micrometer로 계측하고
Spring Boot Actuator `/actuator/prometheus`를 통해 노출한다(CORE-001에서
이미 `micrometer-registry-prometheus`가 클래스패스에 있으므로, 지표를
추가할 때 별도 dependency가 필요한 경우는 드물다). 지표를 늘리는 것 자체가
목표가 아니므로, 실제 운영상 의미 있는 최소 지표만 추가한다 — 새 지표를
추가하는 Task는 그 지표가 무엇을 관찰하기 위한 것인지 근거를 남긴다.

### 구현됨

**JobPosting (JOB-001)**

| 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 | 계측 위치 |
|---|---|---|---|---|---|
| `careerops_job_creation_total` | `careerops.job.creation` | Counter | 없음 | `JobPosting`이 성공적으로 저장된 누적 횟수. `POST /api/jobs` 성공 시 1 증가 | `JobPostingService.create()` |
| `careerops_job_read_total` | `careerops.job.read` | Counter | `result`=`found`\|`not_found` | `GET /api/jobs/{id}` 조회 결과 분포. `not_found`가 비정상적으로 높으면 깨진 링크/오동작 신호로 활용 가능 | `JobPostingService.findById()` |

`source`(원본 출처) 값으로는 태깅하지 않는다 — 현재 자유 문자열 입력이라
Prometheus label cardinality가 무한정 늘어날 위험이 있다. 출처가 고정된
값 집합(enum 등)으로 정리되는 시점에 재검토한다.

### 예정 (미구현, 관련 기능 Task에서 정의)

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
