---
task_id: COLLECT-003
title: ALIO 채용공고 자동 수집 Scheduler
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-15T00:00:00+09:00
codex_thread_id: 01a00580-cbf2-7783-a7e0-4eafd2571a44
---

## Context

COLLECT-001/002로 ALIO 연동(`AlioCollectorService.collect(int numOfRows)`)이
완성되어 실제 서비스키로 E2E 검증까지 끝났고, JOB-002로 수집된 데이터를
조회하는 API도 완료됐다(`docs/ROADMAP.md` Phase 1~3). 그러나 수집은 여전히
`POST /api/collect/alio`를 사람이 직접 호출해야만 실행된다.

이번 Task는 이 수집을 주기적으로 자동 실행하되, 기존 수집 로직
(`AlioCollectorService`)과 수동 API(`CollectController`)를 변경하지 않고
그대로 재사용한다. 사용자 승인을 받은 설계는 다음과 같다(요약):

- `fixedDelay` 기반 `@Scheduled` — 이전 실행이 끝난 뒤에만 다음 실행이
  스케줄되므로 별도 락 없이 단일 인스턴스에서 겹침이 방지된다.
- 기본 주기 6시간(설정으로 변경 가능), 기동 1분 뒤 첫 실행.
- Scheduler 전용 metric은 `careerops.scheduler.alio.*` 네임스페이스에만
  추가하고, 기존 `careerops.collector.*`/`AlioCollectorService`/
  `CollectController`는 시그니처/동작 변경 없이 그대로 둔다.

## Scope

1. **`AlioCollectionScheduler`** 신규 클래스
   (`backend/src/main/java/com/careerops/backend/collector/alio/`):
   - `@Component`
   - `@ConditionalOnProperty(prefix = "careerops.scheduler.alio", name = "enabled", matchIfMissing = true)`
   - `@Scheduled(initialDelayString = "${careerops.scheduler.alio.initial-delay:PT1M}", fixedDelayString = "${careerops.scheduler.alio.fixed-delay:PT6H}")`가
     붙은 메서드(예: `collect()`)에서 `alioCollectorService.collect(numOfRows)`를
     호출한다. `numOfRows`는 `${careerops.scheduler.alio.num-of-rows:50}`으로
     주입.
   - 실행 시간을 측정해 `careerops.scheduler.alio.duration` Timer에 기록한다
     (`Timer.Sample`/`Timer.builder` 등 기존 `AlioCollectorService`/
     `JobPostingService`가 쓰는 Micrometer 패턴을 따른다).
   - `AlioApiException`(및 예상 못한 `RuntimeException`도 방어적으로)을
     catch해 **밖으로 던지지 않는다** — WARN 레벨 로그 남기고
     `careerops.scheduler.alio.run{result=failure}` Counter 증가 후 정상
     종료한다. 예외를 삼켜도 `@Scheduled`(fixedDelay)는 다음 실행을 계속
     스케줄한다.
   - 성공 시 반환된 `CollectResult`의 값으로 아래 5개 Counter를 각각
     `increment(double)`으로 증가시키고 `careerops.scheduler.alio.run
     {result=success}`를 1 증가시킨다:
     - `careerops.scheduler.alio.fetched`
     - `careerops.scheduler.alio.saved`
     - `careerops.scheduler.alio.skipped`
     - `careerops.scheduler.alio.updated`
     - `careerops.scheduler.alio.failed` (개별 item 실패 건수 — collect
       실행 자체는 성공했지만 일부 item이 invalid이었던 경우)
   - `AlioCollectorService`/`CollectController`/`CollectResult`는 **수정하지
     않는다**.
2. **`BackendApplication`**에 `@EnableScheduling` 추가.
3. **`application.yml`**에 설정 추가:
   ```yaml
   careerops:
     scheduler:
       alio:
         enabled: true
         initial-delay: PT1M
         fixed-delay: PT6H
         num-of-rows: 50
   ```
4. **`backend/build.gradle`**의 `tasks.named('test') { ... }` 블록에
   `environment 'CAREEROPS_SCHEDULER_ALIO_ENABLED', 'false'`를 추가해
   자동 테스트 중에는 실제 `@Scheduled` 빈이 등록되지 않게 한다(ADR-0010이
   `SPRING_DATASOURCE_URL`을 override한 것과 동일한 패턴). 이때
   `application.yml`의 `careerops.scheduler.alio.enabled` 값을
   `${CAREEROPS_SCHEDULER_ALIO_ENABLED:true}`처럼 환경변수로 override
   가능하게 바꿔야 한다.
