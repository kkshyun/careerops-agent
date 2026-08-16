---
task_id: COLLECT-004
review_round: 1
reviewer: claude
reviewed_at: 2026-08-16T16:50:00+09:00
verdict: PASS
---

## Acceptance Criteria 체크

- [x] `[자동]` 마이그레이션/기동 — 충족. `V3__add_job_detail_enrichment.sql`이
      `job_postings.detail_fetched_at`, `recruitment_steps`, `attachments`
      테이블/컬럼을 신설하고, `application.yml`의 `ddl-auto: validate` 하에서
      `@SpringBootTest` 컨텍스트가 정상 기동(전체 테스트 통과)함으로써
      엔티티-스키마 정합성이 간접 검증됨.
- [x] `[자동]` 신규 저장 시 상세 보강 — 충족.
      `AlioCollectorServiceTest#enrichesNewPostingOnceAndDoesNotRefetchWhenRediscovered`
      (test 파일 L124-141)에서 신규 item 1건 처리 시 `fetchDetail`이
      `capturedDetailSns` 1회만 기록되고, steps 2건/files 3건이 저장되며
      `detailFetchedAt`이 non-null이 됨을 확인.
- [x] `[자동]` 이미 보강된 공고 재호출 안 함 — 충족. 같은 테스트에서 두 번째
      `collect()` 호출 후에도 `capturedDetailSns`가 그대로이고 steps/files
      건수가 늘지 않음을 검증(L137-140).
- [x] `[자동]` status 갱신 케이스에서도 미보강 공고는 보강됨 — 충족.
      `enrichesPreviouslyUnfetchedPostingDuringStatusUpdate`(L160-177)에서
      `detailFetchedAt=null`인 기존 posting의 status가 `updated`로 바뀌는
      동시에 상세 보강도 수행됨을 확인.
- [x] `[자동]` 상세조회 실패 격리 — 충족.
      `isolatesOneDetailFailureWhileSavingAllListItems`(L143-158)에서 1건
      성공/1건 실패 처리 시 실패 건은 steps/attachments/detailFetchedAt이
      비어 있고, 두 `JobPosting` 레코드 모두 정상 저장되며 `collect()` 자체는
      예외 없이 종료.
- [x] `[자동]` 멱등성(중복 방지) — 충족.
      `AlioDetailEnrichmentServiceTest#savesAllFieldsMarksFetchedAndIsIdempotent`에서
      같은 `posting`에 `enrich()`를 연속 2회 호출해도 steps/files/duration/run
      카운터 증분이 기대한 만큼(2회 호출 모두 success로 집계되지만 저장
      건수는 늘지 않음)만 나타남을 확인. 애플리케이션 레벨
      `existsByRecrutStepSn`/`existsByRecrutAtchFileNo` 확인(`AlioDetailEnrichmentService.java`
      L73, L80) + DB `UNIQUE` 제약(`V3__...sql` L16, L29) 이중 안전장치 모두
      존재.
- [x] `[자동]` Product Metric 계측 — 충족. `careerops.collector.detail.run`
      (태그 `result`), `.steps`, `.files`, `.duration` 4종 모두 명세와
      정확히 일치하는 이름으로 등록됨(`AlioDetailEnrichmentService.java`
      L35-37, L90-92). 테스트에서 `MeterRegistry`로 값 검증.
- [x] `[자동]` `JobPostingService.create()` 반환 타입 변경 회귀 없음 — 충족.
      `ManualImportService.java`가 `JobPosting saved = jobPostingService.create(...)`
      후 `JobPostingResponse.from(saved)`로 감싸 기존 `ManualJobImportResult`
      형태를 그대로 유지.
- [x] `[자동]` 기존 JobPosting 목록/조회 회귀 없음 — 충족. `JobPostingResponse.java`,
      `JobPostingController.java`(GET 엔드포인트), `JobPostingRepository.java`
      (검색 쿼리 부분) 모두 diff 없음(`git diff --stat` 결과 공백).
- [x] `[자동]` 회귀 없음(`./gradlew test`) — 충족. `docker compose up -d`
      기동 상태에서 `./gradlew test --rerun` 재실행, 전체 51건/51건 통과,
      실패 0건(사용자 사전 확인과 일치).
- [x] `[자동]` Git tracked file에 secret 없음 — 충족. `build.gradle` diff
      없음(신규 dependency 없음 확인), `serviceKey`/`api_key` 패턴 및
      실제 도메인(`opendata.alio.go.kr`) 문자열이 테스트 리소스에 없음을
      grep으로 확인. fixture(`alio-detail-response-valid.json` 등)는
      "합성", `example.invalid` 등 명백히 합성 데이터 스타일.
- `[수동]` 실제 키 검증 / Prometheus 노출 확인 — 수동 항목이라 이번 리뷰
  범위에서 실행하지 않음(사용자 별도 확인 필요).

## 특별 확인 요청 사항 (9개 항목)

