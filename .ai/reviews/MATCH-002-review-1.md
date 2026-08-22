---
task_id: MATCH-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-22T21:00:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

- [x] 존재하지 않는 jobId → 404, LLM 미호출 — `SemanticJobMatchControllerTest.missingJobAndEmptyPkbDoNotCallProvider` (line 38) + `SemanticJobMatchService.calculate` (job==null이면 LLM 호출 전 throw, `SemanticJobMatchService.java:61-62`).
- [x] PKB 전부 0건 → LLM 미호출, 200 + 전부 0/빈 배열 — `SemanticJobMatchService.java:65-67` early return, 테스트로 확인(같은 메서드).
- [x] fake client가 AI/RAG 경험 id를 반환하면 `experienceMatches`에 포함되고 `title`은 서버가 실제 엔티티에서 채움 — `SemanticJobMatchControllerTest.restoresServerTitleDeduplicatesSortsTruncatesAndExcludesZeroScores` (line 46), `SemanticJobMatchService.convert()`가 `title.apply(byId.get(...))`로 서버 엔티티 값 사용(`SemanticJobMatchService.java:115`). `Raw*Match` DTO 어디에도 `title` 필드 없음(확인함).
- [x] 없는 id 반환 → `UNKNOWN_PKB_ID` → 502, 응답에 노출 안 됨 — `rejectsUnknownIdsAndOutOfRangeScoresWithoutFallback` (line 60-64).
- [x] score가 [0,1] 밖 → 전체 실패(clamp 안 함) — 같은 테스트(line 65-66), `validateScore()`가 `value<0 || value>1`이면 즉시 throw(`SemanticJobMatchService.java:120-121`), clamp 로직 없음 확인.
- [x] 같은 카테고리 중복 id → 실패 아님, 최고 score만 유지 — `restoresServerTitleDeduplicatesSortsTruncatesAndExcludesZeroScores`에서 `saved.get(0)`에 대해 .2/.9 두 값을 주고, 결과 첫 항목이 `saved.get(0)`으로 정렬되어 있음을 통해 간접 검증(코드상 `unique.put`이 더 높은 score만 유지 — `SemanticJobMatchService.java:110-112`).
- [x] 카테고리별 상한 초과 → 실패 아님, score desc/id asc truncate — 같은 테스트, 6개 경험 중 상위 5개만 포함, 정렬 `ORDER`(`SemanticJobMatchService.java:27-28`) 확인.
- [x] Certification/Education/Award 각각 매칭/비매칭(score=0) 케이스, 0점은 배열에서 제외 — `handlesAllCategoriesZeroFilteringProviderFailuresAndMetricIsolation`(line 69)에서 cert/edu/award 모두 score=0으로 주고 3개 배열 모두 비어있음을 확인.
- [x] provider timeout/malformed → 502, `matchMethod` 등 fallback 필드 없음 — 같은 테스트 후반부(NETWORK_TIMEOUT/MALFORMED_RESPONSE) + `rejectsUnknownIdsAndOutOfRangeScoresWithoutFallback`의 `jsonPath("$.matchMethod").doesNotExist()`. 단, 이 assertion은 컨트롤러가 `void` 응답이라 Spring Boot 기본 에러 바디(`/error`, timestamp/status/error/path)가 대신 채워지는 구조라서, 애초에 어떤 구현이든 `matchMethod` 필드가 나올 수 없는 self-fulfilling 검증에 가깝다 — 치명적이지는 않으나 테스트 가치가 낮음(참고용 findings에 기록).
- [x] `PENDING`/`REJECTED` `ImportCandidate` 데이터가 프롬프트/응답에 안 나타남(회귀 테스트) — `pendingAndRejectedImportCandidatesNeverReachSemanticClient`(line 89) 존재. **단, 실제 LLM 호출 경로를 타지 않고 empty-PKB early-return 경로로만 통과함(아래 Findings 참고) — 의미 있는 회귀 테스트로 보기엔 약함.**
- [x] `deterministicScore`와 `semanticScore` 둘 다 존재, `deterministicScore`는 `GET /match`의 `overallScore`와 동일 — DTO에 필드 존재 확인, 서비스가 `deterministicEngine.calculate(job.getJobCategory(), experienceList, tagMap, certificationList, educationList, awardList)`를 `JobMatchService.calculate()`와 동일한 인자로 호출(`SemanticJobMatchService.java:71-72` vs `JobMatchService.java:97-98`)함을 소스 비교로 직접 확인. **다만 실제 테스트는 0.0==0.0인 empty-PKB 케이스만 확인하고, non-zero 케이스에서 두 endpoint를 모두 호출해 값이 같은지 비교하는 테스트는 없음(아래 Findings).**
- [x] `careerops.match.*`(MATCH-001) metric은 증가하지 않고 `careerops.semantic-match.*`만 증가 — `SemanticJobMatchService`가 `JobMatchService`/`CareerMatchEngine` 외에 MATCH-001 metric 객체를 전혀 참조하지 않음(소스 확인), `handlesAllCategoriesZeroFilteringProviderFailuresAndMetricIsolation`에서 `careerops.match.request{result=success}` 카운트 불변 확인. **`careerops.match.duration`/`careerops.match.score`는 테스트로 직접 확인하지 않음(구조적으로는 안전 — 별개 Timer/DistributionSummary 인스턴스이므로 low risk, 아래 Findings 참고).**
- [x] `eligibility` 관련 필드 DTO 어디에도 없음 — `SemanticJobMatchResponse`/`SemanticMatchEvidence` 소스 확인, `handlesAllCategoriesZeroFilteringProviderFailuresAndMetricIsolation`에서 `jsonPath("$.eligibility").doesNotExist()`.
- [x] system prompt DATA 격리 문구 + `<job>`/`<pkb>` 태그 — `SemanticMatchPromptBuilderTest.isolatesJobAndPkbAsDataAndDoesNotIncludeDeterministicScore`.
- [x] blank API key 즉시 실패, 민감정보/API key 미로깅 — `AnthropicSemanticJobMatchClientTest.missingKeyAndSensitiveInputAreNotLogged`. **단, 이 테스트는 `sensitive` 변수를 선언만 하고 실제로 `client.match(...)` 인자로 전달하지 않음 — 원본 `AnthropicDocumentExtractionClientTest` 패턴(민감 텍스트를 실제로 `extract(rawText, ...)`에 전달)보다 약한 미러링(아래 Findings).**
- [x] 기존 전체 테스트 회귀 없음 — `./gradlew test` 217/217 PASS, 직접 재실행으로 재확인(아래 테스트 결과).
- [x] `docs/METRICS.md` 3행 추가 — 확인(`docs/METRICS.md` diff).
- [x] `application.yml` 신규 키(connect 10/request 45), 기존 `api-key`/`model` 재사용 — 확인(`application.yml` diff, `AnthropicSemanticJobMatchClient` 생성자).
- `[수동]` Case A/B/C/D — 이번 라운드 리뷰 대상 아님(지시대로 제외).

