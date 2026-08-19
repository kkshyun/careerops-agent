---
task_id: MATCH-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-19T21:00:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

1. 없는 jobId → 404 — **충족**. `JobMatchService.calculate()`가
   `ResponseStatusException(HttpStatus.NOT_FOUND)`를 던짐
   (`backend/src/main/java/com/careerops/backend/match/JobMatchService.java:87-90`).
   `JobMatchControllerTest.returns404AndNotFoundMetricForMissingPosting`
   (line 40-45)로 검증, `not_found` 카운터 증가도 함께 확인.

2. PKB 완전히 빈 상태 → overallScore=0.0, 모든 recommended* 빈 배열,
   unmatchedJobCategories에 모든 조각 — **충족**.
   `JobMatchControllerTest.emptyPkbReturnsZeroEmptyEvidenceAllGapsAndEchoFields`
   (line 48-63)에서 3개 카테고리("Java.Backend","Data","Cloud") 모두
   `unmatchedJobCategories`에 원문 그대로 나열됨을 확인, careerLevel/
   educationRequirement echo도 함께 검증.

3. 강한 매치 시 matchedFields 정확성 — **충족**.
   `returnsExactEvidenceScoresGapsMetricsAndDeterministicContent`
   (line 66-98)에서 tag="JAVA"(태그), title="Java API", summary="cloud
   migration"이 매칭되고 detail="unrelated"는 매칭 안 되는 경우
   `matchedFields`가 정확히 `["tags","title","summary"]`임을 확인.

4. top N 상한(Experience 5, Cert/Edu/Award 3) — **충족**.
   `appliesTopLimitsAndIdAscendingTieOrdering`(line 100-121)에서
   Experience 6개→5개, Cert/Edu/Award 각 4개→3개로 잘림을 확인.

5. 동점 시 id asc tie ordering + 반복 호출 결정성 — **부분 미충족(테스트
   커버리지 약함)**. 아래 Findings 참고. `CareerMatchEngine.EVIDENCE_ORDER`
   구현 자체는 `Comparator.comparingDouble(score).reversed()
   .thenComparing(id)`로 올바르게 작성되어 있으나
   (`CareerMatchEngine.java:26-27`), 이를 검증하는
   `appliesTopLimitsAndIdAscendingTieOrdering` 테스트는
   `recommendedExperiences[0].id`만 확인하고 나머지 4개의 순서는
   전혀 검증하지 않는다. 반복 호출 결정성(재현성)은
   `returnsExactEvidenceScoresGapsMetricsAndDeterministicContent`에서
   응답 전체(2회 호출, `computedAt` 제외 비교)로 잘 검증됨 — 이 부분은
   충족.

6. 태그 대소문자 무시 매칭 — **충족**. 단위 테스트
   `KeywordNormalizerTest.normalizesCaseAndWhitespaceAndMatchesBidirectionally`
   (line 11-17)이 대소문자 정규화를 직접 검증하고, 통합 테스트에서도
   태그 `"JAVA"`가 소문자 카테고리 `"java"`와 매칭됨을 실제로 사용.

7. title/summary만 매칭, 태그는 매칭 안 된 케이스에서 matchedFields가
   태그 미포함 — **미충족(테스트 없음)**. 아래 Findings 참고. 구현
   코드(`CareerMatchEngine.scoreExperience`, line 79-90)는 태그가
   매칭 안 되면 `"tags"`를 추가하지 않도록 올바르게 작성되어 있어
   기능적으로는 맞아 보이지만, 이를 명시적으로 검증하는 테스트가
   3개 테스트 파일 어디에도 없다(`matchedFields` 관련 assertion을
   grep한 결과 4곳 모두 "태그가 매칭된" 케이스만 사용).

8. 무관 항목 제외(0점 top N 미포함, 가중합에서도 제외) — **충족**.
   `combinesCategoryMaximaAndExcludesZeroScores`(CareerMatchEngineTest
   line 28-55)에서 `unrelatedCertification`/`unrelatedEducation`/
   `unrelated`(award)가 각각 `id`로 결과에서 빠짐을 확인, overallScore도
   매칭된 항목만으로 계산됨을 확인.

9. Certification/Education/Award 매칭/비매칭, 1.0/0 채점 — **충족(간접
   검증)**. 매칭 시 1.0은 여러 테스트에서 직접 확인. 비매칭 시 0은
   API 설계상(§8 규칙: score>0만 응답에 노출) 직접 노출되지 않으므로
   "결과에서 제외됨"으로 간접 검증되는데, 이는 스펙과 일치하는 설계이며
   합리적인 검증 방식으로 판단.

