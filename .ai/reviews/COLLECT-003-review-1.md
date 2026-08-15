---
task_id: COLLECT-003
review_round: 1
reviewer: claude
reviewed_at: 2026-08-15T22:45:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

- [x] **재사용 확인** — 충족. `git diff --stat`(저장소 루트)에
      `AlioCollectorService.java`, `CollectController.java`,
      `CollectResult.java`, `AlioJobMapper.java`, `AlioJobItem.java`,
      `JobPosting.java`, `JobPostingRepository.java`,
      `JobPostingService.java` 8개 파일이 전혀 등장하지 않음(실제 변경
      파일은 `.ai/metrics/metrics.jsonl`, `backend/build.gradle`,
      `BackendApplication.java`, `application.yml`,
      `FixtureAlioJobClient.java` 뿐이고, 신규 파일은
      `AlioCollectionScheduler.java`/`AlioCollectionSchedulerTest.java`/
      `.ai/tasks/COLLECT-003.md`). Out of Scope 제약 완전 준수.
- [x] **정상 실행** — 충족.
      `AlioCollectionScheduler.java:40-59`의 `collect()`가 반환값 없이
      정상 종료하고, `careerops.scheduler.alio.run{result=success}`
      1 증가(`AlioCollectionScheduler.java:49`), fetched/saved/skipped/
      updated 각 값을 `CollectResult`에서 그대로 증가시킴
      (`AlioCollectionScheduler.java:44-48`).
      `AlioCollectionSchedulerTest.java:34-54`
      (`collectsAndRecordsSuccessfulRunMetrics`)가 fixture
      `alio-list-response-valid.json`로 fetched/saved +2,
      skipped/updated/failed 불변, duration Timer count +1을 검증.
      독립 재실행 결과 통과(아래 테스트 결과 참고).
- [x] **fetch 실패 처리** — 충족. `AlioCollectionScheduler.java:50-52`가
      `AlioApiException`을 catch해 `run{result=failure}` 증가 후 WARN
      로그만 남기고 메서드가 정상 리턴(예외 재throw 없음).
      `AlioCollectionSchedulerTest.java:56-64`
      (`swallowsFetchFailureAndRecordsFailedRun`)가
      `assertThatCode(scheduler::collect).doesNotThrowAnyException()`로
      직접 검증.
- [x] **연속 2회 호출 시 누적** — 충족.
      `AlioCollectionSchedulerTest.java:66-82`
      (`accumulatesMetricsAcrossConsecutiveRuns`)가 서로 다른 fixture
      (`alio-list-response-valid.json` → `alio-list-response-closed.json`)로
      연속 2회 `scheduler.collect()` 호출 후 success run +2, fetched +3,
      saved +2, updated +1(누적, 덮어쓰기 아님)을 검증하고 통과.
- [x] **기본 비활성화(테스트 컨텍스트)** — 충족.
      `backend/build.gradle:43`에
      `environment 'CAREEROPS_SCHEDULER_ALIO_ENABLED', 'false'` 추가,
      `AlioCollectionScheduler.java:15`의
      `@ConditionalOnProperty(..., matchIfMissing = true)`와 결합해 일반
      `@SpringBootTest`(`CollectControllerTest`, `JobPostingControllerTest`,
      `ManualImportControllerTest`, `BackendApplicationTests` 등) 컨텍스트에서
      빈이 아예 등록되지 않음. 독립 재실행에서 전체 45건이 정상 시간 내
      통과(스케줄러로 인한 지연/오염 없음)로 확인.
