---
task_id: MATCH-002
review_round: 2
reviewer: claude
reviewed_at: 2026-08-22T21:10:00+09:00
verdict: PASS
---

## 이번 라운드 범위

1차 리뷰(`MATCH-002-review-1.md`)에서 NEEDS_REVISION 사유였던 3건(필수) + 2건(선택),
그리고 그 수정 과정에서 Claude가 직접 발견해 같은 thread에 추가로 요청한 import
버그(6개 테스트 전멸) 수정이 실제로 반영됐는지만 집중 검토했다. 1차에서 이미 PASS한
핵심 설계 제약(MATCH-001 무변경, `CareerMatchEngine` 직접 호출, prompt에
`deterministicScore` 미포함, ID 기반 hallucination 검증, title 서버 재조회, 명시적
502, timeout/metric/privacy 등)은 `SemanticJobMatchService.java` 전체 재독과
`git diff --stat`으로 재확인했다.

## 1차 Findings 처리 확인

1. **(필수) `REASON_TOO_LONG` 제거** — 충족. `grep -rn "REASON_TOO_LONG" backend/src`
   결과 0건. `SemanticMatchException.java`의 `Reason` enum이
   `NETWORK_TIMEOUT/PROVIDER_4XX/PROVIDER_RETRY_EXHAUSTED/MALFORMED_RESPONSE/
   UNKNOWN_PKB_ID/SCORE_OUT_OF_RANGE` 6개만 남았고, `isValidationFailure()`도
   `UNKNOWN_PKB_ID`/`SCORE_OUT_OF_RANGE`만 검사한다. `SemanticJobMatchService.java`에
   reason 길이 검증 코드 없음(전체 재독 완료).
2. **(필수) privacy 테스트가 실제 민감 데이터를 전달하지 않던 문제** — 충족.
   `AnthropicSemanticJobMatchClientTest.missingKeyAndSensitiveInputAreNotLogged`
   (`backend/src/test/java/com/careerops/backend/match/semantic/AnthropicSemanticJobMatchClientTest.java:31-49`)가
   `sensitiveJobTitle`/`sensitivePkbText`를 실제 `JobPosting`/`CareerExperience`
   생성자에 넣고 `client.match(job, List.of(experience), ...)`로 실제 인자 전달 후
   로그에 해당 문자열이 없음을 검증한다.
3. **(필수) PENDING/REJECTED 테스트가 LLM 경로를 안 타던 문제** — 충족.
   `pendingAndRejectedImportCandidatesNeverReachSemanticClient`
   (`SemanticJobMatchControllerTest.java:100-121`)가 PENDING/REJECTED
   `ImportCandidate`와 별도로 승인된 `CareerExperience("approved manual experience")`를
   저장해 `fake.calls==1`(LLM 경로 실제 실행)을 확인하고,
   `fake.receivedExperiences`가 `approvedManual`만 포함하며
   `"secret pending experience"`/`"secret rejected experience"`를
   `doesNotContain`으로 직접 검증한다.
4. **(선택) `deterministicScore == overallScore` non-zero parity 테스트** — 충족.
   `deterministicScoreMatchesDeterministicEndpointForNonZeroResult`
   (`SemanticJobMatchControllerTest.java:123-134`)가 `POST .../semantic-match`와
   `GET .../match`를 모두 호출해 `deterministicScore`가 `0.0`보다 크면서
   `overallScore`와 동일함을 직접 비교한다.
5. **(선택) MATCH-001 3개 metric 전부 불변 확인** — 충족.
   `handlesAllCategoriesZeroFilteringProviderFailuresAndMetricIsolation`
   (`SemanticJobMatchControllerTest.java:83-93`)가 `careerops.match.request`
   count, `careerops.match.duration` count, `careerops.match.score` count
   3개 모두 호출 전후 불변임을 확인한다.

## 추가로 발견됐던 import 버그 수정 확인

`SemanticJobMatchControllerTest.java:9-10`가 `tools.jackson.databind.JsonNode`/
`tools.jackson.databind.ObjectMapper`를 import한다(Spring Boot 4/Jackson 3에
맞는 패키지 — `CollectControllerTest`/`ManualImportControllerTest`와 동일 패턴).
`grep -rln "com.fasterxml.jackson" backend/src/main backend/src/test`로 저장소
전체를 확인한 결과 남아있는 3개 파일(`collector/alio/Alio*.java`,
`pkbimport/extraction/llm/StructuredExtractionSchemaTest.java`)은 모두 이번
Task와 무관한 기존 파일이라 문제 없음.

