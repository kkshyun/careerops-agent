---
task_id: IMPORT-001
title: Manual Job Import — 사용자가 발견한 외부 채용공고 URL을 CareerOps ingestion source로 등록
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-13T23:10:00+09:00
codex_thread_id: 019ffb29-e69d-7122-ae59-3f58f17f98c0
---

## Context

JOB-001로 `JobPosting`의 범용 저장/조회(`POST /api/jobs`, `GET /api/jobs/{id}`)가,
COLLECT-001로 첫 실제 자동 Source(ALIO, `POST /api/collect/alio`)가 만들어졌다.
그러나 사용자가 잡코리아·자소설닷컴·기업 공식 채용페이지 등 **CareerOps
자동 Source에 없는 곳에서 직접 발견한 공고**를 CareerOps에 넣을 방법이 아직
없다. `POST /api/jobs`가 이미 존재하지만, 이 endpoint는 범용 생성
API라 `source`를 포함한 모든 필드를 클라이언트가 임의로 지정할 수 있고
(`JobPostingCreateRequest.source`는 현재 `@NotBlank`일 뿐 서버가 강제하는
고정값이 없다), URL 기준 중복 방지도 없다. 이번 Task(IMPORT-001)는
"외부에서 발견한 공고를 CareerOps ingestion source로 등록"한다는 명확히 다른
책임을 가진 별도 API를 만든다.

`docs/PROJECT.md` 목표 1(채용공고 자동 수집)의 보완 경로다 — 자동 수집이
아직 커버하지 못하는 공고를 사람이 수동으로 채워 넣는 것이 목적이며, 향후
"URL만 주면 서버가 내용을 자동으로 추출"하는 기능으로 확장될 수 있는 진입점을
만든다(이번 Task 범위 아님).

**이번 Phase에서는 서버가 사용자가 입력한 `sourceUrl`에 절대 접속하지
않는다** — HTTP GET/크롤링/JS 렌더링 없이, 사용자가 입력한 정보를 그대로
저장한다.

## Scope

1. `POST /api/import/jobs/manual` — 사용자가 URL + 최소 정보를 입력해
   `JobPosting`을 생성하는 REST endpoint 1개.
2. `source`는 클라이언트가 지정할 수 없고 서버가 항상 `"MANUAL"`로 강제한다.
3. `sourceUrl` 형식 검증(문법적으로 유효한 URL, `http`/`https` scheme만
   허용) — **접속은 하지 않는다.**
4. 동일 `source="MANUAL"` + 동일 `sourceUrl` 반복 등록 방지(애플리케이션
   레벨, DB 제약 없음 — COLLECT-001의 dedup 설계와 동일한 수준).
