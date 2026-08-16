# ARCHITECTURE.md — CareerOps Agent

## 현재 상태

**Phase 5 완료.** CORE-001(백엔드 실행 기반: Spring Boot + PostgreSQL/
Redis + Actuator)이 완료됐다. JOB-001부터 첫 도메인(Job Posting) 코드가
추가되며, 이때부터 `com.careerops.backend.<도메인>` 형태의 기능 단위
(feature-package) 구조를 쓴다(`domain/service/repository/controller` 같은
계층형 최상위 패키지로는 나누지 않는다 — 이유는 각 도메인 Task 명세
참고). 실제 모듈 구조/API 설계는 각 기능의 Task 명세(`.ai/tasks/`)와 함께
구체화되고, 이 문서에도 반영된다.

## 예정 기술 스택

**Backend**
- Java 21
- Spring Boot 4.1.x (Spring Framework 7 / Jakarta EE 11, ADR-0004)
- Build Tool: Gradle (Groovy DSL, ADR-0005)
- Spring Data JPA
- PostgreSQL 18.x (Docker: `postgres:18-alpine`)
- Redis 8.x (Docker: `redis:8-alpine`)
- Flyway (DB schema migration, ADR-0006 — JOB-001부터)

**Frontend**
- Next.js
- TypeScript

**Infrastructure**
- Docker
- Docker Compose
- 추후 Cloud 배포 (미정 — 필요 시점에 결정)

**AI**
- Claude (Tech Lead / Planner / Reviewer)
- Codex (Developer)
- MCP (Claude ↔ Codex 연동, 향후 다른 도구 연동)
- 추후 Agent SDK 또는 Managed Agent 검토 (제품 기능 내 Agent 실행용. Claude/Codex의
  개발팀 역할과는 별개로, CareerOps 제품 자체가 갖는 Agent 파이프라인에 대한 검토)

**Monitoring**
- Micrometer
- Prometheus
- Grafana

각 기술의 채택 이유와 대안은 [DECISIONS.md](DECISIONS.md)에 기록한다.

### Spring Boot 4.1 알려진 모듈 재구성 이슈 (구현 중 반복 발견)

Boot 4.x는 3.x 대비 여러 모듈을 잘게 쪼갰다. 3.x 시절 지식/예제를 그대로
믿지 말고, 아래처럼 실제로 이미 부딪힌 것들을 먼저 확인할 것 — 새 Task에서
같은 종류의 blocker를 또 처음부터 조사하지 않기 위한 목록이다(발견될 때마다
추가한다):

- **test-autoconfigure 분리** — `@DataJpaTest`→`spring-boot-data-jpa-test`,
  `@AutoConfigureTestDatabase`→`spring-boot-jdbc-test`,
  `@AutoConfigureMockMvc`→`spring-boot-webmvc-test`. 스타터로는
  `spring-boot-starter-data-jpa-test`/`spring-boot-starter-webmvc-test`
  (JOB-001, ADR 없음 — Task 명세에만 기록).
- **Flyway 자동구성 분리** — `flyway-core`만으로는 자동구성되지 않는다.
  `spring-boot-starter-flyway`가 필요(ADR-0006 보충, JOB-001).
- **Micrometer/Prometheus 예약 접미사** — 메타 이름의 마지막 단어가
  `created`/`total`/`count`/`sum`/`bucket` 등과 겹치면 Micrometer가 그
  단어를 잘라내고 자체 접미사를 붙인다. 라이브러리 버그 아님 — 이런 단어로
  끝나는 meter 이름을 피할 것(JOB-001).
- **Jackson 3가 기본** — `com.fasterxml.jackson.core`/`.databind`(Jackson 2)
  대신 **`tools.jackson.core`/`tools.jackson.databind`**(Jackson 3, 새
  groupId `tools.jackson.core`)가 기본 JSON 엔진이다. `ObjectMapper` 등을
  직접 쓰는 코드는 `tools.jackson.databind.ObjectMapper`를 import해야
  한다. 단, **`jackson-annotations`는 그대로 `com.fasterxml.jackson.annotation`**
  패키지를 유지한다(`@JsonProperty`, `@JsonIgnoreProperties` 등은 기존
  import 그대로) — annotation과 core/databind가 다른 규칙을 따르니
  혼동하지 말 것. Jackson 3의 `readValue()` 등은 checked exception이
  아니라 unchecked(`JacksonException` 계열)를 던진다(COLLECT-001).

