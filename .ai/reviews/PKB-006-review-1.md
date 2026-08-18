---
task_id: PKB-006
review_round: 1
reviewer: claude (reviewer subagent)
reviewed_at: 2026-08-18T20:15:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `POST .../candidates` 4개 targetType 유효 payload → 201/PENDING/createdEntityId null
      — 충족. `ImportCandidateControllerTest#createsAllTargetTypesAndSupportsGetListFilterAndBatchIsolation`
      (backend/src/test/java/com/careerops/backend/pkbimport/ImportCandidateControllerTest.java:35-46).
- [x] validation 위반 → 400, row 미생성 — 충족.
      `ImportCandidateControllerTest#rejectsInvalidValidationAndMalformedJsonWithoutLeakingParserText`
      (같은 파일:58-70), `assertThat(candidateRepository.count()).isEqualTo(before)`로 확인.
- [x] JSON 파싱 불가 → 400, 파서 원문 메시지 미노출 — 충족.
      `ImportCandidateService.parseAndValidate()`가 `Exception`을 잡아
      `"invalid payload for targetType " + targetType` 고정 메시지로 감싼다
      (ImportCandidateService.java:106-111). 테스트는 `"{secret malformed"` payload로
      응답 본문에 `"secret"`이 없음을 검증(ImportCandidateControllerTest.java:65-68).
- [x] 존재하지 않는 batchId → 404 — 충족. `missingAndCompletedBatchRejectCreation`
      (ImportCandidateControllerTest.java:74-76), `findByIdForUpdate().orElseThrow(...NOT_FOUND)`
      (ImportCandidateService.java:42-43).
- [x] COMPLETED batch → 409, row 미생성 — 충족(로직상). `create()`가 상태 확인 후
      바로 예외를 던지므로 `repository.save()`에 도달하지 않는다
      (ImportCandidateService.java:44-46). 테스트로 409는 확인하나(같은 파일:79-81)
      이 케이스만 별도 row-count assert는 없음 — 코드 흐름상 구조적으로 안전하여
      blocking 이슈로 보지 않음.
- [x] `GET .../candidates` pagination + status 필터 — 충족(같은 테스트, :47-51).
- [x] `GET .../candidates/{id}` 200/404(batchId 불일치 포함) — 충족(:52-54).
- [x] approve CAREER_EXPERIENCE 승인 → 200/APPROVED/PKB row 생성/provenance 일치 — 충족.
      `approveCreatesAllPkbTypesWithProvenance` → `approveAndAssert`/`assertProvenance`
      (:85-92, 129-152).
- [x] approve CERTIFICATION/EDUCATION/AWARD 동일 검증 — 충족(같은 테스트).
- [x] approve business-rule 위반 → 400, candidate PENDING 유지, PKB row 미생성(rollback) —
      충족. `ImportCandidateApproveRollbackNonTransactionalTest`(비-트랜잭셔널 클래스,
      `@Transactional`/`@Rollback` 없음)에서 실제 commit/rollback 경계로 검증
      (전체 파일). `@BeforeEach`/`@AfterEach`로 생성/정리.
- [x] approve 이미 APPROVED 재승인 → 409, PKB row 추가 생성 없음 — 충족.
      `terminalTransitionsReturnConflictAndRejectCreatesNoPkbRow`(:95-112).
- [x] approve REJECTED 승인 → 409 — 충족(같은 테스트, :110).
- [x] reject PENDING → 200/REJECTED/PKB row 미생성 — 충족(:105-108).
- [x] reject APPROVED → 409 — 충족(:101-102).
- [x] reject REJECTED 재거부 → 409 — 충족(:109).
- [x] **동시성**: 동일 PENDING candidate에 approve 2개 동시 요청 → 정확히 1개 200,
      1개 409, PKB row 정확히 1개 — 충족.
      `ImportCandidateConcurrencyTest#concurrentApproveCreatesExactlyOnePkbRow`(:49-61).
      `@AfterEach cleanUp()`이 생성된 candidate/award/batch/document를 정리
      (:30-46) — 다른 테스트 클래스 오염 없음, 아래 검증 참고.
- [x] `POST .../complete` PENDING 0개 → 200/COMPLETED/completedAt 설정 — 충족.
      `ImportBatchControllerTest#completesOnlyOpenBatchWithoutPendingCandidates`(:78-88).