- [x] **설정 주입 확인** — 충족.
      `AlioCollectionSchedulerTest.java:20-24`에서
      `@TestPropertySource(properties = {"careerops.scheduler.alio.enabled=true", ...,
      "careerops.scheduler.alio.num-of-rows=10"})`로 그 테스트 컨텍스트에서만
      재활성화 + override, `FixtureAlioJobClient.java`에 추가된
      `lastNumOfRows()` 캡처(`FixtureAlioJobClient.java:11,25,29-31`)로
      `passesConfiguredNumOfRowsToCollector`(`AlioCollectionSchedulerTest.java:84-91`)가
      실제 `alioCollectorService.collect(10)` 호출을 검증. `@ConditionalOnProperty`
      평가 시점 문제 없이 정상 동작(Technical Notes가 언급한 대안인 순수
      Java 단위 테스트로 갈 필요 없었음).
- [x] **회귀 없음** — 충족. 아래 "테스트 결과" 참고. Claude가 독립적으로
      `./gradlew test --rerun`을 재실행해 45/45 확인(Task 기록의 "45건 중
      44건→2라운드 수정 후 45건 통과" 서술과 일치).
- [x] **Git tracked file에 secret 없음** — 충족.
      `backend/build.gradle` diff에 dependency 추가 없음(`dependencies {}`
      블록 미변경, `tasks.named('test')`에 환경변수 이름/값만 추가).
      `application.yml:21`은 `${CAREEROPS_SCHEDULER_ALIO_ENABLED:true}`
      형태로 실제 값이 아닌 플레이스홀더만 포함. `git diff` 전체에 실제
      키/토큰 값 없음.
- [ ] `[수동]` **실제 앱 기동 확인** — 미검증(이번 라운드에서는 선택 항목,
      자동 리뷰 범위 밖).

## 테스트 결과

- test_count: 45
- test_pass_count: 45 (failures: 0, errors: 0)
- 실행 방법: 저장소 루트 `docker compose ps`로 PostgreSQL/Redis 기동 확인
  (기존에 이미 `Up ... (healthy)` 상태) → `careerops_test` DB 존재 확인
  (`docker compose exec postgres psql -U careerops -d careerops -c '\l'`) →
  `cd backend && (set -a && source ../.env && set +a) && ./gradlew test --rerun`으로
  캐시 우회 직접 재실행 → `BUILD SUCCESSFUL`. `build/test-results/test/*.xml`
  9개 스위트 합산(`tests=`/`failures=`/`errors=`) 45/0/0으로 재확인.
  신규 `AlioCollectionSchedulerTest`가 4건 모두 통과 포함.
  (참고: 최초 1회는 `.env`를 source하지 않은 셸에서 실행해 `POSTGRES_USER`가
  비어 있어 Flyway 연결 실패로 41건이 잘못 실패했음 — 리뷰어 환경설정 문제였고
  `.env` source 후 재실행에서 해소됨. Codex/Claude 구현 문제 아님.)

## Findings

1. **[NEEDS_REVISION] `docs/METRICS.md` 미갱신** — Task 명세 "## Metrics"
   섹션(`.ai/tasks/COLLECT-003.md:107-108`)이 "신규 metric(전부
   `AlioCollectionScheduler`에서 등록, `docs/METRICS.md` "Collector
   (COLLECT-001)" 표 아래에 새 소제목으로 추가)"라고 명시했으나, 실제
   `docs/METRICS.md`에는 `careerops.scheduler.alio.*` 7개 metric에 대한
   내용이 전혀 추가되지 않았다(`git diff -- docs/METRICS.md` 결과 없음).
   이 저장소는 JOB-001/COLLECT-001/IMPORT-001 모두 신규 metric 도입 시
   `docs/METRICS.md`에 표+설명을 함께 추가해온 확립된 관례이고
   (`docs/METRICS.md:79-131` 참고), COLLECT-003도 Metrics 섹션에서 표까지
   구체적으로 제시하며 이를 요구했다. 다만 Technical Notes의 "패키지/파일
   변경 범위" 목록(`.ai/tasks/COLLECT-003.md:170-183`)에는
   `docs/METRICS.md`가 포함되어 있지 않아 명세 내부에 경미한 불일치가
   있다 — 이는 Task 명세 자체의 누락으로 보이며, Codex의 구현 실수라기보다
   Claude가 Task 작성 시 "패키지/파일 변경 범위" 목록에서 빠뜨린 것에 가깝다.
   AC 체크리스트에 명시적 `[자동]`/`[수동]` 항목으로 존재하지 않으므로
   PASS/FAIL을 가르는 결정적 결함은 아니지만, Task의 명시적 Scope 요구를
   충족하지 못했으므로 NEEDS_REVISION으로 판정한다.
   - CLAUDE.md 기준 `docs/`는 Claude가 직접 수정 가능한 산출물이므로, 굳이
     Codex thread로 되돌리지 않고 Claude(오케스트레이터)가 직접
     `docs/METRICS.md`의 "Collector (COLLECT-001)" 표 아래에 새 소제목
     "**Scheduler (COLLECT-003)**"을 추가해 Task 명세의 표(7개 metric:
     `run`/`duration`/`fetched`/`saved`/`skipped`/`updated`/`failed`, 태그,
     계측 위치 `AlioCollectionScheduler`)를 그대로 반영하는 방식으로 처리해도
     무방하다. Codex 코드 수정은 필요 없다.
2. 과도한 추상화/불필요한 패턴 없음. `AlioCollectionScheduler`는 단일 책임
   (스케줄 트리거 + 위임 + metric 기록)만 수행하며 헬퍼 메서드
   (`counter`/`runCounter`)도 최소한으로 절제됨(`AlioCollectionScheduler.java:61-69`).
3. 신규 production/test dependency 없음(`build.gradle` diff 확인, 위 참고).
4. Secret 노출 없음(위 참고).
5. 자기소개서 관련 로직 아님 — 해당 원칙 위반 없음.
6. `@ConditionalOnProperty(matchIfMissing = true)` + `build.gradle`의 전역
   `CAREEROPS_SCHEDULER_ALIO_ENABLED=false` override + 개별 테스트의
   `@TestPropertySource` 재활성화 조합이 설계 의도대로 정확히 동작함을
   테스트 실행으로 실증 확인.
7. 예외 처리 설계(Technical Notes "Scheduler가 기존 `CollectController`와
   다른 예외 처리를 하는 이유")와 실제 구현이 정확히 일치.
8. `.ai/metrics/metrics.jsonl`의 최신 COLLECT-003 `implement` 줄
   (`test_pass_count: 44`)은 1라운드(수정 전) 결과를 반영한 것으로 보이며
   2라운드 수정/이번 review 결과(45/45)는 아직 반영되지 않은 상태다 — 이번
   리뷰 완료 후 `review` phase 줄 추가 시 최신 값(45/45, review_round=1,
   verdict=NEEDS_REVISION)으로 기록 필요(Codex 수정 대상 아님, 오케스트레이터
   기록 작업).

## 다음 액션

- **NEEDS_REVISION.** Codex thread(`01a00580-cbf2-7783-a7e0-4eafd2571a44`)에
  코드 재수정을 요청할 필요는 없음(코드 자체는 모든 자동 Acceptance
  Criteria를 충족하고 테스트 45/45 통과, 8개 보호 파일 무변경, 신규
  dependency/secret 없음을 리뷰어가 독립적으로 재실행/재확인함).
- 유일한 미해결 항목은 `docs/METRICS.md` 문서 갱신 누락이며, 이는
  애플리케이션 코드가 아니라 CLAUDE.md상 Claude가 직접 처리 가능한
  산출물이므로 **Claude(오케스트레이터)가 직접** `docs/METRICS.md`에
  "Scheduler (COLLECT-003)" 소제목 + 7개 metric 표를 추가하는 것으로
  해결 권장(Codex 재호출 불필요).
- 문서 보완 후에는 별도 코드/테스트 변경이 없으므로 재리뷰 없이 즉시
  PASS로 전환 가능하다고 판단됨(재검증 시 `docs/METRICS.md` diff만
  확인하면 충분).
