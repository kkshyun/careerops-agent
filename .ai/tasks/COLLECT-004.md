---
task_id: COLLECT-004
title: ALIO 상세조회(/detail.do) 연동 — 채용전형단계(steps)/첨부파일(files) 보강
phase: review
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-16T00:00:00+09:00
codex_thread_id: 01a0097d-bbbc-71f3-b889-99e44c3e1884
---

## Context

COLLECT-001부터 반복적으로 Out of Scope 처리됐던 부분이다 — ALIO 목록
API(`POST /list.do`)의 `steps`(전형단계)/`files`(첨부파일)는 항상 빈
배열이고, 상세조회(`/detail.do`)를 호출해야만 채워진다는 것은 COLLECT-001
때부터 알고 있었지만 실제 상세 API 계약(파라미터명, 응답 구조)은 그때
검증하지 못했다(COLLECT-001 "정정된 API 사양" 섹션의 `/detail` 관련 내용은
잘못 선택했던 data.go.kr 스펙에서 온 것이었다 — `GET /detail`, 파라미터
`sn`이라는 설명은 이번 Task 착수 전 재검증 결과 **우연히 파라미터명(`sn`)만
맞고 HTTP method(GET→실제는 POST, 405)와 나머지는 검증되지 않은 추측이었음이
확인됨).

이번 Task 착수 전, 실제 서비스키로 `POST https://opendata.alio.go.kr/new/v1/recruit/detail.do`를
직접 호출해(list.do로 얻은 실제 `recrutPblntSn` 4건 이상) 요청/응답 구조를
검증했다(2026-08-16, fixture 아님 — 아래 "조사 결과"가 그 근거).

## 조사 결과 — 상세조회 API (실제 호출로 검증)

**Endpoint/요청**: `POST {list.do와 동일한 base-url}/detail.do`,
query parameter `sn`(=목록의 `recrutPblntSn`), `serviceKey`, `resultType=json`
+ list.do와 동일한 헤더(`swaggerType: Y`, `Content-Type/Accept: application/json`)
+ 빈 body(`{}`) 필요. GET은 `405`. 파라미터명을 `recrutPblntSn`/`pblntSn` 등
다른 이름으로 바꾸면 `resultCode="3"`(`NODATA_ERROR`)가 나는 것을 직접
재현해 `sn`이 정확한 이름임을 확인했다.

**응답 envelope**: `{ "result": { ... 단일 object(배열 아님) ... },
"resultCode": 200, "resultMsg": "성공했습니다." }`. 목록과 달리 `result`가
배열이 아니다. 존재하지 않는 `sn`/파라미터 오류 시 HTTP 200 +
`resultCode="3"`(`NODATA_ERROR`) — HTTP status만으로 성공 판단 불가(list.do와
동일 패턴).

**`steps`(전형단계) 실제 필드** (4개 실제 공고, 최대 17개 그룹까지 관측):

```json
{"sortNo":0,"cmpttRt":null,"aplyNope":null,"minStepSn":1255842,
 "recrutNope":null,"rsnOcrnYmd":null,"recrutPblntSn":303953,
 "maxStepSn":1255843,"recrutStepSn":1255842,"recrutPbancTtl":"보건직(임상병리사)"}
```

- `recrutStepSn`: 관측 범위 내 전역 고유. natural key로 사용.
- `recrutPbancTtl`: **"서류전형"/"면접전형" 같은 전형 단계명이 아니라
  채용분야/직군명**이 각 단계 레코드에 반복되는 값이다(예:
  "보건직(임상병리사)", "행정직(C1)"). 사람이 읽는 "전형 단계 이름" 필드는
  응답에 없음 — 추측해서 만들지 않는다.
