---
task_id: MATCH-001
title: JobPosting ↔ PKB 매칭 — 단일 공고 적합도 점수/근거/gap API
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-19T00:00:00+09:00
codex_thread_id: 01a019b8-5389-7192-8db0-6ba589dd7ca5
---

## Context

PKB-008까지 완료되어 `CareerExperience`/`Certification`/`Education`/
`Award`가 문서 기반(LLM 추출 → 승인) 및 수동 입력 양쪽에서 구조화된
상태로 쌓이기 시작했다. `docs/ROADMAP.md` "Phase 13 이후 후보"에 남겨둔
`JobPosting` ↔ PKB 적합도 매칭이 다음 단계다 — AGENTS.md가 정의한 제품
목표("지원자의 이력에 맞는 적합도를 판단해 카카오톡으로 알림")로 가는
첫 걸음이지만, 이번 Task는 카카오 알림이나 전체 공고 랭킹이 아니라
**단일 공고에 대해 현재 PKB가 얼마나 관련 있는지 점수와 근거를 보여주는
API** 하나로 범위를 좁힌다.

설계 근거는 ADR-0026(deterministic 채점, relevance/eligibility 분리,
on-demand 계산, 가중치 70/15/10/5, top N 정책, 알려진 한계)에 기록되어
있다 — **구현 전 먼저 읽는다.**

## Scope

신규 패키지 `com.careerops.backend.match`에 단일 JobPosting × 사용자
PKB 매칭 점수/근거/gap을 계산해 반환하는 API를 추가한다.

### 1. 패키지 구조 (신규)

```
backend/src/main/java/com/careerops/backend/match/
├── JobMatchController.java
├── JobMatchService.java
├── CareerMatchEngine.java          (카테고리별 채점 로직, 가중합)
├── KeywordNormalizer.java          (소문자화 + 공백 정규화 + jobCategory split)
└── dto/
    ├── JobMatchResponse.java
    ├── MatchEvidence.java          (type/id/title/score/matchedFields)
    └── ...(필요한 보조 DTO)
```

### 2. 기존 파일 수정 (1개)

`ExperienceTagRepository`에 벌크 조회 메서드를 추가한다(N+1 방지):

```java
List<ExperienceTag> findByCareerExperienceIdIn(List<Long> careerExperienceIds);
```

`career`/`job`/`pkbimport` 패키지의 다른 파일은 수정하지 않는다.
`CareerExperience`/`Certification`/`Education`/`Award`/`JobPosting`
엔티티 및 마이그레이션은 변경 없음(신규 컬럼/테이블 없음).

### 3. API

`GET /api/jobs/{jobId}/match`

- `jobId`에 해당하는 `JobPosting`이 없으면 `404`.
- 인증/사용자 구분은 아직 없으므로(단일 사용자 MVP) PKB 전체를 대상으로
  계산한다.
- 응답 필드:
  - `jobPostingId`
  - `overallScore` (0.0~1.0, **relevance 의미** — "이 공고와 PKB 간
    정보상 관련도"이지 합격 가능성이 아니다. DTO 필드 주석에 이 의미를
    명시한다)
  - `recommendedExperiences` (최대 5개, `MatchEvidence[]`)
  - `recommendedCertifications` (최대 3개, `MatchEvidence[]`)
  - `recommendedEducations` (최대 3개, `MatchEvidence[]`)
  - `recommendedAwards` (최대 3개, `MatchEvidence[]`)
  - `unmatchedJobCategories` (gap — PKB에서 매칭되는 항목을 찾지 못한
    `jobCategory` 조각 목록, 자연어 조언 문장이 아니라 원문 조각 그대로)
  - `careerLevel`, `educationRequirement` (JobPosting 값을 참고 정보로
    그대로 echo, 채점에 관여하지 않음)
  - `computedAt` (계산 시각, `Instant`)
- `MatchEvidence` 구조: `type`(`CAREER_EXPERIENCE`/`CERTIFICATION`/
  `EDUCATION`/`AWARD`), `id`, `title`, `score`(0.0~1.0),
  `matchedFields`(어떤 필드가 매칭에 기여했는지, 예: `["tags", "title"]`)

### 4. 채점 로직 (`CareerMatchEngine`)

카테고리별로 독립적으로 채점한 뒤, 카테고리 점수를
**가중합(70/15/10/5)**한다. 카테고리 안에 여러 PKB 항목이 있으면 그
카테고리 점수는 **최고 점수**(평균 아님)를 사용한다.