- **`RestClient.Builder` autoconfiguration 분리** — `spring-boot-starter-web`만으로는
  `RestClient.Builder` bean이 더 이상 자동구성되지 않는다(`RestClientAutoConfiguration`이
  `spring-boot-autoconfigure`에서 `spring-boot-restclient` 모듈로 분리됨).
  `spring-boot-starter-restclient`가 필요하다(COLLECT-001).

패턴: Boot 4.1에서 컴파일/런타임 에러로 클래스를 못 찾으면, 먼저 이 목록을
확인하고 없으면 Maven Central에서 해당 groupId/artifactId의 jar를 직접
검사해 실제 패키지 위치를 확인한다(추측 금지 — AGENTS.md 원칙).

## 설계 원칙

- 최신 기술이라고 무조건 사용하지 않는다. 기술 추가 전 해결하려는 문제를
  먼저 명확히 한다.
- 과도한 추상화·불필요한 패턴을 피한다. MVP에서는 단순한 구조를 우선한다.
- 근거 기반 검증(Evidence-based verification)은 자기소개서 관련 기능의
  핵심 제약이며, 임의로 우회하지 않는다.
- 모든 주요 Agent 파이프라인 단계는 관측 가능해야 한다 (Metrics 참고).

## 상위 도메인 (예정, 구현 시 구체화)

- **채용공고 수집(Job Ingestion)**: 대기업/공기업/금융권 채용 사이트 수집,
  중복 제거, 정규화.
- **적합도 판단(Fit Scoring)**: 사용자 프로필 대비 공고 적합도 평가.
- **알림(Notification)**: 카카오톡 알림 발송 (신규/추천/마감임박).
- **Personal Knowledge Base(PKB)**: 이력서/포트폴리오/경험/기존 자소서 저장·검색.
- **자기소개서 파이프라인(Cover Letter Pipeline)**: 문항 분석 → 역량 추출 →
  경험 검색 → Evidence Sheet → 초안 → Fact Check → Style Check.
- **Metrics/Observability**: 개발 프로세스 지표 + 제품 지표.