- `sortNo`: 같은 채용분야 그룹 내 단계들이 공유하는 그룹 순번.
- `cmpttRt`(경쟁률)/`aplyNope`(지원자수)/`recrutNope`/`rsnOcrnYmd`: 관측된
  4건 전부 대부분 `null`(진행 중 공고라 미집계로 추정).
  **`rsnOcrnYmd`는 non-null 값을 한 번도 관측하지 못해 날짜 포맷을 추측하지
  않고 원본 문자열 그대로(파싱 없이) 저장한다.**
- `minStepSn`/`maxStepSn`: 같은 그룹 내 단계 sn 범위.

**`files`(첨부파일) 실제 필드**:

```json
{"sortNo":3,"recrutAtchFileNo":3069638,
 "atchFileNm":"붙임. 채용공고문(...).pdf","atchFileType":"A",
 "url":"https://opendata.alio.go.kr/recruit/downloadAtchFile?recrutAtchFileNo=3069638"}
```

- `recrutAtchFileNo`: 전역 고유 정수, natural key.
- `atchFileType`: `A`/`B`/`C`/`Z` 등 코드값만 온다. 사람이 읽는 의미(채용공고문/
  입사지원서/직무기술서 등으로 보이나 API 응답에 정의가 없어 확정 아님) —
  **코드 원문 그대로 저장, 의미 매핑은 만들지 않는다.**
- `url`: 다운로드 가능한 실제 URL. 파일 자체를 다운로드/재호스팅하지 않고
  URL만 저장한다.

**목록 API와의 연결 키**: `recrutPblntSn`(목록) = `sn`(상세 요청 파라미터) =
`result.recrutPblntSn`(상세 응답). `JobPosting.externalId`가 이미 이 값을
문자열로 저장하고 있으므로 그대로 재사용 가능.

**호출 제한**: 응답 헤더에 rate-limit 정보 없음. 문서화된 별도 제한을
발견하지 못했다(COLLECT-001의 "1,000건/일"은 처음에 잘못 선택했던
data.go.kr 쪽 정보이며 현재 사용 중인 `opendata.alio.go.kr`에는 적용
여부를 확인할 수 없다 — 운영 리스크로만 남긴다).

## 사용자 승인된 설계 결정

1. **실행 시점**: `AlioCollectorService.collect()` 내부에서 목록 처리 루프
   중 신규 저장/status 갱신/skip 모든 분기 이후, 그 `JobPosting`이 아직
   상세 보강이 안 됐다면(`detailFetchedAt == null`) 그 자리에서 즉시
   `AlioDetailEnrichmentService.enrich(jobPosting)`를 호출한다(inline,
   별도 Scheduler 없음). `numOfRows`(기본 50)만큼의 목록 조회 range 안에서
   자연히 재발견되는 기존 공고도 같은 방식으로 보강되며, 이미 보강된
   공고는 다시 호출하지 않는다.
2. **소급 백필 없음**: dev DB에 이미 쌓인 COLLECT-001~003 데이터 전체를
   순회하며 상세를 채우는 별도 스크립트/API는 만들지 않는다. 목록 수집
   과정에서 자연히 재발견되는 것(위 1번)만으로 점진적으로 채워진다 — 이미
   ALIO 목록 페이지 1(`numOfRows`개)에서 벗어난 과거 공고는 이번 Phase에서
   보강되지 않을 수 있고, 이는 의도된 제약이다.
3. **API 응답 미노출**: `JobPostingResponse`/`GET /api/jobs`는 이번 Task로
   변경하지 않는다. `RecruitmentStep`/`Attachment`는 DB에는 저장되지만
   조회 API로는 아직 노출되지 않는다 — 노출은 별도 Task.

### 보강 완료 여부 판단 방법

`JobPosting`에 `detailFetchedAt`(Instant, nullable) 필드를 추가한다.
- `null` = 아직 상세 보강 안 됨(신규 생성 시 기본값) 또는 마지막 시도가
  실패함(재시도 대상으로 남김).
- non-null = 상세조회 성공(steps/files 저장 완료) 시각.