- **CareerExperience**: `ExperienceTag.keyword` 매칭 시 +0.5, `title`
  매칭 시 +0.3, `summary` 매칭 시 +0.2, `detail` 매칭 시 +0.1을 합산하되
  최종 항목 점수는 1.0을 넘지 않도록 cap한다. 이 상수 배분은 baseline
  제안이며, 구현 중 더 합리적인 근거(예: 태그가 사람이 직접 부여한
  가장 신뢰도 높은 신호라는 점)로 조정이 필요하면 Codex가 그 근거를
  Codex Thread 기록에 남기고 조정할 수 있다 — 단 "tag > title > summary
  > detail" 우선순위 자체는 유지한다.
- **Certification/Award**: `name`/`title` 또는 `description` 필드에
  매칭이 있으면 1.0, 없으면 0(중간값 없음 — 이 두 카테고리는 텍스트가
  짧아 부분 점수를 나눌 근거가 약하다).
- **Education**: `major` 필드 매칭 시 1.0, 없으면 0. `gpa`는 채점에
  반영하지 않는다.
- 매칭 판정(모든 카테고리 공통)은 `KeywordNormalizer`가 만든 정규화된
  문자열 간 **양방향 substring containment**다(예: PKB 값이 공고 조각을
  포함하거나, 공고 조각이 PKB 값을 포함하면 매칭).

### 5. 정규화 (`KeywordNormalizer`)

- `JobPosting.jobCategory`는 **쉼표(`,`)로만** split한다(점(`.`) 등
  추가 구분자로 더 쪼개지 않는다).
- 각 조각/PKB 필드 값은 소문자화 + 연속 공백을 단일 공백으로 정규화한
  뒤 비교한다.
- **하드코딩 동의어 사전을 두지 않는다** (ADR-0026 결정 5).

### 6. Gap (`unmatchedJobCategories`)

`jobCategory`를 정규화 후 쉼표로 split한 조각 중, 어떤 카테고리의 PKB
항목과도 매칭되지 않은 조각만 원문 그대로(정규화 이전 표기) 나열한다.
"이런 경험을 쌓으세요" 같은 자연어 조언 문장을 생성하지 않는다 —
매칭 실패한 카테고리 이름 나열까지만.

### 7. 정렬/결정성

- 각 카테고리 내 top N 정렬: `score` 내림차순, 동점이면 `id` 오름차순
  (tie ordering 고정 — 매 실행 동일 순서 보장).
- recency weighting(최근 경험 가중치) 없음. 동일 입력(JobPosting +
  PKB 상태)이면 항상 동일한 결과를 반환한다(deterministic).

### 8. Persistence

계산 결과를 저장하지 않는다(ADR-0026 결정 3). 신규 엔티티/테이블/
migration 없음. 매 요청마다 다시 계산한다.

### 9. Provenance 제외

`ImportCandidate`가 `PENDING`/`REJECTED` 상태인 항목은 애초에
`career_experiences`/`career_certifications`/`career_educations`/
`career_awards` 테이블에 행이 생기지 않으므로(승인 시에만
`ImportCandidateService`가 실제 엔티티를 생성 — PKB-006/ADR-0022) 매칭
대상에도 자연히 포함되지 않는다. 이 동작을 검증하는 회귀 테스트가
**필수**다(§ Acceptance Criteria).

### 10. Metrics

`docs/METRICS.md` "Product Metrics" 표 형식을 따라 아래 3개를 추가하고,
`docs/METRICS.md`에도 표 행을 반영한다:

- `careerops.match.request` (Counter, `result`=`success`|`not_found`)
- `careerops.match.duration` (Timer, 태그 없음)
- `careerops.match.score` (DistributionSummary, 태그 없음 —
  `overallScore` 분포를 관찰하기 위함)

`score`/`jobId`/`companyName` 등 cardinality가 큰 값은 태그로 넣지
않는다(기존 `careerops.collector.failed`의 `reason` enum 제한 관례와
동일한 이유).

### 11. Privacy

로그(INFO 이상)에 PKB 원문 텍스트(`detail`/`summary`/`description`)를
출력하지 않는다. 로그가 필요하면 id/카테고리/점수 등 메타데이터만
남긴다.

## Out of Scope

- LLM/embedding 기반 매칭, semantic 유사도, pgvector/vector DB 도입
  (ADR-0026).
- 향후 reranking을 위한 인터페이스/추상화 사전 준비(YAGNI — 필요해지면
  `pkbimport/extraction/llm/DocumentExtractionClient` 패턴을 재사용해
  그때 추가).
- 전체 공고 목록에 대한 일괄 랭킹(`GET /api/jobs?sort=match` 류) —
  이번엔 단일 공고 조회만.
- 매칭 결과 영속화/캐싱(`MatchResult` 엔티티 등).
- 자기소개서 생성 — 이번 Task는 점수/근거/gap 제공까지만.
- 카카오톡 알림 연동.
- 하드코딩 동의어 사전, 한국어-영어 교차 매칭 지원(알려진 한계로 남김).
- 인증/다중 사용자 구분(현재 PKB 전체가 단일 사용자 소유라는 기존
  전제를 그대로 따른다).
