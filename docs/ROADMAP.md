# ROADMAP.md — CareerOps Agent

## Phase 0 — AI 개발팀 협업 구조 구축 (완료)

목표: 기능 구현이 아니라, Claude(Tech Lead/Planner/Reviewer)와 Codex(Developer)가
안정적으로 협업할 수 있는 저장소 구조를 만든다.

- [x] `AGENTS.md` — Claude/Codex 공유 규칙
- [x] `CLAUDE.md` — Claude 역할 명시, AGENTS.md import
- [x] `docs/PROJECT.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`,
      `docs/METRICS.md`, `docs/DECISIONS.md`
- [x] `.ai/tasks/`, `.ai/reviews/`, `.ai/metrics/` (+ 템플릿)
- [x] Claude subagent: `architect`, `reviewer`
- [x] Claude Skill: `codex-implement`
- [x] Codex MCP 연결 확인 + 애플리케이션 코드 없는 최소 테스트 1회
- [x] 사용자 승인

## Phase 1 — 채용공고 수집 파이프라인 v0(단일 소스: ALIO) (완료)

- [x] CORE-001 — Spring Boot + PostgreSQL/Redis + Actuator 백엔드 뼈대
- [x] JOB-001 — `JobPosting` 최소 도메인(저장/조회 REST API)
- [x] COLLECT-001 — ALIO(공공기관 채용정보) Open API 연동. 외부 API →
      DTO → `JobPosting` 매핑 → 저장, dedup skip, 수동 트리거만 지원.
      실제 서비스키로 E2E 검증 완료(`.ai/tasks/COLLECT-001.md`).
- [x] IMPORT-001 — 사용자가 발견한 외부 채용공고 URL 수동 등록

**서비스 범위 결정(2026-08-15)**: 사람인 API 승인을 기다리지 않고, 공공기관
채용 특화(ALIO 단일 Provider)로 MVP를 우선 진행한다. 사람인/타 Provider는
향후 후보로만 남긴다 — `외부 Provider → Adapter → 공통 JobPosting 도메인`
경계는 유지하되, 지금은 ALIO Adapter만 존재한다.

## Phase 2 — ALIO 데이터 활용 확장 (완료)

목표: 이미 연동된 ALIO 응답에서 아직 활용하지 않는 필드(필요학력/고용형태/
경력구분/진행·마감 상태)를 `JobPosting`에 반영하고, 재수집 시 상태를
최신화한다.

- [x] COLLECT-002 — `JobPosting` 필드 확장(경력구분/필요학력/기관코드 신설,
      고용형태 필드 의미 정정) + 진행/마감 상태(`ongoingYn`) 갱신 전략.
      실제 서비스키로 재검증 완료(`.ai/tasks/COLLECT-002.md`).

## Phase 3 — JobPosting 조회/필터링 API (완료)

목표: DB에 쌓인 ALIO 채용공고를 사용자가 실제로 탐색할 수 있도록
`GET /api/jobs` 목록 조회 + 필터링 + pagination API를 제공한다. 수집
구조/외부 API 연동은 변경하지 않음.

- [x] JOB-002 — `GET /api/jobs`(status/careerLevel/companyName/jobCategory
      필터 AND 조합, `applicationEndAt ASC NULLS LAST` 고정 정렬,
      pagination). 구현 중 로컬 dev DB 오염으로 테스트가 불안정함을 발견해
      `careerops_test` 전용 DB로 자동 테스트를 완전히 격리(ADR-0010).
      41/41 테스트 통과(`.ai/tasks/JOB-002.md`).

## Phase 4 — ALIO 채용공고 자동 수집(Scheduler) (완료)

목표: 수동으로만 실행되던 ALIO 수집(`POST /api/collect/alio`)을 주기적으로
자동 실행한다. 기존 수집 로직(`AlioCollectorService`)과 수동 API는 변경
없이 그대로 재사용.

- [x] COLLECT-003 — `AlioCollectionScheduler`(`@Scheduled(fixedDelay)`,
      기본 6시간 간격/기동 1분 뒤 첫 실행, 설정으로 변경 가능) 추가. 단일
      인스턴스 겹침 방지는 `fixedDelay` 자체의 특성으로 해결(별도 락 없음,
      ADR-0011). 실패는 Scheduler 내부에서 흡수해 다음 실행에 영향 없음.
      `careerops.scheduler.alio.*` 전용 metric(run/duration/fetched/saved/
      skipped/updated/failed) 신설, 기존 `careerops.collector.*`는 변경
      없음. 45/45 테스트 통과(`.ai/tasks/COLLECT-003.md`).