실패 시 이 필드를 갱신하지 않는 이유: 재시도를 명시적으로 추적하는 복잡한
상태 머신(예: 실패 횟수, backoff)을 만들지 않기 위함이다. 같은 공고가
계속 목록 page 1 범위 안에 있는 동안(대개 마감 전까지) 6시간마다 최대
1회씩만 재시도되므로 호출량이 무한정 늘지 않는다 — 공고가 page 1 밖으로
밀려나면 그 시점부터는 자연히 재시도도 멈춘다(위 "소급 백필 없음"과 동일한
성격의 제약).

## Scope

1. **`JobPosting`**: `detailFetchedAt`(Instant, nullable) 필드 + getter +
   `markDetailFetched(Instant)` mutator 추가(기존 `updateStatus`와 동일한
   "이 필드 하나만 여는" 원칙).
2. **`JobPostingService.create()` 반환 타입 변경**: `JobPostingResponse` →
   `JobPosting`(엔티티). 상세 보강을 위해 저장 직후 엔티티(id 포함)가
   필요하기 때문. 호출부 수정:
   - `AlioCollectorService`: 반환된 `JobPosting`을 그대로 상세 보강 호출에
     사용.
   - `ManualImportService`: `JobPostingResponse saved = jobPostingService.create(...)`
     →  `JobPosting saved = jobPostingService.create(...)` 후
     `JobPostingResponse.from(saved)`로 감싸서 기존과 동일한
     `ManualJobImportResult` 반환.
3. **신규 Entity/Repository** (`com.careerops.backend.job` 패키지, 기존
   `JobPosting`과 같은 위치 — ALIO 전용이 아니라 JobPosting 도메인의
   일부로 취급):
   - `RecruitmentStep`: `id`, `jobPosting`(`@ManyToOne`), `recrutStepSn`(Long,
     unique), `sortNo`(Integer), `minStepSn`(Long), `maxStepSn`(Long),
     `stepGroupName`(String, ← `recrutPbancTtl` — "전형단계명"이 아니라
     "채용분야 그룹명"임을 필드명으로 반영), `competitionRate`(Double, ←
     `cmpttRt`), `applicantCount`(Integer, ← `aplyNope`), `recruitCount`
     (Integer, ← `recrutNope`), `occurredAtRaw`(String, ← `rsnOcrnYmd`,
     파싱 없이 원본 그대로), `createdAt`(`@CreationTimestamp`).
   - `Attachment`: `id`, `jobPosting`(`@ManyToOne`), `recrutAtchFileNo`(Long,
     unique), `sortNo`(Integer), `fileName`(String, ← `atchFileNm`),
     `fileType`(String, ← `atchFileType`, 코드 원문), `url`(String),
     `createdAt`(`@CreationTimestamp`).
   - `RecruitmentStepRepository`/`AttachmentRepository`: `existsByRecrutStepSn`/
     `existsByRecrutAtchFileNo`(저장 전 존재 확인용), `findByJobPostingId`
     (향후 조회 API 확장 대비, 이번 Task에서 Controller 노출은 안 함).
   - `JobPosting` 엔티티 자체에는 `@OneToMany` 컬렉션을 추가하지 않는다
     (자식이 FK로 부모만 참조하는 단방향 관계 — 불필요한 얽힘 회피).
4. **Flyway `V3__add_job_detail_enrichment.sql`**: `job_postings`에
   `detail_fetched_at TIMESTAMP` 컬럼 추가 + `recruitment_steps`/
   `attachments` 테이블 신설(`recrut_step_sn`/`recrut_atch_file_no` UNIQUE
   제약 포함 — 멱등성 DB 레벨 안전장치) + `job_posting_id` FK 인덱스.
5. **`AlioJobClient`에 `AlioJobDetailResponse fetchDetail(long sn)` 추가**
   (인터페이스 확장). `RestClientAlioJobClient`에 운영 구현 추가 — 위
   "조사 결과"의 실제 요청 형태(POST, query param `sn`, 헤더/body 조합)를
   그대로 반영, `fetchList`와 동일한 예외 매핑 패턴(`!"200".equals(resultCode)`
   → `AlioApiException.Reason.FETCH_ERROR`) 재사용.