- `careerLevel`/`educationRequirement`를 점수 계산에 반영하는 로직.

## Acceptance Criteria

- [x] 존재하지 않는 `jobId`로 `GET /api/jobs/{jobId}/match` 호출 시
      `404`를 반환한다.
- [x] PKB가 완전히 비어 있는 상태에서 존재하는 `jobId`로 호출하면
      `200`과 함께 `overallScore=0.0`, 모든 `recommended*` 배열이 빈
      배열, `unmatchedJobCategories`에 해당 공고의 모든 `jobCategory`
      조각이 나열된 응답을 반환한다.
- [x] `jobCategory`, `ExperienceTag.keyword`, `CareerExperience.title`
      중 하나 이상이 정확히 일치하는 강한 매치 PKB 데이터를 구성하면
      해당 `CareerExperience`가 `recommendedExperiences`에 포함되고
      `matchedFields`에 실제 매칭된 필드가 정확히 표기된다.
- [x] `CareerExperience`가 6개 이상 매칭될 때 `recommendedExperiences`는
      정확히 5개만 반환하고, `Certification`/`Education`/`Award`가 각
      4개 이상 매칭될 때 각각 정확히 3개만 반환한다(top N 상한).
- [x] 동일 `score`를 가진 두 항목이 있을 때 `id` 오름차순으로 정렬되고,
      동일 입력으로 동일 요청을 반복해도 항상 같은 순서/점수를 반환한다
      (결정성).
- [x] `ExperienceTag.keyword`가 대소문자만 다른 경우(예: `"Java"` vs
      `"java"`)에도 매칭된다(정규화 검증).
- [x] `CareerExperience.title`/`summary`만 매칭되고 `keyword` 태그는
      매칭되지 않는 케이스에서 `matchedFields`가 태그를 포함하지 않고
      정확한 근거만 표기한다. (2차 리뷰에서 보강 확인)
- [x] 공고의 `jobCategory`와 전혀 무관한 PKB 항목은 `recommended*`
      배열과 채점(가중합)에서 제외된다(점수 0인 항목은 top N에도 포함
      안 함).
- [x] `Certification`/`Education`/`Award` 각각에 대해 매칭/비매칭
      케이스가 최소 1개씩 존재하고, 매칭 시 항목 점수가 1.0, 비매칭 시
      0으로 채점됨을 검증한다.
- [x] `ImportCandidateStatus.PENDING` 또는 `REJECTED` 상태의
      `ImportCandidate`와 연관된 데이터는(즉 애초에 career 테이블에
      실체가 생성되지 않은 데이터는) 매칭 결과에 전혀 나타나지 않는다
      — 회귀 테스트로 명시적으로 확인한다.
- [x] `ImportCandidateStatus.APPROVED`를 거쳐 실제 생성된
      `CareerExperience`/`Certification`/`Education`/`Award`는 수동
      생성된(MANUAL) 항목과 동일하게 매칭 대상에 포함된다.
- [x] `overallScore`는 항상 `0.0` 이상 `1.0` 이하다(여러 카테고리가 모두
      만점이어도 1.0을 넘지 않음을 가중합 경계 테스트로 확인).
- [x] `MatchEvidence` 응답 구조(`type`/`id`/`title`/`score`/
      `matchedFields`)가 각 카테고리별로 올바른 값을 담고 있다.
- [x] `unmatchedJobCategories`는 매칭되지 않은 `jobCategory` 조각만
      포함하고, 자연어 문장(조언 등)을 생성하지 않는다.
- [x] 같은 `JobPosting`/PKB 상태에 대해 같은 요청을 여러 번 호출해도
      완전히 동일한 응답(순서/점수/필드 포함)을 반환한다(재현성).
- [x] `[자동]` 기존 `JobPosting`/PKB(`career`)/`Application`/
      `Collector`/`pkbimport` 전체 테스트가 이번 변경 이후에도 회귀 없이
      통과한다.
- [x] `docs/METRICS.md` "Product Metrics" 표에 `careerops.match.request`/
      `careerops.match.duration`/`careerops.match.score` 3개 행이
      추가되어 있다.
- [ ] `[수동]` 로컬에서 실제 PKB 데이터(사용자가 이미 입력한 것)와 실제
      `JobPosting` 하나로 `GET /api/jobs/{jobId}/match`를 호출해 응답이
      상식적으로 타당한지(관련 경험이 상위에 오는지) 확인한다.
      **미완료 — 사용자의 실제 dev DB 데이터로 수동 확인이 필요하다
      (합성 fixture로 자동 테스트는 전부 통과했으나, 실제 PKB 톤/분량
      기준 결과 체감은 아직 확인되지 않음).**