위 도메인 중 **채용공고 수집(Job Ingestion)**은 JOB-001에서 최소 저장/조회
범위로 처음 코드화되기 시작했다(`backend/src/main/java/com/careerops/backend/job/`).
JOB-001은 저장/조회만 다루고 실제 수집(외부 API/크롤링)은 포함하지 않았다.
COLLECT-001부터 첫 실제 외부 Source(ALIO 개방데이터 — `opendata.alio.go.kr`
자체 Open API, `POST /new/v1/recruit/list.do`. 최초에는 data.go.kr의
유사 API를 잘못 선택했다가 사용자가 실제 승인받은 것과 달라 정정했다 —
`.ai/tasks/COLLECT-001.md` "정정 이력" 참고)를 연결하는 코드가
`backend/src/main/java/com/careerops/backend/collector/`에 추가된다(외부
API 호출 → 소스별 DTO → `JobPosting` 매핑 → 저장, 수동 트리거만 지원 —
Scheduler·cross-source dedup·웹 크롤링은 아직 없음). IMPORT-001부터는
자동 Source에 없는 공고를 사용자가 URL과 최소 정보를 직접 입력해 등록하는
`com.careerops.backend.manualimport`(`POST /api/import/jobs/manual`,
`source="MANUAL"` 서버 강제, URL 접속 없이 형식 검증만)가 추가된다 — 서버가
임의 URL에 접속하는 기능(HTTP GET/크롤링/JS 렌더링, SSRF 방어 포함)은
아직 없고, 그 판단 근거는 `docs/DECISIONS.md` ADR-0008 참고. COLLECT-002는
사람인 API 승인 없이 ALIO 단일 Provider로 MVP를 진행하기로 한 결정(ADR-0009)
이후, 실제 ALIO 응답을 재검증해 `JobPosting`에 `careerLevel`(경력구분)/
`educationRequirement`(필요학력)/`status`(진행·마감)/`institutionCode`
(기관코드) 필드를 추가하고, 기존에 이름과 의미가 어긋나 있던
`employmentType`(고용형태) 매핑을 정정했다. 재수집 시에는 `status`만
비교해 변경분을 갱신하고 다른 필드는 최초 저장값을 유지한다(전체 필드
동기화는 아직 없음). JOB-002는 이렇게 쌓인 `JobPosting`을 탐색할 수 있게
`GET /api/jobs` 목록 조회 API를 추가했다 — `status`(정확 일치)/
`careerLevel`/`companyName`/`jobCategory`(부분 일치) 4개 optional 필터를
AND로 조합하는 JPQL `@Query` 1개, `applicationEndAt ASC NULLS LAST` 고정
정렬, `page`/`size`(최대 100) pagination을 제공하며 매 요청마다 ALIO를
재호출하지 않고 DB만 조회한다. 이 Task 진행 중 로컬 dev DB(`careerops`)에
쌓인 실제 데이터가 자동 테스트의 개수/순서 검증과 섞여 실패하는 문제가
발견되어, 같은 docker-compose PostgreSQL 컨테이너에 완전히 분리된
`careerops_test` DB를 추가하고 `backend/build.gradle`의 `test` task에서
테스트 JVM의 `SPRING_DATASOURCE_URL`을 그쪽으로 강제하는 방식으로
격리했다(Testcontainers는 도입하지 않음 — 근거는 ADR-0010). COLLECT-003은
지금까지 수동 트리거(`POST /api/collect/alio`)로만 실행되던
`AlioCollectorService.collect(int)`를 전혀 수정하지 않고 그대로 재사용해,
`collector/alio/`에 `AlioCollectionScheduler`(`@Scheduled`, 기본 6시간
간격/기동 1분 뒤 첫 실행, `careerops.scheduler.alio.*` 설정으로 변경
가능)를 추가했다 — 단일 인스턴스 겹침 방지는 `fixedDelay`의 특성만으로
해결하고 별도 분산 락은 도입하지 않았으며(ADR-0011), 실행 실패는 Scheduler
내부에서 흡수해 다음 스케줄 실행에 영향을 주지 않는다. 관측은 기존
`careerops.collector.*`(COLLECT-001)를 건드리지 않고 `careerops.scheduler.alio.*`
전용 metric 네임스페이스를 신설해 분리했다. COLLECT-004는 COLLECT-001부터
반복적으로 Out of Scope 처리됐던 ALIO 상세조회(`/detail.do`)를 실제
서비스키로 요청/응답 구조를 직접 검증한 뒤 연동했다 — 목록 API가 항상 빈
배열로 주는 채용전형단계(`steps`)/첨부파일(`files`)을 `JobPosting`과
FK로 연결된 `RecruitmentStep`/`Attachment` Entity로 저장한다(`job` 패키지,
자연키 `recrutStepSn`/`recrutAtchFileNo`에 DB UNIQUE 제약 + 애플리케이션
exists 체크 이중 멱등성). 별도 Scheduler를 새로 만들지 않고
`JobPosting.detailFetchedAt`(nullable)으로 보강 완료 여부를 추적해,
`AlioCollectorService.collect()`가 목록을 처리하는 3개 분기(신규 저장/
status 갱신/skip) 전부에서 아직 미보강인 공고를 만날 때만 그 자리에서
즉시 상세조회한다 — 소급 백필 스크립트도, `GET /api/jobs` 응답 노출도
이번 Phase에는 포함하지 않았다(ADR-0013). 상세조회 개별 실패는 트랜잭션
경계로 격리해 목록 수집 전체에 영향을 주지 않는다. 나머지 도메인은 아직
코드로 구현되지 않았다. 순서는 [ROADMAP.md](ROADMAP.md)에서 사용자 승인을
받아 정한다.

## 시스템 구성 (개략, 미확정)

```
[수집기] -> [정규화/중복제거] -> [PostgreSQL]
                                     |
                              [Fit Scoring] -> [알림(카카오톡)]
                                     |
[PKB(문서 저장/검색)] <-> [자기소개서 파이프라인] <-> [Redis(세션/캐시)]
                                     |
                          [Frontend(Next.js)]
                                     |
                  [Micrometer -> Prometheus -> Grafana]
```

컨테이너 경계, API 계약, 배치/스트리밍 여부 등은 Phase 1 이후 실제 Task를
진행하며 확정하고 이 문서에 반영한다.