6. **신규 DTO** (`collector/alio/`): `AlioJobDetailResponse`(record:
   `AlioJobDetailItem result, String resultCode, String resultMsg`),
   `AlioJobDetailItem`(record: `Long recrutPblntSn, List<AlioStepItem> steps,
   List<AlioFileItem> files` — 실제 응답에 있는 다른 필드는 목록과 중복이라
   매핑하지 않음, `@JsonIgnoreProperties(ignoreUnknown = true)` 필수),
   `AlioStepItem`(record: `sortNo, recrutStepSn, minStepSn, maxStepSn,
   recrutPbancTtl, cmpttRt, aplyNope, recrutNope, rsnOcrnYmd`),
   `AlioFileItem`(record: `sortNo, recrutAtchFileNo, atchFileNm, atchFileType,
   url`).
7. **`AlioDetailEnrichmentService`** 신규(`collector/alio/`):
   `enrich(JobPosting jobPosting)` — `externalId`를 `long`으로 파싱(실패하면
   조용히 리턴, ALIO는 항상 숫자 문자열이므로 방어적 처리일 뿐) →
   `client.fetchDetail(sn)` 호출 → `steps`/`files` 각각 자연키
   존재 확인(`existsByRecrutStepSn`/`existsByRecrutAtchFileNo`) 후 없는
   것만 저장 → 성공 시 `jobPostingService.markDetailFetched(jobPosting,
   Instant.now())` → 실패(`AlioApiException` 또는 예상 못한
   `RuntimeException`)는 WARN 로그 + metric만 남기고 흡수(밖으로 던지지
   않음, `detailFetchedAt`은 그대로 `null` 유지).
8. **`AlioCollectorService` 최소 수정**: 기존 3개 분기(신규 저장/status
   갱신/skip) 전부에서, 처리 대상 `JobPosting` 참조를 얻은 뒤
   `if (jobPosting.getDetailFetchedAt() == null) { detailEnrichmentService.enrich(jobPosting); }`
   1줄을 추가한다. 기존 fetch/매핑/검증/dedup 로직 자체는 변경하지 않는다.
9. **Product Metric 4종 신설** (`careerops.collector.detail.*` 네임스페이스,
   기존 `careerops.collector.*`/`careerops.scheduler.alio.*`와 분리):
   - `careerops_collector_detail_run_total`(Counter, tag `result`=
     `success`|`failed`) — 상세조회 시도 자체의 성공/실패.
   - `careerops_collector_detail_steps_total`(Counter, 태그 없음) — 신규
     저장된 `RecruitmentStep` 건수 누적.
   - `careerops_collector_detail_files_total`(Counter, 태그 없음) — 신규
     저장된 `Attachment` 건수 누적.
   - `careerops_collector_detail_duration_seconds`(Timer) — 1회 상세조회
     (fetch+저장) 소요 시간.
10. fixture 기반 자동 테스트(아래 Test Plan).

## Out of Scope

- 기관유형/기관분류 임의 매핑, 사람인 API, cross-source dedup, 지원현황
  관리, PKB, 공고-사용자 매칭, 자기소개서 Agent, 알림, 프론트엔드.
- 다중 인스턴스 Scheduler, 분산 락, 복잡한 변경 이력 시스템.
- **dev DB 전체 소급 백필**(위 "사용자 승인된 설계 결정" 2번) — 별도
  스크립트/API 없음.
- **`GET /api/jobs` 응답에 steps/attachments 노출**(위 3번) — `Controller`/
  `JobPostingResponse` 변경 없음. 필요해지면 별도 Task.
