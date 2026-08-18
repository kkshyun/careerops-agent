---
task_id: PKB-007
review_round: 1
reviewer: claude
reviewed_at: 2026-08-18T22:40:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] 정상 PDF 업로드 시 201, rawText 포함, contentHash = SHA-256(rawText) —
  `SourceDocumentControllerTest.uploadsSyntheticPdfAndDocx` (fileName /
  rawText containsString / contentHash `[0-9a-f]{64}` 검증).
  `SourceDocumentService.create()`는 수정되지 않았으므로 해시 계산 로직은
  기존 rawText API와 동일 경로 재사용.
- [x] multi-page PDF 순서 보존 — `PdfTextExtractorTest.extractsTextAndPreservesPageOrder`
  (`indexOf("first page") < indexOf("second page")`), PDFBox `PDFTextStripper`
  기본 동작 그대로 사용(`PdfTextExtractor.java:24`).
- [x] 텍스트 없는 PDF 400, row 미생성 — `PdfTextExtractorTest.returnsBlankForPdfWithoutText`
  (extractor 단위) + `SourceDocumentControllerTest.rejectsNoTextCorruptAndPasswordProtectedDocumentsWithoutLeakingParserDetails`
  (`noText` 케이스, `repository.count()` 불변 확인).
- [x] 손상된 PDF 400, 원문 예외 미노출, row 미생성 — 동일 테스트에서
  `corrupt` fixture(`"%PDF-private-parser-marker"`)를 응답 body와
  Logback `ListAppender` 양쪽에서 `"private-parser-marker"` 미포함 검증.
  `SourceDocumentService.java:74-76`가 `DocumentExtractionException`/`IOException`을
  고정 메시지("문서에서 텍스트를 추출할 수 없습니다")로만 변환.
- [x] 비밀번호 보호 PDF 400, row 미생성 — `PdfTextExtractor.java:21-23`이
  `document.isEncrypted()`를 감지해 고정 메시지 예외로 변환, 컨트롤러
  테스트의 `protectedFile` 케이스로 확인.
- [x] 정상 DOCX(문단만) 201 — `uploadsSyntheticPdfAndDocx`의 docx 파트.
- [x] 표 포함 DOCX, 셀 텍스트 누락 없이 원본 순서 보존 —
  `DocxTextExtractorTest.extractsParagraphsAndTableInDocumentOrder`
  (`before-table` < `left-cell` < `after-table` 순서 확인),
  `DocxTextExtractor.java:26-31`이 `getBodyElements()`를 순회하며 단락/표를
  등장 순서 그대로 이어붙임(표를 끝에 몰아 붙이지 않음).
- [x] 손상된 DOCX 400, row 미생성 — `DocxTextExtractorTest.wrapsCorruptDocxWithFixedMessage`
  + 컨트롤러 테스트의 `corruptDocx` 케이스.
- [x] empty file(0바이트) 400 — `SourceDocumentService.java:52-54`
  (`file.isEmpty()`), 컨트롤러 테스트의 `empty` 케이스.
- [x] 미지원 확장자 400 — `SourceDocumentService.java:61-63`,
  컨트롤러 테스트 `unsupported`(.txt) 케이스.
- [x] 확장자는 `.pdf`이나 Content-Type/매직바이트 불일치 시 400 —
  `SourceDocumentService.java:64-69`, 컨트롤러 테스트 `wrongType`/`wrongMagic`.
- [x] multipart 설정(10MB) 초과 시 413, 별도 `@ExceptionHandler` 없이 —
  `MultipartUploadLimitIntegrationTest`가 실제 HTTP 요청(raw multipart body,
  10MB+1)으로 413 검증(가정에 의존하지 않고 실제 통합 테스트로 확인).
  `application.yml`에 `@ControllerAdvice` 추가 없음(diff 확인).
- [x] **애플리케이션 레벨 재검증(핵심 게이트 1)** —
  `SourceDocumentService.java:55-57`: `file.getSize() > MAX_FILE_SIZE`
  (`10L * 1024 * 1024`, `application.yml`의 10MB와 별개 상수)이면 즉시
  413. `SourceDocumentUploadServiceTest.rejectsApplicationLevelFileLargerThanTenMegabytesWith413`가
  Spring MVC/multipart 설정을 거치지 않는 순수 서비스 단위 테스트로
  이 경로만 독립적으로 검증 — multipart 설정에만 의존하지 않음을 확인.
- [x] `documentType` 누락/유효하지 않은 값 400 — 컨트롤러 테스트
  `rejectsInvalidUploadMetadataAndContentWithoutRows`에서 파라미터
  누락 케이스와 `"INVALID"` 값 케이스 모두 확인(Spring MVC 기본 처리,
  별도 코드 없음 — 명세와 일치).
