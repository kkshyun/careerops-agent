---
task_id: COLLECT-005
title: ALIO 목록 API(list.do) pagination 완성 — page/numOfRows 범위 밖 공고 누락 방지
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-16T00:00:00+09:00
codex_thread_id: 01a009a0-e30f-7301-bb52-ddbdd0287dfd
---

## Context

COLLECT-001부터 `AlioCollectorService.collect(int numOfRows)`는 ALIO
`list.do`를 `pageNo=1`로만 고정 호출해왔다. 즉 `numOfRows`(수동 API
기본값 50, Scheduler 기본값 50)가 그 실행에서 볼 수 있는 전체 범위였고,
그 밖(“page 1 범위 밖”)의 공고는 COLLECT-004(ADR-0013)에서 “재발견 시에만
detail 보강”이라는 의도된 제약과 함께 문서화된 대로, 신규 저장이든 상태
갱신이든 애초에 목록에 잡히지 않으면 영원히 갱신되지 않는다.

이번 Task는 `list.do`의 pagination을 정확히 구현해, 호출자가 지정한 범위
(`numOfRows` = 이번 호출에서 처리할 최대 총 건수) 안에서는 여러 페이지를
순회해도 공고를 빠뜨리거나 중복 저장하지 않도록 만든다. Scheduler의 실제
운영 범위(6시간마다 얼마나 훑을지)는 사용자 승인 사항으로, **전체
112,920건 전수 순회가 아니라 기본 5,000건(설정 가능)** 으로 제한한다 —
detail enrichment가 신규/미보강 공고 저장 직후 즉시 실행되는 현재 구조상
(COLLECT-004), 전수 순회는 한 번에 대량의 `detail.do` 호출을 유발할 수
있어 이번 Phase에서는 배제한다. 전체 히스토리 백필은 별도 운영 작업으로
남긴다(`docs/ROADMAP.md` Phase 5 이후 후보 참고).

## 실측으로 확인한 ALIO `list.do` pagination 계약 (2026-08-16, 실 서비스키 직접 호출, secret 미노출)

- `pageNo`는 정상적으로 페이지를 이동시킨다. 인접 페이지 간 중복 없음(실측:
  `pageNo=1`/`pageNo=2`, 각 1000건씩 요청 시 교집합 0건).
- `numOfRows`는 서버가 **1000으로 조용히 캡(cap)** 한다 — `1001`/`3000`/
  `5000`/`10000`을 요청해도 전부 정확히 1000건만 돌아오고 에러 없음.
  문서화된 값이 아니라 실측으로만 확인됨.
- `totalCount`가 응답에 항상 존재하고 신뢰 가능하다(이번 세션 내 모든
  호출에서 `112920`으로 일관, `numOfRows=50` 기준 마지막 페이지(`pageNo=2259`)가
  정확히 `20`건(=112920−2258×50) 반환해 계산과 정확히 일치).
- 마지막 페이지 이후 호출(`pageNo=2260`, `99999`)은 에러가 아니라
  `resultCode=200` + `result=[]`.
- `pageNo=0`/음수도 에러가 아니라 `resultCode=200` + `result=[]`.
- 필터 없는 기본 조회는 `ongoingYn=Y`(진행)/`N`(마감) 둘 다 반환한다(실측:
  1000건 중 Y=433, N=567).
- 문서화된 호출 제한(rate limit) 없음(COLLECT-004와 동일 결론, 응답
  헤더에도 없음).

### ⚠️ 신규 발견 — 동일 run 내에서 `numOfRows`(페이지 크기)를 바꾸면 오프셋이 깨진다

서버는 `offset = (pageNo-1) × 이번_요청의_numOfRows`로 계산하는 것으로
보인다. 실측: `pageNo=1,numOfRows=1000` 다음 `pageNo=2,numOfRows=1000`(크기
고정)은 정상 연속이지만, `pageNo=1,numOfRows=1000` 다음
`pageNo=2,numOfRows=500`(크기 변경)은 page1에 이어지는 항목이 아니라 전혀
다른(더 앞쪽) 구간을 반환한다.

**→ 한 번의 collection run 안에서는 서버로 보내는 `numOfRows`를 절대
바꾸지 않는다.** "마지막 페이지는 남은 개수만큼만 작게 요청"하는 방식은
쓸 수 없다 — 항상 고정 크기로 요청하고, 짧은/빈 응답으로만 종료를
판단한다. 호출자가 지정한 총 상한(`numOfRows` 파라미터)에 도달해 페이지
중간에서 멈춰야 하면, 서버에 더 작은 페이지를 다시 요청하지 않고
**그 페이지의 응답 리스트를 클라이언트 측에서 슬라이싱**해서 처리를
멈춘다.

