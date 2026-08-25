---
task_id: RECOMMEND-001.1
title: RECOMMEND-001 안정화 — transaction boundary 분리, immutable candidate snapshot, provider output 상한 지시, 좁은 repair retry
phase: done
planned_by: claude
implemented_by: codex
status: passed_with_known_limitation
created_at: 2026-08-25T00:00:00+09:00
codex_thread_id: 01a03848-ad0f-7ef0-92c2-c5e060e051f4
---

**최종 상태(2026-08-25)**: 코드 구현·자동 테스트·Claude 독립 검증·reviewer
PASS 전부 완료. "실제 dev DB + Anthropic API 10회 반복 E2E(성공률
≥90%)" acceptance만 Anthropic 계정 크레딧 소진으로 1/10 완료된 상태에서
사용자 지시로 중단, **향후에도 재시도하지 않음** — 사용자가 이 Task를
계기로 "CareerOps Agent 전체 Phase에서 실제(유료) Anthropic API 호출을
전면 금지, 검증은 Fake/mock/fixture/기존 DB 데이터/자동 테스트만
사용"하는 프로젝트 정책을 확정했다. 상세는 아래 "최종 완료 보고" 섹션과
`docs/DECISIONS.md` ADR-0033 참고.

## Context

RECOMMEND-001(ADR-0031)은 OPEN JobPosting 전체(조사 시점 실측 420~461건,
계속 증가 중)를 candidate로 모아 Claude 구조화 출력 1회로 batch
ranking한다. NOTIFY-001(ADR-0032) 실제 E2E(`.ai/tasks/NOTIFY-001.md`
"실제 E2E 결과")에서 candidate 452~461건 규모의
`POST /api/jobs/recommendations` 호출 4회 중 3회가 각각
`MALFORMED_RESPONSE`/`UNKNOWN_JOB_ID`/`NETWORK_TIMEOUT`으로 실패했다
(각 50~81초, 재시도 시 성공). 이번 Task는 이 실패율을 낮추는 안정화
작업이며, 상세 원인 분석과 설계 결정 근거는 ADR-0033 참고.

**중요**: 이번 조사는 `.ai/reviews/RECOMMEND-001-review-2.md`가 이미
분석한 "too many clients already" 자동 테스트 flake(여러
`@SpringBootTest`가 각자 Hikari pool을 열어 Postgres `max_connections`를
초과하는 pre-existing 인프라 이슈, AGENT-001/AGENT-002에서도 동일 패턴)
와는 **별개 문제**다. 자동 테스트는 전부 즉시 반환하는 Fake client를
쓰므로 실제 47~81초 대기가 테스트 중 발생하지 않는다 — 이번 Task가 그
flake를 해결하지 않는다. 다만 실제 운영에서 47~81초간 DB 커넥션을
점유하는 것은 그 flake의 실질적인 압박 요인 중 하나이므로, 이번 Task로
그 점유 자체를 없애는 것은 flake 완화에 부수적으로 도움이 될 수 있다
(이 문장 이상으로 "flake를 고쳤다"고 주장하지 않는다).

## Scope

### 1. Transaction boundary 분리

- 신규 `RecommendationCandidateReader` 컴포넌트(`recommend` 패키지)를
  만든다. `JobRecommendationService.calculate()`가 현재 직접 하는
  7-repository 조회(승인 PKB 필터링 포함, `approved()` 로직 그대로
  이관) + DTO materialize를 이 클래스의 단일 메서드(예:
  `RecommendationInput read()`)로 옮기고, 이 메서드에만
  `@Transactional(readOnly=true)`를 붙인다.
- `JobRecommendationService.recommend(int limit)`/`calculate(int limit)`
  에서 `@Transactional` 어노테이션을 완전히 제거한다. 흐름을
  `reader.read()`(짧은 트랜잭션 종료) → PKB empty/candidate empty 판정
  (트랜잭션 불필요, 이미 반환된 immutable 값으로 판단) →
  `client.recommend(...)`(Anthropic 호출, DB 커넥션 미점유) → 응답 검증/
  변환(`convert()`, ID 재검증을 위한 `jobs.findAllById(...)` 재조회는
  유지하되 이 자체가 짧은 개별 read-only 트랜잭션) 순서로 재구성한다.
- `RecommendationCandidateReader.read()`는 예외를 던지지 않는다(DB
  자체 오류 제외) — PKB empty(409)/candidate empty(200+빈 배열) 판정은
  `JobRecommendationService`가 반환된 `RecommendationInput`의 리스트
  크기로 직접 수행한다(비즈니스 판단과 데이터 접근을 분리).

### 2. Immutable candidate snapshot DTO

