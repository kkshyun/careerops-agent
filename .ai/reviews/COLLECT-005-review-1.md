---
task_id: COLLECT-005
review_round: 1
reviewer: claude
reviewed_at: 2026-08-16T17:30:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

`[자동]` 항목만 이번 라운드에서 검증 가능(`[수동]` 항목은 실 서비스키 필요,
별도 사용자 확인 대상).

- [x] **여러 페이지 정상 수집** — 충족. `AlioCollectorServiceTest.paginatesWithFixedPageSizeSlicesAtCallerLimitAndRecordsPages`
      (`backend/src/test/java/com/careerops/backend/collector/AlioCollectorServiceTest.java:184-202`)가
      3페이지(1000/1000/500, `numOfRows=2500`)를 순회하고 `capturedCalls()`로
      `ListCall(1,1000), (2,1000), (3,1000)` — 페이지마다 동일한 `numOfRows`로
      요청했음을 검증. AC 예시 숫자(50/50/20)와는 다르지만 의도(다중 페이지
      순회 + numOfRows 불변)는 그대로 충족.
- [x] **누적 상한 도달 시 페이지 중간 슬라이싱 종료** — 충족. 위와 동일 테스트에서
      `numOfRows=2500`이 page3(응답 1000건) 중간(500건)에서 잘리고
      (`AlioCollectorService.java:74-77` `remaining`/`subList` 로직), page4를
      호출하지 않음(`capturedCalls()`가 정확히 3건). 서버에 더 작은 페이지를
      재요청하지 않음을 `ListCall(3, 1000)`(numOfRows 그대로 1000)로 확인.
- [x] **마지막 partial page 정상 종료** — 충족.
      `stopsAfterPartialPageWithoutRequestingAnotherPage`(`AlioCollectorServiceTest.java:204-216`)에서
      page2가 1건만 반환하자 `items.size() < pageSize` 조건(`AlioCollectorService.java:113`)으로
      정상 종료, page3 미호출.
- [x] **빈 페이지 처리** — 충족. `stopsAtEmptyPageAndKeepsEarlierResults`(`AlioCollectorServiceTest.java:218-230`)에서
      page2 빈 배열 응답 시 그 자리에서 종료하고, page1에서 저장된 공고(`externalId=2001`)가
      DB에 남아있음을 확인.
- [x] **중간 page 실패** — 충족. `propagatesMiddlePageFailureKeepsCommittedPageWorkAndDoesNotRecordSuccess`(`AlioCollectorServiceTest.java:232-252`)에서
      page3에서 `AlioApiException` 발생 시 page1~2에서 저장된 2건이 DB에 유지되고
      (`repository.count()` 검증), `collect()`가 예외를 propagate하며
      (`assertThatThrownBy`), `careerops.collector.run{result=failed}`는 +1,
      `result=success`는 불변, page4는 호출되지 않음(`capturedCalls`가 정확히 [1,2,3])을
      모두 확인. `AlioCollectorService.fetchPage()`(`AlioCollectorService.java:128-140`)가
      실패 시 `runCounter("failed")`를 증가시키고 예외를 다시 던지는 구조와 일치.
- [x] **반복 전체 수집 시 중복 없음** — 충족.
      `repeatedMultiPageCollectionDoesNotCreateDuplicatesOrRefetchDetails`(`AlioCollectorServiceTest.java:254-271`)에서
      동일한 다중 페이지 fixture로 `collect()`를 두 번 호출해도 `repository.count()`가
      늘지 않음을 확인.
- [x] **기존 공고 status 갱신(페이지 경계 무관)** — 충족.
      `updatesExistingStatusWhenPostingAppearsOnLaterPage`(`AlioCollectorServiceTest.java:273-292`)에서
      page2에 등장하는 기존 공고의 상태가 정상 갱신됨을 확인.