- **steps/files 갱신(re-sync)** — `detailFetchedAt`이 한 번 설정되면 그
  공고는 다시 상세조회하지 않는다. 전형 일정/첨부파일이 저장 이후 실제로
  바뀌어도 이번 Phase는 반영하지 않는다(Context의 "갱신 전략" 항목에서
  사용자가 "복잡한 변경 이력까지는 만들지 말라"고 명시한 것과 일치 —
  최초 1회 동기화가 이번 Phase의 최소 전략이다).
- `atchFileType` 코드값의 의미(A/B/C/Z가 실제로 무엇을 뜻하는지) 매핑 —
  응답에 정의가 없어 확정할 수 없으므로 원문 코드만 저장.
- `rsnOcrnYmd` 날짜 파싱 — 관측된 실제 값이 없어 포맷을 추측하지 않는다
  (원본 문자열 저장).
- 상세조회 호출량 제한/backoff(별도 rate limiter, 재시도 프레임워크) —
  문서화된 제한을 찾지 못했고, 위 "실패 시 재시도" 설계 자체가 자연히
  호출량을 bound시킨다.

## Acceptance Criteria

`[자동]` = fixture만으로 검증, 실제 ALIO API 미호출. 저장소 루트에서
`docker compose up -d`(PostgreSQL)가 기동 중이어야 한다.

- [ ] `[자동]` **마이그레이션/기동**: `V3__add_job_detail_enrichment.sql`
      적용 후 정상 기동(`ddl-auto=validate` 통과). `job_postings.detail_fetched_at`,
      `recruitment_steps`, `attachments` 테이블/컬럼 존재 확인.
- [ ] `[자동]` **신규 저장 시 상세 보강**: fixture 목록(신규 item 1건) +
      fixture 상세(steps 2건, files 3건)로 `collect()` 호출 시, 새로 저장된
      `JobPosting`에 대해 `fetchDetail`이 정확히 1회 호출되고, `RecruitmentStep`
      2건/`Attachment` 3건이 저장되며 각 필드가 fixture 값과 일치하고,
      `JobPosting.detailFetchedAt`이 non-null이 된다.
- [ ] `[자동]` **이미 보강된 공고 재호출 안 함**: 위 시나리오 이후 같은
      `JobPosting`이 다시(예: status 불변으로 skip되는 케이스) 목록에 나타나
      재수집되면 `fetchDetail`이 추가로 호출되지 않고(fixture client
      호출 횟수로 검증) `recruitment_steps`/`attachments` 건수가 늘지 않는다.
- [ ] `[자동]` **status 갱신 케이스에서도 미보강 공고는 보강됨**: 기존
      `JobPosting`(`detailFetchedAt=null`)의 status가 바뀌어 `updated`로
      집계되는 케이스에서도 같은 실행 안에서 상세 보강이 함께 수행된다.
- [ ] `[자동]` **상세조회 실패 격리**: 여러 건(정상 detail 응답 1건 + 상세
      조회 시 예외를 던지는 1건)을 한 번의 `collect()`에서 처리할 때, 실패한
      건은 `steps`/`attachments`/`detailFetchedAt`이 남지 않지만 나머지
      정상 건은 정상 저장되고, 두 `JobPosting` 레코드 자체(목록 매핑 결과)는
      상세조회 성패와 무관하게 모두 정상 저장된다. `collect()` 호출 자체는
      예외 없이 정상 종료한다(전체 목록 수집이 실패로 전파되지 않음).
- [ ] `[자동]` **멱등성(중복 방지)**: 같은 `JobPosting`에 대해 `AlioDetailEnrichmentService.enrich()`를
      직접 두 번 연속 호출해도(같은 fixture 응답) `recruitment_steps`/
      `attachments` 행 수가 두 번째 호출로 늘지 않는다(자연키 존재 확인 +
      DB unique 제약 이중 검증).
