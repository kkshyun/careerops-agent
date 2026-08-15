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
| `codex_invocation_count` | number | 해당 task에서 Codex를 호출한 누적 횟수 (`codex` + `codex-reply` 합산). 아래 세 필드를 전부 합친 raw 총합이다 |
| `implementation_blocker_count` | number | **(JOB-001부터)** formal review 이전, 구현 단계에서 Codex가 "추측하지 않고 진행을 멈춘" 실제 blocker 보고 횟수(예: 프레임워크/라이브러리 버전 비호환으로 스스로 판단할 수 없어 Tech Lead 결정을 요청한 경우). 버그를 고치는 일반적인 재작업과 구분하기 위한 필드 — Codex가 "막혀서 못 간다"고 명시적으로 멈춘 경우만 센다 |
| `implementation_revision_count` | number | **(JOB-001부터)** 최초 구현 호출(`codex`) 이후, formal review가 시작되기 전까지 발생한 추가 Codex 호출(`codex-reply`) 횟수. blocker 해결 요청뿐 아니라 review 없이 Claude가 선제적으로 요청한 수정도 포함한다. `implementation_blocker_count`의 상위 집합 — 모든 blocker 해결에는 revision이 최소 1회 필요하지만, blocker 없이도 revision이 있을 수 있다(둘이 항상 같은 값은 아니다) |
| `review_round_count` | number | 누적 **formal review** 라운드 수(reviewer subagent가 실제로 판정을 내린 횟수). `implementation_revision_count`와 달리, review가 한 번이라도 시작된 이후의 수정 왕복만 여기 반영된다 |
| `first_review_pass` | boolean \| null | 1차 **formal review**에서 바로 통과했는지(수정 요청 없이 PASS). 구현 단계의 blocker/revision 여부와는 무관 — blocker가 여러 번 있었어도 review 자체가 1차에 통과하면 true. 아직 리뷰 전이면 null |
| `test_count` | number \| null | 실행된 테스트 수 |
| `test_pass_count` | number \| null | 통과한 테스트 수 |
| `human_revision_required` | boolean | 사람(사용자)이 직접 수정 개입했는지 |
| `status` | string | `in_progress` \| `blocked` \| `passed` \| `failed` \| `abandoned` |

**필드 관계식(참고용, 강제 검증하지 않음)**: 대략
`codex_invocation_count ≈ 1(최초 구현) + implementation_revision_count + (review 라운드 중 발생한 재구현 호출 수)`.
`implementation_blocker_count`와 `implementation_revision_count`가 같은 값일
수 있지만(예: JOB-001은 3/3 — blocker마다 정확히 revision 1회씩으로 해결됨),
개념적으로는 다르다: blocker는 "Codex가 멈추고 판단을 요청한 사건", revision은
"그 사건이든 아니든 review 전에 발생한 재구현 호출 자체"다.

### 예시

```jsonl
{"task_id":"CO-0001","phase":"plan","planned_by":"claude","implemented_by":null,"started_at":"2026-08-13T10:00:00+09:00","completed_at":"2026-08-13T10:20:00+09:00","duration":1200,"codex_invocation_count":0,"review_round_count":0,"first_review_pass":null,"test_count":null,"test_pass_count":null,"human_revision_required":false,"status":"in_progress"}
{"task_id":"CO-0001","phase":"implement","planned_by":"claude","implemented_by":"codex","started_at":"2026-08-13T10:20:00+09:00","completed_at":"2026-08-13T10:45:00+09:00","duration":1500,"codex_invocation_count":1,"review_round_count":0,"first_review_pass":null,"test_count":6,"test_pass_count":6,"human_revision_required":false,"status":"in_progress"}
{"task_id":"CO-0001","phase":"review","planned_by":"claude","implemented_by":"codex","started_at":"2026-08-13T10:45:00+09:00","completed_at":"2026-08-13T10:50:00+09:00","duration":300,"codex_invocation_count":1,"review_round_count":1,"first_review_pass":true,"test_count":6,"test_pass_count":6,"human_revision_required":false,"status":"passed"}
```

`implementation_blocker_count`/`implementation_revision_count`는 JOB-001부터
기록한다. CO-0001(위 예시)처럼 이 필드 도입 이전 항목은 필드 자체가 없다 —
과거 줄을 다시 써서 채워 넣지 않는다(값을 사실로 확인할 수 있는 경우에
한해 예외적으로 뒤에 보완 줄을 추가할 수 있다. `.ai/metrics/metrics.jsonl`의
JOB-001 항목 참고).

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

**Collector (COLLECT-001)**

| 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 | 계측 위치 |
|---|---|---|---|---|---|
| `careerops_collector_run_total` | `careerops.collector.run` | Counter | `source`, `result`=`success`\|`failed` | 수집 실행(수동 트리거 1회, `POST /api/collect/{source}`) 자체의 성공/실패 분포. `failed`는 외부 API 호출/응답 파싱 자체가 실패해 수집이 중단된 경우(개별 item의 `invalid_item` 실패는 포함하지 않음 — 그 경우 run은 `success`로 집계됨) | `AlioCollectorService` — 실행 종료 시점 |
| `careerops_collector_fetched_total` | `careerops.collector.fetched` | Counter | `source` | 외부 API로부터 수신한 원본 항목(item) 수 누적(저장 여부 무관) | `AlioCollectorService` — 응답 수신 직후 |
| `careerops_collector_saved_total` | `careerops.collector.saved` | Counter | `source` | **이 collector 실행으로 새로 저장된** `JobPosting` 수 누적(중복 skip, 필수 필드 누락은 제외) | `AlioCollectorService` — 개별 저장 성공 시 |
| `careerops_collector_failed_total` | `careerops.collector.failed` | Counter | `source`, `reason`=`fetch_error`\|`parse_error`\|`invalid_item` | 실패 유형별 분포. `reason`은 고정된 소수의 enum만 사용 — raw exception message를 태그로 넣지 않는다(cardinality 제한) | `AlioCollectorService` — 실패 지점별 |