- 신규 record 5종(`recommend.dto` 패키지, ADR-0031 결정 3의 compact
  필드 그대로 재사용):
  ```java
  public record RecommendationInput(
      List<RecommendationJobCandidate> candidates,
      List<RecommendationExperience> experiences,
      List<RecommendationCertification> certifications,
      List<RecommendationEducation> educations,
      List<RecommendationAward> awards) {}

  public record RecommendationJobCandidate(
      Long id, String companyName, String title, String jobCategory,
      String careerLevel, String educationRequirement, LocalDate applicationEndAt) {}

  public record RecommendationExperience(
      Long id, String title, String organization, String role,
      String summary, List<String> tags) {}

  public record RecommendationCertification(Long id, String name, String issuer) {}

  public record RecommendationEducation(
      Long id, String institution, String major, String degree, String status) {}

  public record RecommendationAward(Long id, String title, String issuer) {}
  ```
  `RecommendationExperience.tags`는 기존 `Map<Long,List<ExperienceTag>>`
  전달 방식을 대체한다 — reader가 `ExperienceTagRepository`로 미리
  `List<String>`(keyword)까지 평탄화해 넣는다.
- `JobRecommendationClient` 인터페이스 시그니처를 변경한다:
  ```java
  RawRecommendationResult recommend(RecommendationInput input, int providerTopK);
  ```
  (Entity/Map 파라미터 전부 제거). `AnthropicJobRecommendationClient`와
  테스트의 Fake 구현체 모두 이 시그니처로 갱신한다.
- `JobRecommendationPromptBuilder`도 Entity 대신 `RecommendationInput`을
  받도록 시그니처를 바꾼다(`userPrompt(RecommendationInput input, int
  providerTopK)`).
- `JobRecommendationService.calculate()`는 최종 API `limit`(1~20)을
  로컬 변수로 유지하며 `convert()`의 최종 `.limit(limit)` truncate에
  그대로 사용한다 — `providerTopK`는 prompt 전달용으로만 쓰고 최종
  응답 개수와 별개다.

### 3. Provider output 상한은 prompt 지시로만 (schema 제약 아님)

- Anthropic structured output이 `maxItems`/`minItems` 등 JSON Schema
  배열·수치 제약 키워드를 지원하지 않고 요청 자체를 400으로 거부한다는
  사실이 확인됐다(ADR-0033 결정 3 근거) — schema에 배열 크기 제약을
  추가하지 않는다.
- `providerTopK = Math.max(limit * 2, 20)`을 계산해(`limit`은 API
  `limit`, 1~20) prompt에 명시적 상한 문장으로 전달한다: 기존
  "요청한 개수 이하만 반환"이라는 느슨한 문장을 "recommendations
  배열은 최대 `{providerTopK}`개까지만 포함하라. 그 이상의 후보는
  평가만 하고 출력하지 않는다"류의 더 명확한 상한 지시로 교체한다
  (정확한 문구는 Codex가 기존 system prompt 톤에 맞춰 작성, injection
  방어 문구/DATA 태그 격리 원칙은 그대로 유지).
- provider가 이 지시를 어기고 `providerTopK`보다 많이 반환해도 서버가
  이를 이유로 즉시 실패시키는 새 검증 코드를 추가하지 않는다 — 기존
  dedup(최고 score 유지) + 정렬 + `limit` truncate 로직이 초과분을
  그대로 흡수한다(ADR-0033 결정 3).

### 4. Repair retry (최대 1회, 좁은 대상)

- `JobRecommendationException`에 `isRepairable()` 메서드를 신설한다:
  `UNKNOWN_JOB_ID`, `UNKNOWN_PKB_ID`, `SCORE_OUT_OF_RANGE`,
  `MALFORMED_RESPONSE` 4개 reason만 true. 기존 `isValidationFailure()`
  (`UNKNOWN_JOB_ID`/`UNKNOWN_PKB_ID`/`SCORE_OUT_OF_RANGE`만, metric
  태깅 전용, `MALFORMED_RESPONSE`는 여전히 `provider_error`로 집계)는
  **그대로 유지**하고 삭제/변경하지 않는다 — 두 메서드는 목적이 다르다
  (재시도 대상 여부 vs metric 버킷)는 사실을 각각 javadoc으로 명시한다.
- `JobRecommendationService.calculate()`에서 `client.recommend(...)` +
  `convert(...)` 호출을 감싸는 재시도 로직을 추가한다: 첫 시도가
  `isRepairable()==true`인 `JobRecommendationException`을 던지면, 동일
  `RecommendationInput`(reader를 다시 호출하지 않음, 동일 객체 재사용)
  으로 **정확히 1회** 재호출한다. 두 번째 시도도 실패하면(같은 이유든
  다른 이유든) 그 예외를 그대로 전파한다(3번째 시도 없음).
  `NETWORK_TIMEOUT`/`PROVIDER_4XX`/`PROVIDER_RETRY_EXHAUSTED`는
  `isRepairable()==false`이므로 재시도 없이 즉시 전파한다.
