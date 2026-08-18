---
task_id: FIX-002
review_round: 1
reviewer: claude
reviewed_at: 2026-08-19T00:05:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] diff가 정확히 그대로 반영됐다 — 충족. `git diff`로 3개 파일을 직접 대조:
  - `backend/src/main/java/com/careerops/backend/pkbimport/ImportBatchRepository.java`:
    `Modifying`/`Instant` import 제거, `completeIfNoPending` 메서드 삭제, 나머지
    (`search`, `findByIdForUpdate`)는 문자 그대로 유지. Task 명세 diff 블록과
    한 글자도 다르지 않음.
  - `backend/src/main/java/com/careerops/backend/pkbimport/ImportBatch.java:63-66`:
    `markCompleted()`가 `markExtracted()` 바로 뒤에 명세 그대로 추가됨
    (`this.status = ImportBatchStatus.COMPLETED; this.completedAt = Instant.now();`).
  - `backend/src/main/java/com/careerops/backend/pkbimport/ImportBatchService.java:16,18-19,22,44-55`:
    생성자 시그니처의 특이한 들여쓰기(`ImportCandidateRepository
    candidateRepository`가 29칸 들여쓰기)까지 포함해 명세 diff와 완전히 일치.
    `complete()`가 `findByIdForUpdate` → status 체크(409 "already completed")
    → `existsByImportBatchIdAndStatus` 체크(409 "has pending candidates") →
    `markCompleted()` → `repository.save(batch)` 순서로 재작성됨, 명세와 순서/
    메시지 문자열까지 일치.
  - `existsByImportBatchIdAndStatus`는 `ImportCandidateRepository.java:16`에
    이미 존재하는 기존 메서드임을 확인(신규 아님, 명세 각주와 일치).

- [x] `POST .../batches/{id}/complete` 기존 동작 회귀 없음 — 충족.
  `ImportBatchControllerTest`를 독립 재실행(`--rerun`), 8개 테스트 전부
  통과(`build/test-results/test/TEST-...ImportBatchControllerTest.xml`:
  `tests="8" failures="0" errors="0"`). `completesOnlyOpenBatchWithoutPendingCandidates()`가
  200/COMPLETED/completedAt 및 재호출 시 409(already completed)를,
  `pendingCandidatePreventsCompletion()`이 409(pending) + batch 상태 OPEN
  유지를, `getsExistingBatchAndReturnsNotFoundForMissingOne()` 계열이 404를
  각각 커버함(테스트 코드 자체는 diff 대상이 아니며 실제로 변경 없음 — 아래
  참고).

- [x] `ImportCandidateConcurrencyTest`를 코드 변경 없이 최소 15회 반복 —
  충족(오케스트레이터 15회 + 리뷰어 독립 재실행 5회, 총 20회 확인).
  리뷰어가 직접 5회 `./gradlew test --tests
  "com.careerops.backend.pkbimport.ImportCandidateConcurrencyTest" --rerun`
  실행, 5/5 exit code 0(실패 0건). Task 명세는 최소 15회를 요구하며
  오케스트레이터가 이미 15회를 확인했으므로 총합으로 충족. (완전히
  독립적으로 20회+ 더 실행하지는 않았으나, 오케스트레이터 보고를 신뢰할
  근거 — 코드가 정확히 명세 diff와 일치함을 직접 확인했고, 리뷰어 자신의
  5회도 전부 통과 — 가 있음.)

- [x] `com.careerops.backend.pkbimport` 패키지 전체 최소 3회 반복 — 충족.
  리뷰어가 직접 3회 실행(`--tests "com.careerops.backend.pkbimport.*"
  --rerun`), 3/3 exit code 0.

- [x] `cd backend && ./gradlew test` 전체 최소 2회 반복, 매번 실패 0건(185개
  이상) — 충족. 오케스트레이터가 2회 확인(185/185)했고, 리뷰어가 추가로
  1회 더 실행해 재확인: `build/test-results/test/*.xml` 집계 결과
  `total tests: 185 failures: 0 errors: 0`.

- [x] 반복 실행 후 `careerops_test`의 4개 테이블 전부 0건(row leak 없음) —
  충족. 리뷰어가 위 모든 반복 실행(concurrency 5회 + pkbimport 3회 +
  controller 1회 + 전체 스위트 1회) 후 직접 `psql`로 확인:
  `import_candidates=0, import_batches=0, source_documents=0,
  career_awards=0`.