- [ ] `[자동]` **Product Metric 계측**: 위 시나리오들 실행 후
      `careerops.collector.detail.run`(태그 `result=success|failed`),
      `careerops.collector.detail.steps`, `careerops.collector.detail.files`
      카운터 값이 기대한 만큼 증가했음을 `MeterRegistry`로 확인한다.
- [ ] `[자동]` **`JobPostingService.create()` 반환 타입 변경 회귀 없음**:
      `ManualImportService`를 통한 수동 등록(`POST /api/import/jobs/manual`)이
      기존과 동일하게 동작하고 응답 형태가 바뀌지 않는다.
- [ ] `[자동]` **기존 JobPosting 목록/조회 회귀 없음**: `GET /api/jobs`,
      `GET /api/jobs/{id}` 응답 스키마/필터/pagination이 이번 Task로 변경되지
      않는다(steps/attachments 필드 추가 없음).
- [ ] `[자동]` **회귀 없음**: `cd backend && ./gradlew test`가 이번 Task 신규
      테스트 포함 전체 실패 0건으로 통과한다(기존 45건 포함).
- [ ] `[자동]` **Git tracked file에 secret 없음**: 신규 dependency 없음, 실제
      키 값/실제 API 응답 원본이 어떤 커밋 파일에도 없다(fixture는 확인된
      스키마 기준 합성 데이터).
- [ ] `[수동]` **실제 키로 1회 이상 검증**: `JOB_ALIO_API_KEY` 설정 후 앱
      기동, 자동/수동 수집 1회 실행해 실제 `RecruitmentStep`/`Attachment`가
      DB에 합리적으로 저장되는지, `already-enriched` 공고가 재수집 시
      상세조회를 다시 호출하지 않는지 확인한다.
- [ ] `[수동]` **Prometheus 노출 확인**: `curl -s
      http://localhost:8080/actuator/prometheus`에서
      `careerops_collector_detail_run_total`,
      `careerops_collector_detail_steps_total`,
      `careerops_collector_detail_files_total`,
      `careerops_collector_detail_duration_seconds`가 노출되는지 확인한다.

## Technical Notes

### 패키지/파일 변경 범위

```
backend/src/main/java/com/careerops/backend/job/
├── JobPosting.java                    # detailFetchedAt 필드+getter+markDetailFetched 추가
├── JobPostingService.java             # create() 반환타입 JobPostingResponse → JobPosting
├── RecruitmentStep.java               # 신규 @Entity
├── RecruitmentStepRepository.java     # 신규
├── Attachment.java                    # 신규 @Entity
└── AttachmentRepository.java          # 신규

backend/src/main/java/com/careerops/backend/manualimport/
└── ManualImportService.java           # create() 반환타입 변경에 맞춰 JobPostingResponse.from(saved) 호출로 수정

backend/src/main/java/com/careerops/backend/collector/alio/
├── AlioJobClient.java                 # fetchDetail(long sn) 추가
├── RestClientAlioJobClient.java       # fetchDetail 구현
├── AlioJobDetailResponse.java         # 신규 record
├── AlioJobDetailItem.java             # 신규 record
├── AlioStepItem.java                  # 신규 record
├── AlioFileItem.java                  # 신규 record
├── AlioDetailEnrichmentService.java   # 신규
└── AlioCollectorService.java          # 3개 분기에 enrich 호출 1줄씩 추가(기존 fetch/매핑/dedup 로직 무변경)

backend/src/main/resources/db/migration/
└── V3__add_job_detail_enrichment.sql  # 신규

backend/src/test/resources/fixtures/alio/
├── alio-detail-response-valid.json          # steps 2건/files 3건 (합성)
├── alio-detail-response-empty.json          # steps/files 빈 배열, resultCode 200
└── (필요 시 실패용 fixture 추가)

backend/src/test/java/com/careerops/backend/collector/
├── FixtureAlioJobClient.java          # fetchDetail 스텁 추가(sn별 응답/예외 매핑 + 호출 sn 기록)
├── AlioJobDetailResponseParsingTest.java  # 신규 — DTO 파싱 단위 테스트
├── AlioDetailEnrichmentServiceTest.java   # 신규 — enrich() 단위/통합 테스트(성공/실패/멱등성)
└── AlioCollectorServiceTest.java      # 상세 보강 연계 시나리오 추가
```