- retry 여부와 무관하게 기존 4개 metric(`careerops.recommendation.
  request/duration/candidates/returned`)은 **최종 결과** 기준으로만
  1회 계측한다(현재 동작 유지, retry로 인해 candidates/duration이
  중복 계측되지 않음 — `candidateMetric.record(...)`는 reader 호출
  직후 1회만).

### 5. 신규 metric 2종

- `careerops.recommendation.provider.retry`(Counter, tag `outcome`=
  `repaired`|`still_failed`): repair retry가 실제로 발생했을 때만
  1회 증가(정상 1회 성공 시에는 증가하지 않음). 재시도 후 최종
  성공이면 `repaired`, 재시도 후에도 실패면 `still_failed`.
- `careerops.recommendation.provider.validation_failure`(Counter, tag
  `reason`=`UNKNOWN_JOB_ID`|`UNKNOWN_PKB_ID`|`SCORE_OUT_OF_RANGE`|
  `MALFORMED_RESPONSE`): `isRepairable()==true`인 실패가 발생할 때마다
  (1차 시도든 2차 시도든 각각 별도로) 1회 증가.
- token usage metric(`StructuredMessage.usage()`)은 PKB-008.1이
  `javap`으로 이미 `usage()` 존재를 확인한 선례가 있다 — 구현 시점에
  실제 jar에서 접근 가능성을 재확인해 가능하면
  `careerops.recommendation.provider.tokens`(DistributionSummary, tag
  `type`=`input`|`output`, job/PKB 내용 없이 숫자만)를 추가한다. 접근이
  어려우면 이번 Task에서 강제하지 않는다(Acceptance Criteria에서
  blocking 항목 아님, 시도 결과를 Codex Thread 기록에 남긴다).
- `docs/METRICS.md`의 "다건 채용공고 추천 (RECOMMEND-001)" 표에 신규
  지표 2~3개를 추가한다(구현 완료 후 Claude가 실측과 함께 갱신 —
  이번 architect 단계에서는 문서를 수정하지 않는다, 명세만 남긴다).

### 6. 진단 로그 (timeout 판단을 위한 관측 장치)

- `AnthropicJobRecommendationClient.classify()`가 반환하는
  `JobRecommendationException`이 `NETWORK_TIMEOUT`/`MALFORMED_RESPONSE`/
  `PROVIDER_RETRY_EXHAUSTED`일 때, 원인이 된 실제 예외의
  `getClass().getSimpleName()`을 `JobRecommendationService`의 실패 로그
  라인에 추가한다(예: `log.warn("... reason={} causeType={}
  durationMs=...", e.reason(), e.getCause()!=null?e.getCause().getClass()
  .getSimpleName():"none", ...)`). 예외 메시지 원문/스택트레이스/
  provider request-response 본문은 여전히 로그에 남기지 않는다(클래스
  simple name은 PKB/공고 원문이 아니므로 기존 privacy 원칙과 무관).
- 이 로그는 timeout 값을 지금 바꾸기 위한 것이 아니라, 다음번
  `NETWORK_TIMEOUT` 재현 시 "진짜 90초 초과인지, 다른 `IOException`이
  잘못 분류된 것인지"를 실제 증거로 판별하기 위한 것이다(ADR-0033
  결정 6). **이번 Task에서 timeout 값(90초)을 바꾸지 않는다.**

## Out of Scope

- KAKAO API/실제 메시지 전송/scheduler 자동 호출 연결.
- Notification persistence 변경(NOTIFY-001 production 코드 무변경 —
  `JobRecommendationService.recommend(int)` 시그니처가 그대로이므로
  코드 변경 불필요).
- `recommendationScore` 의미/척도 변경.
- MATCH-001/MATCH-002/AGENT-001/AGENT-002 로직 변경.
- PKB schema 변경, 사용자 preference, embedding/pgvector/vector DB.
- `JobPosting` schema 대규모 변경, frontend.
- candidate에 대한 hard cap(고정 상한 값) 도입 — ADR-0033 결정 4,
  현재 규모(461건)에서 근거 없음. `careerops.recommendation.candidates`
  지표를 계속 관찰하다가 수천 건 규모가 되면 별도 Task로 재검토한다.
- keyword hard filter, MATCH-001 score threshold hard filter — candidate
  선별에 어떤 relevance 필터도 추가하지 않는다(ADR-0026/ADR-0031 recall
  보호 원칙 재확인).
- candidate 500+ 규모를 위한 chunk/merge/rerank 설계 구현(비교/보류만,
  ADR-0033 대안 항목 참고 — 이번엔 도입하지 않는다).
- schema에 `maxItems` 등 JSON Schema 배열 제약 추가 시도(Anthropic API가
  거부함을 이미 확인, §Scope 3).
- `NETWORK_TIMEOUT`/`PROVIDER_4XX`/`PROVIDER_RETRY_EXHAUSTED`에 대한
  application-level retry 추가.
