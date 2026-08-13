# DECISIONS.md — 아키텍처 결정 기록 (ADR)

모든 중요한 아키텍처 결정과 새 production dependency 추가는 여기에 짧게
기록한다. 형식은 아래 템플릿을 따른다. 결정 번호는 순증가.

## 템플릿

```
## ADR-XXXX: <결정 제목>

- 날짜: YYYY-MM-DD
- 상태: 제안 | 확정 | 폐기(대체: ADR-YYYY)
- 관련 Task: CO-XXXX (있다면)

**문제**: 무엇을 해결하려 하는가.

**결정**: 무엇을 하기로 했는가.

**대안**: 검토했던 다른 선택지와 왜 채택하지 않았는지.

**이유**: 왜 이 결정이 맞다고 판단했는지.

**영향**: 이 결정으로 바뀌는 것 (트레이드오프 포함).
```

---

## ADR-0001: Claude=Tech Lead/Planner/Reviewer, Codex=Developer 역할 분리

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: 없음 (Phase 0 구조 작업)

**문제**: Claude Code와 Codex를 각각 단순 코딩 보조 도구로 쓰면 역할이
겹치고, 구현 품질을 검증할 독립적인 주체가 없다.

**결정**: Claude Code는 계획/설계/리뷰를 담당하고, 실제 애플리케이션 코드
구현은 Codex(MCP)에게 위임한다. Claude는 Codex 결과를 Acceptance Criteria
기준으로 검토하고, 미충족 시 같은 Codex thread에 수정 요청한다.

**대안**:
- Claude가 직접 구현까지 전담 — 기각. 자기 검증(self-review)의 한계, 역할
  분리를 통한 품질 게이트 확보가 목적에 맞지 않음.
- Codex에 설계까지 위임 — 기각. Codex 실행 비용과 컨텍스트 특성상 장기
  설계/의사결정 유지에는 Claude Code(Tech Lead 역할)가 더 적합.

**이유**: 사람 개발팀의 Tech Lead/Developer 분업과 유사한 구조를 만들어
계획-구현-리뷰 사이 책임을 명확히 하고, 리뷰 게이트를 강제할 수 있다.

**영향**: 모든 애플리케이션 코드 Task는 Codex MCP 연결이 전제 조건이 된다.
연결이 끊기면 임의 대체 없이 사용자에게 먼저 보고한다 (AGENTS.md 참고).

---

## ADR-0002: 개발 프로세스 지표를 JSONL 파일로 기록

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: 없음 (Phase 0 구조 작업)

**문제**: Task별 계획/구현/리뷰/검증 과정의 품질과 효율을 처음부터 측정하고
싶지만, 별도 지표 시스템을 만드는 것은 Phase 0 범위를 벗어난다.

**결정**: `.ai/metrics/metrics.jsonl`에 append-only로 한 줄씩 기록한다.
스키마는 `docs/METRICS.md` 참고.

**대안**:
- DB 테이블로 관리 — 기각(현재 단계). 아직 애플리케이션 DB 자체가 없고,
  과도한 선투자.
- Markdown 표로 관리 — 기각. 자동 append/파싱이 JSONL보다 번거로움.

**이유**: 사람이 읽기 쉽고, 코드/스크립트로 파싱하기도 쉬우며, 나중에
Postgres나 Prometheus로 옮기기도 단순하다.

**영향**: 초기엔 정밀한 질의(aggregate 등)가 불편할 수 있음. 필요해지면
별도 Task로 마이그레이션한다.

---

## ADR-0003: 초기 기술 스택 확정 (1차)

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: 없음 (Phase 0 구조 작업)

**문제**: Phase 1부터 코드를 작성하려면 기본 스택이 정해져 있어야 한다.

**결정**: `docs/ARCHITECTURE.md`에 명시된 스택을 1차로 채택한다 — Backend:
Java 21 / Spring Boot / Spring Data JPA / PostgreSQL / Redis, Frontend:
Next.js / TypeScript, Infra: Docker / Docker Compose, Monitoring: Micrometer /
Prometheus / Grafana.

**대안**: 이 시점에서는 별도 대안을 비교 검토하지 않음 — 사용자가 요구사항
단계에서 직접 지정한 스택이며, 사용자의 기존 숙련도(Java/Spring)와 개인
프로젝트 운영 편의성을 고려한 선택으로 간주한다.

**이유**: 사용자 지정 + 개인 프로젝트 규모에서 검증된 스택을 우선 채택해
불필요한 기술 검토 비용을 줄인다.