## MATCH-001 무변경 / 설계 제약 확인

- `git diff --stat`으로 `match/JobMatchController.java`/`JobMatchService.java`/`CareerMatchEngine.java`/`KeywordNormalizer.java`/기존 `match/dto/*`가 전혀 나타나지 않음을 확인(신규 파일만 추가).
- `SemanticJobMatchService`가 `JobMatchService.match()`를 호출하지 않고 `CareerMatchEngine`을 직접 `@Autowired`함을 소스로 확인(`SemanticJobMatchService.java:32,39,71-72`).
- 프롬프트(system/user 둘 다)에 `deterministicScore`/MATCH-001 점수 문자열 없음을 `SemanticMatchPromptBuilder.java` 전체 읽고 확인 + `SemanticMatchPromptBuilderTest`가 `doesNotContain("deterministicScore")`로 회귀 검증.
- `.ai/metrics/metrics.jsonl`은 Claude가 plan/implement phase 기록만 추가했고 Codex가 직접 건드리지 않음(diff 확인 — 기존 라인 수정 없이 2줄 append만 존재).
- Privacy: `AnthropicSemanticJobMatchClient.java`에는 로그 문이 단 한 줄도 없음(grep/전체 읽기로 확인). `SemanticJobMatchService`의 유일한 로그 2곳은 `log.warn("Semantic match failed reason={}", exception.reason())`(enum만)과 `log.warn("Duplicate semantic match id={} category={}", id, category)`(id/카테고리만) — PKB 원문/JobPosting 원문/raw request-response 없음.