- timeout 값(`connect-timeout-seconds`/`request-timeout-seconds`) 변경.
- `docs/METRICS.md` 실제 수정(구현 완료 후 별도로 갱신).

## Acceptance Criteria

**Transaction boundary**

- [x] `[자동]` `RecommendationCandidateReader`의 read 메서드에
      `@Transactional(readOnly=true)`가 있고, `JobRecommendationService`
      클래스/메서드 어디에도 `@Transactional`이 없다(코드 검사 또는
      reflection 기반 자동 테스트로 확인 가능한 형태).
- [x] `[자동]` DB 레벨 통합 테스트: Fake client의 `recommend()`가
      인위적 지연(예: `Thread.sleep`)을 갖도록 만들고, 그 지연이
      진행되는 동안 별도 스레드/커넥션으로 무관한 repository 쿼리가
      정상 수행됨을 확인한다(Hikari pool을 의도적으로 작게 설정하거나
      동시 쿼리 수를 pool 크기 이상으로 걸어, 기존 구조였다면 실패했을
      상황이 새 구조에서는 통과함을 보여준다 — `AlioCollectorConcurrencyTest`
      패턴 참고).
- [x] `[자동]` `client.recommend(...)`가 `reader.read()` 반환 이후에
      호출됨을 Fake reader/client의 호출 순서 기록으로 검증한다.

**Immutable snapshot / 인터페이스 변경**

- [x] `[자동]` `JobRecommendationClient.recommend(RecommendationInput,
      int)` 시그니처로 변경되어 있고, `AnthropicJobRecommendationClient`/
      테스트 Fake 구현체 모두 Entity/Map 파라미터를 받지 않는다.
- [x] `[자동]` repair retry(아래) 발생 시 `reader.read()`가 정확히
      1회만 호출된다(재시도 시 DB 재조회 없음 — mock 호출 횟수로 검증).

**Provider output 상한 지시**

- [x] `[자동]` `JobRecommendationPromptBuilderTest`: `limit=5`일 때
      prompt에 `providerTopK=20`(=`max(10,20)`)이, `limit=20`일 때
      `providerTopK=40`(=`max(40,20)`)이 포함된다.
- [x] `[자동]` prompt 문자열에 배열 상한을 명시하는 문장이 실제로
      포함됨을 assert한다(정확한 문구가 아니라 상한 숫자가 프롬프트에
      나타나는지로 검증).
- [x] `[자동]` provider가 `providerTopK`보다 많은 항목을 반환해도(정상
      ID/score를 가진 fixture로) 서버가 실패시키지 않고 dedup+정렬+
      `limit` truncate가 정상 동작해 최종 `limit`개만 반환된다.

**Repair retry**

- [x] `[자동]` 1차 시도가 `UNKNOWN_JOB_ID`로 실패하고 2차 시도가
      성공하면, 최종 200 응답을 반환하고 `client.recommend()`가 정확히
      2회 호출된다.
- [x] `[자동]` 1차 `UNKNOWN_PKB_ID`, 1차 `SCORE_OUT_OF_RANGE`, 1차
      `MALFORMED_RESPONSE` 각각에 대해서도 2차 성공 시 동일하게 복구됨을
      확인한다(reason별 개별 테스트).
- [x] `[자동]` 1차·2차 모두 `MALFORMED_RESPONSE`로 실패하면 최종 502를
      반환하고, `client.recommend()`가 정확히 2회 호출되며 3차 호출은
      발생하지 않는다.
- [x] `[자동]` `NETWORK_TIMEOUT`은 1회 실패 시 즉시 502를 반환하고
      `client.recommend()`가 정확히 1회만 호출된다(재시도 없음).
- [x] `[자동]` `PROVIDER_4XX`, `PROVIDER_RETRY_EXHAUSTED` 각각에 대해서도
      동일하게 재시도 없이 1회 호출로 즉시 실패함을 확인한다.

**Metrics**

- [x] `[자동]` repair retry가 발생하지 않는 정상 흐름에서
      `careerops.recommendation.provider.retry`가 증가하지 않는다.
- [x] `[자동]` repair retry로 복구된 경우 `careerops.recommendation.
      provider.retry{outcome=repaired}`가 1 증가하고, 2차도 실패한
      경우 `{outcome=still_failed}`가 1 증가한다.
- [x] `[자동]` `careerops.recommendation.provider.validation_failure`가
      reason 태그별로 attempt 횟수만큼(1차 실패+2차 실패 시 같은 reason
      이더라도 누적 2) 증가한다.
- [x] `[자동]` 기존 4개 metric(`request`/`duration`/`candidates`/
      `returned`)이 repair retry 발생 여부와 무관하게 요청당 정확히
      1회씩만 계측된다(중복 계측 없음).

**진단 로그**

- [x] `[자동]` `NETWORK_TIMEOUT`/`MALFORMED_RESPONSE`/
      `PROVIDER_RETRY_EXHAUSTED` 실패 로그에 원인 예외의 simple class
      name이 포함된다.