**영향**: 특정 컴포넌트(예: 채용공고 수집기의 언어, 알림 발송 방식)에 대해
이 스택과 다른 선택이 필요해지면 별도 ADR로 예외를 기록한다. 스택을 바꾸는
경우도 새 ADR로 남기고 이 ADR을 "폐기(대체: ADR-YYYY)"로 표시한다.

---

## ADR-0004: Spring Boot 4.1.x 채택 (Java 21 호환, 현재 공식 지원 안정 버전)

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: CORE-001

**문제**: ADR-0003은 "Spring Boot"만 명시하고 구체 버전은 정하지 않았다.
Phase 1 스켈레톤(CORE-001)을 만들려면 실제 버전을 정해야 하는데, "최신"이
아니라 2026-08 시점에 실제로 공식 지원되는 안정(GA) 버전을 확인 없이
짐작으로 고르지 않기 위해 조사가 필요했다.

**결정**: Spring Boot **4.1.x** 계열을 채택한다(2026-08-13 조사 시점 최신
GA: 4.1.0, 2026-06-10 릴리스, Spring Framework 7 / Jakarta EE 11 기반).
4.1.x는 Java 17을 최소 요구 버전으로 하고 Java 21을 포함한 최신 Java를
지원하므로, ARCHITECTURE.md에 명시된 Java 21과 호환된다. CORE-001 구현
시점에 4.1 라인의 더 최신 patch가 나와 있으면 그것을 사용한다.

**대안**:
- Spring Boot 3.5.x — 기각. 3.5.x는 3.x 라인의 마지막 minor이며, 2026-06-30
  부로 커뮤니티(OSS) 지원이 종료되었다. 이후 3.x 라인에 대한 무료 OSS
  패치는 더 이상 나오지 않는다(별도 계약 기반 commercial/extended support는
  이와 별개로 존재할 수 있으나, 이 프로젝트는 그런 계약 없이 커뮤니티 지원
  버전만 사용하는 것을 전제로 한다). 신규 프로젝트를 이미 OSS 지원이 끝난
  버전으로 시작할 이유가 없다.
- Spring Boot 4.0.x(최신 patch 4.0.7) — 기각(부분적). 4.1이 이미 GA로
  나와 있고 지원 시작 시점이 더 늦어 지원 잔여 기간이 더 길다. 4.0과 4.1
  사이에 Java 21 호환성 차이는 없으므로, 더 최신 minor를 우선한다.

**이유**: "최신 기술이라고 무조건 쓰지 않는다"는 원칙에 따라, 유행이 아니라
지원 상태를 기준으로 확인했다. 조사 결과 Spring Boot 3.x 라인의 마지막
minor인 3.5.x가 이미 커뮤니티(OSS) 지원 종료 상태이고, 현재 커뮤니티
지원이 유지되는 것은 4.0/4.1뿐이며 4.1이 그중 최신 minor다. 두 라인 모두
Java 21과 호환되므로 지원 기간이 더 긴 4.1.x를 선택한다.

**영향**: Spring Boot 4.x는 3.x 대비 Jakarta EE 11, Jackson 3, 모듈
재구성 등 변경점이 있다. CORE-001 구현 시 3.x 시절 예제/튜토리얼을 그대로
따르지 않고 4.1.x 공식 문서를 기준으로 확인해야 한다.
`docs/ARCHITECTURE.md`의 "예정 기술 스택"에도 버전을 반영한다.

---

## ADR-0005: Build Tool로 Gradle 선택 (Maven 대신)

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: CORE-001

**문제**: CORE-001에서 Backend 프로젝트의 build 파일을 Gradle과 Maven 중
무엇으로 만들지 정해야 한다.

**결정**: **Gradle**(Groovy DSL, Gradle Wrapper 커밋)을 채택한다.

**대안**:
- Maven — 기각. XML 기반이라 장기적으로 커스텀 빌드 로직이나 멀티모듈
  확장이 필요해지면 상대적으로 장황해진다. 다만 팀 표준화, IDE 도구 지원의
  보수적 안정성이 더 중요해지는 시점이 오면 재검토 가능한 선택으로 남긴다.
- Gradle Kotlin DSL — 기각. 백엔드가 Java이므로 build 스크립트를 위해
  Kotlin이라는 또 다른 언어를 프로젝트에 추가하고 싶지 않다. Groovy DSL로
  충분하다.

**이유**: 개인 프로젝트 특성상 로컬에서 빌드/테스트를 반복하는 주기가
잦다. Gradle의 incremental build/build cache가 이 반복 주기의 체감 속도에
유리하고, 문법이 간결해 Codex가 생성하는 build 스크립트에서 실수(닫는 태그
누락 등 XML 특유의 실수)가 날 여지도 Maven보다 적다.