- [x] `git diff`가 3개 파일 외 다른 프로덕션 파일을 건드리지 않는다(테스트
  파일도 변경 없음) — 충족. `git status --porcelain` 결과 프로덕션 코드
  변경은 정확히 위 3개 파일뿐. `backend/src/test/` 아래 변경 파일 0건
  (`git status --porcelain backend/src/test/` 결과 없음).
  `.ai/metrics/metrics.jsonl`과 `docs/DECISIONS.md`도 변경돼 있으나, 이는
  Task 명세 `Codex 위임 범위`에 "Codex는 metrics에 self-report하지
  않는다"고 명시된 대로 오케스트레이터(Claude)가 plan 단계에서 직접
  기록한 것이며(`metrics.jsonl`의 `"implemented_by": "codex",
  "codex_invocation_count": 0`, note에 "Applied this fix locally...then
  reverted before handing to Codex" 기록), 애초에 Task의 "다른 프로덕션
  파일"에 해당하지 않는 문서/로그이므로 AC 위반 아님.

## 테스트 결과

- `ImportCandidateConcurrencyTest` 단독: 리뷰어 5회 반복, 5/5 통과 (오케스트레이터
  보고 15회 포함 총 20회 확인, 전부 통과).
- `com.careerops.backend.pkbimport.*` 패키지: 리뷰어 3회 반복, 3/3 통과.
- `ImportBatchControllerTest`: 8/8 통과(complete() 관련 4개 시나리오 포함).
- `cd backend && ./gradlew test` 전체: 리뷰어 1회 추가 실행, 185/185 통과,
  실패/에러 0건 (`build/test-results/test/*.xml` 집계 기준). 오케스트레이터
  보고 2회분 포함 총 3회 전체 스위트 확인, 전부 185/185.
- 반복 실행 후 `careerops_test` row leak: 0건 (4개 테이블 전부 count=0,
  리뷰어가 자신의 실행 이후 직접 확인).

## Findings

- 없음. diff가 명세와 정확히 일치하고(파일별 전체 내용도 직접 Read로 재확인),
  ADR-0025가 설명하는 버그(READ COMMITTED에서 conditional UPDATE의
  subquery가 lock 대기 중 커밋된 변경을 못 보는 문제)를 실제로 회피하는
  구조임을 코드 레벨로 재확인함: `findByIdForUpdate(id)`와
  `existsByImportBatchIdAndStatus(id, PENDING)`는 서로 다른 Repository의
  서로 다른 메서드 호출이라 반드시 별도의 SQL SELECT 문으로 실행되며(같은
  문에 subquery로 합쳐질 방법이 없음), 후자가 전자의 `PESSIMISTIC_WRITE`
  잠금 획득 *이후*에 실행되므로 READ COMMITTED의 문 시작 시점 snapshot
  규칙에 따라 lock 대기 중 커밋된 PENDING candidate를 정확히 관측한다.
  candidate 생성 경로(`ImportCandidateService.create()`)도 동일한
  `findByIdForUpdate()`를 거치므로 두 경로가 완전히 직렬화됨.
- 과도한 추상화/불필요한 패턴 없음. 신규 production dependency 없음, migration
  없음. `markCompleted()`는 기존 `markExtracted()`와 같은 스타일의 도메인
  메서드로 naked setter보다 낫다.
- 자기소개서/근거 기반 검증 원칙과 무관한 순수 동시성 버그 수정이라 해당
  원칙 위반 소지 없음. Secret/API Key 커밋 없음.
- 반복 라운드: 이번이 1라운드이며 Codex가 diff를 정확히 그대로 적용해
  1라운드 만에 PASS. 특이사항 없음.

## 다음 액션

- PASS: 완료 처리. `.ai/metrics/metrics.jsonl`에 review/done phase 기록 추가
  필요(오케스트레이터가 plan phase만 이미 기록했으므로, review_round_count=1,
  first_review_pass=true, test_count=185, test_pass_count=185, status="passed"로
  review/done 엔트리 추가할 것). Task 명세 `Codex Thread 기록` 표의 round 1
  행과 frontmatter `status`도 `done`으로 갱신 필요.