## MATCH-001 무변경 / 핵심 설계 제약 재확인

- `git diff --stat`으로 `JobMatchController.java`/`JobMatchService.java`/
  `CareerMatchEngine.java`/`KeywordNormalizer.java`/`dto/JobMatchResponse.java`/
  `dto/MatchEvidence.java` 전부 빈 diff(변경 없음) 확인.
- `SemanticJobMatchService.java` 전체 재독: `CareerMatchEngine`을
  `@Autowired`로 직접 호출(생성자 주입, `deterministicEngine.calculate(...)`,
  line 71-72)하고 `JobMatchService.match()`는 어디서도 호출하지 않음.
- ID 기반 검증(`convert()` 메서드, line 100-117): 프롬프트에 없던 id →
  `UNKNOWN_PKB_ID`로 즉시 throw(all-or-nothing, partial success 없음),
  score 범위 밖 → `SCORE_OUT_OF_RANGE`로 즉시 throw(clamp 없음), 중복 id →
  높은 score만 유지 + WARN 로그(id/카테고리만), 상한 초과 → `ORDER`
  (`score` desc, `id` asc) 정렬 후 `limit(limit)`으로 truncate(실패 아님) —
  명세 §5 4개 규칙 그대로 구현됨.
- title은 `title.apply(byId.get(rawId.apply(raw)))`로 서버가 조회한 엔티티
  값을 사용(line 114), `Raw*Match` DTO에 `title` 필드 없음.
- 실패 시 컨트롤러는 `502`(`@ExceptionHandler` `HttpStatus.BAD_GATEWAY`,
  `SemanticJobMatchController.java:18-20`)만 반환, fallback/`matchMethod`
  필드 없음.
- `application.yml`/`docs/METRICS.md` diff가 명세(§10/§11) 그대로 3줄/3행만
  추가(위 diff 인용).
- PKB 전체 0건 → LLM 미호출 early-return(`SemanticJobMatchService.java:65-67`),
  `SemanticMatchPromptBuilder`에 `deterministicScore` 문자열 없음(1차에서 확인,
  이번 라운드에서 파일 내용 재확인 — 변경 없음).

## 테스트 결과

- `./gradlew test --rerun` 직접 재실행(캐시 무시) → `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml` 전체 합산: `tests=218 failures=0 errors=0 skipped=0`.
- 신규 3개 테스트 클래스 개별 확인: `SemanticJobMatchControllerTest` 6개
  (`<testcase` 카운트로 직접 확인, 1차 5개 + 신규 parity 테스트 1개),
  `AnthropicSemanticJobMatchClientTest` 2개, `SemanticMatchPromptBuilderTest` 1개
  = 9개(1차 8개 대비 순증 1개, thread 기록과 일치).
- test_count=218, test_pass_count=218.

## Findings

없음 — 1차 지적 5건(필수 3 + 선택 2) 전부 명세대로 반영됐고, 추가로 발생했던
import 버그도 해결되어 회귀 없이 218/218 통과함을 직접 재현했다.

## 다음 액션

- PASS. `.ai/metrics/metrics.jsonl`에 review round 2 최종 상태(PASS, test_count=218,
  test_pass_count=218) 기록 필요(Codex가 아니라 Claude가 기록 — Task 명세 Technical
  Notes 원칙).
- `[수동]` Case A/B/C/D(실제 dev DB + 실제 Anthropic API sanity 확인)는 이번
  라운드 대상이 아니었으므로 별도로 진행해야 Task가 최종 완료 처리된다 —
  Task 상태(`status: in_progress`)를 이 4건 완료 전까지 `done`으로 바꾸지 말 것.
- 반복 라운드 패턴: 이번이 review round 2이고 PASS로 종료됐으므로 Task 명세
  자체의 모호성 문제는 없었다고 판단한다(1차 지적 사항이 모두 구체적이고
  단순한 구현 디테일이었음).