- [ ] **미보강 공고 detail enrichment** — 부분 충족(테스트 커버리지 미흡).
      `enrichIfNeeded()` 자체는 코드 재구현 없이 그대로 재사용되어(`AlioCollectorService.java:142-146`,
      `AlioDetailEnrichmentService.java` 무변경 diff 없음 확인) 구조적으로는
      페이지와 무관하게 정상 동작할 것으로 보이나, **"여러 페이지에 걸쳐
      서로 다른 신규 공고가 각 페이지 처리 시점에 정상 보강된다"는 부분을
      명시적으로 단정(assert)하는 테스트가 없다.** 가장 근접한 테스트인
      `stopsAfterPartialPageWithoutRequestingAnotherPage`(page1 신규 `externalId=2001`,
      page2 신규 `externalId=2002`)조차 `detailFetchedAt`이나
      `capturedDetailSns()`를 검증하지 않는다. 게다가
      `AlioDetailEnrichmentService.enrich()`(`AlioDetailEnrichmentService.java:58-60`)는
      내부에서 `RuntimeException`을 잡아 로그만 남기고 삼키므로(COLLECT-004 설계상
      의도된 격리), fixture에 detail 응답이 등록되지 않아도 예외 없이 조용히
      통과한다 — 즉 "enrich가 실제로 각 페이지에서 호출됐는지"를 테스트 실패로
      드러낼 안전장치가 없다. "이미 보강된 공고는 재호출되지 않는다"는 절반은
      `repeatedMultiPageCollectionDoesNotCreateDuplicatesOrRefetchDetails`가
      `capturedDetailSns()` 불변으로 간접 검증하지만, "각 페이지에서 최초 보강이
      실제로 일어났다"는 명시적 단정이 빠져 있다.
- [x] **`numOfRows`가 페이지 크기 이하인 기존 호출 하위호환** — 충족.
      `keepsSinglePageCallBackwardCompatible`(`AlioCollectorServiceTest.java:294-301`)에서
      `numOfRows=50` 호출 시 `ListCall(1, 50)` 단 1회만 발생함을 확인. 기존
      단일 페이지 테스트(`collectsMapsSavesAndRecordsMetrics` 등)도 코드 변경
      없이 그대로 통과(58/58, 아래 테스트 결과 참고).
- [x] **`careerops.collector.pages` metric** — 충족.
      `paginatesWithFixedPageSizeSlicesAtCallerLimitAndRecordsPages`에서 3페이지
      순회 후 `careerops.collector.pages{source=alio}`가 +3임을 확인. 계측 위치도
      `AlioCollectorService.java:67-68`(페이지 응답 수신 직후, 실패 시에는 증가하지
      않음)로 명세와 일치. `docs/METRICS.md`에도 정확히 문서화됨(계측 위치:
      "페이지 응답 수신 직후").
- [x] **Scheduler 회귀 없음** — 충족. `AlioCollectionScheduler.java`
      diff 없음(`git diff` 확인 완료), 관련 테스트는 전체 `./gradlew test`
      58/58 통과에 포함.
- [x] **`GET /api/jobs` 검색 API 회귀 없음** — 충족(전체 테스트 통과에 포함,
      `CollectController`/`CollectResult` diff 없음 확인).
- [x] **`./gradlew test` 전체 통과** — 충족. 아래 테스트 결과 참고.

## 테스트 결과

- Claude가 직접 `cd backend && ./gradlew test --rerun-tasks` 재실행(캐시 배제)
  → `BUILD SUCCESSFUL`, `find build/test-results/test -name "*.xml"` 집계
  결과 `tests=58 skipped=0 failures=0 errors=0`(기존 51 + 신규 7 전부 통과).
- Codex 자체 sandbox에서는 Gradle 파일 락/소켓 권한 문제로 테스트 실행이
  차단되어 self-report만 진행(코드 자체의 문제 아님, Task 명세의 Codex Thread
  기록에 이미 명시됨).

## Findings

1. **(NEEDS_REVISION, 사소) 미보강 detail enrichment의 다중 페이지 커버리지
   누락** — 위 Acceptance Criteria 체크 참고. `AlioDetailEnrichmentService.enrich()`가
   내부에서 예외를 삼키는 구조상, 현재 테스트만으로는 "여러 페이지에 걸친
   서로 다른 신규 공고가 각 페이지 처리 시점에 실제로 보강되었는지"를
   테스트 실패로 검증할 수 없다. 기능 자체는 `enrichIfNeeded()`가
   재구현 없이 그대로 재사용되므로 구조적으로 정상 동작할 가능성이 높지만,
   Acceptance Criteria가 명시적으로 요구하는 항목이므로 단정 누락은 보완이
   필요하다.

