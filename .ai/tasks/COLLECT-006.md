---
task_id: COLLECT-006
title: JobPosting (source, external_id) DB UNIQUE 제약 + 동시 수집 race 방지
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-16T00:00:00+09:00
codex_thread_id: 01a009ee-ca44-7851-9407-1003e17eb20a
---

## Context

COLLECT-005 실 API 검증 과정에서 수동 수집 API(`POST /api/collect/alio`)와
`AlioCollectionScheduler`가 동시에 실행되면서 동일한 `(source, external_id)`의
`JobPosting`이 중복 저장되는 race condition이 실제로 재현됐다(dev DB에 1,370개
중복 그룹 생성, 이후 사용자가 안전하게 정리 완료 — 현재 dev DB 중복 0건).

**정확한 코드 흐름 근거(추측 아님, 코드 직접 확인)**: `backend/src/main/java/com/careerops/backend/collector/alio/AlioCollectorService.java`의
`collect()`는 신규 항목마다 `repository.findFirstBySourceAndExternalId(...)`로
존재 확인 후 없으면 `jobPostingService.create(...)`로 저장한다. 이 프로젝트
전체에 `@Transactional`이 단 한 곳도 없다(`grep -rn "@Transactional"
backend/src/main/java` 결과 0건) — `AlioCollectorService.collect()`를 감싸는
상위 트랜잭션이 없으므로, `findFirstBySourceAndExternalId`와 그 뒤의
`repository.save()`는 Spring Data JPA(`SimpleJpaRepository`)가 각각 독립적으로
여는 별개의 짧은 트랜잭션이다. 그 사이 창(window)에 다른 run이 끼어들면 둘 다
"없음"을 보고 둘 다 INSERT에 성공해 중복 행이 생긴다. `JobPosting.id`가
`GenerationType.IDENTITY`라 Hibernate가 `persist()` 시점에 즉시 INSERT를
실행하므로, 위반이 있다면 그 INSERT가 일어나는 `save()` 호출 그 자리에서
`DataIntegrityViolationException`으로 나타난다 — 그리고 그 트랜잭션은 이미
독립적으로 닫혀 있으므로(상위 트랜잭션이 rollback-only로 오염될 걱정 없음)
호출부에서 안전하게 catch하고 canonical row를 재조회해 합류할 수 있다(설계
근거는 이번 Task를 시작하기 전 사용자에게 보고하고 승인받았다).

이번 Phase의 목표는 "동시에 여러 ALIO collection run이 실행되더라도 동일한
`source + external_id`의 `JobPosting`이 두 행 이상 생성되지 않도록 DB 수준의
데이터 무결성을 보장하고, 애플리케이션이 그 충돌을 정상적으로 처리하도록
만드는 것"이다.

**사용자 승인된 설계 선택(모두 사전 질의 후 확정)**:

1. **DB UNIQUE + JVM in-process 단일 run lock** 둘 다 채택(A안이 아닌 B안).
   UNIQUE 제약이 correctness를 보장하는 유일한 근거이고, run lock은 외부 API
   중복 호출/부하를 줄이는 optimization이자 detail enrichment race의 창을
   사실상 닫는 부수효과용이다 — lock이 correctness의 필수 조건은 아니다.
2. 이미 collection run이 실행 중일 때 수동 API 요청이 들어오면 **즉시 거절
   (HTTP 409)** 한다.
3. `AlioDetailEnrichmentService`의 동시성 race(같은 미보강 공고를 두 run이
   동시에 발견 시 `detail.do` 중복 호출 + `persistDetail()` 트랜잭션 전체
   롤백으로 `detailFetchedAt` 갱신이 지연될 수 있음 — 기존 COLLECT-004
   UNIQUE 제약 덕분에 데이터 손상 자체는 없음)는 **이번 Phase에서 다루지
   않는다**. 진짜 고치려면 `persistDetail()`의 트랜잭션 경계를 step/file
   단위로 재구조화해야 하는데(Postgres는 트랜잭션 안에서 한 statement가
   실패하면 그 트랜잭션 전체가 aborted 상태가 되어 같은 트랜잭션 안에서
   catch-and-continue가 안 됨 — 단순 catch로 되는 "작은 수정"이 아님), 이건
   `AlioDetailEnrichmentService`의 트랜잭션 구조 자체를 바꾸는 별도 범위라
   후속 Task 후보로 분리한다(`docs/ROADMAP.md`에 기록).

## Scope

### 1. DB UNIQUE 제약 — `V4__add_job_postings_source_external_id_unique.sql`