- [x] rawText 50,000자 초과 시 400, row 미생성, truncate 없음 —
  `SourceDocumentService.java:80-82` (`rawText.length() > MAX_RAW_TEXT_LENGTH`,
  50_000), `SourceDocumentUploadServiceTest.rejectsTextOverBoundaryAndFileNameOverBoundaryBeforeSaving`
  (50,001자 케이스, `repository.save()` 미호출 검증).
- [x] rawText 정확히 50,000자면 201(경계값) —
  `SourceDocumentUploadServiceTest.acceptsExactlyFiftyThousandExtractedCharacters`
  (`hasSize(50_000)`, `repository.save()` 호출 확인). 기존 rawText 직접
  등록 API의 동일 경계값 테스트(`SourceDocumentControllerTest.acceptsFiftyThousandCharacterBoundary`)와
  정책 일치.
- [x] **fileName 255자 초과 400(핵심 게이트 2-a)** —
  `SourceDocumentService.java:83-85`: `fileName != null && fileName.length() > MAX_FILE_NAME_LENGTH`
  (255)면 400. `create()`/`SourceDocumentCreateRequest`의 `@Size(max=255)`가
  `@Valid` 없이 우회되는 지점을 정확히 재현. 테스트로 256자 파일명
  케이스(`"a".repeat(252) + ".pdf"`) 확인.
- [x] path traversal 문자열 파일명 metadata로만 저장, 실제 접근 없음 —
  `storesTraversalLikeNameOnlyAsMetadata`(`"../../secret.pdf"` 그대로
  `fileName` 응답에 반영). sanitize 로직 없음(명세 §9와 일치), 파일시스템
  접근 코드 자체가 없음(코드 전체에 `File`/`Path`/`Files` 사용 없음,
  grep으로 확인).
- [x] rawText/binary/파서 예외 메시지가 로그에 노출되지 않음 —
  `SourceDocumentService.java`/`extraction/*.java` 전체에 `log.*` 호출이
  전혀 없음(grep 결과 0건) — 애초에 로깅 자체를 하지 않아 노출 여지 없음.
  `ListAppender` 기반 테스트(`rejectsNoTextCorruptAndPasswordProtectedDocumentsWithoutLeakingParserDetails`,
  기존 `rawTextIsNotWrittenToApplicationLogsDuringCreateAndRead`)로 재확인.
- [x] 원본 binary 미저장 — `MultipartFile` InputStream은 추출에만 사용되고
  즉시 close(`try-with-resources`), `SourceDocument` 엔티티/응답 DTO에
  binary 컬럼 없음(grep으로 `byte[]`/`Blob` 사용처가 매직바이트 상수
  2개뿐임을 확인). 신규 DB migration 없음(`db/migration` 디렉터리 diff
  없음, 최신 파일 `V9__...`로 PKB-006 시점과 동일).
- [x] 기존 rawText 직접 등록 API 회귀 없음 —
  `git diff backend/.../SourceDocumentService.java`에서 기존 `create()`
  메서드 본문 무변경 확인. `SourceDocumentCreateRequest`/`SourceDocumentController.create()`
  전혀 미수정. 컨트롤러 테스트 diff는 순수 추가(기존 테스트 3개 무변경).
- [x] 기존 GET(목록/단건)/ImportBatch/ImportCandidate 등 전체 회귀 없음 —
  해당 패키지 파일 전혀 미수정(git status: `manualimport` 등 패키지
  변경 없음), 전체 스위트 통과로 재확인.
- [x] `cd backend && ./gradlew test` 전체 실패 0건 — 직접 재실행(`--rerun`)
  결과 `BUILD SUCCESSFUL`, JUnit XML 집계 173 tests / 0 failures / 0 errors.

## 테스트 결과

- test_count = 173, test_pass_count = 173 (실패 0, 에러 0).
- 실행 방법: `docker compose ps`로 postgres/redis 컨테이너 기동 확인 후
  저장소 루트에서 `./gradlew test --rerun`(캐시 무시 강제 재실행) 실행,
  `backend/build/test-results/test/*.xml`을 집계해 독립적으로 재검증.
  Codex가 보고한 173/173과 일치.
- 신규 테스트 파일 6개(`SourceDocumentUploadServiceTest`,
  `MultipartUploadLimitIntegrationTest`, `extraction` 패키지 4개) 전부
  포함되어 실행됨.

## Findings