## Scope

1. **`AlioCollectorService.collect(int numOfRows)` — 시그니처/외부 계약
   불변, 내부만 pagination 루프로 재작성**:
   - `numOfRows`는 기존과 동일하게 "이 호출에서 처리할 최대 총 건수"를
     의미한다(API 하위호환 — `numOfRows=50` 요청 시 기존과 동일하게 1페이지,
     50건만 처리).
   - 내부 페이지 크기(서버에 보내는 `numOfRows`)는 `min(callerNumOfRows,
     1000)`으로 **run 전체에서 고정**한다.
   - `pageNo=1`부터 시작해 순차 호출, 각 페이지의 item을 기존 3분기
     로직(신규 저장/status 갱신/skip) + `enrichIfNeeded()`로 그대로 처리한다
     (COLLECT-004 detail enrichment 로직 재구현 금지).
   - 누적 처리 건수가 `callerNumOfRows`에 도달하면(마지막 페이지 중간이라도)
     그 지점에서 멈춘다(서버에 재요청하지 않고 응답 리스트를 슬라이싱).
   - 종료 조건(우선순위 순): (a) 누적 처리 건수가 `callerNumOfRows` 도달,
     (b) 응답 `result`가 비어있거나 페이지 크기보다 작음(마지막 페이지,
     최종 신뢰 기준 — `totalCount`가 run 도중 신규 공고로 늘어나 stale할
     수 있으므로 이 신호를 항상 우선), (c) 안전장치(아래) 발동.
   - 안전장치(무한 루프 방지, 회로차단기 성격): `maxPages =
     ceil(첫 페이지 응답의 totalCount / pageSize) + 5`(버퍼). 정상 상황에서는
     결코 발동하지 않는 값 — API 계약을 대신하는 임의의 작은 상한이
     아니다. 발동 시 WARN 로그를 남기고 그 시점까지 처리한 결과로 정상
     종료한다(예외 아님, 데이터 손실 신호이므로 로그로만 남김).
2. **`AlioCollectionScheduler`는 코드 변경 없음** — 주입되는
   `careerops.scheduler.alio.num-of-rows` 설정값만 **기본 `50` →
   `5000`으로 변경**(사용자 승인, 전체 112,920건 전수 순회 아님. 필요 시
   환경변수/설정으로 변경 가능한 구조 유지, 이미 `@Value` 기반이라 코드
   변경 불필요).
3. **신규 metric 1개**: `careerops.collector.pages`(Counter, tag `source`) —
   페이지 하나를 성공적으로 가져올 때마다 +1. pagination이 실제로 여러
   페이지를 도는지 관찰하기 위한 최소 지표.
4. **`FixtureAlioJobClient` 확장**: 현재 `pageNo` 무관 단일 응답만 반환하는
   구조를 pageNo별 순차 응답(또는 map)으로 확장하고, 특정 page에서
   예외를 던지도록 설정할 수 있게 한다(중간 페이지 실패 테스트용). 기존
   메서드(`respondWith`, `failWith`, detail 관련)는 하위호환 유지 — 기존
   단일 페이지 테스트가 깨지지 않아야 한다.
5. **신규 fixture JSON**(2~3개): 다중 페이지 정상 케이스(예: page1 가득,
   page2 partial), 빈 페이지 케이스.

## Out of Scope

- 전체 112,920건 전수 순회/백필(수동이든 자동이든) — 별도 운영 작업.
- 분산 락, multi-instance scheduler, queue, 비동기/병렬 page 호출,
  Reactor/WebFlux 전환.
- Retry 프레임워크, 즉시 재시도 로직.
- ALIO 상세정보 주기적 재동기화, steps/attachments 조회 API 노출,
  기관유형 매핑, 전체 필드 refresh 정책 변경, cross-source dedup,
  dev DB 백필/삭제, 운영 배포.
- `CollectController`/`CollectResult` API 시그니처 변경.
- `AlioDetailEnrichmentService` 로직 변경(재사용만, 재구현 금지).

## Acceptance Criteria

`[자동]` = fixture 기반, 외부 ALIO API 미접근. `[수동]` = 실 서비스키로
직접 확인.

- [ ] `[자동]` **여러 페이지 정상 수집**: `numOfRows`(예: 120)가 서버
      페이지 크기(예: 50으로 fixture 구성)보다 커서 3페이지가 필요한
      상황에서, 3페이지 전부(각 50/50/20건) 순회해 총 120건 이하(마지막
      페이지가 짧으면 그만큼)를 처리하고, `pageNo`마다 **동일한
      numOfRows**로 요청했는지 검증한다.