```sql
ALTER TABLE job_postings
    ADD CONSTRAINT uk_job_postings_source_external_id UNIQUE (source, external_id);
```

- Plain UNIQUE(파샬 인덱스 아님) — `external_id`는 nullable이고
  `ManualImportService`는 항상 `externalId=null`을 저장하지만, PostgreSQL은
  UNIQUE 제약에서 NULL끼리 서로 다른 값으로 취급하므로 문제없다(dev DB에
  이미 `MANUAL/NULL` 2건이 공존 확인됨).
- 데이터 정리/삭제 SQL을 포함하지 않는다. 실제 중복이 있는 환경에서는
  migration이 실패해야 한다(조용히 삭제 금지).
- 적용 전 반드시 dev DB(`careerops`)에 현재 `(source, external_id)` 중복
  그룹이 0건임을 재확인한 뒤 적용한다(사용자가 이미 정리했다고 확인했지만
  migration 적용 직전에 다시 한번 조회로 검증).

### 2. `JobPostingService` — conflict 발생 시 canonical row로 합류

`repository.save()`가 `DataIntegrityViolationException`(Spring이 변환한 unique
violation)을 던지면 catch하고 `findFirstBySourceAndExternalId`로 재조회해
반환하는 메서드를 추가한다. 기존 `create()`는 그대로 유지(다른 호출부인
`ManualImportService`가 계속 쓸 수 있게, 이름 그대로).

```java
public record CreateOutcome(JobPosting jobPosting, boolean isNew) {}

public CreateOutcome createOrGetExisting(JobPostingCreateRequest request) {
    try {
        JobPosting saved = create(request); // 기존 create() 재사용
        return new CreateOutcome(saved, true);
    } catch (DataIntegrityViolationException e) {
        JobPosting existing = repository
                .findFirstBySourceAndExternalId(request.source(), request.externalId())
                .orElseThrow(() -> e); // 방어적: 정말 못 찾으면 원래 예외 재던짐
        conflictCounter.increment();
        return new CreateOutcome(existing, false);
    }
}
```

신규 Counter `conflictCounter` = `careerops.collector.conflict`(tag
`source`)를 `JobPostingService`에 추가한다(생성자에서 등록, `createdCounter`
등록 패턴 그대로 따름). `ManualImportService`는 변경하지 않는다(URL 기반
중복 체크만 쓰고 있어 이번 race와 무관 — 동시에 같은 URL로 두 번 수동
등록하는 시나리오는 이번 Task 범위 밖).

### 3. `AlioCollectorService` — 신규 저장 분기 교체 + run-level lock

- 신규 저장 분기(`collect()` 내부, `jobPostingService.create(request)` 호출
  지점)를 `jobPostingService.createOrGetExisting(request)`로 교체한다.
  `isNew=false`로 돌아오면 기존 "existing" 분기와 **동일한 로직**(status가
  다르면 `updateStatus` + `updated++`, 같으면 `skipped++`, 이어서
  `enrichIfNeeded`)을 타도록 합류시킨다 — 신규/충돌 두 분기가 중복 코드를
  갖지 않도록 공통 private 메서드로 추출.
- `collect(int numOfRows)` 전체를 감싸는 **JVM in-process 단일 run lock**을
  추가한다: `java.util.concurrent.locks.ReentrantLock`(non-fair 기본)의
  `tryLock()`(non-blocking)을 `collect()` 진입 시 시도하고, 실패하면 신규
  예외 `AlioCollectionInProgressException`(RuntimeException, 새 파일
  `collector/alio/AlioCollectionInProgressException.java`, `AlioApiException`
  과 무관한 별개 타입 — ALIO API 실패가 아니라 락 경합이므로)을 던진다.
  성공 시 `finally`에서 반드시 `unlock()`. 이 lock은 `AlioCollectorService`
  싱글톤 빈의 인스턴스 필드이므로 수동 API(`CollectController`)와
  Scheduler(`AlioCollectionScheduler`) 양쪽 호출 경로를 자동으로 함께
  커버한다(두 호출 모두 결국 이 `collect()`를 호출하므로 별도 위치에 락을
  중복으로 둘 필요 없음).
- 락 경합으로 즉시 반환할 때 `careerops.collector.run{source=alio,
  result=skipped_locked}`를 increment한다(기존 `runCounter(String result)`
  헬퍼 재사용, 새 tag 값 추가만 — 신규 메트릭 아님, 기존 `success`/`failed`
  조회에 영향 없음).

