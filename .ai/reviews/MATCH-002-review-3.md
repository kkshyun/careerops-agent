---
task_id: MATCH-002
review_round: 3
reviewer: claude
reviewed_at: 2026-08-22T21:40:00+09:00
verdict: PASS
---

## 이번 라운드 범위

2차 PASS 이후 실제 dev DB + 실제 Anthropic API 수동 E2E(Case D)에서 발견된
Education evidence `title` null 노출 문제(고등학교처럼 `major`가 비어 있는
레코드)에 대한 최소 범위 수정만 검증했다. 전체 Task 재검토는 하지 않았다.

## 확인 결과

1. **수정 범위** — `SemanticJobMatchService.java`의 Education title 매핑
   람다(line 80-82)만 변경됨을 확인:
   ```java
   education -> education.getMajor() == null || education.getMajor().isBlank()
           ? education.getInstitution() : education.getMajor(),
   ```
   `major`가 null/blank일 때만 `institution`으로 fallback, 그 외에는
   `major`를 그대로 사용 — 지시한 순서/조건과 정확히 일치한다.
   CareerExperience(`CareerExperience::getTitle`, line 76-77),
   Certification(`Certification::getName`, line 78-79),
   Award(`Award::getTitle`, line 84-85) title 매핑은 단순 method reference
   그대로이며 손대지 않았다. `convert()` 메서드 본체(hallucination 검증,
   duplicate 처리, truncate/sort), prompt builder, DTO, timeout, metrics
   관련 코드도 파일 전체를 재독해 변경 없음을 확인했다.
2. **MATCH-001 무변경** — `git status --porcelain --untracked-files=all`로
   `match/` 디렉터리 전체를 확인한 결과, `JobMatchController.java`/
   `JobMatchService.java`/`CareerMatchEngine.java`/`KeywordNormalizer.java`/
   기존 `match/dto/*`는 목록에 전혀 등장하지 않는다(마지막 커밋
   `01adc73` 이후 무변경, git 추적 대상이라 diff 없으면 곧 무변경).
3. **신규 회귀 테스트** —
   `SemanticJobMatchControllerTest.java:75-88`
   `educationTitleFallsBackToInstitutionButPrefersMajor`가 두 케이스를
   한 테스트에서 함께 검증한다:
   - `major=null`인 `Education("Sehwa High School", null, ...)` →
     `$.educationMatches[0].title` == `"Sehwa High School"`(institution
     fallback, null 아님).
   - `major="Computer Science"`인 `Education("CareerOps University",
     "Computer Science", ...)` → `$.educationMatches[1].title` ==
     `"Computer Science"`(major 우선, fallback 로직이 실수로 덮어쓰지
     않음).
   `Education` 생성자 필드 순서(`institution, major, ...`)도
   `Education.java:29-30`과 대조해 테스트 인자 순서가 맞음을 확인했다.

## 테스트 결과

- `./gradlew test --rerun` 직접 재실행(캐시 무시, 방금 완료) →
  `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml` 전체 합산: `tests=219 failures=0 errors=0
  skipped=0`(2차 218 → 이번 219, 순증 1 = 신규 Education title 테스트와
  일치).
- `SemanticJobMatchControllerTest.xml` `<testcase` 카운트 직접 확인: 7개
  (2차 6개 → 이번 7개, 순증 1).
- test_count=219, test_pass_count=219.

## Findings

없음 — 지시된 좁은 범위(Education title fallback) 그대로 구현됐고, 다른
카테고리/로직/MATCH-001 파일은 전혀 건드리지 않았으며, fallback과
major-우선 두 경로 모두 회귀 테스트로 커버되고 전체 테스트가 회귀 없이
219/219 통과함을 직접 재현했다.

## 다음 액션

- PASS. `.ai/metrics/metrics.jsonl`에 review round 3 최종 상태(PASS,
  test_count=219, test_pass_count=219) 기록 필요.
- 이번 좁은 범위 수정으로 Task 명세(`MATCH-002.md`) Case D의 "별도 발견
  사항"이 해소됐다. Task 상태를 `done`/`passed`로 확정하기 전에, 가능하면
  Case D를 실제 dev DB + Anthropic API로 재실행해 해당 Education evidence
  `title`이 더 이상 null이 아님을 최종 확인하는 것을 권장한다(이번
  라운드는 자동 회귀 테스트만으로 검증했고 수동 E2E는 재실행하지 않았음).