- [x] `POST .../complete` PENDING 1개 이상 → 409, batch OPEN 유지 — 충족.
      `pendingCandidatePreventsCompletion`(:90-99).
- [x] `POST .../complete` 이미 COMPLETED → 409 — 충족(같은 테스트 블록, :84-85).
- [x] COMPLETED batch에 candidate 생성 → 409 — 충족.
      `missingAndCompletedBatchRejectCreation`(ImportCandidateControllerTest.java:77-81).
- [x] **동시성**: candidate 생성 vs complete 동시 요청 시 불변식 유지 — 충족.
      `ImportCandidateConcurrencyTest#concurrentCompleteAndCreatePreserveBatchInvariant`(:63-90),
      성공/실패 조합 양쪽 분기를 모두 assert.
- [x] payload가 로그(INFO/DEBUG)에 노출되지 않음 — 충족.
      `pkbimport`/`career` 패키지 전체에 `log.info/debug/warn/error` 호출 자체가 없음
      (grep 확인). `candidatePayloadIsNotWrittenToApplicationLogs` 테스트로도 재확인
      (ImportCandidateControllerTest.java:114-127).
- [x] 기존 SourceDocument/ImportBatch 회귀 없음 — 충족. `SourceDocument` 관련 파일은
      diff에 없고, `ImportBatchControllerTest`의 기존 생성/목록/단건 테스트 유지+통과.
- [x] 기존 CareerExperience/Certification/Education/Award 회귀 없음, 특히 기존
      생성자/HTTP `create()`가 MANUAL/null — 충족.
      `ManualProvenanceControllerTest#directHttpCreatesAllPkbTypesAsManualWithoutCandidateReference`.
- [x] 기존 JobPosting/COLLECT/JobApplication/ApplicationStage 회귀 없음 — 충족.
      해당 패키지 파일 변경 없음(git status 확인), 전체 스위트 159/159 통과로 재확인.
- [x] `cd backend && ./gradlew test` 전체 실패 0건 — 충족. 아래 테스트 결과 참고.

## 설계상 핵심 안전장치 확인 (요청받은 5개 항목)

1. **Approve/Reject concurrency** — `ImportCandidateService.approve()`/`reject()`가
   각각 `transitionFirst()`를 첫 statement로 호출하고, 이는 곧바로
   `repository.transitionIfPending()`(조건부 `@Modifying UPDATE ... WHERE status='PENDING'`,
   `clearAutomatically=true`)을 실행한다(ImportCandidateService.java:62-91). SELECT 후
   UPDATE하는 check-then-act 패턴이 아니다. 실패 시(영향 row 0)에만 진단용 `find()`를
   호출한다 — ADR-0022 결정 2와 정확히 일치.
2. **ImportBatch complete 불변식** — `ImportBatchRepository.completeIfNoPending()`
   (조건부 UPDATE + `NOT EXISTS PENDING candidate`, ImportBatchRepository.java:24-28)과
   `findByIdForUpdate()`(`@Lock(PESSIMISTIC_WRITE)`, 같은 파일:20-22)가 candidate 생성
   경로(`ImportCandidateService.create()`, ImportCandidateService.java:41-49)에서 부모
   batch를 잠근다. 두 경로 모두 같은 `import_batches` row를 두고 경쟁하는 구조가
   코드상 명확히 확인됨 — `ImportCandidateConcurrencyTest#concurrentCompleteAndCreatePreserveBatchInvariant`가
   실제로 이 경쟁을 재현하고 양쪽 결과 분기를 모두 검증.
3. **career → pkbimport 단방향 의존** — `grep -rn "import com.careerops.backend.pkbimport" backend/src/main/java/com/careerops/backend/career/`
   결과 매치 없음. 확인됨.
4. **payload privacy** — 파서 예외는 고정 메시지로 감싸 원문 노출 없음(위 참고).
   `pkbimport`/`career` 패키지에 로그 호출 자체가 없어 payload가 INFO/DEBUG에
   출력될 경로가 없음.
5. **provenance 정확성** — approve 시 대상 Service의 `create(request, SourceType.IMPORT,
   candidateId)` 오버로드가 호출되어 `sourceType=IMPORT`/`sourceImportCandidateId=candidateId`가
   기록됨(4개 Service 모두 동일 패턴 확인). 기존 HTTP `create(request)`는 여전히
   `create(request, SourceType.MANUAL, null)`로 위임(하위 호환 유지) —
   `ManualProvenanceControllerTest`로 재확인.