### 4. `CollectController` — 락 경합 시 HTTP 409

```java
} catch (AlioCollectionInProgressException exception) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "ALIO collection already in progress");
}
```
기존 `AlioApiException` catch 앞(또는 뒤, 타입이 겹치지 않으므로 순서 무관)에
추가. 이 409 응답은 이번 Task로 승인된 새 API 계약이다(기존 200/400/502
응답 코드는 변경 없음).

### 5. `AlioCollectionScheduler` — 락 경합을 실패로 집계하지 않음

`AlioCollectionInProgressException`을 `AlioApiException`/일반
`RuntimeException` catch보다 먼저 별도로 잡아 INFO 로그만 남기고
`careerops.scheduler.alio.run{result=skipped}`을 increment(기존
`runCounter(String result)` 재사용, 새 tag 값 추가 — 신규 메트릭 아님).
`failure`로 집계하지 않는다(정상적인 경쟁 상황이지 장애가 아니므로).

## Out of Scope

- `AlioDetailEnrichmentService`의 트랜잭션 재구조화(위 Context 3번 참고) —
  후속 Task 후보로 `docs/ROADMAP.md`에만 기록.
- 분산 락, Redis 락, multi-instance coordination, queue.
- ON CONFLICT/native SQL 도입(이번엔 exception catch/re-read 채택 — 이유는
  Technical Notes 참고).
- `ManualImportService`의 동시성 강화(URL 기반 dedup은 이번 race와 무관).
- 전체 히스토리 백필, steps/attachments 조회 API 노출, 상세정보 주기적
  재동기화, 사람인, cross-source dedup, 지원현황, PKB, matching, 자기소개서
  Agent, 알림, frontend, dev DB 자동 dedup/delete.

## Acceptance Criteria

`[자동]` = fixture/careerops_test 기반, 외부 ALIO API 미접근. `[수동]` = 실
서비스키 또는 실제 동시 HTTP 요청으로 사람이 직접 확인.

- [ ] `[자동]` **UNIQUE 제약 실제 적용**: `careerops_test`에서 동일
      `(source, external_id)`로 `repository.saveAndFlush()`를 두 번(순차)
      호출하면 두 번째 호출이 `DataIntegrityViolationException`을 던진다.
- [ ] `[자동]` **순차 재수집 시 중복 없음(회귀)**: 같은 fixture로 `collect()`를
      두 번 연속 호출해도 두 번째에서 `JobPostingRepository.count()`가
      늘지 않는다(기존 COLLECT-001~005 동작 유지).
- [ ] `[자동]` **동시 신규 발견 시 최종 행 수 = 1**: 클래스 레벨
      `@Transactional` 없이(다른 스레드가 commit을 봐야 하므로) 두 스레드가
      `CyclicBarrier`로 타이밍을 맞춰 동일한 신규
      `(source="ALIO", externalId=X)`에 대해 `JobPostingService.createOrGetExisting(...)`을
      동시 호출 → 최종 `repository.count()`(해당 externalId 기준)가 정확히
      1, 정확히 한쪽만 `isNew=true`, 양쪽 `jobPosting.getId()`가 동일, 양쪽
      호출 모두 예외 없이 정상 반환.
- [ ] `[자동]` **conflict metric**: 위 동시성 테스트에서
      `careerops.collector.conflict{source=alio}`가 정확히 1 증가.
- [ ] `[자동]` **conflict가 전체 collection run을 실패시키지 않음**: 겹치는
      신규 item이 포함된 다중 페이지 fixture로 `AlioCollectorService.collect()`를
      두 스레드에서 동시 호출 → 양쪽 `CollectResult.result()`가 모두
      `"success"`(하나가 500/예외로 끝나지 않음), 최종 `repository.count()`
      증가분이 딱 신규 건수만큼(중복만큼 더 늘지 않음).
- [ ] `[자동]` **run-level lock 동작**: 첫 번째 `collect()` 호출이 진행
      중일 때(예: fixture client에 지연을 주거나 lock을 미리 점유한 상태로
      테스트) 두 번째 `collect()` 호출이 `AlioCollectionInProgressException`을
      즉시 던진다(대기하지 않음). lock 해제 후에는 다음 `collect()` 호출이
      정상 처리된다.
- [ ] `[자동]` **`CollectController` 409**: `POST /api/collect/alio` 요청이
      진행 중인 상태에서 두 번째 요청을 보내면 HTTP 409를 반환하고
      `JobPostingRepository.count()`가 늘지 않는다(락 경합으로 아무것도
      처리하지 않았으므로).
