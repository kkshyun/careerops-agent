---
task_id: PKB-001
review_round: 2
reviewer: claude
reviewed_at: 2026-08-18T18:05:00+09:00
verdict: PASS
---

## 배경

round 1(NEEDS_REVISION)의 유일한 블로킹 사유는
`CareerExperienceControllerTest.returnsNotFoundAndDeletesWithDatabaseCascade()`가
persistence context 오염으로 `TransientPropertyValueException`을 던지며 실패한
것(테스트 코드 문제로 판정, `CareerExperienceService.delete()` 등 production 코드는
결함 없음). 함께 경미 사항 2건(type-only/keyword-only 필터 개별 검증, tags-only
PATCH 반대방향 독립성 검증)도 보강 요청했다. round 2는 같은 Codex thread
(`01a01408-3413-7ec3-9529-7dad843edb16`)에 두 요청을 함께 전달한 결과에 대한
최종 확인이다.

## 수정 사항 확인

### 1. (필수/블로킹) `entityManager.clear()` 추가 — 충족

`CareerExperienceControllerTest.java`:
- L3, L26: `jakarta.persistence.EntityManager` import + `@Autowired EntityManager
  entityManager;` 필드 추가.
- L108-121 `returnsNotFoundAndDeletesWithDatabaseCascade()`:
  - L110-112: 부모(`repository.saveAndFlush`) + 자식 bullet/tag
    (`bulletRepository.saveAndFlush`/`tagRepository.saveAndFlush`) 생성.
  - **L113: `entityManager.clear();`** — 부모/자식 saveAndFlush 직후, L114
    MockMvc `delete(...)` 호출 **이전**에 정확히 위치. 요청한 위치와 일치.
  - L115-116: `bulletRepository.countByCareerExperienceId(...)`/
    `tagRepository.countByCareerExperienceId(...)`가 `isZero()`를 그대로 검증
    (DB 직접 조회 — assertion 약화나 예외 삼킴 없음). L117-120도 기존 404
    검증 그대로 유지.
  - production 코드(`CareerExperienceService.java`)는 무변경 — round 1이
    요구한 대로 테스트만 수정했고, delete()는 여전히 명세 12번(DB
    `ON DELETE CASCADE`에만 의존)대로 구현되어 있다. 우회/땜질이 아니라
    "테스트가 만든 stale managed 참조를 detach해 실제 요청 흐름(새
    persistence context)과 동일한 조건을 재현"하는 정공법 수정이다 —
    `CareerExperienceRepositoryTest.deletingExperienceCascadesToChildren()`
    (round 1에서 이미 정상 통과로 확인된 선례)과 동일한 패턴.

### 2. (선택/경미) 테스트 커버리지 보강 — 반영됨

신규 `@Test` 메서드 추가 없이 기존 메서드에 assertion을 추가하는 형태로 반영:

- **type-only 필터**: `listsWithCombinedFiltersAsFlatResponses` L70-72
  (`param("type","WORK")` 단독) — 결과 1건, title "추천 운영" 검증. round 1이
  지적한 "combined 파라미터로만 검증되던" 문제 해소.
- **keyword-only 필터**: 같은 테스트 L73-75 (`param("keyword","시스템")`
  단독) — 결과 1건, title "추천 시스템" 검증.
- **tags-only PATCH(bullets 생략) 반대방향**: `getsDetailAndPatchesListsUsingWholeReplacementRules`
  L99-102 — `{"tags":["PostgreSQL"]}` (bullets 필드 자체 생략) 요청 후
  `$.bullets[0].content`가 직전 값("새 결과")으로 그대로 유지되고
  `$.tags[0]`만 "PostgreSQL"로 바뀐 것을 확인. round 1이 지적한 "bullets만
  바뀌는 방향만 있고 반대 방향이 없다"는 갭이 해소됨 — 양방향 독립성 증명
  완료.

기존 메서드에 통합하는 방식이라 개별 `@Test`로 분리했을 때보다 가독성은
약간 떨어지지만, 검증 내용 자체는 요청한 그대로 정확히 반영됐고 이 항목은
애초에 "선택/경미"였으므로 PASS 판정에 영향 없음.

## Acceptance Criteria 재확인 (round 1 대비 변경분만)

- AC 6 (`GET` type 필터) — round 1 "충족(격리 검증 아님)" → **충족(개별
  검증 완료)**.
- AC 7 (`GET` keyword 필터) — 동일하게 **충족(개별 검증 완료)**.
- AC 15 (`PATCH` bullets/tags 독립적 처리) — round 1 "부분 충족(단방향만)"
  → **충족(양방향 검증 완료)**.