1. **불변 파일 검증**: `CollectController.java`, `CollectResult.java`,
   `AlioJobMapper.java`, `AlioJobItem.java`, `AlioCollectionScheduler.java`,
   `JobPostingRepository.java`, `JobPostingResponse.java` 전부 `git diff`
   결과 공백(변경 없음) 확인.
2. **`JobPostingController.java`**: 요청한 1줄 변경만 정확히 반영됨.
   ```java
   -        return service.create(request);
   +        JobPosting saved = service.create(request);
   +        return JobPostingResponse.from(saved);
   ```
   그 외 diff 없음.
3. **실행 시점 로직**: `AlioCollectorService.java` L92-105 `enrichIfNeeded`
   헬퍼가 3개 분기(신규 저장/status 갱신/skip) 모두에서 호출되고,
   내부에서 `jobPosting.getDetailFetchedAt() == null` 조건으로만 `enrich()`를
   호출함을 확인.
4. **멱등성**: 위 참고. natural key exists 체크 + DB UNIQUE 제약 모두 존재.
5. **실패 격리**: `AlioDetailEnrichmentService.enrich()`(L41-64)가
   `RuntimeException`을 잡아 WARN 로그+`failed` 카운터만 남기고 밖으로
   던지지 않음. `persistDetail`은 `TransactionTemplate`로 감싸져 있어
   부분 실패 시 트랜잭션 롤백되고 `detailFetchedAt`도 갱신되지 않음(트랜잭션
   내 마지막에 호출되므로).
6. **fixture 합성 데이터 확인**: `alio-detail-response-valid.json`의
   회사/파일명이 "합성 채용공고.pdf", "합성-원문", `recrutPbancTtl:"정보기술"`
   등 명백한 placeholder이고 URL도 `https://example.invalid/...`. 실제
   서비스키/실제 응답 원문 흔적 없음.
7. **Secret 없음**: 확인됨(위 참고).
8. **Metric 이름 일치**: `careerops.collector.detail.run{result}`,
   `.steps`, `.files`, `.duration` 4종 모두 명세와 정확히 일치.
9. **`rsnOcrnYmd` 원본 문자열 그대로 저장**: `AlioStepItem.rsnOcrnYmd()`가
   `String`이고, `RecruitmentStep` 생성자에 그대로 전달(`AlioDetailEnrichmentService.java`
   L75)됨 — 날짜 파싱 로직 없음.
10. **`atchFileType` 원문 그대로 저장**: `Attachment.fileType`이 `String`이고
    매핑 테이블/enum 없이 `AlioFileItem.atchFileType()` 값을 그대로 저장.

## 테스트 결과

- `./gradlew test --rerun` (사전조건: `docker compose up -d`로 PostgreSQL/Redis
  기동 확인 후 실행) → BUILD SUCCESSFUL, JUnit XML 집계 기준
  test_count=51, test_pass_count=51, failures=0, errors=0, skipped=0.
  (사용자가 사전에 로컬에서 확인한 51/51과 일치, 재확인 완료.)

## Findings

- 위반 사항 없음. 과도한 추상화나 불필요한 패턴 없음(`enrichIfNeeded`
  헬퍼 하나로 3개 분기의 중복 코드 최소화, DTO는 record로 단순하게 구성).
- 신규 production/test dependency 없음(`build.gradle` diff 없음) — 명세와
  일치.
- 자기소개서/근거 기반 검증 원칙과는 직접 관련 없는 Task(채용정보 수집).
  사용자가 제공하지 않은 경험/수치를 생성하는 로직 없음.
- Secret 커밋 없음.
- 사소한 관찰(블로킹 아님): `AlioJobDetailResponse`/`AlioJobListResponse`
  모두 `resultCode`를 `String`으로 선언했는데, ALIO 상세 API 실제 관측
  결과(Context 섹션)는 `resultCode`가 JSON 정수(`200`, 따옴표 없음)로
  온다. fixture도 이를 반영해 숫자 리터럴로 작성했고(`alio-detail-response-valid.json`
  L15: `"resultCode": 200`), Jackson이 숫자→문자열 강제변환을 지원해
  파싱 테스트(`AlioJobDetailResponseParsingTest`)가 실제로 통과하는 것을
  확인했다. 동작에는 문제가 없으나, 향후 Jackson 설정 변경(strict 모드 등)
  시 깨질 수 있는 잠재적 취약점이라는 점만 기록해 둔다. Acceptance
  Criteria에 명시된 요구사항은 아니므로 이번 라운드의 PASS 판정에는 영향
  없음 — 후속 조치가 필요하면 별도로 판단.

## 다음 액션

- PASS: 완료 처리. `.ai/metrics/metrics.jsonl`에 최종 상태 기록 필요
  (Claude가 Task 상태를 `done`으로 갱신).
- 남은 `[수동]` 항목 2건(실제 키로 1회 이상 검증, Prometheus 노출 확인)은
  사용자가 직접 수행 필요.