10. PENDING/REJECTED ImportCandidate 데이터가 매칭 결과에 전혀 안 나타남
    — **충족, 잘 작성됨**.
    `pendingAndRejectedCandidatesDoNotMatchButApprovedCreatedEntityDoes`
    (line 124-138)이 PENDING 생성 + REJECTED 처리 후
    `recommendedExperiences`가 비어 있음을 먼저 확인하고, 이어서 PENDING
    승인 후 정확히 1개(제목까지 확인)가 나타남을 확인 — 가장 중요한
    회귀 테스트가 명확하게 작성되어 있다.

11. APPROVED 데이터가 MANUAL과 동일하게 포함 — **충족(구조적으로 보장)**.
    `experienceRepository.findAll()`(`JobMatchService.java:91`) 등이
    `sourceType`으로 필터링하지 않으므로 APPROVED 경유 생성 항목과
    MANUAL 생성 항목이 코드 경로상 구분되지 않는다. 위 10번 테스트가
    승인 후 정상 매칭됨을 보여주므로 사실상 충족.

12. overallScore 0.0~1.0 범위(가중합 상한 경계) — **충족**.
    `combinesCategoryMaximaAndExcludesZeroScores`에서 4개 카테고리
    모두 만점일 때 `overallScore == 1.0`이고 `isBetween(0.0, 1.0)`으로
    확인. (참고: 가중치 합이 정확히 1.0이므로 수학적으로 1.0을 절대
    넘을 수 없는 구조이며, `Math.min(1.0, ...)` cap은 방어적 코드로
    실질적으로 트리거되지는 않음 — 문제는 아니나 참고 사항.)

13. MatchEvidence 구조(type/id/title/score/matchedFields) 정확성 —
    **충족**. 여러 테스트에서 카테고리별로 `type`/`id`/`score`/
    `matchedFields` 값을 개별 확인.

14. unmatchedJobCategories가 gap 조각만 포함, 자연어 문장 없음 —
    **충족**. `CareerMatchEngine.calculate()`(line 69-74)가
    `categories.get(index).raw()`만 담아 반환, 자연어 조합 로직 없음.
    테스트에서도 원문 그대로("Java.Backend","security" 등) 확인.

15. 재현성 — **충족**. 위 5번 항목 설명 참고.

16. 기존 JobPosting/career/Application/Collector/pkbimport 전체 회귀 —
    **충족**. 아래 "테스트 결과" 참고 (194/194 PASS, 기존 185개 전부
    포함).

17. docs/METRICS.md에 3개 metric 행 추가 — **충족**.
    `git diff -- docs/METRICS.md`로 `careerops.match.request`/
    `careerops.match.duration`/`careerops.match.score` 3개 행과
    cardinality 관련 설명 문단이 "JobPosting ↔ PKB Match (MATCH-001)"
    섹션으로 추가된 것을 확인.

18. `[수동]` 로컬 실제 PKB/JobPosting 응답 타당성 확인 — **스킵**(자동화
    불가, 사용자 몫으로 남김).

## 테스트 결과

- 독립 재실행: `cd backend && ./gradlew test --rerun` → **BUILD
  SUCCESSFUL**, `build/test-results/test/*.xml` 집계 결과
  `tests=194 skipped=0 failures=0 errors=0`. match 패키지 3개 테스트
  클래스(`KeywordNormalizerTest` 2, `CareerMatchEngineTest` 2,
  `JobMatchControllerTest` 5 = 9개) 모두 실패/에러 0. 오케스트레이터가
  보고한 194/194 결과와 독립적으로 재확인 일치.
- 사전에 `docker compose ps`로 postgres/redis 컨테이너가 이미 healthy
  상태임을 확인 후 실행.

## Findings

1. **[테스트 커버리지 누락] AC #7 미검증** — `CareerExperience.title`/
   `summary`만 매칭되고 `ExperienceTag.keyword`는 매칭되지 않는 케이스에서
   `matchedFields`가 `"tags"`를 포함하지 않음을 검증하는 테스트가 전혀
   없다. `matchedFields` assertion이 등장하는 4곳
   (`CareerMatchEngineTest.java:23`, `:52`,
   `JobMatchControllerTest.java:84-86`, `:89`)을 모두 확인했는데, 태그가
   매칭되는 케이스만 다루고 있다. `CareerMatchEngine.scoreExperience()`
   구현(`CareerMatchEngine.java:79-90`) 자체는 `matchesAny(tags...)`가
   false면 `"tags"`를 추가하지 않도록 올바르게 짜여 있어 보이지만, Task
   명세가 명시적으로 요구한 회귀 테스트가 없다는 점에서 AC 미충족으로
   판단한다.
   - **요청**: `CareerMatchEngineTest` 또는
     `JobMatchControllerTest`에 태그가 없거나(또는 매칭 안 되는 태그만
     있는) `CareerExperience`에 title/summary만 매칭되는 케이스를 추가해,
     `matchedFields`가 `["title","summary"]`처럼 `"tags"` 없이 정확히
     구성됨을 assert하는 테스트 메서드를 추가해 달라.