- [ ] `[자동]` **`AlioCollectionScheduler` 회귀 + 락 경합 시 failure 아님**:
      기존 `AlioCollectionSchedulerTest` 전부 통과. 락이 이미 점유된 상태에서
      `scheduler.collect()`를 호출하면 `careerops.scheduler.alio.run{result=failure}`가
      증가하지 않고 `{result=skipped}`가 증가한다.
- [ ] `[자동]` **기존 status 갱신 동작 회귀 없음**: 다중 페이지 중 기존 공고의
      상태가 바뀌는 케이스가 신규 코드 경로 변경 후에도 그대로 동작한다.
- [ ] `[자동]` **detail enrichment 회귀 없음**: `detailFetchedAt`이 이미
      설정된 공고는 재수집 시 `detail.do` 재호출이 없다(COLLECT-004/005
      동작 유지, `createOrGetExisting` 도입으로 인한 회귀 없음 명시적
      확인).
- [ ] `[자동]` **pagination 회귀 없음**: 기존 `AlioCollectorServiceTest`의
      다중 페이지 테스트(COLLECT-005) 전부 통과.
- [ ] `[자동]` **`GET /api/jobs` 회귀 없음**: 기존 `JobPostingControllerTest`/
      `JobPostingRepositoryTest` 전부 통과.
- [ ] `[자동]` **`./gradlew test` 전체 통과**: `careerops_test` DB 격리
      유지(ADR-0010), dev DB(`careerops`) 데이터 삭제/오염 없음. 신규
      concurrency 테스트는 `@Transactional`/`@Rollback` 없이 작성되므로
      `@AfterEach`(또는 동등한 지점)에서 자신이 만든 행만 명시적으로 정리한다.
- [ ] `[수동]` **실제 동시 실행 재현**: `numOfRows`를 크게 주고 `POST
      /api/collect/alio`를 거의 동시에 두 번 호출(또는 Scheduler 실행 중
      수동 API 호출)해 두 번째가 409를 받는지, dev DB에 중복 그룹이 생기지
      않는지 확인한다.
- [ ] `[수동]` **최종 dev DB 중복 그룹 0건 확인**: migration 적용 전후로
      `SELECT source, external_id, count(*) FROM job_postings GROUP BY 1,2
      HAVING count(*) > 1;`이 0행임을 재확인한다.

## Technical Notes

### 1. exception catch/re-read를 선택한 이유 (ON CONFLICT 대신)

`JobPosting.id`는 `GenerationType.IDENTITY`라 `repository.save()`는
`entityManager.persist()` 호출 즉시 INSERT를 실행하고, 그 INSERT는
`SimpleJpaRepository.save()` 자신이 여는 독립적인 짧은 트랜잭션 안에서
일어난다. `AlioCollectorService.collect()`에는 이를 감싸는 상위
`@Transactional`이 없으므로(프로젝트 전체에 `@Transactional`이 없음), 그
트랜잭션은 예외가 호출부까지 전파되는 시점에는 이미 rollback되고 닫혀
있다 — 즉 "rollback-only로 오염된 상위 트랜잭션에서 계속 진행"하는 문제
없이, 호출부에서 안전하게 catch하고 별도의(새) 트랜잭션으로 재조회할 수
있다. `INSERT ... ON CONFLICT`는 native SQL을 요구하고(이 프로젝트는 지금
까지 native SQL을 쓴 적이 없음), 결과 row를 다시 로드하는 후속 조회가
어차피 필요해 왕복이 줄지도 않으며, `@CreationTimestamp` 같은 Hibernate
콜백이 native insert 경로에서 동작하지 않아 별도 처리가 필요해진다.

### 2. run-level lock은 `AlioDetailEnrichmentService`를 직접 고치지 않는다

lock이 `collect()` 전체(목록 페이지 순회 + inline detail enrichment 포함)를
감싸므로, 단일 인스턴스 안에서는 두 run이 동시에 같은 미보강 공고에 대해
`enrich()`를 호출하는 상황 자체가 사실상 사라진다(부수효과). 하지만 이건
"race가 발생할 확률을 낮추는 것"이지 `AlioDetailEnrichmentService` 내부의
구조적 취약점(트랜잭션 경계)을 고치는 것은 아니다 — `AlioDetailEnrichmentService`
코드는 이번 Task에서 전혀 수정하지 않는다.

### 3. `AlioCollectionInProgressException`을 `AlioApiException`과 분리하는 이유