## 테스트 결과

- `./gradlew test --rerun` 직접 재실행(캐시 무시) → `BUILD SUCCESSFUL`, `build/test-results/test/*.xml` 52개 파일 합산 `tests=217 skipped=0 failures=0 errors=0`.
- 신규 3개 테스트 클래스 개별 확인: `SemanticJobMatchControllerTest`(5) + `AnthropicSemanticJobMatchClientTest`(2) + `SemanticMatchPromptBuilderTest`(1) = 8, Claude 최초 보고와 일치.
- test_count=217, test_pass_count=217.

## Findings

1. **(NEEDS_REVISION) 명세에 없는 `REASON_TOO_LONG` 실패 모드 추가, 미테스트.** `SemanticJobMatchService.java:109`에서 `reason.length() > 200`이면 `SemanticMatchException.Reason.REASON_TOO_LONG`으로 전체 응답을 실패 처리한다. Task 명세 §5 "ID 기반 hallucination 방지"에는 `UNKNOWN_PKB_ID`/`SCORE_OUT_OF_RANGE`만 "응답 전체 실패" 대상으로 명시되어 있고, `reason` 필드에 대해서는 "서버가 내용을 검증하지는 않으나 prompt로 강하게 지시한다"고 명시적으로 **서버 검증을 하지 않는다**고 되어 있다(§3 API 응답 필드 설명). ADR-0028에도 이 실패 모드는 기록되어 있지 않다. 실사용(Case A~D 수동 검증) 중 LLM이 200자를 살짝 넘는 정상적인 reason을 생성하면 전체 매칭이 불필요하게 502로 실패할 위험이 있다. `SemanticMatchException.Reason` enum과 `isValidationFailure()`에도 포함되어 있으나(`SemanticMatchException.java:6,25`) 테스트가 전혀 없다.
   - **요청**: (a) `REASON_TOO_LONG` 검증을 제거하고 명세대로 길이도 서버가 강제하지 않도록 하거나, (b) 정말 필요하다고 판단되면 이 결정을 ADR-0028에 새 결정 항목으로 명시하고 최소 1개의 단위 테스트(200자 초과 reason → 502)를 추가할 것. 둘 중 하나로 정리해달라.

2. **(NEEDS_REVISION) `AnthropicSemanticJobMatchClientTest.missingKeyAndSensitiveInputAreNotLogged`가 실제로 민감 데이터를 전달하지 않음.** `AnthropicSemanticJobMatchClientTest.java:28-37`에서 `sensitive="JOB-AND-PKB-SENSITIVE"` 변수를 선언하지만 `client.match(null, List.of(), Map.of(), List.of(), List.of(), List.of())` 호출 시 `job`은 `null`이고 PKB 리스트는 전부 빈 리스트라 `sensitive` 문자열이 메서드 인자로 전혀 전달되지 않는다. 원본 패턴인 `AnthropicDocumentExtractionClientTest.missingKeyAndSensitiveInputAreNotLogged`는 `client.extract(rawText, DocumentType.RESUME)`처럼 민감 텍스트를 실제 인자로 전달한다(같은 blank-key 즉시 실패 경로이긴 하지만, 최소한 인자 전달 자체는 검증). 현재 테스트는 "전달되지도 않은 문자열이 로그에 없다"는 자명한 사실만 검증해 실질적 커버리지가 없다.
   - **요청**: 실제 민감 문자열을 담은 `JobPosting`(예: title/companyName에 `sensitive` 포함)과 `CareerExperience`(예: `detail`/`summary`에 `sensitive` 포함) 등을 만들어 `client.match(job, experiences, ..., ...)`처럼 실제 인자로 전달하도록 수정해달라(blank key 체크가 먼저 발생해 여전히 `PROVIDER_4XX`로 즉시 실패하는 구조는 유지해도 된다 — 인자를 실제로 흘려보내는 것 자체가 목적).