5. 새 테스트 클래스에서는 위 4번과 별개로, 스케줄러 로직 자체를 검증하기
   위해 `@TestPropertySource(properties = "careerops.scheduler.alio.enabled=true")`
   등으로 그 테스트 컨텍스트에서만 다시 활성화한 뒤, `AlioCollectionScheduler`
   빈을 직접 `@Autowired`로 주입받아 그 메서드를 **직접 호출**한다(실제
   타이머가 돌 때까지 기다리지 않는다 — 6시간을 기다릴 수 없으므로 당연히
   메서드 직접 호출로 검증).

## Out of Scope

- 다중 인스턴스 분산 Scheduler(ShedLock 등 분산 락) — ADR에 향후 고려사항으로만 기록.
- 과도한 retry framework(Spring Retry 등) 도입 — `AlioApiException` 발생 시
  즉시 실패 처리하고 다음 스케줄 실행을 기다린다(재시도는 시간 기반 재실행
  자체가 대신한다).
- 수동 API(`POST /api/collect/{source}`) 자체의 동작 변경 — 그대로 유지.
- `AlioCollectorService`/`AlioJobMapper`/`JobPosting` 등 기존 수집·저장
  로직 변경.
- ALIO 상세조회, 전형단계/첨부파일 수집, 기관유형/기관분류 매핑, 사람인
  API, PKB, 알림, 프론트엔드, `GET /api/jobs` 필터 확장 — 전부 이번 Task
  범위 밖.
- Scheduler 실행 이력을 DB에 별도로 저장하는 기능(예: `scheduler_runs`
  테이블) — metric으로 충분하다고 판단, 필요해지면 별도 Task.
- Scheduler를 수동으로 즉시 1회 트리거하는 관리자 API(예:
  `POST /api/scheduler/alio/trigger`) — 기존 `POST /api/collect/alio`가
  이미 그 역할을 하므로 중복 생성하지 않는다.

## Metrics

기존 `careerops.collector.*`(fetched/saved/failed/run, `AlioCollectorService`
내부)는 변경 없음 — 수동/자동 호출 모두 계속 이 값을 증가시킨다.

신규 metric(전부 `AlioCollectionScheduler`에서 등록, `docs/METRICS.md`
"Collector (COLLECT-001)" 표 아래에 새 소제목으로 추가):

| 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 |
|---|---|---|---|---|
| `careerops_scheduler_alio_run_total` | `careerops.scheduler.alio.run` | Counter | `result`=`success`\|`failure` | Scheduler 실행 자체의 성공/실패 횟수(개별 item 실패는 포함 안 함 — collect 호출 자체가 예외 없이 끝나면 success) |
| `careerops_scheduler_alio_duration_seconds` | `careerops.scheduler.alio.duration` | Timer | 없음 | 1회 실행 소요 시간 |
| `careerops_scheduler_alio_fetched_total` | `careerops.scheduler.alio.fetched` | Counter | 없음 | Scheduler가 수집한 원본 item 수 누적 |
| `careerops_scheduler_alio_saved_total` | `careerops.scheduler.alio.saved` | Counter | 없음 | Scheduler로 신규 저장된 건수 누적 |
| `careerops_scheduler_alio_skipped_total` | `careerops.scheduler.alio.skipped` | Counter | 없음 | Scheduler 실행 중 변경 없어 skip된 건수 누적 |
| `careerops_scheduler_alio_updated_total` | `careerops.scheduler.alio.updated` | Counter | 없음 | Scheduler 실행 중 상태가 갱신된 건수 누적 |
| `careerops_scheduler_alio_failed_total` | `careerops.scheduler.alio.failed` | Counter | 없음 | Scheduler 실행 중 개별 item 실패 건수 누적(collect 자체 실패는 `run{result=failure}`로 별도 집계) |