`AlioApiException`은 `Reason` enum(`FETCH_ERROR`/`PARSE_ERROR`)으로 "ALIO API
호출/파싱 실패"를 표현한다. 락 경합은 ALIO API와 무관한 애플리케이션 내부
상태이므로 별도 타입으로 분리해, `CollectController`/`AlioCollectionScheduler`
양쪽에서 명확히 다른 분기(409 vs 502, skipped vs failure)로 처리한다.

### 4. `ReentrantLock`을 선택한 이유(다른 동시성 도구 대신)

단일 인스턴스 MVP, 신규 dependency 불필요, `tryLock()`의 non-blocking
의미가 "즉시 거절" 요구사항과 정확히 일치한다. `synchronized`로도 비슷하게
만들 수 있지만 non-blocking 시도(`tryLock`)가 없어 별도 플래그 관리가
필요해지므로 `ReentrantLock`이 더 직접적이다.

### 5. `JobPostingService.createOrGetExisting()`이 예외를 재던지는 방어 코드

`findFirstBySourceAndExternalId`로 재조회했는데도 못 찾는 경우(이론적으로는
불가능해야 함 — 방금 unique violation이 났다는 것은 동일 키 row가 존재한다는
뜻)는 원래의 `DataIntegrityViolationException`을 그대로 재던진다. 이 경로는
정상 동작에서 도달하지 않아야 하며, 도달한다면 그 자체가 더 근본적인
버그(예: 다른 프로세스가 그사이 그 row를 삭제) 신호이므로 조용히 삼키지
않는다.

## Test Plan

- `[자동]` `JobPostingRepositoryTest`(또는 신규 `JobPostingServiceTest`)에
  UNIQUE 제약 직접 검증 + `createOrGetExisting()` 단위 테스트 추가.
- `[자동]` 신규 concurrency 테스트 클래스(`AlioCollectorConcurrencyTest` 또는
  유사 이름, `careerops_test` 대상, 클래스 레벨 `@Transactional` 사용 금지 —
  스레드 간 commit 가시성이 필요하므로) — 위 Acceptance Criteria의 동시성
  케이스 전부.
- `[자동]` 기존 `AlioCollectorServiceTest`/`AlioCollectionSchedulerTest`/
  `CollectControllerTest`/`JobPostingControllerTest`/`JobPostingRepositoryTest`
  회귀 확인(필요한 곳만 최소 수정 — 예: `CollectControllerTest`에 409 케이스
  추가).
- `[자동]` `cd backend && ./gradlew test` 전체 통과, `careerops_test` DB
  격리 유지, dev DB 오염 없음.
- `[수동]` 실제 동시 HTTP 호출/Scheduler 겹침 재현, dev DB 최종 중복 그룹
  0건 재확인, Prometheus에서 신규/변경 metric 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | 첫 구현 지시(V4 UNIQUE migration, `JobPostingService.createOrGetExisting()` + conflict counter, `AlioCollectorService` run-level lock + 충돌 합류, `CollectController` 409, `AlioCollectionScheduler` skip 집계, concurrency 테스트, `docs/METRICS.md`) | `AlioCollectionInProgressException`(신규) + 위 10개 파일 변경 + `AlioCollectorConcurrencyTest`(신규) 완료. Codex 자체 sandbox에서 `./gradlew test` 실행이 파일 락/권한 문제로 차단(self-report, COLLECT-005와 동일 패턴). Claude가 직접 실행해 64개 중 62개 통과, 2개 실패 확인(`JobPostingControllerTest`/`JobPostingRepositoryTest`의 `save()` 테스트 헬퍼가 `externalId`로 `companyName`을 재사용해 신규 UNIQUE 제약과 충돌) → `reviewer` subagent 1차 리뷰 NEEDS_REVISION(`.ai/reviews/COLLECT-006-review-1.md`) |
| 2 | 1차 리뷰 수정 요청(두 테스트 헬퍼의 `externalId`를 `companyName`과 분리해 호출마다 유일한 값으로 변경) | `JobPostingControllerTest.java`/`JobPostingRepositoryTest.java` 2개 파일만 수정(`UUID.randomUUID().toString()`로 교체, 다른 필드/assertion 불변 확인). Codex sandbox에서 이번에도 테스트 실행 차단(self-report). Claude가 직접 재실행해 64/64 통과 확인(`BUILD SUCCESSFUL`) → 2차 리뷰 PASS(`.ai/reviews/COLLECT-006-review-2.md`) |