- AC 17 (`DELETE` 204 → 단건조회 404, 존재하지 않는 id → 404) — round 1
  "테스트 실패로 미검증" → **충족**(아래 테스트 결과, `CareerExperienceControllerTest`
  5건 전부 통과).
- AC 18 (`DELETE` cascade) — 동일하게 **충족**.
- AC 20 (`./gradlew test` 전체 실패 0건) — round 1 "미충족(100건 중 1건
  실패)" → **충족**.
- 그 외 AC 1-5, 8-14, 16, 19 — round 1에서 이미 충족 판정, 이번 round의
  변경(테스트 파일만 수정, production 코드 무변경)이 해당 판정에 영향을 줄
  이유가 없으므로 재확인 없이 유지. `git status --short` 결과 `backend/src/main`
  하위에 수정된 추적 파일이 없음을 재확인해 이 전제를 뒷받침함(아래 참고).

## production 코드 무변경 확인

```
$ git diff --stat -- backend/src/main
(출력 없음)
$ git status --short
 M docs/DECISIONS.md
 M docs/ROADMAP.md
?? .ai/reviews/PKB-001-review-1.md
?? .ai/tasks/PKB-001.md
?? backend/src/main/java/com/careerops/backend/career/
?? backend/src/main/resources/db/migration/V7__create_career_experiences_tables.sql
?? backend/src/test/java/com/careerops/backend/career/
```

`career` 패키지/migration은 round 1에서 이미 리뷰된 신규 파일(untracked)이며
round 2에서 추가 수정 없음. 기존 추적 파일 중 `backend/src/main` 하위는
전혀 modified 상태가 아님(수정된 추적 파일은 `docs/DECISIONS.md`,
`docs/ROADMAP.md`뿐이며 이는 PKB-001과 무관한 문서 변경). round 2에서 Codex가
`CareerExperienceService.java` 등 production 코드를 건드리지 않았다는 보고와
일치.

## 테스트 결과

`cd backend && ./gradlew cleanTest test` 직접 실행(캐시된 UP-TO-DATE 결과가
아닌 강제 재실행, docker compose postgres/redis는 이미 healthy 상태로 기동
중이었음).

- **BUILD SUCCESSFUL**
- test_count: 100
- test_pass_count: 100 (`build/test-results/test/*.xml` 전체 합산 `failures=0
  errors=0`)
- `CareerExperienceControllerTest`: `tests="5" failures="0" errors="0"`
  (`build/test-results/test/TEST-com.careerops.backend.career.CareerExperienceControllerTest.xml`),
  round 1에서 실패했던 `returnsNotFoundAndDeletesWithDatabaseCascade`도 포함해
  전부 통과.
- Codex 자신은 이번에도 sandbox 제약으로 `./gradlew test`를 실행하지 못했다고
  보고했으나, Claude가 로컬에서 직접 재현·확인함.

## Findings

- 블로킹 사항 없음.
- 경미: 커버리지 보강 3건이 신규 `@Test` 메서드가 아니라 기존 대형 테스트
  메서드에 assertion을 추가하는 형태로 반영되어, 실패 시 어느 시나리오가
  깨졌는지 테스트 이름만으로 특정하기가 round 1 요청 의도(개별 테스트로
  분리)보다는 약간 어려워졌다. 다만 검증 내용 자체는 정확하고 이 항목은
  애초에 선택 사항이었으므로 PASS 판정을 막을 사유는 아니다.
- Secret/API Key 커밋 없음. 신규 production dependency 없음. 자기소개서
  관련 로직 없음(이번 Task 범위 아님) — 근거 기반 검증 원칙 위반 사항 없음.
- round 1에서 언급된 `CareerExperienceRepository.findByType(ExperienceType,
  Pageable)`(테스트 전용, production 미사용) 건은 round 2 수정 요청에
  포함되지 않았고 여전히 남아있으나, round 1에서도 "수정 필수 아님"으로
  분류한 경미 사항이라 재차 블로킹하지 않음.

## 다음 액션

**PASS** — round 1의 유일한 블로킹 사유(DELETE cascade 테스트 실패)와 경미
보강 요청 2건 모두 해소 확인. `cd backend && ./gradlew test` 100/100 통과,
production 코드 무변경. Task 명세 상태를 `done`으로 전환하고
`.ai/metrics/metrics.jsonl`에 최종 완료 기록을 남기는 절차로 넘어가면 된다
(metrics 기록은 Claude가 직접 수행 — Codex가 self-report하지 않도록 round 1
findings 유지).