`CollectController.java`, `CollectResult.java`, `AlioJobMapper.java`,
`AlioJobItem.java`, `AlioCollectionScheduler.java`, `JobPostingController.java`,
`JobPostingRepository.java`(검색 쿼리), `JobPostingResponse.java`는 이번
Task로 변경하지 않는다.

### `FixtureAlioJobClient` 확장 방향

`fetchList`는 기존 그대로 두고, `fetchDetail(long sn)`을 위해 `Map<Long,
AlioJobDetailResponse> detailResponses`, `Map<Long, AlioApiException>
detailFailures`, `List<Long> capturedDetailSns`(호출 sn 기록, "재호출 안
함" 검증용)를 추가한다. 등록되지 않은 `sn`으로 호출되면 테스트가 명확히
실패하도록(예: `IllegalStateException`) 처리해 fixture 설정 누락을 바로
드러낸다.

### `AlioCollectorService` 수정 방식(최소 변경 원칙)

기존 3개 분기 각각에서 이미 `JobPosting` 참조(신규 저장 시
`jobPostingService.create()`의 반환값, 기존 공고 시 `existing.get()`)를
얻고 있으므로, 그 참조에 대해 아래 1줄만 추가한다(신규 helper 메서드 하나로
묶어도 무방):

```java
if (jobPosting.getDetailFetchedAt() == null) {
    detailEnrichmentService.enrich(jobPosting);
}
```

skip 분기(`status`도 동일해 아무것도 안 하던 경우)에도 동일하게 적용 —
이 분기가 "재발견"에 해당하므로 사용자가 승인한 "재발견 시 보강" 요구를
충족한다.

### 왜 `JobPostingService.create()`의 반환 타입을 바꾸는가

상세 보강에는 저장된 엔티티(특히 `id`, FK 연결용)가 필요한데 기존
`create()`는 `JobPostingResponse`(DTO)만 반환해 엔티티를 다시 조회해야
했다. 불필요한 추가 쿼리 대신 반환 타입 자체를 엔티티로 바꾸고, DTO 변환
책임을 호출부(`ManualImportService`)로 옮긴다. `JobPostingController`는
`create()`를 직접 호출하지 않으므로(현재 `POST /api/jobs` endpoint 자체가
없음 — 저장은 collector/manualimport 경로로만 발생) 영향 없음.

### 멱등성 이중 안전장치

애플리케이션 레벨(`existsByRecrutStepSn`/`existsByRecrutAtchFileNo` 저장
전 확인)과 DB 레벨(UNIQUE 제약) 둘 다 둔다 — COLLECT-001의 "애플리케이션
레벨 skip + DB 제약은 별도 고려" 원칙과 달리 이번엔 자연키의 안정성이 이미
스펙(상세조회 lookup key)으로 증명돼 있으므로 DB 제약까지 함께 거는 것이
안전하다고 판단했다(다른 소스가 같은 자연키 값을 재사용할 위험이 없는
독립적인 정수 시퀀스이기 때문).

### Dependency

신규 production/test dependency 없음.

### Codex 위임 범위

이 Task 명세 전체(Entity/Repository/DTO/Service/Controller 미변경 확인/
Migration/fixture/테스트)를 `codex-implement` Skill로 위임한다. Codex는
전체 코드를 대화로 출력하지 않고 저장소 파일을 직접 수정하며, 변경 파일/
핵심 구현/테스트 결과/남은 이슈/Claude 검토 필요 사항만 요약 보고한다.
프레임워크 버전 관련 blocker(Boot 4.1 모듈 재구성 등, `docs/ARCHITECTURE.md`
"Spring Boot 4.1 알려진 모듈 재구성 이슈" 참고)를 만나면 추측하지 말고
즉시 보고한다.

## Test Plan

- `[자동]` `AlioJobDetailResponseParsingTest` — 고정 JSON(fixture)을
  `AlioJobDetailResponse`로 역직렬화, `result`가 단일 object, `steps`/`files`
  중첩 구조/필드 값 검증.
- `[자동]` `AlioDetailEnrichmentServiceTest` — `enrich()` 성공(steps/files
  저장 + `detailFetchedAt` 설정), 실패(예외 흡수, `detailFetchedAt` 그대로
  null), 멱등성(같은 입력 2회 호출해도 중복 저장 없음) 시나리오.
- `[자동]` `AlioCollectorServiceTest` — 신규 저장+보강, 재발견+skip 시
  재호출 안 함, status 갱신+미보강 공고 보강, 부분 실패 격리 시나리오
  추가(기존 4개 테스트에 이어서, 기존 테스트는 그대로 통과해야 함).
- `[자동]` `ManualImportServiceTest`(있다면) / `ManualImportControllerTest` —
  `create()` 반환 타입 변경 후에도 기존 동작 회귀 없음.
- `[자동]` `cd backend && ./gradlew test` 전체 통과. 사전조건: 저장소
  루트에서 `docker compose up -d`.
- `[수동]` 실제 키로 `collect()` 1회 실행, DB에 저장된 `RecruitmentStep`/
  `Attachment` 값과 `detail_fetched_at` 확인 + `/actuator/prometheus`에서
  신규 metric 4종 확인.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | COLLECT-004 명세 기반 최초 구현 지시(상세 DTO 4개, `AlioDetailEnrichmentService`, `RecruitmentStep`/`Attachment` Entity/Repository, V3 마이그레이션, `JobPostingService.create()` 반환타입 변경 + 호출부 수정, fixture/테스트, 신규 dependency 없음 전제) | 전 파일 구현 완료, fixture/테스트 보강까지 마쳤으나 sandbox 제약(`~/.gradle/wrapper/dists/.../*.lck` 쓰기 거부)으로 `./gradlew test`를 스스로 실행하지 못하고 결과 미확인 상태로 보고. Claude가 로컬에서 직접 실행 → **컴파일 실패**(`JobPostingController.create()`가 여전히 `JobPostingResponse`를 기대). 원인은 Task 명세 자체의 오류 — "`JobPostingController.java`는 변경하지 않는다"고 잘못 기술했었다(`POST /api/jobs`가 실제로 존재하는데 없다고 잘못 가정, grep 없이 단정한 것이 원인) |
| 2 | 위 명세 오류를 인정하고, `JobPostingController.create()` 1줄만 `JobPostingResponse.from(saved)`로 감싸도록 정확히 지목해 수정 요청(다른 파일 변경 금지 명시) | 요청한 1줄만 정확히 수정. 이번에도 동일한 sandbox 제약으로 테스트 결과 미확인 보고. Claude가 로컬(`docker compose up -d` 기동 확인 후) `./gradlew test` 실행 → **51/51 전체 통과**(기존 45건 + 신규 6건 클래스 추가분). `reviewer` subagent가 Acceptance Criteria 11개 전항목 충족, 불변 파일 목록 무변경(`JobPostingController.java`는 지시한 1줄만), 실행시점/멱등성/실패격리/metric 이름/fixture 합성 여부/`rsnOcrnYmd`·`atchFileType` 원문 저장 여부까지 전부 확인 → PASS(`.ai/reviews/COLLECT-004-review-1.md`). 사소한 관찰(블로킹 아님): `AlioJobDetailResponse.resultCode`가 `String`인데 실제 응답은 JSON 정수로 옴 — Jackson coercion으로 현재는 문제없음(COLLECT-001의 `AlioJobListResponse.resultCode`와 동일한 기존 패턴) |