2. **(정보 공유, 코드 품질과 무관) `codex-implement` Skill "2.5. Codex에게
   시키지 않는 것" 위반 재발 방지 필요** — Codex가 이번 라운드에서
   `.ai/metrics/metrics.jsonl`에 self-report 줄을 직접 추가했고, 호출한
   Claude가 이를 발견해 `git checkout`으로 되돌린 뒤 규칙대로 직접
   재기록했다(Task 명세 "Codex Thread 기록" 표에도 명시). 코드 품질 판정과는
   별개로, 다음 Codex 요청 시 "metrics.jsonl에 직접 쓰지 말 것"을 명시적으로
   다시 상기시키는 것을 권장한다.

3. **원칙 위반 없음** — 새 production dependency 추가 없음(`build.gradle` diff
   없음), Secret/API Key 커밋 없음(diff 내 키워드 grep 결과 없음), 과도한
   추상화 없음(페이지 순회 루프가 Technical Notes 의사코드와 거의 1:1로 단순
   구현됨). `numOfRows`(페이지 크기)가 동일 run 내에서 절대 바뀌지 않는다는
   핵심 제약도 `fetchPage(pageNo, pageSize)`가 loop 전체에서 `pageSize`
   변수 하나만 참조하는 구조로 정확히 지켜짐 — 모든 관련 테스트에서
   `capturedCalls()`의 `numOfRows`가 페이지마다 동일함을 확인.

4. **Out of Scope 준수** — `AlioCollectionScheduler`/`CollectController`/
   `CollectResult`/`AlioDetailEnrichmentService` 4개 파일 모두 diff 없음을
   `git diff`로 직접 확인. `application.yml`의 `num-of-rows`는 `50` →
   `5000`으로만 변경(전수 순회 아님, `Integer.MAX_VALUE` 등 사용 안 함).
   분산 락/비동기/Retry 프레임워크/API 시그니처 변경 등 어떤 흔적도 없음.

## 다음 액션

NEEDS_REVISION — 아래 요청을 같은 Codex thread(`01a009a0-e30f-7301-bb52-ddbdd0287dfd`)에
그대로 전달 요청:

> `AlioCollectorServiceTest`에 다중 페이지에 걸친 detail enrichment를
> 명시적으로 단정하는 테스트(또는 기존 테스트에 단정 추가)를 보완해줘.
> 최소 요구사항:
> - `stopsAfterPartialPageWithoutRequestingAnotherPage` (또는 별도 신규
>   테스트)에서, page1의 신규 공고(`externalId=2001`)와 page2의 신규
>   공고(`externalId=2002`) 양쪽 모두 `detailFetchedAt`이 `not null`인지
>   명시적으로 assert 해줘(현재는 예외 없이 통과한다는 것으로만 간접
>   확인되고 있어 `enrich()`가 실제로 호출됐는지 보장하지 못함).
> - 가능하면 `client.capturedDetailSns()`가 `[2001, 2002]`(또는 순서 무관
>   `containsExactlyInAnyOrder`)를 포함하는지도 함께 확인해줘.
> - `AlioDetailEnrichmentService.enrich()`가 내부에서 예외를 삼키는 구조이므로,
>   "detail fixture가 등록 안 된 sn을 조회하면 조용히 통과하고 마는" 현재의
>   약점을 이 테스트가 메우도록 해줘(즉 fixture가 등록 안 된 상태라면 반드시
>   `detailFetchedAt == null`로 드러나게).
> 다른 파일은 변경하지 말고 이 테스트 보완만 해줘.

Round 1이므로 아직 재작업 반복 우려 단계는 아님. 위 1건은 사소한 테스트
보완 요청이며, 그 외 모든 Acceptance Criteria(`[자동]` 전체)는 충족으로
판정됨.
