---
task_id: PKB-005
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T19:35:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

1. `POST` `documentType`+`rawText`만(fileName 생략) → 201,
   `SourceDocumentDetailResponse`(`fileName` null, `contentHash` 64자
   hex) — **충족**. `SourceDocumentControllerTest.createsMinimalAndFullDocuments`
   L36-43: `fileName` 없이 요청 → `$.fileName` empty, `$.contentHash`가
   `sha256("hello")`의 알려진 값과 정확히 일치(`2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824`
   — 실제 SHA-256("hello") 값과 일치함을 별도로 확인).
2. `POST` 전체 필드 → 201, 응답이 요청과 일치 — **충족**. 같은 테스트
   L45-52: `fileName`/`documentType`/`rawText` 3개 필드 모두 요청값과
   응답값 1:1 대조.
3. `documentType` 누락 → 400, row 미생성 — **충족**.
   `rejectsInvalidRequestsWithoutCreatingRows` L58-68, 4개 케이스(누락
   포함) 각각 400 확인 후 `repository.count()`가 이전과 동일함을 확인.
4. `rawText` 누락/공백 → 400, row 미생성 — **충족**. 같은 테스트, `"   "`
   공백 케이스 포함.
5. `rawText` 50,001자 초과 → 400, row 미생성 — **충족**. 같은 테스트
   `"x".repeat(50_001)`.
6. `rawText` 정확히 50,000자 → 201(경계값) — **충족**.
   `acceptsFiftyThousandCharacterBoundary` L72-78: JsonNode로 직접 파싱해
   `response.get("rawText").asText()`의 길이를 Java에서 `hasSize(50_000)`로
   검증(round 3에서 JsonPath `.length()` 미적용 문제를 이 방식으로 정정한
   것 확인).
7. `contentHash`가 실제 SHA-256과 일치(정확성 검증, 존재 여부만이 아님) —
   **충족**. 위 1번 근거와 동일 — 알려진 입력("hello")의 알려진 SHA-256
   digest와 정확히 일치시킴.
8. 동일 `rawText`로 두 번 `POST` → 둘 다 201, `contentHash` 동일/`id` 다름
   — **충족**. `permitsDuplicateRawTextWithSameHashAndDifferentIds`
   L81-88.
9. `GET` 목록 — pagination/`documentType` 필터/목록에 `rawText` 없음 —
   **충족**. `listsWithPaginationClampAndFilterWithoutRawText` L91-104:
   `documentType=RESUME` 필터 시 2건만(OTHER 1건 제외), `page`/`size`
   슬라이스 확인, `$.content[0].rawText` `doesNotExist()`로 JSON 계약
   확인, `size=1000` → 100 clamp.
10. `GET /{id}` — 존재 시 200+`rawText` 포함, 미존재 시 404 — **충족**.
    `getsExistingDocumentWithRawTextAndReturnsNotFoundForMissingOne`
    L107-113.
11. `POST .../documents/{documentId}/batches` — 존재하는 documentId →
    201, `status="OPEN"`, `completedAt` null, `sourceDocumentId` 일치 —
    **충족**. `ImportBatchControllerTest.createsOpenBatchForExistingDocument`
    L28-37.
12. 존재하지 않는 documentId → 404, row 미생성 — **충족**.
    `missingDocumentReturnsNotFoundWithoutCreatingBatch` L40-45.
13. `GET /batches` — pagination/`sourceDocumentId` 필터 — **충족**.
    `listsWithPaginationClampAndSourceDocumentFilter` L48-63.
14. `GET /batches/{id}` — 200/404 — **충족**.
    `getsExistingBatchAndReturnsNotFoundForMissingOne` L66-74.
15. `rawText`가 애플리케이션 로그에 노출되지 않음(자동 테스트 최소 1건) —
    **충족(단, 아래 Findings에 테스트 유의미성에 대한 의견 있음)**.
    `rawTextIsNotWrittenToApplicationLogsDuringCreateAndRead` L116-133,
    Logback `ListAppender`로 ROOT 로거에 붙여 create+get 호출 동안
    포맷된 메시지 어디에도 sentinel 문자열이 없는지 확인. 신규
    dependency 없이 기존 transitive `logback-classic` 사용.
