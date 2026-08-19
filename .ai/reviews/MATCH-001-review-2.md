---
task_id: MATCH-001
review_round: 2
reviewer: claude
reviewed_at: 2026-08-19T21:20:00+09:00
verdict: PASS
---

## Scope of this round

1차 리뷰(`.ai/reviews/MATCH-001-review-1.md`)가 NEEDS_REVISION으로 판정한
2건(AC #5, AC #7 테스트 커버리지 부족)만 재검증한다. 나머지 16개 AC와
설계 원칙은 1차 리뷰에서 이미 충족 확인되었고, 이번 라운드에서 해당 영역의
코드가 바뀌지 않았음만 아래에서 별도로 확인한다.

## 변경 범위 확인 (프로덕션 코드 미변경 검증)

Codex 구현이 아직 커밋되지 않은 상태라 `git diff`로는 신규/미추적 파일의
변경 이력을 볼 수 없어, 파일 mtime으로 라운드 1→2 사이의 변경분을 특정했다.

- `backend/src/main/java/com/careerops/backend/match/*.java` (Controller/
  Service/Engine/Normalizer/DTO) — 전부 `20:13:33`~`20:42:53` (1차 리뷰
  작성 시각 `20:51:28` 이전) — **1차 리뷰 이후 변경 없음**.
- `backend/src/main/java/com/careerops/backend/career/ExperienceTagRepository.java`
  — `20:13:33`, 1차 리뷰와 동일 시점 — **미변경**.
- `docs/DECISIONS.md`(`20:09:10`), `docs/METRICS.md`(`20:39:42`) — 모두
  1차 리뷰 작성 시각 이전 — **미변경**.
- 변경된 것은 다음 2개 테스트 파일뿐:
  - `backend/src/test/java/com/careerops/backend/match/CareerMatchEngineTest.java`
    (`20:52:39`, 1차 리뷰 작성 시각보다 뒤)
  - `backend/src/test/java/com/careerops/backend/match/JobMatchControllerTest.java`
    (`20:52:39`)

Codex가 보고한 "프로덕션 코드는 변경하지 않았다"는 사실과 일치한다.

## Acceptance Criteria 재검증

- **AC #7** (title/summary만 매칭, tags 태그는 매칭 안 되는 케이스에서
  `matchedFields`가 태그 미포함) — **충족**.
  `CareerMatchEngineTest.reportsOnlyTitleAndSummaryWhenTagDoesNotMatch()`
  (`CareerMatchEngineTest.java:29-42`)에서 `title="java platform"`,
  `summary="java delivery"`는 job category `"java"`와 매칭시키고,
  `detail="unrelated detail"`은 비매칭, `ExperienceTag.keyword="design"`도
  비매칭으로 구성한 뒤 `result.experiences().getFirst().score()` == `0.5`
  (title +0.3, summary +0.2)와 `matchedFields()`가
  `.containsExactly("title", "summary")` 그리고 `.doesNotContain("tags")`임을
  명시적으로 assert한다. 1차 리뷰가 지적한 "태그 미매칭 케이스를 검증하는
  테스트가 아예 없다"는 갭을 정확히 메운다.

- **AC #5** (동점 시 id 오름차순 tie ordering, top N 절단) — **충족**.
  `JobMatchControllerTest.appliesTopLimitsAndIdAscendingTieOrdering()`
  (`JobMatchControllerTest.java:106-131`)이 동일 점수(title만 매칭, 0.3점)의
  `CareerExperience` 6개를 DB auto-increment 순서(= id 오름차순)로 생성해
  `experienceIds`에 저장한 뒤, 응답의
  `$.recommendedExperiences[*].id`를 hamcrest `contains(experienceIds.get(0),
  ..., experienceIds.get(4))`로 검증한다 — `contains`는 순서까지 포함해
  정확히 일치해야 통과하므로, 상위 5개의 id가 정확히 오름차순으로 나열됨을
  증명한다. 이어서 `not(hasItem(experienceIds.get(5)))`로 가장 큰 id(6번째
  생성)가 결과에서 배제됨도 별도로 확인한다. 1차 리뷰가 지적한 "0번째
  id만 확인해 우연히 통과할 수 있는 약한 테스트" 문제를 해소했다 —
  이제 "조회 순서를 우연히 유지하는 안정 정렬"만으로는 이 assertion을
  통과할 수 없고, 실제로 `id` 기준 명시적 오름차순 정렬이 필요하다.

두 항목 모두 프로덕션 코드 변경 없이 테스트 보강만으로 갭을 메웠고, 새로
추가/보강된 assertion이 실제로 1차 리뷰가 지적한 시나리오를 정확히
겨냥하고 있다.

## 테스트 결과

- 독립 재실행: `cd backend && ./gradlew test --rerun` (사전에
  `docker compose ps`로 postgres/redis healthy 확인) → **BUILD
  SUCCESSFUL**.
- `build/test-results/test/*.xml` 전체 집계(46개 테스트 클래스 합산):
  `tests=195, failures=0, errors=0, skipped=0`.
- `match` 패키지 개별 확인: `CareerMatchEngineTest tests=3`(기존 2 +
  신규 `reportsOnlyTitleAndSummaryWhenTagDoesNotMatch` 1),
  `JobMatchControllerTest tests=5`(메서드 개수는 그대로, 기존
  `appliesTopLimitsAndIdAscendingTieOrdering`을 보강),
  `KeywordNormalizerTest tests=2`(미변경) — 전부 실패/에러 0.
- 오케스트레이터가 보고한 195/195(기존 194 + 신규 1)와 독립적으로
  일치함을 재확인.

## Findings

없음. 1차 리뷰의 2개 지적사항 모두 요청한 방식대로 정확히 해소되었고,
그 과정에서 새로운 버그나 회귀도 발견되지 않았다. 그 외 원칙 위반(과도한
추상화, 미기록 dependency, secret 커밋, 자기소개서 근거 없는 생성 로직 등)도
발견되지 않았다 — 이번 라운드는 테스트 파일 2개만 수정되었으므로 1차
리뷰에서 확인한 원칙 준수 상태가 그대로 유지된다.

## 다음 액션

- **PASS**. MATCH-001 구현 단계 종료 조건 충족:
  - Acceptance Criteria 18개 전부 충족(`[수동]` 항목 1개는 자동화 범위
    밖으로 사용자 몫으로 남김, 나머지 17개 자동 검증 항목 전부 충족).
  - 전체 회귀 195/195 PASS, 신규 match 패키지 테스트 10개(3+5+2) 전부
    통과.
  - 프로덕션 코드는 1차 리뷰 시점 이후 변경 없음, 테스트 보강만으로
    갭 해소 확인.
- 후속 조치 제안: Task 명세(`.ai/tasks/MATCH-001.md`) 프런트매터의
  `status: in_progress`를 `done`(또는 프로젝트에서 쓰는 완료 상태 값)으로
  갱신하고 `.ai/metrics/metrics.jsonl`에 최종 완료 레코드를 기록할 것
  (Codex가 아니라 오케스트레이터/Claude가 기록 — AGENTS.md 규칙).
