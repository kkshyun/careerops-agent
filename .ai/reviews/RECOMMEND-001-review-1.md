---
task_id: RECOMMEND-001
review_round: 1
reviewer: claude
reviewed_at: 2026-08-24T00:00:00+09:00
verdict: NEEDS_REVISION
---

## Acceptance Criteria 체크

- [x] Anthropic 호출 정확히 1회 — 충족. `JobRecommendationService.calculate()`가
      `client.recommend(...)`를 try 블록 안에서 단 1회만 호출
      (`backend/src/main/java/com/careerops/backend/recommend/JobRecommendationService.java:52`).
      candidate 수와 무관하게 1회 호출됨을
      `JobRecommendationServiceTest.allOpenCandidatesIncludingBroadCategoryAreSentWithoutCap`
      (25건 candidate에도 `client.calls==1`)로 확인.
- [x] status='OPEN' 아닌 JobPosting 미노출 — 충족.
      `JobPostingRepository.findAllByStatus(String status)`
      (`backend/src/main/java/com/careerops/backend/job/JobPostingRepository.java:10`)를
      `"OPEN"` 인자로만 호출하고, Pageable/limit 없이 전체 List 반환 —
      mechanical truncate 없음(cap 없음 요구사항도 함께 충족).
      다만 "CLOSED가 실제로 빠진다"를 직접 검증하는 테스트는 없음(Test Plan
      #7 관련 — 아래 Findings 참고, derived query 신뢰 수준이라 리스크는 낮음).
- [x] broad category(정보통신) candidate 배제 안 됨 — 충족.
      `recommend` 패키지 어디에도 `job.match` / `CareerMatchEngine` /
      `overallScore` 참조 없음(grep 확인). candidate 선정은 오직
      `status='OPEN'` 조건뿐이며, MATCH-001 점수를 candidate 필터에
      전혀 쓰지 않음 — ADR-0031 결정 1/3 준수.
      `allOpenCandidatesIncludingBroadCategoryAreSentWithoutCap` 테스트가
      jobCategory="정보통신" 공고가 client에 전달됨을 확인
      (`JobRecommendationServiceTest.java:29`).
- [x] 응답 companyName/title/applicationEndAt이 DB 재조회 값 — 충족.
      `convert()`가 `this.jobs.findAllById(unique.keySet())`로 별도 재조회한
      `refreshed` 맵에서 값을 채움(`JobRecommendationService.java:72,74`),
      LLM 원본(`RawJobRecommendation`)에는애초에 companyName/title 필드
      자체가 없음(`RawJobRecommendation.java`). 테스트
      `normalRecommendationUsesDatabaseFieldsAndOneCall`로 확인.
- [ ] limit 미지정→5, limit=20 정상, limit=0/21→400 — **코드는 존재하나
      테스트 없음(미충족)**. `JobRecommendationController`에
      `@RequestParam(defaultValue="5") @Min(1) @Max(20) int limit`,
      클래스에 `@Validated` (`JobRecommendationController.java:10,17`)가
      있어 Spring Framework 6.1+ 관례상 범위 밖이면 400으로 매핑될
      것으로 보이나, 이 프로젝트에서 이 패턴(`@RequestParam` +
      `@Validated`)을 쓰는 곳이 이 파일이 유일하고 검증하는 테스트가
      전무함. `JobRecommendationControllerTest` 자체가 없음(아래 Findings).
- [x] candidate 밖 jobId 반환 → 전체 502 — 충족. `convert()`가
      `!byJob.containsKey(raw.jobId())`면 `UNKNOWN_JOB_ID`로 즉시 throw
      (`JobRecommendationService.java:67`), 부분 결과 반환 없음.
      `unknownJobFailsAll` 테스트로 확인.
- [x] 승인 PKB id 밖 반환 → 전체 502 — 충족. `validateIds()`가 4종 모두에
      대해 동일하게 적용(`JobRecommendationService.java:69,78`).
      `everyUnknownPkbCategoryFailsAll` 테스트로 4종 각각 확인.
- [x] 중복 jobId → 최고 score만 유지 — 충족.
      `unique.put`이 `raw.recommendationScore()>prior.recommendationScore()`일
      때만 교체(`JobRecommendationService.java:70`).
      `duplicateKeepsHighestAndSortsDeterministically` 테스트로 확인
      (0.2/0.9 중 0.9만 남음, reason="high").
- [x] score 범위 밖 → clamp 없이 전체 실패 — 충족. `<0 || >1 ||
      !isFinite`이면 즉시 `SCORE_OUT_OF_RANGE` throw
      (`JobRecommendationService.java:68`), clamp 로직 없음.
      `invalidScoresFailWithoutClamp` 테스트가 -0.1/1.1/NaN 3케이스 확인.
- [x] 서버 재정렬(score desc, jobId asc tie-break) — 충족. `ORDER` comparator
      (`JobRecommendationService.java:20`)가 정확히 이 규칙이고 `convert()`
      마지막에 `.sorted(ORDER).limit(limit)` 적용(line 74). 배열 순서를
      신뢰하지 않음. `duplicateKeepsHighestAndSortsDeterministically`가
      tie-break(jobId 1,3 동점 0.8 → jobId asc)까지 함께 검증.
- [x] reason 200자 truncate — 충족. `truncate(raw.reason(),200)` 항상 적용
      (`JobRecommendationService.java:80`). `appliesTopNAndReasonLimit`
      테스트로 201자 입력이 200자로 잘림을 확인.
- [x] PKB 4종 전부 0건 → 409, LLM 미호출 — 충족. `calculate()` 초반에
      candidate 조회/LLM 호출 이전에 체크(`JobRecommendationService.java:46`).
      `emptyPkbIs409AndDoesNotCallClient` 테스트로 확인.
- [x] OPEN candidate 0건 → 200 + 빈 배열, LLM 미호출 — 충족.
      (`JobRecommendationService.java:48`). `noOpenJobsReturnsEmptyWithoutClient`
      테스트로 확인.
- [x] provider timeout/malformed/일반 실패 → 502, MATCH-001로 대체 안 됨 —
      충족. `JobRecommendationException` 발생 시 그대로 rethrow, 그 외
      RuntimeException은 `MALFORMED_RESPONSE`로 wrap해서 rethrow
      (`JobRecommendationService.java:57-58`). Controller의
      `@ExceptionHandler(JobRecommendationException.class)`이 502로 매핑
      (`JobRecommendationController.java:18`). MATCH-001/semanticScore로
      fallback하는 코드 없음(grep 확인).
      `timeoutMalformedAndProviderFailuresBecome502Exception`,
      `nullStructuredOutputIsMalformed` 테스트로 Service 레벨은 확인됨.
      다만 실제 `AnthropicJobRecommendationClient.classify()`가 위 상황을
      올바른 Reason으로 분류하는지 검증하는 테스트가 없음(아래 Findings).
- [x] PENDING/REJECTED ImportCandidate 유래 career 데이터 미포함 — 충족
      (다소 약한 근거). `approved()`가 `SourceType.MANUAL`이거나
      `SourceType.IMPORT`이면서 candidateId가 APPROVED 집합에 있을 때만
      true(`JobRecommendationService.java:76`) — MATCH-002/AGENT-001과
      동일 패턴. `onlyApprovedOrManualPkbIsSent` 테스트가 이를 검증하지만,
      PENDING 케이스를 "ImportCandidate 자체가 mock에 없음"으로 대체
      구현해 REJECTED 상태를 명시적으로 만든 케이스는 없음(approved() 로직상
      결과는 동일하지만 테스트 의도가 다소 간접적).
- [ ] 로그 privacy(detail/summary/PKB 원문/provider req·resp/API key/
      title 원문 미노출) — **코드는 문제 없어 보이나 검증 테스트 없음
      (미충족)**. `JobRecommendationService`의 로그 라인들은 candidates
      count/returned count/durationMs/jobId 목록/score 목록만 남겨
      (`JobRecommendationService.java:48,55,57,58`) 스펙과 일치. 다만
      `AnthropicJobRecommendationClient`에는 로그 코드가 아예 없어 안전한
      것으로 보이지만, 이를 확인하는 전용 테스트(MATCH-002/AGENT-001의
      `missingKeyAndSensitiveInputAreNotLogged` 패턴)가 이 Task에는 전혀
      없음(아래 Findings).
- [ ] Test Plan 24개 케이스 전부 Fake client로 통과 — **미충족**. 아래
      "테스트 결과"에 케이스별 매핑 정리.
- [x] `./gradlew test` 전체 통과 — 충족(Claude 사전 실행 270/270,
      본 리뷰에서 `recommend` 패키지만 재실행해 13/13 재확인).

## 테스트 결과

- 전체: Claude가 사전 실행한 `./gradlew test` 270/270 PASS (기존 257 +
  신규 13)를 신뢰. 본 리뷰에서 `./gradlew test --tests
  "com.careerops.backend.recommend.*"`를 재실행해 실패 없음을 재확인
  (`JobRecommendationPromptBuilderTest` 1/1, `JobRecommendationServiceTest`
  12/12 — `backend/build/test-results/test/TEST-com.careerops.backend.recommend.*.xml`).
- Test Plan 24개 케이스 매핑:
  - 1,2,3,8,9,10,11-14,15,16,17,18,19,20,21,23 → `JobRecommendationServiceTest`로
    충분히 커버됨 (assertion도 구체적: 예외 reason, 순서, 값 등).
  - 4(limit 미지정→5), 5(limit=20 정상), 6(limit=0/21→400) → **테스트 없음**.
    `Controller`에 로직은 있으나 이를 실행하는 테스트가 프로젝트 전체에
    하나도 없음.
  - 7(CLOSED job 미노출) → **직접 검증 테스트 없음**. Repository derived
    query(`findAllByStatus`)에 의존하는 구조상 리스크는 낮지만, 명시적
    검증(예: `verify(jobs).findAllByStatus("OPEN")` 같은 계약 확인조차)이
    없음.
  - 22(PENDING/REJECTED 미노출) → 간접적으로만 커버(위 AC 항목 참고).
  - 24(로그 privacy) → **테스트 없음**. `AnthropicJobRecommendationClientTest`
    자체가 존재하지 않음.
  - 결론: 24개 중 최소 3개(4,5,6)는 완전히 누락, 1개(24)는 완전히 누락,
    2개(7,22)는 근거가 약함. "24개 케이스 모두 통과" AC를 충족한다고 보기
    어려움.

## Findings

1. **[Blocking] Controller 레벨 limit 검증(400) 미검증.**
   `JobRecommendationController.recommend()`는 `@Min(1) @Max(20)` +
   클래스 `@Validated`로 범위를 강제하려 하지만
   (`backend/src/main/java/com/careerops/backend/recommend/JobRecommendationController.java:10,17`),
   이 조합(`@RequestParam` 제약 + `@Validated`)을 쓰는 곳이 이 프로젝트
   전체에서 이 파일이 유일하고, 검증하는 테스트가 전혀 없음. 반면 같은
   패턴의 다른 LLM 연동 기능들(MATCH-002/AGENT-001 등)은 예외 없이
   `MockMvc` 기반 `*ControllerTest`를 갖고 있음
   (`backend/src/test/java/com/careerops/backend/agent/AgentAnalysisControllerTest.java`,
   `backend/src/test/java/com/careerops/backend/match/SemanticJobMatchControllerTest.java`).
   `JobRecommendationControllerTest`가 아예 없어 이 Task만 관례를
   벗어남. limit=0/21이 실제로 400인지, limit 미지정 시 정확히 5가
   서비스로 전달되는지, limit=20이 정상 동작하는지 전혀 실증되지
   않았다 — AC 체크박스("limit 미지정 시 5개, limit=20 정상, limit=0
   또는 21은 400.")를 그대로 위반.

2. **[Blocking] `AnthropicJobRecommendationClient`에 대한 테스트가 전무.**
   `AnthropicJobRecommendationClient.java`가 신규 production 코드로
   추가됐지만 대응하는 `AnthropicJobRecommendationClientTest`가 없음.
   비교: `AnthropicSemanticJobMatchClientTest`,
   `AnthropicAgentAnalysisClientTest`,
   `AnthropicApplicationDraftClientTest`,
   `AnthropicDocumentExtractionClientTest` 모두 동일한 패턴의
   `missingKeyAndSensitiveInputAreNotLogged` 테스트를 갖고 있음
   (`ListAppender`로 root logger를 잡아 sensitive job title/PKB
   text/API key가 로그에 없음을 검증, 예:
   `backend/src/test/java/com/careerops/backend/match/semantic/AnthropicSemanticJobMatchClientTest.java:31-49`).
   RECOMMEND-001은 이 테스트가 없어 Test Plan #24, 그리고 AC의 로그
   privacy 체크박스를 실증하지 못함. 부수적으로 `classify()`의
   timeout/4xx/5xx 분류 로직(`AnthropicJobRecommendationClient.java:32-36`)도
   단위 테스트 없이 코드만 존재함 — MATCH-002 client는 이 분류 로직을
   테스트로 고정해 둠.

3. **[Minor] CLOSED job 배제를 명시적으로 잠그는 테스트 없음.**
   `findAllByStatus("OPEN")`이 하드코딩 문자열로 호출되므로 리스크는
   낮지만, `verify(jobs).findAllByStatus("OPEN")` 같은 최소 계약
   테스트조차 없어 향후 리팩터링(예: 다른 status 문자열 실수 도입)을
   막아주지 못함. Blocking은 아니지만 다른 항목 수정 시 함께 추가 권장.

4. **[Minor] PENDING/REJECTED 필터링 테스트가 간접적.**
   `onlyApprovedOrManualPkbIsSent`(`JobRecommendationServiceTest.java:37`)는
   "ImportCandidate가 아예 조회되지 않는" 케이스로 PENDING을 대체
   시뮬레이션함. `approved()` 로직상 결과는 동일하지만, 실제
   `ImportCandidateStatus.REJECTED`/`PENDING` mock을 만들어 명시적으로
   확인하는 편이 Test Plan #22 문구("PENDING/REJECTED ImportCandidate")와
   더 정확히 일치함. Blocking 아님.

5. **[정보] 코드 스타일이 프로젝트 관례보다 과도하게 압축됨.**
   `JobRecommendationService`/`JobRecommendationController`/
   `AnthropicJobRecommendationClient` 등은 한 줄에 여러 statement,
   중괄호 생략 if 등 극단적으로 압축된 스타일(예:
   `JobRecommendationService.java:41-48` 등)을 씀. 기존
   `SemanticJobMatchService` 등도 다소 압축적이지만 이 정도는 아님.
   기능적 결함은 아니고 AC 항목도 아니므로 blocking하지 않지만,
   유지보수성 관점에서 참고용으로 남김(수정 필수 아님).

6. **[확인 완료, 문제 없음] Out of Scope 침범 없음.** AGENT-001/AGENT-002
   내부 호출, persistence/migration, `matchedThemes` 필드, candidate
   cap/truncate 로직 모두 코드에 없음(grep/코드 리딩으로 확인).

7. **[확인 완료, 문제 없음] 신규 production dependency 없음, `build.gradle`
   변경 없음(`git diff --stat -- backend/build.gradle` 결과 없음).**

8. **[확인 완료, 문제 없음] `.ai/metrics/metrics.jsonl` Codex가 수정하지
   않음** (`git status --short`에 해당 파일 변경 없음).

## 다음 액션

- **판정: NEEDS_REVISION.** 핵심 로직(candidate cap 없음, 단일 Anthropic
  호출, MATCH-001 hard filter 없음, DB 재조회, ID 검증 all-or-nothing,
  결정적 재정렬, PKB/candidate empty 처리, provider 실패 502, prompt
  injection 방어, compact 필드 선택, timeout 네임스페이스, 서비스 레벨
  로그 privacy)는 코드 리딩 기준으로 ADR-0031/Task 명세와 정확히
  일치하며 문제를 찾지 못했다. 다만 **AC에 명시된 테스트 커버리지
  요구(limit 검증 400/기본값/최대값, 로그 privacy) 2건이 실제로 전혀
  테스트되지 않아** "24개 케이스 모두 통과" 조건과 로그 privacy
  체크박스를 충족하지 못한다.

- **같은 Codex thread에 보낼 수정 요청**:
  1. `backend/src/test/java/com/careerops/backend/recommend/JobRecommendationControllerTest.java`
     신규 작성 (`AgentAnalysisControllerTest`/
     `SemanticJobMatchControllerTest` 패턴 참고, `@SpringBootTest` +
     `@AutoConfigureMockMvc`). 최소 케이스: limit 미지정 시 기본 5로
     동작(예: fake/stub bean으로 limit 전달값 확인 또는 응답 검증),
     `limit=20` 정상 200, `limit=0` → 400, `limit=21` → 400.
  2. `backend/src/test/java/com/careerops/backend/recommend/llm/`(또는
     기존 패키지 구조에 맞는 위치)에
     `AnthropicJobRecommendationClientTest.java` 신규 작성
     (`AnthropicSemanticJobMatchClientTest`의
     `missingKeyAndSensitiveInputAreNotLogged` 패턴 그대로 재사용):
     빈 API key로 client 생성 후 `recommend()` 호출 시 예외 발생 +
     root logger에 sensitive job title/PKB summary/detail/API key가
     전혀 없음을 `ListAppender`로 검증. 가능하면 `classify()`의
     timeout/4xx/5xx/malformed 분류를 검증하는 케이스도 함께 추가
     (MATCH-002 client test 수준).
  3. (선택, 권장) `JobRecommendationServiceTest`에
     `verify(jobs).findAllByStatus("OPEN")` 같은 최소 계약 검증을 추가해
     CLOSED job 배제 계약을 명시적으로 고정.
  4. (선택, 권장) `onlyApprovedOrManualPkbIsSent` 테스트에서 PENDING을
     "ImportCandidate 미조회"가 아니라 실제
     `ImportCandidateStatus.PENDING`/`REJECTED` mock으로 명시적으로
     구성해 Test Plan #22 의도와 더 정확히 맞춤.
  - 위 1, 2는 필수(AC 미충족 항목), 3, 4는 선택사항으로 명시해 전달.