16. 기존 JobPosting/COLLECT/JobApplication/ApplicationStage/
    CareerExperience/Certification/Education/Award 회귀 없음 — **충족**.
    `git status --short` 확인 결과 `career`/`job`/`collector`/
    `application`/`manualimport` 패키지 전혀 수정되지 않음(신규
    `pkbimport` 패키지 + migration 2개 + `.ai/tasks/PKB-005.md` + Claude가
    수정한 `docs/DECISIONS.md`/`docs/ROADMAP.md`/`.ai/metrics/metrics.jsonl`만
    변경/추가). 전체 재실행 결과도 회귀 없음(아래 테스트 결과 참고).
17. `cd backend && ./gradlew test` 전체 실패 0건 — **충족**. 아래 참고.

## 테스트 결과

reviewer가 직접 재실행(호출자 Claude가 사전에 146/146을 보고했으나 독립적으로
재확인함).

- 사전조건: `docker compose ps` → `careerops-agent-postgres-1`/
  `careerops-agent-redis-1` 둘 다 `healthy`(이미 기동 중).
- `cd backend && ./gradlew test --rerun` → `BUILD SUCCESSFUL`.
- `build/test-results/test/*.xml`을 직접 파싱해 합산: **test_count = 146,
  failures = 0, errors = 0**, 총 29개 테스트 클래스(기존 25 + 신규 4).
- 신규 4개 클래스 개별 tests 속성: `SourceDocumentControllerTest`=7,
  `SourceDocumentRepositoryTest`=2, `ImportBatchControllerTest`=4,
  `ImportBatchRepositoryTest`=2 → 합계 15건. 기존 131 + 신규 15 = 146,
  Codex/Claude가 보고한 수치와 정확히 일치.
- test_pass_count = 146/146.

## Findings

- **[Out of Scope 준수 확인]** `grep -rl "ImportCandidate" backend/src/`
  결과 없음 — `ImportCandidate` entity/API 미생성 확인. `career`/
  `manualimport` 패키지는 `git status --short`상 완전히 무변경. 파일
  업로드(`MultipartFile`)/PDF·DOCX 파싱/LLM client 관련 코드/의존성
  없음(`SourceDocumentController.java`에 `@RequestBody
  SourceDocumentCreateRequest`만 있고 multipart 처리 없음). PATCH/DELETE
  엔드포인트가 `SourceDocumentController`/`ImportBatchController`
  어디에도 없음(`GET`/`POST`만). `git diff --stat -- backend/build.gradle`
  결과 없음 — 신규 production/test dependency 없음(명세와 일치).
- **[rawText 노출 계약 확인]** `dto/SourceDocumentResponse.java`(목록용)에
  `rawText` 필드 자체가 없고, `dto/SourceDocumentDetailResponse.java`(단건/
  생성 응답)에는 `rawText` 필드가 있음 — 코드 레벨에서 직접 확인, DTO
  타입 자체가 다르므로 실수로 누락될 수 없는 구조. 목록 컨트롤러 테스트도
  `jsonPath("$.content[0].rawText").doesNotExist()`로 JSON 계약을 재확인.