## Phase 5 — ALIO 상세조회(`/detail.do`) 연동 (완료)

목표: COLLECT-001부터 반복적으로 Out of Scope 처리됐던 채용전형단계
(`steps`)/첨부파일(`files`) 메타정보를 실제 상세조회 API로 보강한다.

- [x] COLLECT-004 — 실제 서비스키로 `/detail.do`(`POST`, query param
      `sn`=`recrutPblntSn`) 요청/응답 구조를 직접 검증(파라미터명/응답
      envelope/`steps`·`files` 실제 필드 전부 실호출로 확인, 추측 없음).
      `RecruitmentStep`/`Attachment` Entity 신설(자연키 `recrutStepSn`/
      `recrutAtchFileNo` UNIQUE + 애플리케이션 exists 체크 이중 멱등성).
      `JobPosting.detailFetchedAt`으로 보강 완료 여부를 추적해, 목록 수집
      (`AlioCollectorService.collect()`) 중 재발견되는 공고(신규/status
      갱신/skip 전부 포함) 중 미보강 건만 그 자리에서 즉시 상세조회 —
      별도 Scheduler·소급 백필 스크립트 없음, `GET /api/jobs` 응답에도
      아직 노출 안 함(저장까지만). 상세조회 개별 실패는 트랜잭션 격리로
      목록 수집 전체에 영향 없음. `careerops.collector.detail.*` metric
      4종 신설. 51/51 테스트 통과 + 실제 키로 수동 검증(신규 기동 시
      미보강 50건 전부 보강, 재수집 시 재호출 없음 확인)
      (`.ai/tasks/COLLECT-004.md`).

## Phase 6 — ALIO 목록 API pagination 완성 (완료)

목표: `AlioCollectorService.collect(int numOfRows)`가 `pageNo=1` 한 페이지만
보던 것을, 호출자가 지정한 범위(`numOfRows`) 안에서는 여러 페이지를 정확히
순회해 공고를 빠뜨리거나 중복 저장하지 않도록 완성한다.

- [x] COLLECT-005 — 실 서비스키로 `list.do` pagination 계약을 직접
      검증(페이지당 서버 캡 1000건, `totalCount` 신뢰 가능, 마지막 페이지
      이후 호출은 에러가 아니라 빈 배열, **동일 run 내 페이지 크기를
      바꾸면 오프셋이 깨지는 것을 실측으로 발견**). `numOfRows`의 기존
      의미("이 호출에서 처리할 최대 총 건수")를 그대로 유지하며 내부만
      고정 페이지 크기 pagination 루프로 재작성(ADR-0014). Scheduler는
      코드 변경 없이 기본 수집 범위만 50 → **5,000건**으로 확대(전체
      112,920건 전수 순회 아님 — 사용자 승인, detail enrichment가 신규
      저장 직후 즉시 실행되는 구조상 전수 순회 시 대량 `detail.do` 호출이
      발생하는 것을 피함). `careerops.collector.pages` metric 신설. 58/58
      테스트 통과(`.ai/tasks/COLLECT-005.md`).

## Phase 7 — 동시 수집 race 방지(DB 무결성) (완료)

목표: COLLECT-005 실 API 검증 중 실제로 재현된 동시 수집 race(수동 API와
Scheduler가 동시 실행되며 동일 `(source, external_id)`의 `JobPosting`이
중복 저장)를 DB 수준에서 근본적으로 막고, 애플리케이션이 그 충돌을 정상
처리하도록 만든다.

- [x] COLLECT-006 — `job_postings(source, external_id)` DB UNIQUE 제약
      (`uk_job_postings_source_external_id`, 데이터 정리 SQL 없음 — 다른
      환경에 실제 중복이 있으면 migration이 실패해야 함) 추가.
      `JobPostingService.createOrGetExisting()`이 unique violation을
      catch해 canonical row로 합류(이 프로젝트 전체에 `@Transactional`이
      없어 `repository.save()`가 독립된 짧은 트랜잭션으로 끝난다는 것을
      코드로 직접 확인한 뒤 exception catch/re-read 채택, native SQL/`ON
      CONFLICT` 도입 안 함 — ADR-0015). `AlioCollectorService.collect()`
      전체를 감싸는 JVM in-process `ReentrantLock`(non-blocking) 병행
      도입 — correctness의 필수 조건은 아니고 외부 API 중복 호출을 줄이는
      optimization. 락 경합 시 수동 API는 HTTP 409, Scheduler는 `failure`
      아닌 `skipped` 집계. `careerops.collector.conflict` metric 신설.
      64/64 테스트 통과(`.ai/tasks/COLLECT-006.md`).