- [x] `[자동]` 위 로그를 포함해 CareerExperience.detail/summary, PKB
      원문, provider request/response, API key, JobPosting title 원문이
      로그에 남지 않는다(기존 privacy 테스트 패턴 회귀 확인 + 신규 로그
      필드 포함해 재확인).

**회귀**

- [x] `[자동]` RECOMMEND-001의 기존 24개 테스트 케이스(§RECOMMEND-001
      Test Plan)가 인터페이스 변경 이후에도 동일한 동작을 계속
      보장한다(테스트 코드 자체는 새 시그니처에 맞게 갱신되어야 하지만,
      검증하는 동작·기대 결과는 회귀 없이 유지).
- [x] `[자동]` `MATCH-001`/`MATCH-002`/`AGENT-001`/`AGENT-002`/
      `NOTIFY-001`/`job`/`career`/`application`/`collector`/`pkbimport`
      패키지 전체 테스트가 회귀 없이 통과한다(NOTIFY-001은
      `JobRecommendationService.recommend(int)`만 호출하므로 production
      코드 변경이 없어야 하고, 그 사실 자체를 테스트 또는 git diff로
      확인한다).
- [x] `[자동]` `cd backend && ./gradlew test` 전체 실패 0건.

**실제 E2E (자동 테스트 범위 밖, Claude가 dev DB + 실제 Anthropic API로
직접 수행)**

- [ ] `[수동]` **더 이상 수행하지 않음(정책 결정, 2026-08-25).**
      `POST /api/jobs/recommendations?limit=20` 10회 연속 호출 성공률
      ≥90% 실측을 시도했으나 1/10에서 Anthropic 계정 크레딧이 소진됐다
      (아래 "최종 완료 보고" 참고). 사용자가 추가 충전을 거부하고
      "이후 모든 Phase에서 실제 유료 Anthropic API 호출을 전면 금지"하는
      프로젝트 정책을 확정함에 따라, 이 acceptance 기준 자체를 이 Task의
      완료 조건에서 제외한다. **known limitation으로 영구 기록**(재시도
      계획 없음).
- [ ] `[수동]` **더 이상 수행하지 않음(정책 결정).** Case A/B/C/D 실제
      ranking sanity 재검증 — 위와 동일한 이유로 미수행. RECOMMEND-001
      원 E2E(2026-08-24, `.ai/tasks/RECOMMEND-001.md`)에서 이미 검증된
      기록은 유효한 과거 증거로 유지하되, RECOMMEND-001.1 변경 이후
      재검증은 하지 않는다.
- [ ] `[수동]` **더 이상 수행하지 않음(정책 결정).** late-position
      candidate 배제 여부 실제 확인 — 동일 이유로 미수행.
- [ ] `[수동]` **더 이상 수행하지 않음(정책 결정).** NOTIFY-001 실제
      E2E 재수행 — 동일 이유로 미수행. `JobRecommendationService
      .recommend(int)` 시그니처/`NOTIFY-001` production 코드가 무변경임은
      정적 검증(`git diff --stat`, reviewer 확인)으로 이미 확보했다 —
      실제 API를 통한 동작 재확인만 생략한다.
- [ ] `[수동]` **더 이상 수행하지 않음(정책 결정).** Prometheus 신규
      metric 2종의 실제 트래픽 노출 확인 — 동일 이유로 미수행. metric
      등록/증가 로직 자체는 `SimpleMeterRegistry` 기반 자동 테스트로
      검증됨(reviewer PASS 항목 참고).

## 최종 완료 보고 (2026-08-25)

**상태: 코드 및 자동 검증 완료. 실제 반복 안정성 검증(위 실제 E2E
섹션)은 외부 유료 API 제약으로 보류(known limitation).**

- 구현: Codex(thread `01a03814-...`→세션 만료→`01a03848-...`)가 §Scope
  1~6 전체 구현. Claude 독립 검증에서 실제 버그 1건(테스트 코드
  Mockito NPE)과 회귀 커버리지 공백 4건을 발견해 수정 요청 → 전부 해소.
- 자동 테스트: `recommend` 패키지 35/35 PASS, `notification`/`match`/
  `agent` 패키지 격리 회귀 없음, 전체 스위트 반복 실행 시 관찰되는
  실패는 전부 pre-existing DB 커넥션 풀 flake(RECOMMEND-001-review-2에
  이미 문서화, 이번 Task와 무관, 격리 재실행 시 항상 통과).
- reviewer PASS(`.ai/reviews/RECOMMEND-001.1-review-1.md`): 자동
  Acceptance Criteria 22개 전부 충족 확인.