- **[contentHash 확인]** `SourceDocumentService.sha256()`
  (`SourceDocumentService.java:44-51`)이 `MessageDigest.getInstance("SHA-256")`
  + `HexFormat.of().formatHex(...)`로 소문자 64자 hex를 서버에서 계산.
  `V11__create_source_documents_table.sql`에 `content_hash VARCHAR(64) NOT
  NULL`만 있고 `UNIQUE` 제약 없음(명세 요구사항 "DB UNIQUE 제약을 걸지
  않는다"와 일치) — 동일 rawText 재등록 허용 테스트로도 실동작 확인됨.
- **[로그 미노출 확인, 코드 레벨]** `grep -rn "Logger\|log\." backend/src/main/java/com/careerops/backend/pkbimport/`
  결과 없음 — Service/Controller 어디에도 명시적 로깅 코드 자체가 없음.
  다만 자동 테스트(`rawTextIsNotWrittenToApplicationLogsDuringCreateAndRead`)는
  ROOT 로거에 sentinel 문자열이 안 나타나는지만 확인하는데, 현재
  `application.yml`에 Hibernate SQL/bind parameter 로깅 설정이 전혀 없어
  (`hibernate.show_sql`/`logging.level.org.hibernate...` 미설정) 애초에
  아무것도 로그에 안 찍히는 상태에서 실행되는 테스트다. 즉 이 테스트는
  "현재 로깅 설정 + 현재 코드에서 rawText가 새지 않는다"는 것을
  회귀 방지용으로 잡아주지만(누군가 실수로 `log.info(request.toString())`
  같은 코드를 추가하면 실패할 것이므로 완전히 무의미한 테스트는 아님),
  "SQL bind 로깅을 켜도 안전하다"는 더 강한 보장까지는 검증하지 않는다.
  명세 문구("로그에 원문 그대로 출력되지 않는다")는 현재 코드/설정
  기준으로는 충족되며, 이 갭은 Out of Scope(로깅 설정 변경은 이번 Task
  범위 아님)에 해당하므로 블로킹 사유 아님.
- **[FIX-001 재발 없음]** `SourceDocumentRepository.search()`/
  `ImportBatchRepository.search()` 모두 `d.documentType = :documentType`,
  `b.sourceDocument.id = :sourceDocumentId` 형태로 파라미터가 매핑된
  컬럼과 직접 비교됨(`LIKE`/`LOWER`/`CONCAT` 함수 인자 위치 아님) —
  FIX-001류 null-parameter typing 버그 없음.
- **[Migration 확인]** `ls backend/src/main/resources/db/migration/`
  결과 V11/V12로 실제 적용됨(V10까지 기존, 충돌 없음). 컬럼/제약이
  명세의 CREATE TABLE 문과 정확히 일치(`file_name` nullable,
  `document_type`/`content_hash`/`raw_text` NOT NULL, UNIQUE 없음;
  `source_document_id` FK `ON DELETE` 절 없음). `ImportBatchRepositoryTest.databaseRejectsOrphanBatch`가
  실제로 FK 위반을 재현해 고아 row가 거부됨을 검증.
- **[`ImportBatchStatus.COMPLETED` 미사용 확인]**
  `grep -rn "COMPLETED" backend/src/main/java/com/careerops/backend/pkbimport/`
  결과 `ImportBatchStatus.java`의 enum 선언 한 곳뿐 — `ImportBatchService`/
  `ImportBatch` 생성자 어디서도 `COMPLETED`를 세팅하는 코드 경로 없음
  (`ImportBatch` 생성자는 `create()`에서 항상 `ImportBatchStatus.OPEN`
  하드코딩으로만 호출됨).
- **[Service `@Transactional` 미적용 확인]**
  `SourceDocumentService`/`ImportBatchService` 어디에도
  `@Transactional` import/애노테이션 없음 — 명세("단일 row CRUD이므로
  `@Transactional`을 붙이지 않는다")와 일치.
- Secret/API Key 커밋 없음. 자기소개서 관련 로직 없음(이번 Task 범위 밖)
  — 근거 기반 검증 원칙 위반 사항 해당 없음. `docs/DECISIONS.md`/
  `docs/ROADMAP.md`는 Claude가 계획 단계에서 수정한 것으로 보이며
  애플리케이션 코드 변경 아님.

## 다음 액션

**PASS** — Acceptance Criteria 17개 전항목 충족, 전체 테스트 146/146 통과
(reviewer가 `./gradlew test --rerun`으로 독립 재실행하여 XML 결과까지
직접 파싱해 확인), Out of Scope 위반 없음(`ImportCandidate` 없음,
`career`/`manualimport` 패키지 무변경, 파일업로드/파싱/LLM/PATCH/DELETE
없음, 신규 dependency 없음), `contentHash` SHA-256 서버 계산 및 UNIQUE
미적용, `rawText` 목록/단건 응답 DTO 분리, `COMPLETED` 미사용,
FIX-001류 버그 없음 모두 코드 레벨로 확인됨.

- `.ai/metrics/metrics.jsonl`에 PKB-005 `review`/`done` phase 라인을
  기록하고 Task를 완료 처리할 것을 호출자(Claude)에게 권고.
- 블로킹 아닌 참고 사항 1건: 로그 미노출 테스트는 현재 로깅 설정(SQL
  bind 로깅 비활성) 하에서만 유의미하다. 향후 디버깅 목적으로 Hibernate
  SQL/bind parameter 로깅(`logging.level.org.hibernate.orm.jdbc.bind=trace`
  등)을 켜는 변경이 생기면, 이 테스트가 그 순간 rawText 노출을 잡아줄지
  다시 확인 필요 — 별도 Codex round 불필요, 참고만.