- [ ] `[자동]` **누적 상한 도달 시 페이지 중간에서 슬라이싱 종료**: 페이지
      크기(예: 50)로 여러 페이지가 있는데 `callerNumOfRows`(예: 70)가 정확히
      페이지 경계 중간에 걸치는 경우, 2페이지째 응답 중 앞의 20건만
      처리하고(50+20=70) 서버에 크기가 다른 페이지를 재요청하지 않는다.
- [ ] `[자동]` **마지막 partial page 정상 종료**: 마지막 페이지가
      페이지 크기보다 작게 오면 그다음 페이지를 요청하지 않고 정상
      종료한다.
- [ ] `[자동]` **빈 페이지 처리**: 어떤 페이지가 빈 배열을 반환하면 그
      자리에서 종료하고, 그 이전 페이지들의 처리 결과는 유지된다.
- [ ] `[자동]` **중간 page 실패**: page1~2 성공 후 page3에서
      `AlioApiException`이 발생하면, page1~2에서 저장/갱신된 `JobPosting`은
      DB에 그대로 남고(`repository.count()`로 검증), `collect()`는 예외를
      propagate하며, `careerops.collector.run{result=failed}`가 증가하고
      `success`로는 기록되지 않는다. page4는 호출되지 않는다.
- [ ] `[자동]` **반복 전체 수집 시 중복 없음**: 같은 다중 페이지 fixture로
      `collect()`를 두 번 연속 호출해도 `JobPostingRepository.count()`가
      두 번째 호출에서 늘지 않는다(전부 skip).
- [ ] `[자동]` **기존 공고 status 갱신**: 다중 페이지 중 한 페이지에 있는
      기존 공고의 상태가 바뀌면 그 페이지 처리 중 정상 갱신된다(단일
      페이지 때와 동일 동작, 페이지 경계와 무관).
- [ ] `[자동]` **미보강 공고 detail enrichment**: 여러 페이지에 걸쳐
      `detailFetchedAt == null`인 공고가 섞여 있어도 각 페이지 처리 시점에
      정상 보강되고, 이미 보강된 공고는 재호출되지 않는다(COLLECT-004
      동작 회귀 없음).
- [ ] `[자동]` **`numOfRows`가 페이지 크기 이하인 기존 호출 하위호환**:
      `numOfRows=50`(기존 기본값)로 호출 시 여전히 1페이지만 호출되고
      기존 `AlioCollectorServiceTest`의 기존 단정(assertion)이 코드 변경
      없이 그대로 통과한다(회귀 없음).
- [ ] `[자동]` **`careerops.collector.pages` metric**: 3페이지를 순회한
      run 이후 이 Counter(tag `source=alio`)가 3 증가했음을 확인한다.
- [ ] `[자동]` **Scheduler 회귀 없음**: `AlioCollectionSchedulerTest`가
      기존 그대로 통과한다(`AlioCollectionScheduler` 코드 변경이 없으므로
      회귀 위험 낮음, 그래도 명시적으로 재확인).
- [ ] `[자동]` **`GET /api/jobs` 검색 API 회귀 없음**: 기존 `JobPosting`
      조회/필터 테스트가 그대로 통과한다.
- [ ] `[자동]` **`./gradlew test` 전체 통과**: `careerops_test` DB 격리
      유지, dev DB(`careerops`) 데이터 삭제/오염 없음.
- [ ] `[수동]` **실 API로 실제 다중 페이지 수집 확인**: `numOfRows`를
      1000보다 크게(예: 2500) 설정해 `POST /api/collect/alio` 실행,
      실제로 3페이지(1000+1000+500)를 순회해 응답 `fetched`가 기대치와
      일치하는지, `/actuator/prometheus`에서 `careerops_collector_pages_total`이
      3 증가했는지 확인한다.
- [ ] `[수동]` **반복 실행 멱등성(실 데이터)**: 같은 호출을 다시 실행해
      DB 건수가 새 공고만큼만 증가하고 기존 건은 중복 저장되지 않는지
      확인한다.
- [ ] `[수동]` **Scheduler 기본값(5000) 동작 확인**: 앱을 기동해 첫
      Scheduler 실행(기동 1분 뒤)이 5000건 상한으로 여러 페이지를
      정상 순회하는지, 소요 시간이 6시간 주기 대비 합리적인지 확인한다.

## Technical Notes

### 1. 페이지 순회 의사코드