- 실제 API E2E: 10회 시도 중 1회 성공(정상 패턴, candidates=461,
  duration=82.8초, jobId=7470 score=0.95) 후 나머지 9회 실패. 진단
  로그(causeType, 이번 Task가 추가한 관측 장치)로 즉시 원인 특정 —
  `PROVIDER_4XX`/`BadRequestException`(7건)의 실제 원인은 Anthropic
  계정 크레딧 소진("Your credit balance is too low")이었다(1회성 scratch
  진단 테스트로 확인 후 즉시 삭제, 커밋 안 함, PKB-008.1 선례와 동일
  절차). 앞선 `NETWORK_TIMEOUT`/`AnthropicInvalidDataException` 2건도
  같은 크레딧 소진 전이 구간과 겹쳐 동일 근본 원인으로 추정된다 —
  **RECOMMEND-001.1 코드 결함이 아니다.**
- 사용자가 크레딧 재충전을 거부하고, 이후 모든 Phase에서 실제 유료
  Anthropic API 호출을 전면 금지하는 프로젝트 정책을 확정함에 따라
  (`.env` 기반 실제 호출 전면 중단), 이 Task의 실제 반복 E2E acceptance는
  **더 이상 수행하지 않고 known limitation으로 종결**한다.
- Public API(`POST /api/jobs/recommendations?limit=N`) 계약, NOTIFY-001
  production 코드, `application.yml`, `build.gradle` 전부 무변경
  (`git diff --stat`으로 확인).
- Known limitation: RECOMMEND-001.1이 목표한 "실제 candidate 450+ 규모에서
  반복 호출 성공률 ≥90%"는 **실측으로 최종 확인되지 못했다**. 코드
  변경(transaction boundary 분리, repair retry, providerTopK, 진단 로그)이
  이론적/자동 테스트 수준에서는 설계 의도대로 정확히 동작함이 검증됐고,
  1회 성공한 실제 호출도 정상 패턴을 보였으나, 통계적으로 유의미한 실제
  안정성 개선 여부는 향후 실제 트래픽/모니터링(Prometheus 지표 관찰)을
  통해서만 간접적으로 확인 가능하다.

## Technical Notes

- 관련 파일: `backend/src/main/java/com/careerops/backend/recommend/
  JobRecommendationService.java`, `AnthropicJobRecommendationClient.java`,
  `JobRecommendationClient.java`, `JobRecommendationPromptBuilder.java`,
  `JobRecommendationException.java`, `dto/*.java`. 테스트:
  `JobRecommendationServiceTest.java`, `JobRecommendationControllerTest.java`,
  `AnthropicJobRecommendationClientTest.java`,
  `JobRecommendationPromptBuilderTest.java`.
- `JobPostingRepository.findAllByStatus(String)`, PKB 4종 repository,
  `ExperienceTagRepository.findByCareerExperienceIdIn(List<Long>)` 등
  기존 메서드는 이관만 하고 새로 만들지 않는다(`RecommendationCandidateReader`
  로 이동).
- Anthropic Java SDK 2.54.0 조사 결과(이번 architect 조사, GitHub
  공식 소스/문서 기반 — 구현 시점에 실제 jar로 재확인 권장, PKB-008.1
  선례와 동일):
  - 기본 `maxRetries=2`(총 3회 시도), retry 대상은 연결 오류/408/409/
    429/5xx(400/401/403/404/413은 재시도 안 함).
  - `Timeout`은 "재시도를 제외한" 개별 시도 단위로 적용된다(SDK
    javadoc: "excluding retries") — ADR-0027에서 실측된 "120초 설정 시
    실패까지 약 6분(120초×3회)" 현상과 정확히 일치, 이번 조사로 그
    현상의 근본 메커니즘을 문서로 재확인했다.
  - Anthropic structured output(JSON Schema 기반)은 `maxItems`/
    `minItems`/`minimum`/`maximum`/`minLength` 키워드를 지원하지 않고
    요청을 400으로 거부한다 — schema 기반 배열 상한은 애초에 불가능.
  - 위 근거로 §Scope 6처럼 "지금은 timeout을 바꾸지 않고 진단 로그만
    추가"하는 결정을 내렸다. 구현 중 실제 jar(`javap`)로 이 동작이
    2.54.0에서도 동일한지 한 번 더 확인할 것(사소한 버전 차이 가능성
    배제).
- 로그 필드 추가(§Scope 6)는 `slf4j` 기존 패턴(`log.warn(...)`)에 필드
  하나만 추가하는 수준으로, 새 로깅 프레임워크/구조화 로깅 도입 없음.