5. 중복 요청 시 예외를 던지지 않고, 이미 등록된 기존 `JobPosting`을 응답에
   담아 자연스럽게 알려주는 API semantics(아래 "Duplicate Response
   Semantics" 참고).
6. Manual Import 전용 Product Metric(`careerops_manual_import_total`,
   태그 `result=saved|duplicate`) — 기존 `careerops_job_creation_total`과의
   관계를 일관되게 유지.
7. 새 컬럼/Flyway migration 없이 기존 `job_postings` 스키마만 사용(아래
   "스키마 변경 불필요 확인" 참고).
8. 외부 인터넷 접근이 필요 없는 자동 테스트로 전체 시나리오 검증.

## Out of Scope

이번 Task에서 명시적으로 하지 않는다:

- **서버의 임의 URL 접속 일체**: 사용자가 입력한 `sourceUrl`에 대한 HTTP
  GET, HTML scraping, JS rendering(Playwright/Selenium 등), LLM 기반 페이지
  내용 추출, 잡코리아/자소설닷컴 전용 crawler, robots.txt 우회, 인증/로그인
  우회, Browser automation. 서버는 이번 Phase에서 `sourceUrl` 문자열을
  형식 검증만 하고 저장할 뿐, 그 URL로 어떤 네트워크 요청도 만들지 않는다.
- **SSRF 방어 계층**: allow-list, private/loopback/link-local IP 차단, DNS
  rebinding 방지, redirect 제한, response size 제한, timeout, content-type
  검증 등 — 서버가 URL에 접속하지 않으므로 이번 Phase에는 공격 표면 자체가
  없다. 이 판단은 소극적 누락이 아니라 `docs/DECISIONS.md` **ADR-0008**로
  근거를 남긴 아키텍처 결정이다.
- 사람인/고용24 등 추가 자동 Source API 구현 — 이번 Task는 자동 수집이
  아니라 사용자 수동 입력이다.
- Cross-source fuzzy dedup(제목 유사도, 회사명 정규화/표기 통일 등) — 이번
  Phase는 `source=MANUAL` + 동일 `sourceUrl` 완전 일치만 다룬다.
- DB unique constraint 추가 — 애플리케이션 레벨 dedup만 사용한다(아래
  "Dedup 구현 및 Known Limitation" 참고). COLLECT-001과 동일한 판단.
- Collector 공통 interface 추상화(`Collector`/`ImportSource` 등 전략 패턴) —
  Manual Import는 자동 Source(`collector` 패키지)와 트리거 방식·입력 형태가
  근본적으로 다르므로(배치 fetch가 아니라 단건 사용자 제출), 지금 억지로
  공통 인터페이스를 만들지 않는다. Source/Import 방식이 더 늘어나 실제
  공통점이 드러나면 그때 재검토한다.
- Frontend(Next.js), 카카오톡 알림, Scheduler, Fit Scoring/AI Matching,
  PKB, 자기소개서 파이프라인.
- `JobPosting`에 새 컬럼 추가, Flyway migration 신규 파일 — 스키마 변경
  없이 기존 필드만으로 충분함을 확인했다(아래 참고).
- `invalid`(검증 실패) 케이스의 Product Metric 계측 — 아래 "Product Metric
  정의"에서 제외 이유를 설명한다.
- 공통 예외 처리(`@ControllerAdvice`/`@RestControllerAdvice`) — JOB-001/
  COLLECT-001과 동일 원칙 유지. Bean Validation 실패는 Spring 기본 동작
  (`400 Bad Request`)에 맡긴다.

## Acceptance Criteria

`[자동]` = 외부 네트워크 접근 없이 자동 테스트로 검증 가능. `[수동]` = 사람이
직접 확인해야 함. 이번 Task는 "서버가 실제 URL에 접속하지 않는다"는 사실
자체가 핵심이므로, `[수동]` 항목도 실제 채용사이트에서 실제로 접속을
시도하는 것이 아니라 **URL 문자열만 입력하고 서버가 거기 접속하지 않는지**를
확인하는 수준으로 한정한다(scraping 없음). 자동 항목은 JOB-001/COLLECT-001과
동일하게 저장소 루트에서 `docker compose up -d`(PostgreSQL)가 기동 중이어야
한다.

- [ ] `[자동]` **생성 성공**: 유효한 `sourceUrl`(`https://...`),
      `companyName`, `title`을 포함한 요청으로 `POST /api/import/jobs/manual` →
      `201 Created`, 응답 `result == "saved"`, `job.source == "MANUAL"`,
      `job.externalId == null`, `job.sourceUrl`/`companyName`/`title`이 요청과
      일치, `job.id`/`job.createdAt`이 채워져 있다.
- [ ] `[자동]` **선택 필드 매핑**: `employmentType`/`jobCategory`/`location`/
      `applicationStartAt`/`applicationEndAt`을 포함해 요청하면 응답의
      해당 필드에 그대로 반영된다. 포함하지 않으면 `null`로 저장된다.
- [ ] `[자동]` **source 강제 확인**: `ManualJobImportRequest`에는 애초에
      `source` 컴포넌트가 없으므로, 정상 성공 케이스에서 저장된
      `JobPosting.source`가 항상 정확히 `"MANUAL"`이다(클라이언트가 다른 값을
      지정할 방법이 API 계약상 존재하지 않음 — 요청 JSON에 임의로 `"source"`
      키를 추가로 보냈을 때의 정확한 동작(무시 vs 400 거부)은 Jackson
      unknown-property 처리 설정에 따라 달라질 수 있어 이 Task의 Acceptance
      Criteria로 고정하지 않는다 — 어느 쪽이든 `source=MANUAL` 강제 계약
      자체는 깨지지 않는다).
- [ ] `[자동]` **URL 필수**: `sourceUrl`이 없거나 빈 문자열인 요청 →
      `400 Bad Request`, `JobPostingRepository.count()` 증가 없음.
- [ ] `[자동]` **URL 형식 검증**: scheme이 없는 문자열(예: `"not-a-url"`)을
      `sourceUrl`로 보내면 `400 Bad Request`.
- [ ] `[자동]` **금지 scheme 거부**: `sourceUrl`이 `"javascript:alert(1)"`,
      `"file:///etc/passwd"`, `"ftp://example.com/a"` 각각인 요청은 모두
      `400 Bad Request`이며 저장되지 않는다.
- [ ] `[자동]` **companyName/title 필수**: 각각 없거나 빈 문자열인 요청 →
      `400 Bad Request`, 저장되지 않는다.
- [ ] `[자동]` **Duplicate semantics**: 동일한 `sourceUrl`로 `POST
      /api/import/jobs/manual`을 2회 연속 호출하면, 1차 응답은
      `201`/`result == "saved"`이고 2차 응답은 `200 OK`/`result ==
      "duplicate"`이며 2차 응답의 `job.id`가 1차와 동일하다(기존 레코드를
      그대로 반환). 2차 호출 이후 `JobPostingRepository.count()`가 1차
      호출 이후와 동일하다(추가 저장 없음).
- [ ] `[자동]` **Product Metric — saved/duplicate**: 위 duplicate 시나리오
      실행 후 `MeterRegistry`에서 `careerops.manual.import`(태그
      `result=saved`)가 1 이상, `careerops.manual.import`(태그
      `result=duplicate`)가 1 이상 증가했음을 확인한다.
- [ ] `[자동]` **Product Metric — 기존 카운터와의 일관성**: 위 duplicate
      시나리오에서 `careerops.job.creation` 카운터는 정확히 1회만
      증가한다(1차 saved 호출에서만 증가, 2차 duplicate 호출에서는
      증가하지 않음).
- [ ] `[자동]` **기존 회귀 없음**: 기존 JOB-001(`JobPostingControllerTest`,
      `JobPostingRepositoryTest`) / COLLECT-001 관련 테스트가 그대로 통과한다.
      `cd backend && ./gradlew test` 전체 실패 0건.
- [ ] `[자동]` **Git tracked file에 secret 없음**: 새로 추가되는 파일에
      비밀 값이 하드코딩되지 않는다(관례 유지, 이번 Task는 secret을 다루지
      않으므로 변경 없을 것으로 예상).
- [ ] `[수동]` **URL 미접속 확인**: 실제 채용사이트에서 복사한 진짜 URL
      문자열(예: 잡코리아/자소설닷컴/특정 기업 채용페이지 URL) 1개 이상을
      `sourceUrl`로 입력해 `POST /api/import/jobs/manual`을 직접 호출한다.
      (1) 응답이 정상 저장되고 (2) `com.careerops.backend.manualimport`
      패키지 코드에 HTTP client(RestClient/RestTemplate/HttpClient 등)
      의존성이 전혀 없음을 코드로 재확인해, 서버가 그 URL로 실제 네트워크
      요청을 만들 방법 자체가 없음을 확인한다(mock 서버로 "호출 없음"을
      증명할 필요 없음 — 애초에 호출 코드가 존재하지 않는다는 사실이 증거).
- [ ] `[수동]` **Prometheus 노출 확인**: `curl -s
      http://localhost:8080/actuator/prometheus`에서
      `careerops_manual_import_total{result="saved",...}`,
      `careerops_manual_import_total{result="duplicate",...}` 라인이
      노출되는지 확인한다.

## Technical Notes

### 1. API 계약과 경로 선택 이유

**`POST /api/import/jobs/manual`** (사용자 제시 기본 후보를 그대로 채택).

기존 두 endpoint와의 관계를 명확히 구분한다:

| Endpoint | 책임 | `source` | 입력 형태 | dedup |
|---|---|---|---|---|
| `POST /api/jobs` (JOB-001, 유지) | 범용 `JobPosting` 생성 — 클라이언트가 `source` 포함 모든 필드 지정 | 클라이언트 지정(자유 문자열) | 단건, 전체 필드 노출 | 없음 |
| `POST /api/collect/{source}` (COLLECT-001, 유지) | 서버가 능동적으로 외부 API를 pull — 배치 fetch/save/skip/failed 집계 | 서버가 소스별로 고정(`"ALIO"`) | 트리거만(body 없음), N건 배치 | source+externalId 존재 확인 |
| `POST /api/import/jobs/manual` (신설) | 사용자가 발견한 공고를 CareerOps가 아는 ingestion source로 등록 — 사용자가 능동적으로 push | 서버가 항상 `"MANUAL"`로 고정 | 단건, URL 중심 최소 필드 | source=MANUAL+sourceUrl 완전 일치 확인 |

`/api/jobs`를 그대로 재사용하지 않는 이유: `JobPostingCreateRequest`는
`source`를 클라이언트가 지정하는 자유 문자열로 두고 있어(JOB-001 결정),
여기에 "source는 항상 MANUAL"이라는 제약을 얹으려면 결국 별도 DTO/검증/
dedup 로직이 필요해진다 — 즉 이름만 같을 뿐 실질적으로 다른 계약이 된다.
차라리 책임이 다른 API로 명시적으로 분리하는 것이 "URL 기준 dedup",
"source 강제", "향후 URL 자동 추출로 확장" 같은 Manual Import 고유의
관심사를 `/api/jobs`의 범용성을 해치지 않고 담을 수 있다.

`/api/collect/{source}`에 `source=manual`로 얹지 않는 이유: `collect`
계열은 "서버가 능동적으로 fetch한다"는 전제 위에 `CollectResult`(fetched/
saved/skipped/failed 정수 집계, N건 배치)를 응답으로 쓴다. Manual Import는
fetch 단계 자체가 없고(사용자가 이미 정보를 다 채워 보낸다) 정확히 1건만
다루므로, `CollectResult`의 배치 집계 형태를 억지로 재사용하면 의미상
어긋난다(예: `fetched`가 항상 1이라는 의미 없는 필드가 된다).

`/api/import/jobs/manual` 경로가 `/api/collect/{source}`와 대구를 이루는
지점: 둘 다 "일반 CRUD가 아니라 특정 ingestion 방식으로 JobPosting을
추가하는 액션"이라는 최상위 네임스페이스(`collect`/`import`)를 쓴다.
`collect`=자동(서버가 당김), `import`=수동(사용자가 밀어넣음)으로 대칭
구조를 이룬다. `/api/import/jobs` 하위에 `manual` 서브패스를 둔 것은,
향후 "URL만 주면 서버가 자동으로 정보를 추출하는" 기능(이번 범위 아님)이
생기면 `/api/import/jobs/url-extract` 같은 형제 endpoint를 같은 네임스페이스
아래 추가할 수 있게 여지를 남기기 위함이다.

### 2. 패키지/클래스 구조

새 패키지 `com.careerops.backend.manualimport`를 만든다(`job`/`collector`와
동일한 최상위 레벨의 feature-package). **`job` 패키지에 추가하지 않는
이유**: `job` 패키지는 지금까지 "범용 저장/조회"라는 단일 책임을 유지해왔고
(JOB-001 Out of Scope: source별 클래스를 만들지 않는다), Manual Import
고유의 DTO/검증(scheme 제한 URL)/dedup(source+sourceUrl)/metric을 얹으면
그 단일 책임이 깨진다. 대신 `collector` 패키지가 이미 확립한 패턴 —
"자체 패키지에서 `JobPostingCreateRequest`를 조립해 기존
`JobPostingService.create()`/`JobPostingRepository`를 재사용한다" — 를
그대로 따른다. `collector` 패키지 자체에 넣지 않는 이유는 `collector`가
"자동 Source pull" 개념에 강하게 결합돼 있어(`AlioCollectorService`,
`CollectResult` 등 배치 개념) 사용자 단건 제출과 섞으면 오히려 혼란스럽다.

```
backend/src/main/java/com/careerops/backend/manualimport/
├── ManualImportController.java     # @RestController, POST /api/import/jobs/manual
├── ManualImportService.java        # 단일 클래스(인터페이스 없음 — job/collector와 동일 원칙,
│                                    #   구체 구현체가 하나뿐이고 다중 구현/테스트 mocking 필요 없음)
└── dto/
    ├── ManualJobImportRequest.java   # record, Bean Validation
    └── ManualJobImportResult.java    # record: result("saved"|"duplicate"), job(JobPostingResponse)

backend/src/test/java/com/careerops/backend/manualimport/
└── ManualImportControllerTest.java   # @SpringBootTest + @AutoConfigureMockMvc + @Transactional
```

`job` 패키지에는 **딱 하나** 추가한다 — `JobPostingRepository`에 dedup용
쿼리 메서드 1개(아래 "Dedup 구현" 참고). 이건 기존 `existsBySourceAndExternalId`
패턴을 그대로 확장하는 것이라 `job` 패키지의 책임 범위를 벗어나지 않는다
(Repository는 원래 여러 소비자가 쓰는 공유 자산 — COLLECT-001도 같은 방식으로
`job` 패키지의 Repository에 메서드를 추가했었다).

`ManualImportService` 생성자는 COLLECT-001의 `AlioCollectorService`와
동일한 패턴으로 `JobPostingRepository`(dedup 조회용), `JobPostingService`
(실제 저장용 — 이 메서드를 반드시 거쳐야 `careerops.job.creation` 카운터가
자동으로 함께 증가한다), `MeterRegistry`(자체 metric)를 주입받는다.

### 3. Request/Response 스펙

**`ManualJobImportRequest`** (record):

```java
public record ManualJobImportRequest(
        @NotBlank @Size(max = 2048)
        @URL(regexp = "^https?://.+", flags = Pattern.Flag.CASE_INSENSITIVE)
        String sourceUrl,
        @NotBlank @Size(max = 255) String companyName,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String employmentType,
        @Size(max = 255) String jobCategory,
        @Size(max = 255) String location,
        LocalDate applicationStartAt,
        LocalDate applicationEndAt
) {
}
```

`id`/`source`/`externalId`/`createdAt`은 이 DTO에 없다:
- `source`: 서버가 항상 `"MANUAL"`로 고정(아래 상수). 클라이언트가 지정할
  방법을 API 계약에서 원천적으로 제거한다.
- `externalId`: Manual Import는 "외부 시스템의 안정적 식별자"라는 개념이
  없다 — 사용자가 URL을 복사해 붙여넣는 것뿐이므로, `sourceUrl` 자체가
  이 source에서의 사실상 식별자 역할을 한다(dedup 키로도 사용). 따라서
  `externalId`는 항상 `null`로 저장한다.
- `id`/`createdAt`: 서버 생성 값(JOB-001과 동일 원칙).

**URL validation 구현**: 새 프레임워크를 만들지 않고, JOB-001이 이미 쓰고
있는 Hibernate Validator 내장 `@URL`(신규 dependency 아님)을 그대로
확장한다. `@URL`(파라미터 없음)은 기본적으로 Apache Commons
`UrlValidator`를 통해 `http`/`https`/`ftp` scheme과 문법적으로 유효한
URL만 통과시킨다(scheme이 없거나 `javascript:`/`file:`처럼 인식되지 않는
scheme은 이미 이 기본 검사에서 걸러진다). 여기에 `regexp` 속성(Hibernate
Validator의 `@URL`이 지원하는 추가 필터 — 기본 검사와 AND 조건으로
결합됨)으로 `^https?://.+`를 덧붙여 `ftp://`까지 명시적으로 차단한다.
**이 조합의 정확한 동작(특히 `regexp`가 기본 검사와 AND로 결합되는지)은
Hibernate Validator 실제 버전에서 Codex가 로컬 테스트로 반드시 재검증할
것** — JOB-001/COLLECT-001에서 반복됐듯 라이브러리 세부 동작을 추측하지
말고 실제 실행 결과로 확인한다. 만약 예상과 다르게 동작하면(예: `regexp`가
기본 검사를 대체해버려 문법 검증이 약해지는 경우) blocker로 보고하고,
대안으로 `@Pattern(regexp = "^https?://.+")` 단독 사용 또는 커스텀
`@AssertTrue` 메서드 검증(신규 dependency 없이 record에 검증 메서드 추가)
중 더 단순한 쪽을 Claude와 상의해 선택한다. **이번 검증은 형식 검사일
뿐이며, `sourceUrl`이 실제로 접속 가능한지/실제로 존재하는지는 검증하지
않는다(서버가 접속하지 않으므로 애초에 검증할 방법도 없다).**

**`ManualJobImportResult`** (record, 과도한 응답 계층 없이 flat):

```java
public record ManualJobImportResult(
        String result,          // "saved" | "duplicate"
        JobPostingResponse job  // 기존 JOB-001 DTO 재사용, 새 응답 DTO 만들지 않음
) {
}
```

`CollectResult`가 배치 결과(fetched/saved/skipped/failed)를 한 응답에
담아 예외 없이 상태를 표현하는 것과 같은 철학을, 단건 시나리오에 맞게
`result` 필드 하나로 단순화한 것이다. 새 응답 DTO는 `JobPostingResponse`를
그대로 재사용해 "이미 CareerOps에 등록된 공고"임을 사용자가 `result`
필드와 `job`(기존 레코드, 원래 `createdAt` 포함)으로 자연스럽게 알 수 있다.

### 4. Dedup 구현과 Duplicate Response Semantics

**Repository**: `JobPostingRepository`에 아래 메서드 1개를 추가한다
(Spring Data JPA 파생 쿼리, 구현 코드 불필요):

```java
Optional<JobPosting> findFirstBySourceAndSourceUrl(String source, String sourceUrl);
```

`findBySourceAndSourceUrl`(First/Top 없이)이 아니라 `findFirst...`를 쓰는
이유: 이번 Task는 DB unique 제약을 걸지 않으므로(아래 Known Limitation),
이론적으로 동일 `source`+`sourceUrl` row가 2건 이상 존재할 수 있다. 단순
`findBy...Optional<T>` 파생 쿼리는 결과가 2건 이상이면
`NonUniqueResultException`(Spring이 `IncorrectResultSizeDataAccessException`로
변환)을 던져 그 이후의 모든 요청이 500으로 깨진다. `findFirst`(Spring Data
JPA가 지원하는 `First`/`Top` 키워드)는 결과를 1건으로 제한해 이 시나리오에서도
안전하게 동작한다 — 데이터 정합성 문제 자체를 고치지는 못하지만, 최소한
서비스가 계속 정상 응답하도록 방어한다.

**Service 로직**:
1. `ManualJobImportRequest` → `JobPostingCreateRequest` 매핑(`source =
   "MANUAL"` 상수, `externalId = null` 고정, 나머지 필드 그대로 복사) —
   `AlioJobMapper`처럼 별도 static 매퍼 클래스로 뺄지, 간단한 private
   메서드로 둘지는 Codex 재량(이번 매핑은 날짜 파싱 등 복잡한 변환이 없어
   `AlioJobMapper` 수준의 별도 클래스가 필수는 아니다).
2. `repository.findFirstBySourceAndSourceUrl("MANUAL", request.sourceUrl())`
   조회.
3. 존재하면: `careerops.manual.import`(`result=duplicate`) 증가, `new
   ManualJobImportResult("duplicate", JobPostingResponse.from(existing))`
   반환. **`JobPostingService.create()`를 호출하지 않는다** — 이래야
   `careerops.job.creation`이 함께 증가하지 않는다(아래 Metric 절 참고).
4. 존재하지 않으면: `jobPostingService.create(request)` 호출(저장 +
   `careerops.job.creation` 자동 증가), `careerops.manual.import`
   (`result=saved`) 증가, `new ManualJobImportResult("saved",
   response)` 반환.

**Controller의 HTTP status 처리**: `result`에 따라 상태 코드가 달라져야
하므로(`saved`→`201`, `duplicate`→`200`), `JobPostingController`처럼
`@ResponseStatus` 고정 애노테이션을 쓸 수 없다 — `ResponseEntity<ManualJobImportResult>`를
반환하고 `result` 값에 따라 `status(HttpStatus.CREATED)` 또는
`status(HttpStatus.OK)`를 명시적으로 설정한다. 이건 기존 스타일에서
의도적으로 벗어나는 지점이며, 이유(단건 응답에 조건부 status가 필요함)를
코드 주석이나 커밋 설명에 남길 것을 권장한다.

**Duplicate를 예외(409 등)로 처리하지 않고 200+본문으로 처리하는 이유**:
COLLECT-001의 선례를 따른다 — 그쪽에서도 dedup skip은 예외나 별도 에러
상태가 아니라 `CollectResult` 응답 본문(`skipped` 필드)의 정상적인 한
결과로 표현된다("중복은 실패가 아니라 예상 가능한 정상 상태"라는 이
프로젝트의 기존 태도). Manual Import에서도 "같은 URL을 또 등록하려 함"은
클라이언트 오류(4xx 계열 강한 에러)라기보다 "이미 해결된 요청"에 가까우므로,
예외를 던져 클라이언트가 별도 에러 핸들링을 하게 만들기보다 항상 같은
응답 형태(`ManualJobImportResult`)로 결과를 알려주는 쪽이 이 프로젝트
스타일과 일관되고 사용성도 더 자연스럽다(사용자가 "이미 등록됨"과 함께
기존 등록 정보를 바로 확인할 수 있음).

**Known Limitation — concurrent duplicate race**: DB unique 제약이 없으므로,
정확히 동시에 들어온 두 요청이 모두 3단계("존재 확인")를 통과한 뒤 각각
4단계("저장")를 실행하면 동일 `source=MANUAL`+`sourceUrl` row가 2건
생성될 수 있다(check-then-act TOCTOU race). 이번 Task는 이 race를
막지 않는다 — 개인 프로젝트 단일 사용자 환경에서 동시에 같은 URL을 두 번
등록 시도할 확률이 매우 낮고, 본격적인 방지(DB unique 제약 + 충돌 처리)는
COLLECT-001이 이미 유보한 "본격 dedup 설계"와 같은 성격의 작업이라 함께
Phase 4 후보로 남긴다. 방지책 후보: `(source, source_url)` 복합 unique
제약 추가(Flyway migration) + 저장 시 `DataIntegrityViolationException`을
잡아 duplicate 응답으로 전환.

### 5. Product Metric 정의

| 지표명 (Prometheus 노출명) | Micrometer 이름 | 타입 | 태그 | 의미 | 계측 위치 |
|---|---|---|---|---|---|
| `careerops_manual_import_total` | `careerops.manual.import` | Counter | `result`=`saved`\|`duplicate` | Manual Import 요청의 결과 분포 | `ManualImportService` — 저장/중복 판정 직후 |

이름은 예약 접미사(`created`/`total`/`count`/`sum`/`bucket`)로 끝나지
않으므로 JOB-001에서 겪은 이름 잘림 문제가 재발하지 않을 것으로 예상되나,
Codex가 실제 `/actuator/prometheus` 출력으로 반드시 확인한다(ARCHITECTURE.md
"Spring Boot 4.1 알려진 모듈 재구성 이슈" 패턴 준수).

**`careerops_job_creation_total`과의 관계(반드시 지킬 것)**:
`ManualImportService`는 실제 신규 저장(`result=saved`)일 때만
`JobPostingService.create()`를 호출한다 — 이 메서드 내부에서 이미
`careerops.job.creation`을 증가시키므로 별도 배선이 필요 없다. `duplicate`
분기에서는 이 메서드를 호출하지 않으므로 `careerops.job.creation`이
증가하지 않는다. 즉 "신규 저장 여부"와 "두 카운터의 동시 증가 여부"가
구현 경로 자체로 항상 일치하도록 강제된다(COLLECT-001과 동일한 설계
원칙 — `JobPostingService.create()`를 우회해 `JobPostingRepository.save()`를
직접 호출하지 않는다).

**`invalid`(검증 실패) 케이스를 계측하지 않는 이유**: `sourceUrl`/
`companyName`/`title` 검증은 `@Valid`를 통한 Bean Validation으로 Controller
메서드 진입 **전**(Spring MVC의 `MethodArgumentNotValidException`)에
처리된다. 이 시점을 계측하려면 `ManualImportController`/`Service`에
전용 `@ExceptionHandler` 또는 프로젝트 전체 `@ControllerAdvice`를 새로
도입해야 하는데, 이는 JOB-001/COLLECT-001이 일관되게 유지해온 "공통 예외
처리 계층을 만들지 않는다"는 원칙과 충돌하고, 이 metric 하나를 위해 구조를
비정상적으로 복잡하게 만드는 것은 배보다 배꼽이 큰 선택이라고 판단했다.
`invalid` 요청은 HTTP `400` 자체로 이미 클라이언트에 신호가 가고, 서버
로그로도 확인 가능하다 — Product Metric 목록에서 제외한다(metric 정확성이
metric 개수보다 중요하다는 `docs/METRICS.md` 원칙에 따름). 향후 여러
도메인에 걸쳐 이런 계측 필요성이 반복되면 그때 공통 처리 도입을 재검토한다.

`docs/METRICS.md`의 "Product Metrics" 섹션에 위 표와 관계 설명을 반영한다.

### 6. Dependency

**신규 dependency 없음.** `@URL`(Hibernate Validator, JOB-001부터 이미
클래스패스), Bean Validation(`spring-boot-starter-validation`, JOB-001부터),
Micrometer(`MeterRegistry`, CORE-001부터)를 그대로 재사용한다.

### 7. 스키마 변경 불필요 확인

기존 `job_postings` 테이블(V1 migration) 컬럼을 확인했다 — `company_name`,
`title`, `employment_type`, `job_category`, `location`,
`application_start_at`, `application_end_at`, `source VARCHAR(255)`,
`source_url VARCHAR(2048)`, `external_id`, `created_at`이 이미 전부 존재하고,
Manual Import가 쓰는 값(`source="MANUAL"`(6자), `sourceUrl`(≤2048자))이
기존 컬럼 크기 제약 안에 완전히 들어온다. **새 컬럼도, 새 Flyway
migration도 필요 없다.**

### 8. AI 개발 Workflow 프로세스 반영

- Codex는 `.ai/metrics/metrics.jsonl`을 직접 수정하지 않는다(JOB-001/
  COLLECT-001과 동일 — Claude/오케스트레이터가 담당).
- `@URL(regexp=...)` 조합의 실제 동작, Micrometer 이름 잘림 여부 등
  라이브러리 세부동작과 관련된 불확실성은 반드시 실제 테스트 실행 결과로
  확인하고, 예상과 다르면 추측으로 우회하지 말고 blocker로 보고한다
  (JOB-001/COLLECT-001에서 반복 확인된 패턴).

## Test Plan

- `[자동]` `ManualImportControllerTest` —
  `backend/src/test/java/com/careerops/backend/manualimport/ManualImportControllerTest.java`.
  `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`(JOB-001/
  COLLECT-001과 동일 패턴). 아래 케이스 전부 포함:
  - 성공 저장: `201`, `result=saved`, `job.source=="MANUAL"`,
    `job.externalId==null`, 필드 매핑 확인.
  - 선택 필드 포함/미포함 각각의 매핑.
  - URL 누락/빈 문자열 → `400`.
  - URL 형식 오류(scheme 없음) → `400`.
  - `javascript:`/`file:`/`ftp:` scheme → 각각 `400`.
  - `companyName`/`title` 누락/빈 문자열 → `400`.
  - 동일 URL 2회 호출 → 1차 `201`/`saved`, 2차 `200`/`duplicate`, 같은
    `job.id`, `JobPostingRepository.count()` 불변(2차 호출 전후).
  - `MeterRegistry` 주입받아 `careerops.manual.import`(`result=saved`,
    `result=duplicate`) 및 `careerops.job.creation` 카운터 값 검증(위
    "일관성" AC와 동일 시나리오).
- `[자동]` `cd backend && ./gradlew test` — 위 신규 테스트 + 기존
  JOB-001/COLLECT-001 테스트 전체 통과. 사전조건: 저장소 루트에서
  `docker compose up -d`(PostgreSQL).
- `[수동]` `./gradlew bootRun` 기동 후 `curl -X POST
  http://localhost:8080/api/import/jobs/manual`로 실제 채용사이트 URL
  문자열을 담아 직접 호출, 응답/DB 저장 결과 확인. `manualimport` 패키지
  코드에 HTTP client 의존성이 없음을 코드로 재확인(위 Acceptance Criteria
  참고). `curl .../actuator/prometheus | grep careerops_manual_import`로
  metric 노출 확인. 확인 후 프로세스/컨테이너 정리.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | IMPORT-001 명세 기반 최초 구현 지시(manualimport 패키지, source/externalId 클라이언트 지정 불가, findFirstBySourceAndSourceUrl dedup, duplicate semantics, metric 2종, 신규 dependency/migration 없음 전제) | 블로커 없이 한 번에 성공. 32/32 테스트 통과(신규 16 + 기존 CORE-001/JOB-001/COLLECT-001 회귀). `@URL(regexp=...)` 조합이 javascript/file/ftp scheme을 실제로 거부함을 자체 테스트로 확인. 실제 커리어 URL 문자열로 수동 검증(saved→201, 재요청→200 duplicate, 동일 job.id), Prometheus에서 두 metric + job_creation 불변식 확인 → reviewer 1차 리뷰 PASS (`.ai/reviews/IMPORT-001-review-1.md`) |