6. **동시성 테스트 격리** — `ImportCandidateConcurrencyTest`가 `@AfterEach cleanUp()`에서
   생성한 candidate/award/batch/document를 FK 순서로 정리
   (ImportCandidateConcurrencyTest.java:30-46). 아래 "테스트 결과"에서 실제로
   `careerops_test`의 관련 테이블이 스위트 실행 후 전부 0건임을 직접 쿼리로 재확인함.

## 앞선 3 round 수정사항 재확인

- (a) 테스트 파일 괄호 문법 오류 — `ManualProvenanceControllerTest.java` 정상 컴파일
  확인(`./gradlew compileTestJava` 성공, 아래 전체 빌드에 포함).
- (b) `ImportCandidateConcurrencyTest` row 오염 — `@AfterEach cleanUp()` 존재 확인,
  실행 후 DB 직접 조회로 leak 없음 재확인(아래).
- (c) `approvalBusinessFailureRollsBackTransitionAndPkbInsert` rollback 검증 —
  `ImportCandidateApproveRollbackNonTransactionalTest`로 이동됨, 클래스/메서드 레벨
  `@Transactional`/`@Rollback` 전혀 없음(파일 전체 확인) — `CareerExperienceSearchNonTransactionalTest`
  전례와 동일 패턴.

세 수정 모두 실제로 반영되어 있음을 코드로 확인.

## 테스트 결과

- `cd backend && ./gradlew test --rerun` 직접 실행 → `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml` 집계: **tests=159, skipped=0, failures=0, errors=0**
  (159/159 통과, Task 명세의 "최종 159/159" 재확인).
- 실행 후 `careerops_test`에서 직접 쿼리(`import_candidates`/`import_batches`/
  `source_documents`/`career_awards`/`career_experiences`) → 전부 0건. 동시성 테스트를
  포함한 모든 새 테스트가 자신이 만든 row를 완전히 정리함을 확인.
- Docker Compose(`postgres`/`redis`)는 이미 기동 상태(`Up 2 days (healthy)`)에서 실행.

## Findings

- 과도한 추상화/불필요한 패턴: 없음. ADR-0021/0022가 기각한 대안(4개 정형
  candidate 테이블, jsonb, polymorphic 관계, 자동 완료 등)을 그대로 피하고
  명세대로 최소 구조를 구현함.
- 신규 production dependency: 없음(`build.gradle` diff 없음, `git status`로 확인).
- Secret/API Key: 커밋된 diff에 노출 없음(`password|secret|api[_-]?key|token` grep 결과 없음).
- 근거 기반 검증 원칙: `ImportCandidate.payload`는 사용자가 API로 직접 입력한
  구조화 데이터이고, 승인 시에도 기존 `career` Service의 검증을 그대로 재사용할 뿐
  AI가 임의로 값을 생성/보정하는 로직이 전혀 없음. 이번 Task 자체가 LLM 추출을
  명시적으로 Out of Scope로 남겨 원칙과 충돌하지 않음(PKB-008 후보에서 재검토
  필요하다는 점을 ADR-0021이 이미 명시).
- 사소한 개선 여지(비차단): "COMPLETED batch에 candidate 생성 시도 → row 미생성"
  Acceptance Criteria가 별도의 명시적 row-count assert 없이 코드 흐름으로만
  보장됨(다른 시나리오들은 대부분 count assert를 갖고 있음). 실제 위험은 없으나,
  다음 관련 작업 때 일관성 차원에서 assert 한 줄을 추가하면 더 좋음.

## 다음 액션

- **PASS**. Acceptance Criteria 26개 전항목 충족, 핵심 안전장치(동시성 2건,
  단방향 의존, privacy, provenance, 테스트 격리) 코드 레벨로 재확인, 로컬
  `./gradlew test` 재실행 결과 159/159 재확인. Codex에 추가 수정 요청 없음.
- 호출한 Claude가 `.ai/tasks/PKB-006.md`의 `status`를 `done`으로 전이하고
  `.ai/metrics/metrics.jsonl`에 review/done phase를 기록할 것을 권장
  (round 3 왕복 이력 포함: implementation_revision_count=3, review_round_count=1,
  first_review_pass=true, test_count=159, test_pass_count=159).