- 신규 production dependency 없음.
- `application.yml` 변경 없음(timeout 값 그대로).
- migration 없음.
- **지표 기록**: 이 Task는 `docs/METRICS.md`가 이미 정의한 "Development
  Metrics"(`.ai/metrics/metrics.jsonl`) 스키마에 따라 `codex_invocation_count`,
  `implementation_blocker_count`, `implementation_revision_count`,
  `review_round_count`, `first_review_pass`, `test_count`/
  `test_pass_count`를 phase마다 기록한다. Product Metrics 측면에서는
  §Scope 5의 신규 2개 Counter(`provider.retry`, `provider.validation_failure`)
  가 "repair retry가 실제로 문제를 얼마나 줄이는가"를 관찰하는 핵심
  지표이며, 구현 완료 후 실제 E2E 실측치와 함께 `docs/METRICS.md`의
  RECOMMEND-001 표에 추가되어야 한다(이번 architect 단계에서는 문서
  본문을 수정하지 않고 이 Task 명세에만 남긴다 — 구현 완료 후 Claude가
  갱신).
- 자동 테스트는 이번에도 실제 Anthropic API를 호출하지 않는다(Fake
  `JobRecommendationClient`/`RecommendationCandidateReader` 사용,
  전송 지연 테스트도 `Thread.sleep`으로 시뮬레이션).

## Test Plan

**단위/통합(자동, Fake client, 실제 Anthropic 호출 금지) — 약 15개 신규
+ 기존 24개 유지**

1. `RecommendationCandidateReader` 메서드 `@Transactional(readOnly=true)`
   존재 확인.
2. `JobRecommendationService`에 `@Transactional` 부재 확인.
3. 트랜잭션 해제 통합 테스트(인위적 지연 중 동시 쿼리 성공, §Acceptance
   Criteria 참고).
4. `reader.read()` → `client.recommend()` 호출 순서 검증.
5. `JobRecommendationClient` 신규 시그니처로 Fake 구현체 전환, 기존
   Entity/Map 파라미터 미사용 확인.
6. repair retry 시 `reader.read()` 1회만 호출(재조회 없음).
7. prompt에 `providerTopK` 값이 `limit=5→20`, `limit=20→40` 공식대로
   포함됨(`JobRecommendationPromptBuilderTest`).
8. prompt에 배열 상한 지시 문장 포함 확인.
9. provider가 `providerTopK`초과 반환해도 정상 truncate.
10. `UNKNOWN_JOB_ID` 1차 실패 → 2차 성공 → 200, 호출 2회.
11. `UNKNOWN_PKB_ID` 1차 실패 → 2차 성공 → 200, 호출 2회.
12. `SCORE_OUT_OF_RANGE` 1차 실패 → 2차 성공 → 200, 호출 2회.
13. `MALFORMED_RESPONSE` 1차 실패 → 2차 성공 → 200, 호출 2회.
14. `MALFORMED_RESPONSE` 1차·2차 모두 실패 → 502, 호출 정확히 2회(3회차
    없음).
15. `NETWORK_TIMEOUT` 1회 실패 → 즉시 502, 호출 1회(재시도 없음).
16. `PROVIDER_4XX` 1회 실패 → 즉시 502, 호출 1회.
17. `PROVIDER_RETRY_EXHAUSTED` 1회 실패 → 즉시 502, 호출 1회.
18. 정상 흐름(재시도 없음)에서 `provider.retry` 카운터 미증가.
19. repair retry 성공 시 `provider.retry{outcome=repaired}` +1.
20. repair retry 후 재실패 시 `provider.retry{outcome=still_failed}` +1.
21. `provider.validation_failure{reason=...}`가 attempt별로 누적 증가.
22. 기존 4개 metric이 retry 여부와 무관하게 요청당 1회만 계측.
23. `NETWORK_TIMEOUT`/`MALFORMED_RESPONSE`/`PROVIDER_RETRY_EXHAUSTED`
    로그에 원인 예외 simple class name 포함.
24. 로그 privacy 회귀(`missingKeyAndSensitiveInputAreNotLogged` 패턴,
    신규 로그 필드 포함해 재확인).
25. `isRepairable()`/`isValidationFailure()`가 서로 다른 reason 집합을
    반환함을 직접 assert(`MALFORMED_RESPONSE`가 전자는 true, 후자는
    false).

RECOMMEND-001 기존 24개 케이스(§`.ai/tasks/RECOMMEND-001.md` Test Plan
1~24)는 새 인터페이스/클래스 구조에 맞게 테스트 코드를 갱신하되, 검증
대상 동작(PKB empty 409, candidate empty 200+빈배열, limit 검증, unknown
id 전체 실패, 중복 jobId 최고 score, score 범위, 정렬, reason 200자,
provider 실패 502, MATCH-001 0점 공고 배제 안 됨, PENDING/REJECTED 미노출,
로그 privacy 등)는 회귀 없이 그대로 유지한다.

회귀: `MATCH-001`/`MATCH-002`/`AGENT-001`/`AGENT-002`/`NOTIFY-001`/`job`/
`career`/`application`/`collector`/`pkbimport` 전체 + `./gradlew test`
전체 통과. 특히 `NOTIFY-001`은 production 코드 변경이 전혀 없어야
한다(`JobRecommendationService.recommend(int)` 시그니처 불변).

**실제 E2E(자동 테스트 범위 밖, Claude가 dev DB + 실제 Anthropic API로
수행)**

