---
task_id: AGENT-001
title: 채용공고별 지원 전략 분석 — MATCH-002 후보 풀 기반 우선순위/포지셔닝 Agent API
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-22T22:00:00+09:00
codex_thread_id: 01a029a7-71af-7370-b38f-77486328b5d1
---

## Context

MATCH-001(ADR-0026, deterministic)과 MATCH-002(ADR-0028, Claude semantic)는
"이 경험이 이 공고와 얼마나 관련 있는가?"에 답한다. `docs/PROJECT.md`의
제품 목표("지원자의 이력에 맞는 적합도를 판단해 카카오톡으로 알림을 보내며,
근거 기반 검증을 거쳐 자기소개서 작성을 돕는다")와 `docs/ROADMAP.md` "Phase
13 이후 후보"의 다음 단계는 그 다음 질문 — "그렇다면 무엇을 강조하고 어떤
경험을 어떤 관점에서 활용해야 하는가?"다. 이 질문에 답하는 것이 AGENT-001의
목표다: 특정 채용공고를 분석하고, 현재 사용자의 실제 PKB 중 어떤 경험과
역량을 지원 과정에서 우선적으로 활용해야 하는지 근거와 함께 지원 전략으로
정리한다.

이 Task의 아키텍처는 Tech Lead(Claude)의 조사·설계 논의를 거쳐 사용자
승인을 받은 상태이며, 이 문서는 그 승인된 설계를 Codex가 구현할 수 있는
명세로 옮긴 것이다. 핵심 전제는 두 가지다:

1. **MATCH-002가 이미 전체 PKB를 훑어 골라놓은 관련도 상위 후보 풀(최대
   14개: 경험 5/자격·학력·수상 각 3)만 두 번째 LLM 호출의 입력으로 쓴다** —
   전체 PKB를 다시 LLM에 넣지 않는다. 이렇게 하면 프롬프트가 작아지고,
   MATCH-002가 애초에 매치를 거의 찾지 못한 공고(관련성 낮은 공고)에서는
   후보 풀 자체가 작아져 억지 positioning이 구조적으로 억제된다.
2. **AGENT는 새로운 관련도 점수를 만들지 않는다.** semanticScore는
   MATCH-002가 계산한 값이 유일한 source of truth이고, 이번 두 번째 LLM은
   오직 "이 후보 풀 안에서 무엇을 얼마나 우선적으로 강조할 것인가"라는
   전략적 우선순위만 판단한다. 이 원칙은 사용자가 1차 설계 리뷰에서
   명시적으로 수정 요청한 핵심 사항이므로 반드시 그대로 반영한다.

AGENTS.md의 핵심 제약("AI가 사용자가 하지 않은 경험/수치를 만들어내지
못하게 막는다")은 이 Task에도 동일하게 적용된다 — LLM은 후보 풀 id 밖의
어떤 경험/자격/학력/수상도 생성할 수 없고, MATCH-002가 만든 자연어
`reason`(첫 번째 LLM의 판단)을 두 번째 LLM에게 사실처럼 전달해 환각을
증폭시키지 않는다(§3.4 참고).

## Scope

`com.careerops.backend.agent` 신규 패키지에 `POST
/api/jobs/{jobId}/agent-analysis` API를 추가한다. `match`/`match/semantic`
패키지는 **한 글자도 수정하지 않는다** — `SemanticJobMatchService.match()`를
기존 Spring `@Autowired` 서비스로 그대로 재사용한다(HTTP 재호출도,
Controller 간 호출도 아니다).

### 1. 패키지 구조 (신규)

```
backend/src/main/java/com/careerops/backend/agent/
├── AgentAnalysisController.java
├── AgentAnalysisService.java
├── dto/
│   ├── AgentAnalysisResponse.java     (공개 응답 최상위 record)
│   ├── ExperienceRecommendation.java  (priority 있음)
│   ├── PkbRecommendation.java         (Certification/Education/Award 공용, type 필드 없음)
│   └── AgentEvidenceSource.java       (신규 별도 enum, match.dto.EvidenceSource 무관)
└── llm/
    ├── AgentAnalysisClient.java             (interface)
    ├── AnthropicAgentAnalysisClient.java     (구현체 1개뿐, provider registry 없음)
    ├── AgentAnalysisPromptBuilder.java
    ├── AgentAnalysisException.java           (Reason enum)
    └── dto/
        ├── RawAgentAnalysisResult.java
        ├── RawExperienceRecommendation.java
        └── RawPkbRecommendation.java
```

`pkbimport/extraction/llm/`(ADR-0024) / `match/semantic/`(ADR-0028) 패턴을
그대로 미러링한다.

### 2. 기존 파일 수정 범위

- **`match/` 패키지 전체(컨트롤러/서비스/엔진/DTO/semantic 하위 전부) —
  무변경.** `SemanticJobMatchService.match(Long jobId)`를 `@Autowired`로
  주입받아 그대로 호출한다.
- `career/ExperienceBulletRepository.java`에 신규 메서드 1개 추가:
  `List<ExperienceBullet> findByCareerExperienceIdIn(List<Long> careerExperienceIds);`
  (`ExperienceTagRepository.findByCareerExperienceIdIn`과 동일한 스타일 —
  명시적 정렬 없음, 정렬이 필요하면 서비스/프롬프트 빌더에서
  `ExperienceBullet::getSortOrder`로 직접 정렬).
- `application.yml`에 `careerops.ai.agent.*` timeout 2개 키만 추가(§8).
- `docs/METRICS.md`에 신규 metric 표 3행 추가(§11, Technical Notes에 정확한
  행 내용 명시).
- 신규 엔티티/컬럼/migration 없음.

### 3. Request Flow

```
POST /api/jobs/{jobId}/agent-analysis  (요청 바디 없음)
  → AgentAnalysisController
  → AgentAnalysisService.analyze(jobId)
      1. jobs.findById(jobId) 없으면 404 (semantic match/agent LLM 둘 다 미호출)
      2. CareerExperience/Certification/Education/Award 4종 count 전부 0이면
         즉시 409(§9) — semantic match/agent LLM 둘 다 미호출
      3. semanticJobMatchService.match(jobId) 호출 → SemanticJobMatchResponse
         확보(deterministicScore/semanticScore/experienceMatches/
         certificationMatches/educationMatches/awardMatches/gaps).
         이 호출이 SemanticMatchException을 던지면 AgentAnalysisException
         (Reason.SEMANTIC_MATCH_FAILED)으로 감싸 재던짐 → 컨트롤러 502.
      4. 위 응답의 4개 match 배열에서 id만 모아 카테고리별 "후보 풀"
         구성. 그 id만 CareerExperienceRepository/CertificationRepository/
         EducationRepository/AwardRepository.findAllById(...)로 실제
         엔티티 재조회(전체 PKB 재조회 아님). CareerExperience는 추가로
         ExperienceTagRepository.findByCareerExperienceIdIn(기존 재사용)과
         신규 ExperienceBulletRepository.findByCareerExperienceIdIn으로
         태그/불릿까지 재조회.
      5. AgentAnalysisPromptBuilder로 JobPosting 필드 + 후보 풀의 실제
         원본 데이터 + MATCH-002 score/evidence enum(카테고리 힌트) +
         MATCH-002 gaps(참고자료)를 프롬프트에 구성. **MATCH-002 reason은
         절대 포함하지 않는다(§4).**
      6. agentAnalysisClient.analyze(...) 호출 → RawAgentAnalysisResult.
         실패 시 AgentAnalysisException → 컨트롤러 502.
      7. 검증(§5)·dedup·truncate·priority 부여 → 서버가 실제 엔티티로
         title 채움 → gaps는 MATCH-002 gaps를 그대로 복사 →
         AgentAnalysisResponse 반환.
```

`SemanticJobMatchService.match()`를 그대로 호출하므로
`careerops.semantic-match.*` 지표가 이 API 호출로도 함께 증가하는 것은
**의도된 정상 동작**이다(별도 우회/복제 코드 금지). Anthropic 호출은 성공
경로 기준 요청당 최대 2회(semantic matching 1회 + agent analysis 1회).

### 4. Agent LLM 입력 — reason 제외

두 번째 LLM 호출 입력에는 다음만 포함한다:

- `JobPosting`: `companyName`/`title`/`jobCategory`/`careerLevel`/
  `educationRequirement`(MATCH-002와 동일 필드).
- 후보 풀 각 항목의: MATCH-002 `id`, MATCH-002 `score`(숫자), MATCH-002
  `evidence`(`match.dto.EvidenceSource` 목록, 카테고리 힌트로만), 그리고
  DB에서 재조회한 실제 PKB 원본 필드:
  - `CareerExperience`: `title`/`organization`/`role`/`summary`/`detail`/
    tags(`ExperienceTag.keyword`)/bullets(`ExperienceBullet.content`,
    `sortOrder` 순 정렬)
  - `Certification`: `name`/`issuer`/`description`
  - `Education`: `institution`/`major`/`degree`/`status`/`description`
  - `Award`: `title`/`issuer`/`description`
- **MATCH-002가 LLM으로 생성한 자연어 `reason` 문자열은 절대 포함하지
  않는다.** 첫 번째 LLM의 판단을 두 번째 LLM이 사실처럼 증폭(hallucination
  전파)하는 것을 막기 위함이다. Agent는 실제 PKB 원본 데이터와 evidence
  enum 카테고리 힌트, 숫자 score만 보고 스스로 reason/emphasisPoints를
  새로 생성해야 한다.
- MATCH-002의 `gaps`는 참고자료로 프롬프트에 포함할 수 있다(출력에는
  영향 없음, §6 참고).

### 5. Structured Output DTO 및 검증 정책

PKB-008.1(ADR-0027)의 "schema당 nullable(union) 파라미터 최대 16개" 제약을
존중해 **nullable 필드를 하나도 만들지 않는다**(MATCH-002와 동일 전략 —
모든 필드 required, "없음"은 빈 List로 표현).

```java
// agent/llm/dto/RawAgentAnalysisResult.java
public record RawAgentAnalysisResult(
    String roleSummary,
    List<String> keyThemes,
    List<String> knownRequirements,
    String positioningHeadline,
    String positioningSummary,
    List<RawExperienceRecommendation> recommendedExperiences,
    List<RawPkbRecommendation> recommendedCertifications,
    List<RawPkbRecommendation> recommendedEducations,
    List<RawPkbRecommendation> recommendedAwards,
    String primaryMessage,
    List<String> secondaryMessages,
    List<String> avoidOrBeCareful) {}
// gaps 필드 없음 — §6, 서버가 MATCH-002 gaps를 그대로 pass-through

// agent/llm/dto/RawExperienceRecommendation.java
public record RawExperienceRecommendation(
    Long id, String reason, List<String> emphasisPoints,
    List<AgentEvidenceSource> evidence) {}
// score/relevanceScore/priority 필드 없음 — 배열 순서가 우선순위(§2 원칙)

// agent/llm/dto/RawPkbRecommendation.java
public record RawPkbRecommendation(
    Long id, String reason, List<AgentEvidenceSource> evidence) {}
// score 없음, type 없음
```

**검증/후처리 정책(카테고리: 경험/자격/학력/수상 각각 동일 알고리즘 적용,
경험만 추가로 priority 부여)**:

1. **all-or-nothing 실패(구조적 hallucination)**: LLM이 반환한 `id`가 해당
   카테고리 후보 풀(그 카테고리에서 실제로 프롬프트에 포함시켰던 id
   집합)에 없으면 **응답 전체를 실패 처리**
   (`AgentAnalysisException.Reason.UNKNOWN_CANDIDATE_ID` → 502, `result`
   metric `validation_failed`). MATCH-002 "all-or-nothing" 원칙(ADR-0024
   결정 6 계승)과 동일 — 검사는 카테고리 상한 truncate 이전, 반환된 모든
   항목에 대해 순서대로 수행한다(캡을 넘는 뒤쪽 항목이라도 unknown id면
   실패).
2. **중복 id — 사소한 이탈(실패 아님)**: 같은 카테고리에서 같은 id가
   두 번 이상 나오면 **배열에서 먼저 등장한 것만 유지**하고 이후 등장은
   버리며 WARN 로그(id/카테고리만, 민감정보 없음)를 남긴다. score 기반
   비교가 불가능하므로(LLM이 score를 만들지 않음) MATCH-002의 "최고 score
   유지"와 tie-break 기준만 다르고 사상은 동일(사소한 이탈, 실패 아님).
3. **카테고리별 상한 초과 — 사소한 이탈(실패 아님)**: 중복 제거 후 남은
   목록이 상한(경험 5, 자격/학력/수상 각 3)을 넘으면 **배열 순서상 앞쪽
   N개만 유지**하고 나머지는 버린다(score 재정렬 없음).
4. **`priority` — 경험 추천에만 존재, 서버 계산**: 위 1~3 처리 후 남은
   `recommendedExperiences` 순서 그대로 `priority = 1..N`을 부여한다.
   LLM 출력 어디에도 `priority`/`score`/`relevanceScore` 필드는 존재하지
   않는다(컴파일 타임에 이미 보장되지만 회귀 방지 테스트로도 확인, §12
   테스트 9/13).
5. **`semanticMatchScore` — 서버가 MATCH-002 원본에서 채움**: 최종 응답의
   각 추천 항목 `semanticMatchScore`는 해당 `id`의 MATCH-002
   `SemanticMatchEvidence.score()`를 그대로 사용한다(LLM이 이 값을 다시
   만들지 않는다). 필드명을 `relevanceScore`처럼 모호하게 짓지 않고
   `semanticMatchScore`로 명명해 출처(MATCH-002)가 이름에서 드러나게 한다.
6. **텍스트 길이/개수 상한 — 초과 시 truncate(실패 아님)**: 자연어 내용
   자체(사실 여부)는 검증하지 않는다(reason substring 검증 등 금지, prompt
   제약으로만 사실 기반을 강하게 지시).
   - `reason`(경험/자격/학력/수상 공통): 300자 초과 시 300자로 truncate.
   - `emphasisPoints`(경험 전용): 개수 5개 초과 시 앞 5개만 유지, 각 항목
     150자 초과 시 150자로 truncate.
   - `keyThemes`/`knownRequirements`: 개수 5개 초과 시 앞 5개만 유지.
   - `secondaryMessages`/`avoidOrBeCareful`: 개수 3개 초과 시 앞 3개만
     유지.
   - `primaryMessage`/`roleSummary`/`positioningHeadline`/
     `positioningSummary`는 배열이 아니라 단일 자연어 필드라 반복
     비용/DoS 증폭 우려가 없어 별도 상한을 두지 않는다(과잉 엔지니어링
     방지, MATCH-002가 `reason` 외 다른 자유 텍스트 필드에 상한을 두지
     않은 것과 동일 원칙).

### 6. Gaps — Agent가 생성/재작성하지 않음

`RawAgentAnalysisResult`에는 `gaps` 필드가 없다. 최종
`AgentAnalysisResponse.gaps`는 **서버가 `SemanticJobMatchResponse.gaps`를
그대로 복사**해서 채운다 — Agent LLM 호출과 완전히 무관한 pass-through다.
MATCH-002 gaps는 Agent 프롬프트에 참고자료로 제공될 수 있으나(§4), 출력
`gaps` 필드에는 영향을 주지 않는다.

### 7. 공개 응답 DTO

```java
// agent/dto/AgentAnalysisResponse.java
public record AgentAnalysisResponse(
    Long jobPostingId,
    String roleSummary,
    List<String> keyThemes,
    List<String> knownRequirements,
    String positioningHeadline,
    String positioningSummary,
    List<ExperienceRecommendation> recommendedExperiences,
    List<PkbRecommendation> recommendedCertifications,
    List<PkbRecommendation> recommendedEducations,
    List<PkbRecommendation> recommendedAwards,
    String primaryMessage,
    List<String> secondaryMessages,
    List<String> avoidOrBeCareful,
    List<String> gaps,
    Instant computedAt) {}

// agent/dto/ExperienceRecommendation.java
public record ExperienceRecommendation(
    Long id, String title, int priority, double semanticMatchScore,
    String reason, List<String> emphasisPoints,
    List<AgentEvidenceSource> evidence) {}

// agent/dto/PkbRecommendation.java — Certification/Education/Award 공용
// (배열 자체가 카테고리를 나타내므로 type 필드 없음)
public record PkbRecommendation(
    Long id, String title, double semanticMatchScore, String reason,
    List<AgentEvidenceSource> evidence) {}
```

`ExperienceRecommendation`/`PkbRecommendation`의 `title`은 LLM 출력이
아니라 서버가 검증된 id로 재조회한 실제 엔티티 값이다(MATCH-002의
`SemanticMatchEvidence.title` 원칙과 동일 — LLM이 실제와 다른 제목을
지어낼 표면 자체를 없앤다). `semanticMatchScore`/`title` DTO 주석에
"MATCH-002가 계산/보유한 값이며 이 API의 LLM이 재생성하지 않는다"를
명시한다.

### 8. Evidence Enum — 별도 enum 신설(MATCH-002 무변경 유지)

MATCH-002는 이미 3라운드 리뷰를 거쳐 PASS된 production API다. 기존
`match.dto.EvidenceSource`에 값을 추가하는 안은 "MATCH-002 전 파일
무변경"이라는 전제와 모순되므로 채택하지 않는다. `com.careerops.backend
.agent.dto.AgentEvidenceSource`를 별도 enum으로 신설한다:

```java
public enum AgentEvidenceSource {
    JOB_TITLE, JOB_CATEGORY, CAREER_LEVEL, EDUCATION_REQUIREMENT,
    EXPERIENCE_TAG, EXPERIENCE_TITLE, EXPERIENCE_SUMMARY, EXPERIENCE_DETAIL,
    EXPERIENCE_BULLET,
    CERTIFICATION_NAME, CERTIFICATION_DESCRIPTION,
    EDUCATION_MAJOR, EDUCATION_DESCRIPTION,
    AWARD_TITLE, AWARD_DESCRIPTION
}
```

`match.dto.EvidenceSource`와 값 집합이 `EXPERIENCE_BULLET` 1개를 제외하고
동일하지만, 두 enum은 서로 다른 API 버전 관리 하에 독립적으로 진화할 수
있어야 하므로(MATCH-002는 이미 PASS된 production 계약) 공유 타입으로
합치지 않는다. 근거: ADR-0029.

### 9. Timeout

```yaml
careerops:
  ai:
    agent:
      connect-timeout-seconds: 10
      request-timeout-seconds: 60
```

MATCH-002의 `careerops.ai.match.*`(connect 10/request 45)와 별개
네임스페이스이고, PKB-008.1의 300초(16,000 output token 구조화 추출
용도)는 재사용하지 않는다 — 이번 스키마(자연어 요약 + 최대 14개 후보에
대한 reason/emphasisPoints)는 그보다 훨씬 작은 출력 규모다.

**주의**: "45s + 60s = 105s가 endpoint의 hard maximum"이라고 표현하지
않는다. 이는 provider별 request timeout일 뿐이며, Anthropic SDK 기본
재시도(429/5xx/네트워크 timeout 한정)에 따라 실제 wall-clock은 이보다
더 길어질 수 있다. 이번 Task에서 별도 end-to-end HTTP timeout은 추가하지
않는다.

### 10. PKB Empty 정책

승인된 PKB(`CareerExperience`/`Certification`/`Education`/`Award`) 4개가
모두 0건이면 **Agent LLM도 semantic matching도 호출하지 않고 명시적으로
`409 CONFLICT`**를 던진다:

```java
if (experienceRepository.count() == 0 && certificationRepository.count() == 0
        && educationRepository.count() == 0 && awardRepository.count() == 0) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
        "승인된 PKB가 없어 지원 전략을 분석할 수 없습니다");
}
```

이 체크는 `JobPosting` 404 체크 다음, `semanticJobMatchService.match()`
호출 **이전**에 수행한다(순서 중요 — 불필요한 semantic match 호출 자체를
막는다). `ImportBatchService`/`ImportCandidateService`가 "현재 상태가 이
동작을 지원하지 않음"을 전역 `@ControllerAdvice` 없이 서비스가 직접
`ResponseStatusException(CONFLICT, message)`를 던지는 기존 컨벤션을 그대로
따른다 — `AgentAnalysisException`을 거치지 않는다. `result` metric은
`pkb_empty`.

### 11. Metrics

`docs/METRICS.md` "Product Metrics" 표에 아래 3행을 추가한다(정확한 표
행 내용은 Technical Notes 참고):

- `careerops.agent-analysis.request` (Counter, `result`=`success`|
  `job_not_found`|`pkb_empty`|`provider_error`|`validation_failed`)
- `careerops.agent-analysis.duration` (Timer, 태그 없음 — semantic match
  호출을 포함한 전체 처리 시간)
- `careerops.agent-analysis.recommended_experiences` (DistributionSummary,
  태그 없음 — 성공 요청의 최종 `recommendedExperiences` 개수 분포)

`result` 분류: `job_not_found`=JobPosting 404, `pkb_empty`=§10 조기 반환,
`validation_failed`=`AgentAnalysisException.Reason.UNKNOWN_CANDIDATE_ID`만,
그 외 모든 `AgentAnalysisException`(semantic match 실패 wrapping 포함,
network timeout/4xx/retry exhausted/malformed response)은
`provider_error`. `careerops.semantic-match.*`(MATCH-002)는 이 API 호출로
자연히 함께 증가하는 것이 의도된 동작이라 별도로 다루지 않는다.

### 12. Privacy / Logging / Prompt Injection

- 로그(INFO 이상)에 PKB 원문(`detail`/`summary`/`description`/bullet
  `content`), JobPosting `title` 원문, raw LLM request/response, API key를
  출력하지 않는다. 실패 시에도 id/카테고리/`AgentAnalysisException.Reason`
  값 등 메타데이터만 남긴다(`AnthropicSemanticJobMatchClientTest`/
  `AnthropicDocumentExtractionClientTest`의 `missingKeyAndSensitiveInputAreNotLogged`
  패턴 재현).
- JobPosting/PKB/evidence 힌트는 모두 **DATA**다. system prompt에서
  `SemanticMatchPromptBuilder.systemPrompt()`와 동일한 원칙으로 명확히
  분리한다 — `<job>`/`<pkb>` 태그 안 내용은 지시가 아니라는 문구를
  명시한다.

### 13. Provider 실패 처리 — 컨트롤러 로컬 핸들러

Partial response/silent fallback 없음. semantic matching 실패 →
`AgentAnalysisException.Reason.SEMANTIC_MATCH_FAILED`로 감싸 재던짐, agent
analysis(2차 LLM) 실패 → provider 예외를 `AgentAnalysisException`으로
분류(네트워크/4xx/retry exhausted/malformed는
`AnthropicSemanticJobMatchClient.classify()`와 동일한 분류 로직을
`AnthropicAgentAnalysisClient`에도 적용). 두 경우 모두
`AgentAnalysisController`의 로컬 `@ExceptionHandler(AgentAnalysisException.class)`가
502를 반환한다(전역 `@ControllerAdvice` 신설 금지 — 컨트롤러마다 로컬
핸들러라는 기존 컨벤션 유지, `SemanticJobMatchController`와 동일 패턴).
`JobPosting` 404/PKB empty 409는 `ResponseStatusException`이라 Spring
기본 처리로 충분하고 별도 로컬 핸들러가 필요 없다.

```java
public class AgentAnalysisException extends RuntimeException {
    public enum Reason {
        NETWORK_TIMEOUT, PROVIDER_4XX, PROVIDER_RETRY_EXHAUSTED,
        MALFORMED_RESPONSE, UNKNOWN_CANDIDATE_ID, SEMANTIC_MATCH_FAILED
    }
    public boolean isValidationFailure() { return reason == Reason.UNKNOWN_CANDIDATE_ID; }
}
```

(`SCORE_OUT_OF_RANGE`가 없는 이유: 이번 LLM은 score를 생성하지 않으므로
범위 검증 대상 자체가 없다 — §5 원칙의 구조적 귀결.)

## Out of Scope

- 자기소개서 완성본 생성, 문항별 답변 생성.
- 웹검색, 합격 가능성 예측/판정.
- 카카오톡 알림, scheduler, frontend.
- embedding/vector DB 도입.
- PKB 생성/수정(`career/*` 엔티티/서비스/컨트롤러 무변경, `ExperienceBulletRepository`
  신규 메서드 1개 추가는 예외).
- MATCH-001/MATCH-002 채점 로직/DTO/컨트롤러/서비스 수정(패키지 전체
  무변경, git diff로 확인).
- OCR, `Application`(`JobApplication`) 자동 생성.
- AGENT-002(및 그 이후) — 이번 Task는 "무엇을 강조할지" 전략 산출까지만.
- 인증/다중 사용자 구분(MATCH-001/002와 동일 전제).
- 매칭/분석 결과 영속화·캐싱.
- `AnthropicClientFactory` 같은 provider 공유 abstraction 신설(ADR-0024
  결정 3 재적용).
- 신규 production dependency.
- `match.dto.EvidenceSource`에 값 추가 또는 두 enum 통합(§8, ADR-0029).
- eligibility(경력/학력 충족 여부) 판정(ADR-0028 결정 7과 동일 이유로
  범위 밖).

## Acceptance Criteria

- [ ] 존재하지 않는 `jobId`로 `POST /api/jobs/{jobId}/agent-analysis` 호출 시
      `404`를 반환하고 semantic match/agent LLM 어느 쪽도 호출하지 않는다.
- [ ] `CareerExperience`/`Certification`/`Education`/`Award`가 전부 0건인
      상태에서 존재하는 `jobId`로 호출하면 `409`를 반환하고 semantic
      match/agent LLM 어느 쪽도 호출하지 않는다.
- [ ] fake `SemanticJobMatchClient` + fake `AgentAnalysisClient`를 함께
      주입한 상태에서 semantic match가 정상 응답을 반환하면 agent 분석도
      정상 `200` 응답을 반환한다.
- [ ] AI 관련 공고에 대해 fake `AgentAnalysisClient`가 AI 관련 경험 id를
      배열의 첫 번째로 반환하면, 최종 응답에서 해당 경험이
      `recommendedExperiences[0].priority == 1`로 나타난다(순서 기반
      우선순위가 실제로 서버 계산값임을 fake로 순서를 통제해 확인).
- [ ] MATCH-002 후보 풀(해당 공고의 `experienceMatches` 등)에 포함되지
      않은(무관한) 경험은, fake client가 그 id를 반환하지 않는 한
      `recommendedExperiences`에 나타나지 않는다(애초에 후보 풀 밖이므로
      LLM이 반환하면 unknown id 실패로 이어짐 — 다음 항목과 연결 확인).
- [ ] fake `AgentAnalysisClient`가 카테고리 후보 풀에 없는 id를 하나라도
      반환하면(경험/자격/학력/수상 각각 최소 1개 케이스) 응답 전체가
      실패 처리(`502`)되고, 응답 어디에도 해당 항목이 노출되지 않는다.
- [ ] fake client가 `recommendedExperiences`를 6개 이상(카테고리 상한 5
      초과) 반환하면, 앞쪽 5개만(배열 순서 유지, score 기준 재정렬 없음)
      최종 응답에 남는다. `Certification`/`Education`/`Award`도 각각
      4개 이상 반환 시 상한 3개로 동일하게 truncate된다.
- [ ] fake client가 같은 카테고리에서 동일 id를 두 번 반환하면 요청이
      실패하지 않고, **먼저 등장한 항목만** 최종 응답에 남으며(두 번째
      등장은 폐기), `recommendedExperiences`의 `priority`가 dedup 이후
      1..N으로 빈틈없이 재부여된다.
- [ ] `RawAgentAnalysisResult`/`RawExperienceRecommendation`/
      `RawPkbRecommendation`에는 `score`/`relevanceScore`/`priority`/
      `gaps`/`type` 필드가 존재하지 않는다(컴파일 타임 구조 보장 + 회귀
      방지 테스트로 명시적으로 확인).
- [ ] 최종 `ExperienceRecommendation`/`PkbRecommendation`의
      `semanticMatchScore`가 해당 `id`에 대해 `SemanticJobMatchResponse`가
      가진 원본 `score`와 정확히 일치한다(LLM이 이 값을 다시 만들지
      않음을 fake `SemanticJobMatchClient`가 반환한 score와 최종 응답을
      비교해 검증).
- [ ] `AgentAnalysisPromptBuilder`가 만든 최종 프롬프트 문자열에
      MATCH-002 `reason`(fake reason에 특이 문자열을 심어 검증) 문자열이
      전혀 포함되지 않는다(단위 테스트, PromptBuilder 직접 호출).
- [ ] `AgentAnalysisResponse.gaps`가 `SemanticJobMatchResponse.gaps`와
      정확히 동일하다(agent LLM 출력과 무관, fake client가 다른 gaps 유사
      문자열을 만들어내도 무시됨을 확인).
- [ ] `reason`이 300자를 초과하면 300자로 truncate되고, `emphasisPoints`가
      5개를 초과하면 앞 5개만 유지되며 각 항목이 150자 초과 시 150자로
      truncate된다(전체 실패 아님). `keyThemes`/`knownRequirements`가
      5개, `secondaryMessages`/`avoidOrBeCareful`가 3개를 초과하면 각각
      앞쪽만 유지된다.
- [ ] fake `AgentAnalysisClient`가 timeout
      (`AgentAnalysisException.Reason.NETWORK_TIMEOUT`) 또는 malformed
      structured response(`MALFORMED_RESPONSE`)를 던지면 컨트롤러는
      `502`를 반환한다.
- [ ] fake `SemanticJobMatchClient`가 실패(`SemanticMatchException`)를
      던지면 agent LLM(`AgentAnalysisClient`)은 전혀 호출되지 않고,
      컨트롤러는 `502`를 반환한다(fake 호출 카운트로 미호출 검증).
- [ ] `ImportCandidateStatus.PENDING`/`REJECTED` 상태와 연관된 career
      데이터는 후보 풀/프롬프트/응답 어디에도 나타나지 않는다(회귀,
      MATCH-002 패턴 재사용 — 애초에 `career_*` 테이블에 실체가 없음).
- [ ] `ExperienceBulletRepository.findByCareerExperienceIdIn(List<Long>)`
      신규 메서드가 여러 `careerExperienceId`에 걸친 bullet을 올바르게
      조회한다(단위 테스트).
- [ ] `docs/METRICS.md` "Product Metrics" 표에
      `careerops.agent-analysis.request`/`careerops.agent-analysis.duration`/
      `careerops.agent-analysis.recommended_experiences` 3개 행이 추가되어
      있다.
- [ ] `application.yml`에 `careerops.ai.agent.connect-timeout-seconds`
      (기본 10)/`careerops.ai.agent.request-timeout-seconds`(기본 60)가
      추가되어 있고, `careerops.ai.api-key`/`careerops.ai.model`은 기존
      값을 그대로 재사용한다(신규 key 없음).
- [ ] `[자동]` 기존 `JobPosting`/`career`/`Application`/`Collector`/
      `pkbimport`/`match`(MATCH-001/MATCH-002) 전체 테스트가 이번 변경
      이후에도 회귀 없이 통과한다. `git diff --stat`(또는
      `git status --porcelain`)로 `match/` 디렉터리 전체가 이번 Task로
      전혀 수정되지 않았음을 확인한다.
- [ ] `./gradlew test` 전체 실행이 통과한다.
- [x] `[수동]` **Case A** — 실제 dev DB + 실제 Anthropic API로 "한국교통안전공단
      AI서비스개발"(jobId=7552) 공고에 `POST .../agent-analysis`를 호출해 (1) 응답의
      모든 `id`가 실제 존재하는 PKB id인지, (2) AI/RAG 관련 경험이
      `recommendedExperiences` 상위에 포함되는지, (3) `positioningSummary`/
      `primaryMessage`가 공고에 실제로 없는 요구사항을 만들어내지 않는지
      육안 확인한다(정확한 문구/개수 hardcode 없이 상식적 타당성만 확인).
      **결과: PASS.** 소요 85.7초(200 OK). Top3: FinSight(0.85, priority 1)/
      계층적 다중 에이전트 RAG 연구(0.8, priority 2)/LG Aimers AI(0.7, priority 3)
      — 전부 실제 PKB id(8/7/6). `avoidOrBeCareful`가 Education.description
      원문("2027.02 졸업 예정")에 근거해 "개방형직위는 경력을 요구하는데 PKB가
      재학/인턴/교육과정 위주"라는 정확한 caution을 생성(hallucination
      아님 — 서버가 프롬프트에 넣은 실제 description 필드를 DB에서 재확인해
      원문 그대로임을 검증함). 공고에 없는 기술(Kubernetes/AWS 등) 생성 없음.
- [x] `[수동]` **Case B** — "한전KDN(주) AI기반 무정전활선작업 무인화 로봇플랫폼
      개발"(jobId=7501)에 대해서도 Case A와 동일한 sanity check(실제 id/명백한
      hallucination 없음/AI·개발 경험 우선순위 반영)를 수행한다.
      **결과: PASS.** 소요 66.4초. Top3 동일(FinSight 0.3/RAG연구 0.4/LG
      Aimers 0.45, id 8/7/6). `positioningSummary`가 "지원자는 해당 도메인
      (활선작업, 로봇 하드웨어, 실증) 직접 경험은 보유하고 있지 않다"를
      명시적으로 인정, `avoidOrBeCareful`에 "로봇 플랫폼(하드웨어/제어) 개발
      및 실증 경험이 없음"을 명시 — "로봇 개발 경험 있음"처럼 없는 경험을
      생성하지 않음(FAIL 조건 회피 확인). `gaps`=["AI기반 로봇플랫폼",
      "무정전활선작업","무인화","실증"](MATCH-002 pass-through, 실제 공고
      제목 단어와 일치).
- [x] `[수동]` **Case C** — "한전KDN(주) 구미지사 AMI분야 일용근로자
      모집공고"(jobId=960)에 대해 MATCH-002 후보 풀 자체가 작거나(낮은
      관련도) 빈약함이 Agent 응답에도 그대로 반영되는지(억지 positioning이
      생기지 않는지) 확인한다.
      **결과: PASS.** 소요 68.9초. `recommendedExperiences` 3건뿐(Case A/B/D는
      5건) — 후보 풀 자체가 작음이 그대로 반영됨. `semanticMatchScore`
      0.2~0.3로 Case A(0.4~0.85) 대비 명확히 낮음. `positioningSummary`/
      `avoidOrBeCareful` 모두 "AMI/전기설비 실무와는 성격이 다르므로 신중하게
      접근"/"과대 포장하지 않도록 주의"를 명시 — Case A와 같은 강한 positioning
      생성 안 됨. `gaps`=["AMI","전기.전자"].
- [x] `[수동]` **Case D** — "국방과학연구소 '26년 하반기 임용 수시
      공개채용"(jobId=988, 종합 공고)에 대해 Case A와 전략적 강조점(어떤
      경험을 왜 우선하는지)에 상식적인 차이가 있는지, 공고에 없는 요구사항을
      창작하지 않는지 확인한다.
      **결과: PASS.** 소요 63.2초. Top1은 RAG연구(0.6)이지만 Case A(0.4~0.85,
      스프레드 0.45)와 달리 top5 score가 0.5~0.6로 매우 좁게 밀집 — "연구"
      단어 하나로 RAG 연구가 압도적 1순위가 되지 않음(다른 경험들과 점수
      차이가 크지 않음)을 확인. `roleSummary`/`knownRequirements`가 공고의
      실제 다직군 특성(사업관리/경영/법률/음식서비스/기계/재료/화학/전기전자/
      정보통신/연구)을 그대로 반영, 없는 기술 요구사항 생성 없음.
      `gaps`=8개 카테고리(공고에 실제 존재하나 PKB와 무관한 직군, MATCH-002
      pass-through).

  공통 검증: 4건 모두 `recommendedExperiences`/`recommendedCertifications`/
  `recommendedEducations`/`recommendedAwards`의 모든 `id`가 실제 PKB
  엔티티(experience 3/4/5/6/7/8, certification 6/7/8/11, education 4)와
  일치, unknown id 없음, 502 없음. `careerops_agent_analysis_request_total
  {result="success"}=4`, `careerops_semantic_match_request_total
  {result="success"}=4`(Agent 호출로 자연 증가, 설계대로), `careerops_match_
  request_total`(MATCH-001)은 4건 모두 0으로 불변 확인(Prometheus 실측).
  서버 로그에 예외/에러 없음.

## Technical Notes

- 설계 근거: `docs/DECISIONS.md` **ADR-0029**(이 Task를 위해 신규 추가 —
  score 소유권을 MATCH-002에만 두는 이유, reason을 2차 LLM에 전달하지 않는
  이유, 별도 `AgentEvidenceSource` enum 신설 이유, PKB empty를 409로 처리하는
  이유, 순서 기반 tie-break/truncate 정책, timeout 값과 표현 주의사항).
  `ADR-0026`/`ADR-0028`도 함께 참고해 "관련도 ≠ 합격 가능성", "존재하지
  않는 요건/경험을 만들어내지 않는다" 원칙을 이번 Task에도 동일하게
  적용한다.
- 참고할 기존 패턴: `match/semantic/`(ADR-0028) 전체 — `SemanticJobMatchClient`
  interface + `AnthropicSemanticJobMatchClient` 구현체 1개 + prompt builder
  + exception(Reason enum) + `SemanticJobMatchService.convert()`의
  hallucination 검증/dedup/truncate 로직. 이번 Task는 여기에 "score 기반"
  대신 "배열 순서 기반" tie-break/truncate로 바꾼 버전을 그대로 미러링한다
  (§5). `SemanticJobMatchController`의 로컬 `@ExceptionHandler(...) → 502`
  패턴도 그대로 재사용한다.
- `AgentAnalysisClient.analyze(...)` 시그니처 제안(정확한 형태는 Codex
  재량, 단 아래 계약은 반드시 지킨다 — reason 문자열이 어떤 경로로도
  프롬프트에 들어가지 않아야 하고, 후보 풀의 실제 PKB 원본 필드/태그/불릿/
  MATCH-002 score/evidence enum/gaps가 모두 입력으로 전달돼야 한다):
  ```java
  RawAgentAnalysisResult analyze(
      JobPosting posting,
      List<CareerExperience> experiences,
      Map<Long, List<ExperienceTag>> tagsByExperience,
      Map<Long, List<ExperienceBullet>> bulletsByExperience,
      List<SemanticMatchEvidence> experienceMatchContext,
      List<Certification> certifications,
      List<SemanticMatchEvidence> certificationMatchContext,
      List<Education> educations,
      List<SemanticMatchEvidence> educationMatchContext,
      List<Award> awards,
      List<SemanticMatchEvidence> awardMatchContext,
      List<String> matchGaps);
  ```
  `*MatchContext` 파라미터는 `com.careerops.backend.match.dto.SemanticMatchEvidence`
  객체를 그대로 전달하되, `AgentAnalysisPromptBuilder`는 이 객체의
  `.reason()`을 **절대 호출/출력하지 않는다** — 이 계약을 §12의 PromptBuilder
  단위 테스트로 강제한다.
- N+1 방지: `CareerExperienceRepository`/`CertificationRepository`/
  `EducationRepository`/`AwardRepository`는 이미 `JpaRepository`를
  상속하므로 `findAllById(Iterable<Long>)`가 기본 제공된다(신규 쿼리
  메서드 불필요). 태그는 기존 `ExperienceTagRepository.findByCareerExperienceIdIn`,
  불릿은 신규 `ExperienceBulletRepository.findByCareerExperienceIdIn`을
  사용한다.
- `AnthropicAgentAnalysisClient`의 timeout/예외 분류는
  `AnthropicSemanticJobMatchClient`의 `classify()` 메서드(network
  timeout/4xx/5xx/malformed 구분)를 그대로 참고해 동일 원칙을 적용하되,
  timeout 값은 §9 기준(connect 10/request 60)으로 별도 설정한다.
- `docs/METRICS.md`에 추가할 정확한 표 행(MATCH-002 표 형식 그대로):

  ```
  **채용공고 지원 전략 분석 (AGENT-001)**

  | 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 | 계측 위치 |
  |---|---|---|---|---|---|
  | `careerops_agent_analysis_request_total` | `careerops.agent-analysis.request` | Counter | `result`=`success`\|`job_not_found`\|`pkb_empty`\|`provider_error`\|`validation_failed` | 지원 전략 분석 요청 결과 분포 | `AgentAnalysisService.analyze()` |
  | `careerops_agent_analysis_duration_seconds` | `careerops.agent-analysis.duration` | Timer | 없음 | semantic match 호출을 포함한 전체 처리 시간 | `AgentAnalysisService.analyze()` |
  | `careerops_agent_analysis_recommended_experiences` | `careerops.agent-analysis.recommended_experiences` | DistributionSummary | 없음 | 성공 요청의 최종 `recommendedExperiences` 개수 분포 | `AgentAnalysisService.analyze()` |
  ```

  이 지표들이 실제로 관찰하려는 것: `request`의 `result` 분포로 두 단계
  LLM 호출 체인의 안정성(특히 `provider_error` 비율이 MATCH-002 단독
  대비 얼마나 늘어나는지 — 두 번째 LLM 호출이 추가 실패 지점이 되는지),
  `duration`으로 60초 request timeout이 실제 두 LLM 호출 합산 시간에
  충분한지, `recommended_experiences` 분포로 후보 풀이 작은 공고(Case C
  유형)에서 실제로 추천 개수가 함께 줄어드는지(§1 설계 의도의 실측
  검증)를 관찰한다. `docs/METRICS.md` "예정" 목록의 "Agent 실행 성공률"과
  연결되는 선행 지표다.
- 신규 production dependency 없음(기존 `anthropic-java` 범위 안에서 구현).
- Codex는 `.ai/metrics/metrics.jsonl`에 직접 기록하지 않는다(Claude가
  기록).

## Test Plan

- Unit: `AgentAnalysisPromptBuilder`(DATA 격리 문구/태그 포함 검증,
  MATCH-002 `reason` 미포함 검증), `AnthropicAgentAnalysisClient`(blank
  key 즉시 실패, 예외 분류, 민감정보 미로깅), `ExperienceBulletRepository
  .findByCareerExperienceIdIn` 단위 테스트(`@DataJpaTest`).
- Service/Controller 단위(`@SpringBootTest` + `@AutoConfigureMockMvc`,
  `SemanticJobMatchClient`와 `AgentAnalysisClient` 둘 다 fake로
  `@TestConfiguration` 주입 — 완전히 결정론적): 위 Acceptance Criteria
  각 항목을 1:1에 가깝게 커버한다. 최소 커버 항목(정확한 문구는 구현 중
  다듬어도 됨):
  1. JobPosting 404(semantic match/agent LLM 둘 다 미호출, fake 호출
     카운트로 검증)
  2. PKB 4종 전부 0건 → 409(semantic match/agent LLM 둘 다 미호출)
  3. semantic match 성공 → agent 분석 정상 200 응답
  4. AI 관련 공고 → fake가 배열 순서로 준 우선순위가 `priority=1`에
     그대로 반영
  5. 후보 풀 밖 경험이 응답에 나타나지 않음(unknown id 실패 경로와 연결)
  6. 카테고리 상한(경험 5/자격·학력·수상 각 3) 초과 시 앞쪽 N개만(순서
     유지, 재정렬 없음) 유지
  7. unknown id(경험/자격/학력/수상 각 최소 1건) → 502, all-or-nothing
  8. 중복 id → 첫 등장만 유지, `priority` 1..N 재부여(빈틈 없음)
  9. `priority`가 서버 계산값이고 LLM 출력에는 존재하지 않음(구조 회귀
     테스트)
  10. `semanticMatchScore`가 MATCH-002 원본 score와 정확히 일치
  11. `AgentAnalysisPromptBuilder` 프롬프트 문자열에 MATCH-002 `reason`
      미포함(PromptBuilder 단위 테스트, 특이 문자열 마커로 검증)
  12. `AgentAnalysisResponse.gaps` == `SemanticJobMatchResponse.gaps`
  13. `RawAgentAnalysisResult`/`RawExperienceRecommendation`/
      `RawPkbRecommendation`에 `gaps`/`type`/`score`/`priority` 필드
      부재 확인(구조 회귀 방지)
  14. reason/emphasisPoints 길이·개수 상한 truncate(전체 실패 아님)
      검증, keyThemes/knownRequirements/secondaryMessages/avoidOrBeCareful
      개수 상한 truncate 검증
  15. agent LLM provider timeout/malformed response → 502
  16. semantic matching provider 실패 시 agent LLM 미호출(fake 호출
      카운트로 검증) → 502
  17. `ImportCandidateStatus.PENDING`/`REJECTED` career 데이터가 후보
      풀/응답 어디에도 없음(회귀, MATCH-002 패턴 재사용)
  18. `ExperienceBulletRepository.findByCareerExperienceIdIn` 단위 테스트
  19. MATCH-001/MATCH-002 회귀(패키지 전체 테스트 통과 + `git diff --stat`로
      파일 무변경 확인)
  20. `career`/`job`/`application`/`collector`/`pkbimport` 전체 회귀
  21. 전체: `cd backend && ./gradlew test`
- 실제 Anthropic API는 자동 테스트에서 절대 호출하지 않는다.
- `[수동]`: 실제 dev DB + 실제 Anthropic API로 Case A~D(MATCH-002와 동일
  4개 공고 재사용) sanity 확인 — 점수/문장 hardcode 없이 "실제 존재하는
  id만 사용, 관련 경험 포함, 명백한 hallucination 없음, Case A/C의
  전략적 차이가 상식적으로 존재, 공고에 없는 요구사항 생성 없음"만
  확인한다.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | Task 명세(§Scope 1-13) + ADR-0029 전체 위임(신규 `agent`/`agent/dto`/`agent/llm`/`agent/llm/dto` 패키지, `ExperienceBulletRepository` 신규 메서드, `application.yml`/`docs/METRICS.md` 갱신), 14개 핵심 제약(§본문) 명시, 자동 테스트만 작성(fake client, 실제 Anthropic API 미호출) | 신규 production 13개 파일 + 신규 테스트 4개 파일(`AgentAnalysisControllerTest` 9개/`AgentAnalysisContractTest` 2개/`AgentAnalysisServiceTest` 1개/`AnthropicAgentAnalysisClientTest` 2개) 구현. 허용된 기존 파일 3개만 수정(`ExperienceBulletRepository`/`application.yml`/`docs/METRICS.md`, diff 12줄). `match/` 디렉터리 무변경 확인. Codex sandbox의 Gradle daemon lock으로 `./gradlew test` 실행 불가(선례와 동일) — Claude가 로컬 실행. 컴파일 실패 1건 발견(`AgentAnalysisService.java`가 `io.micrometer.core.instrument.*`와 `java.time.*` 와일드카드 임포트를 동시에 써서 `Clock` 타입 모호) → 같은 thread에 2줄 import 수정 요청 → Codex가 해결. 재실행 결과 236개 중 2개 실패(둘 다 테스트 코드 버그로 Claude가 진단: ①`AgentAnalysisContractTest`가 미저장 엔티티(id=null)+`Map.of()` 조합으로 JDK 불변맵의 null-key NPE 유발, ②`AgentAnalysisControllerTest`가 Jayway JsonPath의 배열 전용 `length()`를 String 필드에 적용해 `null` 반환) → 정확한 원인/수정 방법과 함께 같은 thread에 재요청 → Codex가 테스트 파일 2개만 수정. 최종 Claude 로컬 재실행: 233개 전체 통과(`--max-workers=1`로 확인 — 기본 병렬 실행 시 `MultipartUploadLimitIntegrationTest` 1건이 DB 커넥션 풀 경합으로 간헐적 실패하나 격리 실행 시 항상 통과, AGENT-001과 무관한 기존 pkbimport 테스트의 리소스 경합 flake로 진단). reviewer 검토 대기. |