`careerops.collector.*`와 겹치는 것처럼 보이지만 관측 목적이 다르다(기존
`METRICS.md`의 "careerops_job_creation_total과 careerops_collector_saved_total의
관계" 설명과 동일한 원칙) — `careerops.collector.*`는 트리거 출처 무관 총량,
`careerops.scheduler.alio.*`는 "자동 실행이 실제로 동작하고 있는가"를 사람
개입 없이 관측하기 위한 전용 지표다.

## Acceptance Criteria

`[자동]` = fixture만으로 검증, 실제 ALIO API 미호출. 저장소 루트에서
`docker compose up -d`(PostgreSQL) 기동 중이어야 한다.

- [ ] `[자동]` **재사용 확인**: `AlioCollectorService.collect(int)`,
      `CollectController`, `CollectResult`의 소스가 이번 Task로 전혀
      변경되지 않았다(git diff에 해당 파일 없음).
- [ ] `[자동]` **정상 실행**: fixture 응답으로 `AlioCollectionScheduler`의
      스케줄 메서드를 직접 호출하면, 반환값 없이 정상 종료하고
      `careerops.scheduler.alio.run{result=success}`가 1 증가하며,
      fetched/saved/skipped/updated 값이 fixture와 일치한다.
- [ ] `[자동]` **fetch 실패 처리**: fixture client가
      `AlioApiException(FETCH_ERROR)`를 던지도록 설정한 뒤 스케줄 메서드를
      호출하면, 예외가 밖으로 전파되지 않고(테스트에서 호출부가 그대로
      정상 리턴), `careerops.scheduler.alio.run{result=failure}`가 1
      증가한다.
- [ ] `[자동]` **연속 2회 호출 시 누적**: 같은 스케줄 메서드를 연속 2회
      직접 호출하면(다른 fixture 2개, 두 번째는 status 변경 등) 각 metric이
      1회차 값에 2회차 값을 더한 만큼 누적된다(덮어쓰기 아님).
- [ ] `[자동]` **기본 비활성화(테스트 컨텍스트)**: `CAREEROPS_SCHEDULER_ALIO_ENABLED`가
      설정되지 않은 일반 `@SpringBootTest` 컨텍스트(예: 기존
      `CollectControllerTest`/`JobPostingControllerTest`)에서 실제
      `@Scheduled` 빈이 6시간을 기다리지 않고도 그대로 기존처럼
      통과한다(= 테스트 실행 시간이 비정상적으로 길어지거나 스케줄러가
      테스트 중 실제로 실행되어 데이터가 오염되는 일이 없다).
- [ ] `[자동]` **설정 주입 확인**: `careerops.scheduler.alio.num-of-rows`를
      테스트 프로퍼티로 다른 값(예: 10)으로 override하면, 실제
      `alioCollectorService.collect(...)` 호출 시 그 값이 전달됨을
      (fixture client가 받은 `numOfRows` 파라미터 캡처 등으로) 검증한다.
- [ ] `[자동]` **회귀 없음**: `cd backend && ./gradlew test`가 이번 Task
      신규 테스트 포함 전체 실패 0건으로 통과한다(41건 기존 테스트 +
      신규 테스트 전부 포함).
- [ ] `[자동]` **Git tracked file에 secret 없음**: 신규 dependency 없음,
      실제 키 값이 어떤 커밋 파일에도 없다.
- [ ] `[수동]` **실제 앱 기동 확인**(선택, 가능하면 수행): `docker compose up -d` +
      `JOB_ALIO_API_KEY` 설정 후 애플리케이션을 실행해 로그에 기동 1분 뒤
      Scheduler가 1회 실행되고 `/actuator/prometheus`에
      `careerops_scheduler_alio_run_total` 등 신규 metric이 노출됨을 확인한다.

## Technical Notes

### 패키지/파일 변경 범위

```
backend/src/main/java/com/careerops/backend/
├── BackendApplication.java                         # @EnableScheduling 추가
└── collector/alio/
    └── AlioCollectionScheduler.java                # 신규

backend/src/main/resources/
└── application.yml                                 # careerops.scheduler.alio.* 추가

backend/build.gradle                                 # test task에 CAREEROPS_SCHEDULER_ALIO_ENABLED=false 추가

backend/src/test/java/com/careerops/backend/collector/
└── AlioCollectionSchedulerTest.java                # 신규
```

`AlioCollectorService.java`, `CollectController.java`, `CollectResult.java`,
`AlioJobMapper.java`, `AlioJobItem.java`, `JobPosting.java`,
`JobPostingRepository.java`, `JobPostingService.java`는 이번 Task로
**변경하지 않는다**.

### `@Scheduled`의 duration 프로퍼티 형식

`fixedDelayString`/`initialDelayString`은 순수 숫자(ms)뿐 아니라 ISO-8601
`Duration` 형식(`PT1M`, `PT6H`)도 지원한다(Spring `Duration.parse` 기반).
`application.yml` 기본값은 `PT1M`/`PT6H`로 사람이 읽기 쉽게 남긴다.

### Scheduler가 기존 `CollectController`와 다른 예외 처리를 하는 이유

`CollectController`는 `AlioApiException`을 502로 변환해 **호출자(사람)에게
알린다** — 사람이 호출하는 API이므로 실패를 즉시 알아야 한다.
`AlioCollectionScheduler`는 호출자가 없다(스케줄러 자신) — 예외를 밖으로
던지면 `@Scheduled` 메서드 실행 스레드에서 스택트레이스만 로그로 남고
애플리케이션 자체에는 영향 없지만(Spring 기본 동작), 의도를 명시적으로
드러내고 실패를 metric으로 반드시 남기기 위해 try/catch로 직접 처리한다.

### 테스트에서 `@Scheduled` 자동 실행을 어떻게 막으면서도 로직은 검증하는가

- 프로덕션 코드는 `@ConditionalOnProperty`로 `AlioCollectionScheduler` 빈
  자체의 등록 여부를 제어한다(`enabled=false`면 빈이 아예 생성되지 않아
  `@Scheduled` 등록도 없음).
- `backend/build.gradle`의 `test` task 환경변수로 **전역 기본값**을
  비활성화해 기존 `@SpringBootTest` 클래스들(`CollectControllerTest` 등)이
  영향받지 않게 한다.
- `AlioCollectionSchedulerTest`만 별도로
  `@TestPropertySource(properties = "careerops.scheduler.alio.enabled=true")`
  (또는 동등한 방식)로 그 테스트 클래스의 컨텍스트에서만 다시 켜서 빈을
  주입받고, **스케줄 메서드를 직접 호출**해 검증한다(실시간 대기 없음).
  이 방식이 정상 동작하지 않으면(예: `@ConditionalOnProperty` 평가 시점
  문제), 대안으로 `AlioCollectionScheduler`를 어떤 Spring 컨텍스트에도
  기대지 않고 순수 Java 단위 테스트(직접 `new`)로 검증해도 된다 — Codex
  판단으로 더 단순/안정적인 쪽을 선택.

### Dependency

신규 production/test dependency 없음. Spring Boot의 `@Scheduled`는
`spring-boot-starter-web`이 이미 가져오는 `spring-context`에 포함되어
있으므로 추가 dependency가 필요 없다(Codex 구현 중 만약 필요하다고
판단되면 먼저 blocker로 보고 — 이 Task의 "신규 dependency 없음" 전제와
어긋나는 신호이므로 임의로 추가하지 말 것).

## Test Plan

- `[자동]` `AlioCollectionSchedulerTest` — 위 Acceptance Criteria의 자동
  항목을 전부 커버(정상 실행/실패 처리/누적/설정 주입).
- `[자동]` 기존 `CollectControllerTest`/`JobPostingControllerTest`/
  `ManualImportControllerTest` 등 `@SpringBootTest` 전체가 회귀 없이
  통과(스케줄러가 테스트 중 실제로 발화하지 않음을 간접 검증).
- `[자동]` `cd backend && ./gradlew test` 전체 통과. 사전조건: 저장소
  루트에서 `docker compose up -d`.
- `[수동]` (선택) 실제 앱 기동 후 로그/`/actuator/prometheus`로 1회 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | COLLECT-003 명세 기반 최초 구현 지시(`AlioCollectionScheduler` 신규, `@EnableScheduling`, `application.yml`/`build.gradle` 설정, fixture 기반 테스트, 기존 collector 파일 변경 금지) | 전 파일 구현 완료. sandbox 제약(`~/.gradle` lock, Docker socket 접근 거부)으로 Codex가 직접 `./gradlew test`를 실행하지 못하고 결과 미확인 상태로 보고. Claude가 로컬(Docker Compose 기동 중 확인 후) `./gradlew test` 직접 실행 → **45건 중 44건 통과, 1건 실패**(`AlioCollectionSchedulerTest.collectsAndRecordsSuccessfulRunMetrics`, `AlioCollectionSchedulerTest.java:48`). 실패 원인은 프로덕션 로직이 아니라 테스트 코드 타입 불일치: `Timer.count()`가 `long`을 반환하는데 `double durationBefore`로 받아 `assertThat(long).isEqualTo(double)`을 비교 → AssertJ가 박싱된 `Long(4)`/`Double(4.0)`을 `equals()`로 비교해 값이 같아도 실패(`expected: 4.0 but was: 4L`) |
| 2 | 위 타입 불일치 버그의 정확한 원인/위치를 지목하고 `durationBefore`를 `long`으로 고쳐 재테스트 요청 | `durationBefore` 타입을 `long`으로 수정, 다른 assertion/프로덕션 코드는 변경 없음. sandbox 제약(`~/.gradle/...zip.lck` 쓰기 거부, 권한 승인 실패)으로 이번에도 Codex가 직접 테스트를 실행하지 못함. Claude가 로컬에서 재실행 → **45건 전체 통과**. `reviewer` subagent가 8개 보호 파일 무변경, Acceptance Criteria 전항목 충족, metric 네이밍/태그, dependency 미추가를 독립적으로 재확인(`.ai/reviews/COLLECT-003-review-1.md`). 유일한 지적은 `docs/METRICS.md` 미반영(Task 명세 내 "Metrics" 섹션과 "Technical Notes 파일 목록"이 서로 어긋나 있던 명세 자체의 불일치) — 코드/테스트 재수정 불필요 판단, Claude가 문서만 직접 보완 |
