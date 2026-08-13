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
단, JOB-001은 저장/조회만 다루고 실제 수집(크롤링)·중복 제거·정규화는
아직 포함하지 않는다. 나머지 도메인은 아직 코드로 구현되지 않았다. 순서는
[ROADMAP.md](ROADMAP.md)에서 사용자 승인을 받아 정한다.

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