- `POST /api/jobs/recommendations?limit=20` 최소 10회 연속 호출, 성공률
  90% 이상 확인(개별 호출의 성공/실패/reason/duration/causeType 기록).
- Case A(7552)/B(7501)/C(960)/D(988) sanity check(§Acceptance Criteria).
- OPEN candidate 목록 뒤쪽(late-position)의 실제 관련 공고가 배제되지
  않음을 확인.
- NOTIFY-001 실제 E2E 전체 절차 재수행(prepare/저장 확인/재호출/재시작
  후 persistence).
- Prometheus에서 신규 2개 metric 노출 확인.
- 로그에 causeType 필드가 실제로 나타나는지, PKB/공고 원문이 없는지
  육안 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | 최초 구현 요청(thread `01a03814-a199-7983-9da6-0fd3873118ed`): §Scope 1~6 전체(Reader 분리, RecommendationInput 5종 record, JobRecommendationClient 시그니처 변경, providerTopK prompt 지시, isRepairable() 1회 repair retry, 신규 metric 2종, 진단 로그) + 신규 25개/기존 24개 테스트 케이스 | Codex가 구현 완료 보고(신규 RecommendationCandidateReader/6개 record/신규 테스트 4개/기존 테스트 3개 갱신). NOTIFY-001/application.yml 무변경 git diff로 자체 확인. Codex sandbox가 Gradle 실행(wrapper lock 접근 거부)을 차단해 컴파일/테스트 실행은 못하고 정적 확인만 보고. Claude가 로컬 검증: 컴파일 성공, `recommend` 패키지 격리 실행 31 tests 중 2 failed(`nonRepairableReasonsDoNotRetry`/`eachRepairableReasonRetriesAndRecovers`, `resetFlow()`가 `jobs` mock을 리셋하지 않아 발생하는 실제 Mockito NPE, flake 아님, 재현 확인). 전체 스위트 319 tests 중 8 failed — 6개는 격리 재실행 시 전부 통과하는 pre-existing Postgres 커넥션 풀 경합(`.ai/reviews/RECOMMEND-001-review-2.md` 기록과 동일 패턴)으로 판별, 나머지 2개가 위 NPE. 추가로 RECOMMEND-001 기존 24개 케이스 대비 테스트 커버리지 공백 4건 발견(broad category 후보 미검증, `convert()`의 score 범위/PKB id 검증/null 응답 처리 경로가 시뮬레이션 예외로만 대체되고 실제 경로 미검증). round2로 같은 thread에 수정 요청 시도. |
| 2 | thread `01a03814-...`에 `codex-reply`로 수정 요청 시도 | **세션 만료**(`Session not found for thread_id`) — 이전 세션이 중간에 종료되어 이어갈 수 없었다. 디스크의 기존 변경사항은 그대로 보존한 채, 새 thread(`01a03848-ad0f-7ef0-92c2-c5e060e051f4`)를 열어 "처음부터 재구현이 아니라 기존 diff를 이어서 수정"이라는 점을 명시하고 동일한 수정 요청(NPE 수정 + 커버리지 공백 4건 보강)을 전달. |
| 3 (신규 thread `01a03848-...`) | round1과 동일한 수정 요청(NPE + 커버리지 공백 4건), production 코드는 이미 올바르므로 손대지 말라고 명시 | Codex가 `JobRecommendationServiceTest.java`만 수정해 완료 보고(`resetFlow()`에 `reset(reader,jobs)` 추가, broad category/score 범위/PKB id 4종/null 응답 2종 테스트 추가). 이번에도 Gradle sandbox 차단으로 로컬 실행 못함. Claude가 로컬 검증: 컴파일 성공, `recommend` 패키지 격리 실행 BUILD SUCCESSFUL(전부 통과). 전체 스위트 2회 반복 실행 결과 323 tests 중 각각 12개/6개 실패했으나 실패 클래스 조합이 매번 다르고(`ImportBatchExtractionServiceTest`가 한 번은 포함, 한 번은 미포함) 전부 격리 재실행 시 통과 — pre-existing DB 커넥션 풀 경합으로 재확인(RECOMMEND-001.1 코드 결함 아님). |
| — (기록 보완, Codex 재요청 아님) | reviewer PASS(`.ai/reviews/RECOMMEND-001.1-review-1.md`) Finding #2: token usage metric(`careerops.recommendation.provider.tokens`) 시도 결과가 이 표에 기록되지 않음 | 코드 확인 결과(`grep -rn "tokens\|usage(" backend/src/main/java/.../recommend/`) 해당 metric 미구현, round1/round3 Codex 보고 어디에도 `usage()` 접근 시도 언급 없음 — **시도 자체를 하지 않은 것으로 판단**(Task 명세 §Scope 5상 non-blocking이므로 재작업 요청하지 않음). 향후 필요 시 별도로 재검토. |
