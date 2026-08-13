# ARCHITECTURE.md — CareerOps Agent

## 현재 상태

**Phase 2 진행 중.** CORE-001(백엔드 실행 기반: Spring Boot + PostgreSQL/
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
아직 없고, 그 판단 근거는 `docs/DECISIONS.md` ADR-0008 참고. 나머지
도메인은 아직 코드로 구현되지 않았다. 순서는 [ROADMAP.md](ROADMAP.md)에서
사용자 승인을 받아 정한다.

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