```
pageSize = min(callerNumOfRows, 1000)   // run 전체에서 고정, 절대 안 바뀜
pageNo = 1
totalProcessed = 0
knownTotalCount = null
maxPages = null  // 첫 응답에서 totalCount 확인 후 계산

loop:
  response = client.fetchList(pageNo, pageSize)
  if knownTotalCount == null:
      knownTotalCount = response.totalCount()
      maxPages = ceil(knownTotalCount / pageSize) + 5
  items = response.result() ?? []

  remaining = callerNumOfRows - totalProcessed
  itemsToProcess = items.size() <= remaining ? items : items.subList(0, remaining)
  // itemsToProcess를 기존 3분기 로직 + enrichIfNeeded로 처리, 카운터 누적
  totalProcessed += itemsToProcess.size()
  pagesCounter.increment()  // 신규 metric

  if totalProcessed >= callerNumOfRows: break
  if items.size() < pageSize: break        // 마지막 페이지(최종 신뢰 기준)
  if pageNo >= maxPages: log.warn(...); break  // 안전장치

  pageNo++
```

### 2. `FixtureAlioJobClient` 확장 방향(Codex 재량, 아래는 최소 요구)

- pageNo → 응답 매핑(또는 호출 순서대로 소비하는 큐) 지원 추가.
- 특정 pageNo에서 예외를 던지도록 설정하는 메서드 추가(중간 페이지 실패
  테스트용).
- 호출마다 `(pageNo, numOfRows)` 기록을 남겨, "동일 run 내 numOfRows
  고정" 검증에 사용할 수 있게 한다(예: `List<int[]> capturedCalls()` 또는
  유사한 형태 — 기존 `lastNumOfRows()`를 대체하거나 확장).
- 기존 단일 응답 API(`respondWith`, `failWith`)는 하위호환 유지 — 기존
  COLLECT-004 테스트가 수정 없이 통과해야 한다.

### 3. 왜 서버에 "남은 개수만큼" 작은 페이지를 재요청하지 않는가

위 "실측으로 확인한 pagination 계약" 섹션의 오프셋 버그 때문이다. 실제
검증 로그: `pageNo=1,numOfRows=1000`의 마지막 5건이 `[302901..302897]`인
상태에서 `pageNo=2,numOfRows=500`(크기 변경)을 호출하면 `[303423..303419]`가
돌아온다(page1과 이어지지 않고, 오히려 더 앞쪽 구간). 반드시 이 Technical
Note를 Codex 구현 시 그대로 전달해 같은 실수를 반복하지 않게 한다.

### 4. 설정값 변경

`backend/src/main/resources/application.yml`:

```yaml
careerops:
  scheduler:
    alio:
      num-of-rows: 5000   # 기존 50 → 사용자 승인, 전수(112,920건) 아님
```

## Test Plan

- `[자동]` `AlioCollectorServiceTest`에 다중 페이지 시나리오 추가(위
  Acceptance Criteria 전부).
- `[자동]` `AlioCollectionSchedulerTest`, `CollectControllerTest`,
  `JobPosting` 조회 테스트 회귀 확인.
- `[자동]` `cd backend && ./gradlew test` 전체 통과, `careerops_test` DB
  격리 유지.
- `[수동]` 실 서비스키로 `numOfRows>1000` 수동 트리거 + Scheduler 기본값
  5000 동작 확인, Prometheus `careerops_collector_pages_total` 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | 첫 구현 지시(pagination 루프, 고정 페이지 크기, 슬라이싱 종료, Scheduler 기본값 5000, `careerops.collector.pages` metric, FixtureAlioJobClient 확장) | `AlioCollectorService`/`FixtureAlioJobClient`/`AlioCollectorServiceTest`/`application.yml`/`docs/METRICS.md` 변경 + fixture 3개 신규. Codex 자체 sandbox에서 `./gradlew test` 실행이 Gradle 파일 락/소켓 권한 문제로 차단(blocker 1건, self-report). Claude가 직접 `./gradlew test` 실행해 58/58 통과 확인(기존 51 + 신규 7). `.ai/metrics/metrics.jsonl`에 Codex가 직접 self-report 줄을 추가한 것을 발견해 되돌리고 Claude가 규칙대로 재기록(codex-implement Skill 2.5절 위반 — reviewer 체크리스트에 반영) → 1차 리뷰 NEEDS_REVISION(`.ai/reviews/COLLECT-005-review-1.md`, 다중 페이지 detail enrichment 단정 누락 1건) |
| 2 | 1차 리뷰 수정 요청(다중 페이지 detail enrichment `detailFetchedAt`/`capturedDetailSns` 명시적 단정 추가, metrics.jsonl 직접 수정 금지 재상기) | `AlioCollectorServiceTest.java` 1개만 수정(요청한 assertion 정확히 반영, metrics.jsonl 미건드림 확인). Codex sandbox에서 이번에도 테스트 실행 차단(self-report). Claude가 직접 재실행해 58/58 통과 재확인 → 2차 리뷰 PASS(`.ai/reviews/COLLECT-005-review-2.md`) |