### 핵심 게이트 확인 결과 (사용자 명시 요구사항)

1. **크기 제한 이중화** — 충족. `application.yml`(`max-file-size: 10MB`,
   `max-request-size: 11MB`)과 `SourceDocumentService.MAX_FILE_SIZE`
   (`10L * 1024 * 1024`, `file.getSize()` 비교)가 서로 독립적인 코드
   경로로 존재. 통합 테스트(`MultipartUploadLimitIntegrationTest`)는 실제
   HTTP 요청으로 Spring 자동 413을 검증하고, 별도 단위 테스트
   (`SourceDocumentUploadServiceTest.rejectsApplicationLevelFileLargerThanTenMegabytesWith413`)는
   `MockMultipartFile`로 서비스 메서드만 직접 호출해 애플리케이션 레벨
   재검증이 multipart 설정과 무관하게 동작함을 증명 — "단순히 multipart
   설정에만 의존하지 않는지" 우려가 실제로 해소됨.
2. **`@Valid` 우회 대응** — 충족, 3가지 전부 코드로 확인:
   - fileName 255자 초과 거부: `SourceDocumentService.java:83-85`.
   - rawText blank 거부: `SourceDocumentService.java:77-79`.
   - rawText 50,000자 초과 거부(정확히 50,000자는 허용):
     `SourceDocumentService.java:80-82`(`>` 비교이므로 정확히 50,000은
     통과) — 경계값 양쪽 모두 테스트로 커버됨.

### 그 외

- 검증 순서가 Task 명세 §5의 1~10단계와 정확히 일치(존재/empty →
  크기 → 확장자 → Content-Type → 매직바이트 → 추출 → blank → 길이 →
  fileName 길이 → create()).
- ADR-0023(Tika 미도입, PDFBox/POI 버전 고정, 원본 미저장, zip bomb
  기본 방어 유지)이 코드와 정확히 일치. `build.gradle`에 두 dependency
  버전이 명세 그대로(3.0.8 / 5.5.1) 명시됨.
- 과도한 추상화 없음 — extractor 2종 + dispatch service만 존재, 별도
  registry/factory/strategy 패턴 없음(리스트 순회로 충분히 단순).
- 새 production dependency(PDFBox, POI) 2개는 ADR-0023과 Task 명세에
  이유가 기록되어 있음.
- Secret/API Key 커밋 없음(diff 전체 확인).
- 자기소개서 관련 로직 없음(Out of Scope와 일치, LLM/근거 검증 관련
  코드 없음) — 근거 기반 검증 원칙과 무관한 Task.
- 테스트는 전부 PDFBox/POI로 코드 내에서 합성한 fixture 사용
  (`DocumentFixtureSupport`), 실제 사용자 문서 없음.
- 신규 DB migration 없음(명세와 일치).
- 사소한 개선 여지(품질 게이트를 좌우하지 않는 수준, 이번 라운드
  PASS 판정에 영향 없음):
  - `DocxTextExtractor.extract()`의 `catch (IOException | RuntimeException exception)`가
    다소 광범위해서 extractor 내부 로직 버그(예: NPE)까지 "손상된
    DOCX"로 삼켜버릴 수 있음. 현재는 문제를 일으키지 않지만, 추후 파서
    로직이 복잡해지면 진짜 버그를 마스킹할 위험이 있다는 점만 기록해
    둔다(지금 수정을 요구하지는 않음).
  - `hasExpectedMagic()`가 `file.getInputStream()`을 매직바이트 검증과
    추출(`extractionService.extract`) 두 번 호출한다 — `MultipartFile`은
    반복 읽기가 되는 구현(메모리/임시파일 기반)이라 현재는 문제없이
    동작하고 테스트도 통과하지만, 명시적으로 문서화되어 있지 않다는
    점만 참고로 남긴다.

## 다음 액션

- **PASS**. Acceptance Criteria 24개 항목 전부 충족(코드 인용 근거
  확인), 사용자가 지정한 두 핵심 게이트(크기 이중 검증, `@Valid` 우회
  대응) 모두 코드와 테스트로 확인됨. `./gradlew test` 173/173 통과를
  독립적으로 재실행해 재검증. 기존 rawText API/Career/Job/Collect/
  Application 전체 회귀 없음(diff 범위가 `pkbimport` 패키지와
  `docs/DECISIONS.md`로 정확히 한정됨).
- 완료 처리 가능. `.ai/metrics/metrics.jsonl`에 최종 상태 기록 필요
  (planned_by/implemented_by/round 수/test 결과).
- Task 명세 frontmatter의 `status: in_progress`를 `done`으로 갱신 필요.