**영향**: `backend/gradlew`, `gradlew.bat`, `gradle/wrapper/*`를 커밋해
Gradle 버전을 고정한다. 이후 프로젝트가 멀티모듈(예: backend 내 여러
모듈)로 커진다면 Gradle의 멀티모듈 구성을 그대로 확장한다.

---

## ADR-0006: DB Schema 관리 전략 — Flyway 채택 (Hibernate `ddl-auto` 대신)

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: JOB-001

**문제**: JOB-001부터 첫 실제 도메인 테이블(`job_postings`)이 생긴다.
CORE-001에서는 `@Entity`가 하나도 없어 `ddl-auto=none`으로 충분했지만,
이제부터는 스키마를 실제로 어떻게 만들고 변경 이력을 관리할지 정해야
한다. CareerOps는 앞으로 여러 채용공고 소스(JOB-ALIO, 기업 공식 사이트,
금융사 공식 사이트)를 연결할 예정이라, 데이터가 계속 쌓이는 동시에
스키마 변경(컬럼 추가/조정)도 반복적으로 일어날 것으로 예상된다.

**결정**: **Flyway**(`flyway-core` + `flyway-database-postgresql`)를
도입해 모든 스키마 변경을 버전 관리되는 SQL 마이그레이션 파일
(`backend/src/main/resources/db/migration/V{n}__description.sql`)로
관리한다. `spring.jpa.hibernate.ddl-auto`는 `validate`로 설정해, Hibernate가
entity 매핑과 실제(Flyway가 만든) 스키마가 일치하는지 기동 시 검증만 하고
스스로 스키마를 바꾸지 않게 한다.

**대안**:
- `ddl-auto=update` — 기각. 매 기동 시 Hibernate가 엔티티 기준으로
  스키마를 추론해 변경한다. 개발 초기엔 편하지만 (1) 실제 실행된 변경의
  이력이 남지 않아 "언제 어떤 컬럼이 왜 추가/변경됐는지" 추적이 안 되고,
  (2) 컬럼 삭제·타입 변경·rename 같은 케이스를 안전하게 처리하지 못해
  실수로 데이터 손실 위험이 있으며, (3) 여러 소스를 붙이며 스키마가 자주
  바뀔 CareerOps 특성상 데이터가 쌓인 뒤에 결국 마이그레이션 도구로
  전환하게 될 가능성이 높다. "지금 당장 데이터가 적으니 괜찮다"는 이유로
  미루면, 이후 Task마다 같은 리스크가 반복 누적된다.
- `ddl-auto=none` + 수동 SQL 실행 — 기각. 버전 관리도 자동화도 없어
  "누가 로컬/운영 DB에 무엇을 언제 실행했는지"가 사람 기억에 의존한다.
  Claude(설계)/Codex(구현)/사용자가 함께 다루는 환경에서 재현성이 떨어진다.
- Liquibase — 기각. Flyway보다 XML/YAML/JSON 기반 changelog가 상대적으로
  복잡하다. 이 프로젝트 규모에서는 SQL-first인 Flyway가 더 단순하고,
  Codex가 마이그레이션을 생성/Claude가 리뷰하기에도 plain SQL이 더
  직관적이다.
- 지금은 아무 도구도 쓰지 않고 필요해지면 그때 도입 — 기각. 이번이
  처음으로 실제 테이블이 생기는 시점이다. `ddl-auto`로 스키마를 몇 차례
  자동 변경한 뒤 나중에 Flyway로 전환하면, 기존 스키마와 첫 baseline
  마이그레이션을 맞추는 추가 작업이 필요해진다. 처음부터 Flyway로
  시작하면 이 비용이 없다.

**이유**: "최신/유명해서"가 아니라 CareerOps가 계속 쌓이는 채용공고
데이터를 다루고, 소스가 늘어날수록 스키마 변경 빈도가 늘어난다는 이
프로젝트의 구체적 특성에 근거한다. 스키마 변경이 파일로 남고 리뷰
가능해야, Claude-Codex 협업 흐름에서 "무엇이 왜 바뀌었는지"를 코드
리뷰처럼 검증할 수 있다.

**영향**: 매 스키마 변경마다 마이그레이션 SQL 파일을 새로 추가해야 한다
(자동 생성이 아니라 수기 작성). `ddl-auto=validate`이므로 엔티티와 실제
스키마가 어긋나면 애플리케이션 기동이 즉시 실패한다(조기 오류 탐지 장점이자
트레이드오프 — 마이그레이션 작성을 빠뜨리면 앱이 아예 뜨지 않는다). 신규
dependency 2개(`flyway-core`, `flyway-database-postgresql`)가 추가되지만,
`build.gradle`의 Spring Boot dependency management(BOM)가 버전을 관리하므로
버전 명시는 불필요하다.