## Technical Notes

- 설계 근거: `docs/DECISIONS.md` ADR-0026 (deterministic 채점 채택
  이유, relevance/eligibility 분리, 가중치 70/15/10/5 확정 근거,
  synonym 사전 배제, top N 정책, 한국어-영어 교차 매칭 미지원이라는
  알려진 한계).
- 참고할 기존 패턴: `career/CareerExperienceRepository`의 `@Query`
  기반 검색(파라미터 바인딩 스타일), `job/JobPostingController`의
  `@RestController` + `@GetMapping("/{id}")` 구조,
  `pkbimport/extraction/llm/` 패키지가 "인터페이스로 감싸고 나중에
  구현체를 바꾼다" 패턴의 선례이므로 향후 LLM reranking을 도입할 때
  참고하되 **이번 Task에서는 그 인터페이스를 만들지 않는다**.
- N+1 방지: `CareerExperience` 목록을 조회한 뒤 태그를 개별 조회하지
  말고 `ExperienceTagRepository.findByCareerExperienceIdIn(List<Long>)`
  한 번으로 모든 태그를 가져와 메모리에서 `careerExperienceId` 기준으로
  그룹핑한다.
- `JobPosting.jobCategory`/`careerLevel`/`educationRequirement`는 ALIO
  API가 제공하는 짧은 분류값/쉼표 목록이지 자유 서술형 요건 텍스트가
  아니다(COLLECT-002/ADR-0009) — 채점 로직을 설계할 때 이 전제를 벗어나
  존재하지 않는 요건 텍스트가 있다고 가정하지 않는다.
- 신규 production dependency 없음(전부 기존 Spring Data JPA/Micrometer
  범위 안에서 구현 가능).
- `docs/METRICS.md`의 "새 metric은 필요할 때만 늘린다" 원칙에 따라
  §10에 명시한 3개 외의 추가 metric은 만들지 않는다.
- Codex는 `.ai/metrics/metrics.jsonl`에 직접 기록하지 않는다(과거
  COLLECT-005에서 위반 사례가 있었음 — Claude가 규칙대로 기록한다).

## Test Plan

- Unit: `KeywordNormalizer`(소문자화/공백 정규화/jobCategory split
  경계값 — 빈 문자열, 공백만 있는 조각, 중복 쉼표), `CareerMatchEngine`
  (카테고리별 채점 함수 단위 — 태그/제목/요약/상세 조합별 점수, cap
  동작, 가중합 상한).
- Integration(`@SpringBootTest` 또는 `@DataJpaTest` + 서비스 레이어):
  실제 `JobPosting`/`CareerExperience`/`Certification`/`Education`/
  `Award`/`ExperienceTag`/`ImportCandidate`를 저장한 뒤
  `GET /api/jobs/{jobId}/match` 전체 흐름 검증 — 위 Acceptance Criteria
  각 항목을 커버하는 테스트 메서드를 1:1에 가깝게 대응시킨다.
- 회귀: `./gradlew test` 전체 실행으로 기존 `job`/`career`/
  `application`/`collector`/`pkbimport` 테스트 스위트가 깨지지 않음을
  확인한다.
- `[수동]`: 실제 로컬 dev DB의 PKB/JobPosting으로 응답 타당성 육안 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | `match` 패키지 신규 구현 (Controller/Service/Engine/Normalizer/DTO), `ExperienceTagRepository` 벌크조회 추가, `docs/METRICS.md` 갱신, 단위/통합 테스트 9개 작성 | 컴파일 성공, 신규 테스트 9개 작성. Codex 샌드박스에서 `./gradlew test` 실행 불가(Gradle file-lock 소켓 차단)로 Claude가 직접 실행 — 전체 194/194 PASS(기존 185 + 신규 9), MATCH 3개 테스트 클래스 전부 통과 확인. reviewer 1차 판정: NEEDS_REVISION (AC#7 matchedFields 태그 미포함 케이스 미검증, AC#5 tie ordering 검증 약함) |
| 2 | 리뷰 지적 2건 테스트 보강: `CareerMatchEngineTest.reportsOnlyTitleAndSummaryWhenTagDoesNotMatch()` 신규, `JobMatchControllerTest.appliesTopLimitsAndIdAscendingTieOrdering()` top-5 id 전체순서+6번째 배제 assert로 보강 | 프로덕션 코드 변경 없음(테스트 파일 2개만 수정, mtime으로 확인). 전체 195/195 PASS. reviewer 2차 판정: PASS — `.ai/reviews/MATCH-001-review-2.md` |