`source` 값은 현재 `"alio"` 하나뿐이라 cardinality 문제가 없다(향후 Source가
늘어나도 고정된 소스 이름 집합이므로 자유 문자열 입력인 `JobPosting.source`와
달리 계속 태그로 사용 가능하다고 판단).

**`careerops_job_creation_total`(JOB-001)과의 관계**: `careerops_job_creation_total`은
저장 경로(수동 `POST /api/jobs` + collector 등 모든 경로)를 합친 전체 누적
저장 건수다. `careerops_collector_saved_total`은 그중 **이 collector 실행이
기여한 몫만** 별도로 센다 — collector가 저장에 성공하면 두 카운터가 함께
증가한다(겹침, 의도된 것). 전자는 "총 저장량", 후자는 "이 수집기의 기여도/
효과"를 보기 위한 것으로 관측 목적이 다르다.

**Scheduler (COLLECT-003)**

| 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 | 계측 위치 |
|---|---|---|---|---|---|
| `careerops_scheduler_alio_run_total` | `careerops.scheduler.alio.run` | Counter | `result`=`success`\|`failure` | ALIO 자동 수집(Scheduler) 실행 자체의 성공/실패 횟수(개별 item 실패는 포함 안 함 — `collect()` 호출 자체가 예외 없이 끝나면 success) | `AlioCollectionScheduler` — 실행 종료 시점 |
| `careerops_scheduler_alio_duration_seconds` | `careerops.scheduler.alio.duration` | Timer | 없음 | 1회 실행 소요 시간 | `AlioCollectionScheduler` — 실행 전체 |
| `careerops_scheduler_alio_fetched_total` | `careerops.scheduler.alio.fetched` | Counter | 없음 | Scheduler 실행으로 수집한 원본 item 수 누적 | `AlioCollectionScheduler` — 성공 실행 후 |
| `careerops_scheduler_alio_saved_total` | `careerops.scheduler.alio.saved` | Counter | 없음 | Scheduler 실행으로 신규 저장된 건수 누적 | `AlioCollectionScheduler` — 성공 실행 후 |
| `careerops_scheduler_alio_skipped_total` | `careerops.scheduler.alio.skipped` | Counter | 없음 | Scheduler 실행 중 변경 없어 skip된 건수 누적 | `AlioCollectionScheduler` — 성공 실행 후 |
| `careerops_scheduler_alio_updated_total` | `careerops.scheduler.alio.updated` | Counter | 없음 | Scheduler 실행 중 상태가 갱신된 건수 누적 | `AlioCollectionScheduler` — 성공 실행 후 |
| `careerops_scheduler_alio_failed_total` | `careerops.scheduler.alio.failed` | Counter | 없음 | Scheduler 실행 중 개별 item 실패 건수 누적(`collect()` 자체 실패는 `run{result=failure}`로 별도 집계) | `AlioCollectionScheduler` — 성공 실행 후 |

`careerops_collector_*`(COLLECT-001)와 겹치는 것처럼 보이지만 관측 목적이
다르다(위 "`careerops_job_creation_total`(JOB-001)과의 관계"와 동일한
원칙) — `careerops_collector_*`는 트리거 출처(수동/자동) 무관 총량이고,
`careerops_scheduler_alio_*`는 "자동 실행이 실제로 동작하고 있는가"를 사람
개입 없이 관측하기 위한 전용 지표다. `AlioCollectorService`/
`CollectController`/`CollectResult`는 COLLECT-003으로 전혀 수정되지
않았다.

**Manual Job Import (IMPORT-001)**

| 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 | 계측 위치 |
|---|---|---|---|---|---|
| `careerops_manual_import_total` | `careerops.manual.import` | Counter | `result`=`saved`\|`duplicate` | 사용자가 URL로 수동 등록(`POST /api/import/jobs/manual`)한 결과 분포 | `ManualImportService` — 저장/중복 판정 직후 |

`invalid`(입력 검증 실패)는 태그 값으로 쓰지 않는다 — 검증이 Bean
Validation(`@Valid`)으로 Controller 진입 전에 처리돼 계측 지점을 추가하려면
전용 예외 처리 계층(`@ControllerAdvice` 등)을 새로 들여야 하고, 이는
JOB-001/COLLECT-001이 유지해온 "공통 예외 처리 계층을 만들지 않는다"는
원칙과 충돌한다. metric 개수보다 정확성이 우선이라는 원칙에 따라 이번엔
`saved`/`duplicate`만 계측한다(`.ai/tasks/IMPORT-001.md` Technical Notes
참고).

**`careerops_job_creation_total`과의 관계**: `ManualImportService`는 신규
저장(`result=saved`)일 때만 기존 `JobPostingService.create()`를 호출하고,
`duplicate`일 때는 호출하지 않는다 — 따라서 `careerops_job_creation_total`은
`careerops_manual_import_total{result="saved"}`가 증가할 때만 함께
증가하고, `result="duplicate"`일 때는 증가하지 않는다. 두 카운터가 항상
같은 방향으로 움직이도록 구현 경로 자체(`JobPostingService.create()`를
반드시 거침)로 강제한 것이며, COLLECT-001의 `careerops_collector_saved_total`과
동일한 설계 원칙이다.

### 예정 (미구현, 관련 기능 Task에서 정의)

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