## Phase 8 — JobPosting 상세조회 API에 recruitmentSteps/attachments 노출 (완료)

목표: COLLECT-004부터 DB에 저장되고 있지만 조회 API로는 노출되지 않던
`RecruitmentStep`/`Attachment`(ADR-0013에서 의도적으로 미노출)를
`GET /api/jobs/{id}` 상세조회 응답에 포함한다. `GET /api/jobs` 목록/검색
응답은 payload 비대화를 막기 위해 변경하지 않는다. 수집 인프라(ALIO 연동/
Scheduler/enrichment)는 이번 Phase에서 전혀 손대지 않는다.

- [x] JOB-003 — `JobPostingDetailResponse`(기존 `JobPostingResponse` 필드 +
      `recruitmentSteps`/`attachments`) 신설, `RecruitmentStepResponse`(6필드:
      `sortNo`/`stepGroupName`/`competitionRate`/`applicantCount`/
      `recruitCount`/`occurredAtRaw`)/`AttachmentResponse`(4필드: `sortNo`/
      `fileName`/`fileType`/`url`) 신설 — ALIO 내부 식별값(`recrutStepSn`/
      `minStepSn`/`maxStepSn`/`recrutAtchFileNo`)과 엔티티 PK는 노출하지
      않기로 사용자 승인. 정렬은 `sortNo ASC` + 동일 `sortNo` 내 ALIO
      natural key(`recrutStepSn`/`recrutAtchFileNo`) ASC를 Repository 파생
      쿼리로 명시(DB 조회 순서 의존 없음). `JobPostingService.findById()`를
      확장해 3-query 조합(기존 목록 `search()` 경로는 무변경, N+1 없음)으로
      Controller/Repository 계층을 그대로 재사용, 새 Query Service 계층은
      만들지 않음. 신규 migration/metric 없음(기존 V3 스키마 + 기존
      `careerops.job.read` 카운터로 충분). 66/66 테스트 통과 + 실제 dev DB
      ALIO 공고(id=182)로 정렬/필드노출범위 수동 검증 완료
      (`.ai/tasks/JOB-003.md`).

## Phase 9 — JobApplication 핵심 도메인(지원 관리 CRUD/상태 관리) (완료)

목표: 사용자가 관심 있는 채용공고를 자신의 지원 대상으로 등록하고, 현재
지원 상태와 기본 지원 정보(메모)를 관리할 수 있게 한다. 단일 사용자 MVP라
User/Auth 도메인은 추가하지 않고, 전형 단계별 일정/결과(`ApplicationStage`),
알림, Calendar, 자기소개서 등은 이번 Phase에서 다루지 않는다. 수집
인프라(ALIO 연동/Scheduler)는 손대지 않음.

- [x] APPLICATION-001 — 신규 `com.careerops.backend.application` 패키지에
      `JobApplication`(`JobPosting` 1:0..1, `RecruitmentStep`과 동일한
      `@ManyToOne(optional=false, fetch=LAZY)` FK 패턴) + `ApplicationStatus`
      enum(`INTERESTED`/`PLANNED`/`SUBMITTED`/`OFFERED`/`REJECTED`/
      `WITHDRAWN` 6값 — 서류/필기/면접 전형 단계는 상태에 넣지 않고 향후
      APPLICATION-002의 `ApplicationStage`로 역할 분리, ADR-0016) 신설.
      `POST/GET/GET{id}/PATCH/DELETE /api/applications` 5개 엔드포인트,
      동일 `jobPostingId` 중복 등록은 사전 체크 + DB
      `UNIQUE(job_posting_id)` 이중 방어로 **409 Conflict**(COLLECT-006의
      idempotent 흡수 패턴과 달리 사용자 명시적 액션이라 실패로 처리,
      ADR-0016). 목록/단건 조회는 JPQL constructor expression으로
      `JobPosting`과 JOIN해 `JobApplicationResponse`(companyName/title/
      applicationEndAt/jobPostingStatus snapshot 포함)를 단일 쿼리로 조립
      (N+1 없음, JOB-003 원칙 재사용). PATCH는 부분 수정(요청 필드 null=
      무변경), `appliedAt` 자동 설정 없음(명시적 입력만). `job_posting_id`
      FK는 `ON DELETE` 절 없이 V3 컨벤션 재사용(`CascadeType.ALL` 미사용).
      `@Transactional` 미사용(ADR-0015와 동일 근거). 신규 migration
      `V5__create_job_applications_table.sql`, 신규 metric 없음(일반
      CRUD, 기존 Spring HTTP metric으로 충분). 구현 2 round(1차 최초 구현,
      2차는 테스트 코드 Java text block 컴파일 오류 수정) + 리뷰 1
      round(1차 통과) 거쳐 80/80 테스트 통과(기존 66 + 신규 14)
      (`.ai/tasks/APPLICATION-001.md`).