3. **(NEEDS_REVISION) `pendingAndRejectedImportCandidatesNeverReachSemanticClient`가 LLM 호출 경로를 타지 않음.** `SemanticJobMatchControllerTest.java:89-104`에서 PENDING/REJECTED `ImportCandidate`만 만들고 실제 승인된 `CareerExperience`/`Certification`/`Education`/`Award`는 전혀 생성하지 않는다. 이 상태에서는 `fake.calls==0`이 되는 이유가 "PENDING/REJECTED가 필터링돼서"가 아니라 단순히 PKB 4개 테이블이 전부 비어 §6 empty-PKB early-return 분기(`SemanticJobMatchService.java:65-67`)를 타기 때문이다 — `missingJobAndEmptyPkbDoNotCallProvider`(line 37-44)와 사실상 같은 코드 경로를 한 번 더 검증하는 셈이라, "PENDING/REJECTED 데이터가 프롬프트 입력에 안 나타난다"는 것을 실질적으로 증명하지 못한다.
   - **요청**: 같은 테스트(또는 새 테스트)에서 PENDING/REJECTED `ImportCandidate`와 **별도로** 최소 1개의 실제 승인된 `CareerExperience`를 저장해 LLM 경로(`fake` 호출)가 실제로 실행되도록 하고, `fake`가 전달받은 `experiences` 인자(또는 `SemanticMatchPromptBuilder.userPrompt(...)` 결과)에 pending/rejected 항목의 title(`"secret pending experience"`/`"secret rejected experience"`)이 포함되지 않음을 직접 검증해달라.

4. **(선택, 낮은 우선순위) `deterministicScore == GET /match의 overallScore` parity를 non-zero 케이스로 직접 비교하는 테스트가 없음.** 현재는 PKB가 비어 있어 둘 다 자명하게 `0.0`인 경우만 확인된다(`missingJobAndEmptyPkbDoNotCallProvider`). 소스 코드 비교로 `SemanticJobMatchService.calculate()`(line 71-72)와 `JobMatchService.calculate()`(line 97-98)가 `CareerMatchEngine.calculate(...)`를 동일한 인자로 호출함은 확인했지만, 이 parity를 지키는 회귀 테스트는 없어 향후 리팩터링 시 두 값이 갈라져도 테스트가 잡아내지 못한다.
   - **요청**: PKB가 비어있지 않은 시나리오에서 `POST .../semantic-match`와 `GET .../match`를 모두 호출해 `deterministicScore == overallScore`임을 직접 비교하는 테스트 1개 추가를 권장(필수는 아님, PASS를 막는 사유는 아니지만 다음 라운드에서 함께 처리하면 좋음).

5. **(선택, 낮은 우선순위) metric 격리 테스트가 `careerops.match.request`만 확인.** AC는 `careerops.match.request`/`careerops.match.duration`/`careerops.match.score` 3개 모두를 "증가하지 않음" 대상으로 명시하는데, `handlesAllCategoriesZeroFilteringProviderFailuresAndMetricIsolation`은 `request` 카운터만 비교한다. 구조적으로(별개 Timer/DistributionSummary 인스턴스라 위험 낮음) 안전하다고 소스로 확인했지만, 테스트로도 닫아두면 더 안전하다.
   - **요청**: 여유가 되면 `careerops.match.duration`/`careerops.match.score`의 count도 호출 전후 비교하는 assertion 추가(선택 사항).

6. **(참고, action 불요) `matchMethod` 부재 검증이 self-fulfilling에 가까움.** `SemanticJobMatchController`의 `@ExceptionHandler`가 `void`를 반환해 실제로는 Spring Boot 기본 `/error` 바디(timestamp/status/error/path)가 채워지는 구조라, 어떤 구현이든 `matchMethod` 필드는 애초에 나올 수 없다. 치명적이진 않으나 이 assertion의 실질 가치는 낮다는 점만 기록해둔다(수정 요청 아님).

## 다음 액션

- NEEDS_REVISION — 코드/설계는 명세를 대부분 충실히 따랐고 217/217 회귀 없음(직접 재확인)을 확인했으나, 위 Findings 1~3(필수)을 같은 Codex thread(`01a02943-cba4-7270-a335-6b85813a2814`)에 수정 요청으로 보낼 것. Findings 4~5는 선택 사항으로 함께 요청해도 되고 다음 라운드로 미뤄도 무방. Finding 6은 정보 제공용, 조치 불필요.
- 이번이 review round 1이므로 반복 FAIL 패턴은 아직 없음 — Task 명세 자체의 모호성 문제는 없어 보이고, Codex가 스스로 추가한 미승인 검증 로직(REASON_TOO_LONG)과 테스트 커버리지 디테일 정도의 이슈다.