2. **[테스트 검증 약함] AC #5 tie ordering이 완전히 검증되지 않음** —
   `appliesTopLimitsAndIdAscendingTieOrdering`
   (`JobMatchControllerTest.java:100-121`)이 6개의 동일 점수(0.3, title만
   매칭) `CareerExperience`를 만들고 `recommendedExperiences[0].id`만
   확인한다. `experienceRepository.findAll()`은 JPA 스펙상 정렬 순서를
   보장하지 않으므로, 만약 엔진이 명시적 `id` 오름차순 tie-break 없이
   단순히 "조회 순서를 유지하는 안정 정렬"만 했더라도 이 테스트는 (많은
   경우) 우연히 통과할 수 있다 — 즉 이 테스트는 "explicit id-ascending
   tie-break가 실제로 동작하는지"를 강하게 증명하지 못한다. 실제 구현
   (`CareerMatchEngine.java:26-27`,
   `.thenComparing(MatchEvidence::id)`)은 맞게 작성되어 있어 기능적
   버그는 아니지만, Task 명세가 "동일 score 시 id 오름차순 정렬"을
   AC로 명시했으므로 이를 확실히 증명하는 assertion이 필요하다.
   - **요청**: 같은 테스트에서 `recommendedExperiences` 5개 전체의 `id`
     리스트를 추출해 오름차순으로 정렬되어 있고 가장 큰 id(6번째 생성된
     항목)가 제외되어 있음을 명시적으로 assert하도록 보강해 달라
     (예: `.extracting(MatchEvidence::id)` 방식으로 5개 id를 모두
     비교, 또는 정수 배열을 오름차순 정렬한 기대값과 비교).

이 두 건 외에 원칙 위반은 발견하지 못했다:
- 가중치 70/15/10/5 하드코딩 확인 (`CareerMatchEngine.java:21-24`).
- 채점 상수(태그+0.5/제목+0.3/summary+0.2/detail+0.1, 1.0 cap) 구현과
  일치 확인.
- 카테고리 내 최고점수(max) 사용, 평균 아님 확인
  (`CareerMatchEngine.maxScore`, line 130-132).
- `jobCategory`는 쉼표로만 split, 점(`.`)으로 추가 분해 안 됨 확인
  (`KeywordNormalizerTest.splitsOnlyOnCommasAndDropsEmptyPiecesWhileKeepingRawText`,
  `"Java.Backend"`가 하나의 raw 조각으로 유지됨).
- 하드코딩 동의어 사전 없음 확인(grep 결과 없음).
- recency weighting 없음 확인(`match` 패키지에서
  createdAt/updatedAt/startDate/endDate를 참조하는 코드 없음).
- 매칭 결과 DB 미저장 확인(신규 엔티티/Flyway migration 파일 없음,
  `git status`로 `backend/src/main/resources/db/migration/` 변경 없음
  확인).
- `career`/`job`/`pkbimport` 패키지 중 `ExperienceTagRepository` 외
  다른 파일 수정 없음 확인(`git diff --stat`).
- PKB 원문 로깅 없음 확인(`match` 패키지에 `log.`/`Logger` 참조 자체가
  없음).
- metrics 태그에 score/jobId/companyName 등 고cardinality 값 없음
  확인(`result` 태그만 사용, `duration`/`score`는 태그 없음).
- 신규 production dependency 없음(build.gradle 변경 없음).
- `.ai/metrics/metrics.jsonl`은 Codex가 아니라 Claude가 기록한 것으로
  보임(과거 COLLECT-005 위반 재발 없음).

## 다음 액션

- **NEEDS_REVISION**. 같은 Codex thread
  (`01a019b8-5389-7192-8db0-6ba589dd7ca5`)에 아래 두 가지 수정을 요청한다:
  1. `JobMatchControllerTest` 또는 `CareerMatchEngineTest`에 "title/
     summary만 매칭되고 태그(keyword)는 매칭 안 됨" 케이스를 추가하고
     `matchedFields`에 `"tags"`가 없음을 명시적으로 assert하는 테스트
     메서드 추가 (AC #7).
  2. `appliesTopLimitsAndIdAscendingTieOrdering`에서 동점 6개 Experience
     중 상위 5개의 `id` 전체가 오름차순으로 정확히 일치하고, 가장 큰
     id(6번째)가 배제됨을 명시적으로 assert하도록 보강 (AC #5).
  - 두 건 모두 프로덕션 코드 변경이 아니라 **테스트 보강**만 필요할 것으로
    판단됨(구현 코드 자체는 검토 결과 올바르게 작성된 것으로 보임). 단,
    새 테스트를 추가하는 과정에서 실제로 버그가 드러날 가능성을 배제할
    수 없으므로, 테스트 추가 후 `./gradlew test` 결과를 다시 확인해야
    한다.
  - 그 외 모든 Acceptance Criteria(1~4, 6, 8~17)는 충족, 원칙 위반
    없음, 전체 회귀 194/194 PASS 독립 재확인 완료.