### Phase 7 이후 후보

- **`AlioDetailEnrichmentService` 트랜잭션 재구조화(동시성 강화)** — 같은
  미보강 공고를 두 run이 동시에 발견하면 `detail.do` 중복 호출+
  `persistDetail()` 트랜잭션 전체 롤백으로 `detailFetchedAt` 갱신이
  지연될 수 있다(기존 COLLECT-004 UNIQUE 제약 덕분에 데이터 손상 자체는
  없음). 진짜 고치려면 `persistDetail()`의 트랜잭션 경계를 step/file
  단위로 재구조화해야 하는데, PostgreSQL은 트랜잭션 안에서 한 statement가
  실패하면 그 트랜잭션 전체가 aborted 상태가 되어 같은 트랜잭션 안에서
  catch-and-continue가 불가능해 "작은 수정"으로 끝나지 않는다(COLLECT-006/
  ADR-0015). COLLECT-006의 run-level lock이 이 race의 발생 창을 사실상
  닫아 우선순위가 낮아졌지만, 완전히 해소된 것은 아니다.

### Phase 9 이후 후보

- **APPLICATION-002 — 전형 단계(`ApplicationStage`) 관리** — `JobApplication`
  1건당 서류/필기/면접/최종 등 여러 전형 단계와 각각의 `scheduledAt`/
  `result`/`memo`를 관리(ADR-0016에서 역할 분리를 미리 설계해둠). 사용자
  승인 후 착수.

### Phase 6 이후 후보

- **다중 인스턴스 분산 Scheduler**(ShedLock 등) — 현재 단일 인스턴스
  전제. 여러 인스턴스로 확장하는 시점에 ADR-0011 "영향" 절 참고해 재설계.
- **전체 112,920건 히스토리 백필** — COLLECT-005로 pagination 자체는
  정확히 동작하지만, Scheduler는 매 6시간 최근 5,000건만 훑는다(ADR-0014).
  그보다 오래된 과거 공고는 여전히 미보강 상태로 남을 수 있다 — 전체
  히스토리를 훑는 것은 별도 운영 작업(1회성 배치 등)으로 분리하기로
  결정했다(ADR-0014). 필요성이 확인되면 별도 Task.
- **steps/files 갱신(re-sync)** — 현재는 `detailFetchedAt`이 한 번 설정되면
  다시 상세조회하지 않는다(최초 1회 동기화만). 전형 일정/첨부파일이 저장
  이후 바뀌어도 반영되지 않음 — 필요성이 확인되면 별도 설계.

### Phase 3 이후 후보 (우선순위 미확정, 필요 시점에 별도 Task로 분리)

- **기관유형/기관분류 텍스트 매핑** — ALIO 목록 API 응답에는 기관 코드
  (`pblntInstCd`)만 있고 유형/분류명 필드가 없음이 확인됨(COLLECT-002 조사).
  참조 데이터(코드정의서/기관목록 API 등)를 확보해야 가능.
- **공고 전체 필드 갱신 전략** — 현재(COLLECT-002 기준)는 재수집 시 상태만
  갱신하고 나머지 필드는 최초 저장값 유지. 필드 전체를 최신화하려면 별도
  설계 필요.
- **조회/필터링 API 추가 확장** — `employmentType`/`educationRequirement`/
  게시일 범위 필터, 클라이언트 지정 정렬 등 JOB-002에서 의도적으로 제외한
  항목. 필요성이 실사용에서 확인되면 별도 Task.
- **cross-source dedup**, **Personal Knowledge Base(PKB) v0**, **Metrics
  계측 확장**, **알림(카카오톡)** 등 — 아직 착수 전, 우선순위는 사용자
  승인 후 결정. (Scheduler(정기 수집)는 Phase 4/COLLECT-003으로 완료됨)

이 문서는 Phase가 진행되며 갱신된다. 완료된 Phase는 체크박스로 남기고,
다음 Phase/후보를 그 아래 갱신한다.
