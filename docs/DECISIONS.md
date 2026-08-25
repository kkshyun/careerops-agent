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

---

## ADR-0007: 외부 채용정보 API 클라이언트 — 인터페이스 추상화 + fixture 기반 테스트(WireMock 배제)

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: COLLECT-001

**문제**: COLLECT-001부터 CareerOps가 처음으로 실제 외부 API(ALIO 공공기관
채용정보 조회서비스)를 호출한다. 이 호출은 (1) 사용자가 아직 실제 인증키를
발급받지 않은 상태에서 구현/테스트를 시작해야 하고, (2) 향후 외부 API가
일시적으로 죽어 있거나 응답이 느려도 자동 테스트 전체(`./gradlew test`)가
실패해서는 안 되며, (3) 앞으로 붙을 다른 Source(기업 공식 사이트, 금융권
공식 사이트)에도 반복될 문제라 이번에 방식을 정해두는 편이 낫다.

**결정**: 외부 API 호출부를 인터페이스(`AlioJobClient`)로 감싸고, 운영
구현체(`RestClientAlioJobClient`, Spring이 이미 제공하는 `RestClient` 사용,
신규 dependency 없음)와 테스트 전용 구현체(손으로 작성한 fixture 반환
stub)를 분리한다. 자동 테스트는 전부 이 인터페이스의 테스트 구현체만
사용하고, 실제 외부 API를 호출하는 검증은 사람이 실제 키로 수행하는
`[수동]` Acceptance Criteria로 분리한다. **WireMock 등 HTTP mocking
서버 프레임워크는 도입하지 않는다.**

**대안**:
- **WireMock으로 실제 HTTP 요청/응답을 흉내내기** — 기각. 인터페이스
  추상화만으로 이미 "외부 API 없이도 전체 로직을 테스트 가능"이라는 목표를
  달성할 수 있다. WireMock은 "실제 HTTP 계층(직렬화, 헤더, 상태 코드 처리
  등)까지 정확히 재현해야 한다"는 요구가 있을 때 가치가 있는데, 이번
  Task 규모(단일 GET 오퍼레이션, 단순 JSON 응답)에서는 그 정확도가 실제로
  중요한 버그를 잡아줄 가능성이 낮다. 프로젝트가 지금까지 Testcontainers
  등 무거운 테스트 인프라를 선제적으로 들이지 않은 관례(JOB-001 Out of
  Scope)와도 맞지 않는다. 필요성이 실제로 드러나면(예: 여러 Source의 HTTP
  계층 특이사항이 반복적으로 버그를 만들면) 재검토한다.
- **실제 API를 테스트에서 직접 호출** — 기각. 사용자가 아직 키를 발급받지
  않았고, CI/로컬 환경에서 외부 서비스 가용성에 자동 테스트가 의존하게
  되어 JOB-001 이후 지켜온 "자동 테스트는 로컬 인프라(Docker Compose
  PostgreSQL/Redis)까지만 의존한다"는 전제를 깬다.
- **Mockito(`@MockBean` 등)로 인터페이스를 mocking** — 기각하지 않고
  "선택 가능한 대안"으로 남김. `spring-boot-starter-test`에 이미 포함돼
  있어 신규 dependency는 아니지만, Boot 4.1에서 관련 애노테이션 패키지가
  재배치됐을 가능성(JOB-001에서 3차례 발생한 패턴)이 있어 손으로 작성한
  stub을 기본값으로 권장했다. 강제하지는 않는다.

**이유**: 인터페이스 하나로 "외부 API 미접근 시에도 흔들리지 않는 자동
테스트"와 "실제 서비스 코드는 운영에서 실제 API를 호출"이라는 두 목표를
동시에 만족시키면서, 새 프로덕션/테스트 dependency를 하나도 추가하지 않는다.
과도한 추상화(전략 패턴 레지스트리, 여러 구현체 관리)는 아직 만들지 않고
Source가 실제로 2개 이상이 될 때 재검토한다.

**영향**: 외부 API를 호출하는 모든 향후 Source(기업 공식 사이트 등)도 이
패턴(클라이언트 인터페이스 + fixture stub)을 기본값으로 따른다. 자동 테스트
커버리지가 "우리 코드가 API 응답을 올바르게 처리하는가"까지만 보장하고
"실제 API가 스펙대로 응답하는가"는 보장하지 않는다는 트레이드오프가 있다 —
이 부분은 각 Task의 `[수동]` Acceptance Criteria가 메운다.

---

## ADR-0008: Manual Job Import에 SSRF 방어 계층을 아직 만들지 않는다

- 날짜: 2026-08-13
- 상태: 확정
- 관련 Task: IMPORT-001

**문제**: IMPORT-001(사용자가 발견한 외부 채용공고 URL을 CareerOps에 수동
등록하는 기능)은 사용자가 임의의 URL 문자열(`sourceUrl`)을 입력하게 한다.
일반적으로 "서버가 사용자 입력 URL로 요청을 보내는" 기능은 SSRF(내부망
스캔, cloud metadata endpoint 접근 등) 위험을 동반하므로 allow-list,
private/loopback IP 차단, redirect 제한 같은 방어 계층이 필요한 것이
보통이다. IMPORT-001에서 이 방어 계층을 지금 만들지, 만든다면 얼마나
만들지 판단이 필요했다.

**결정**: 이번 Phase(IMPORT-001)에서는 SSRF 방어 계층을 만들지 않는다.
서버는 사용자가 입력한 `sourceUrl`에 **어떤 네트워크 요청도 보내지
않는다** — HTTP GET/크롤링/JS 렌더링 없이 입력값을 형식 검증(문법적으로
유효한 URL, `http`/`https` scheme만) 후 그대로 저장할 뿐이다. 이 Phase의
SSRF 방어는 "공격 표면 자체가 존재하지 않는다"는 사실 그 자체다 — 서버
코드 어디에도 사용자 입력 URL로 outbound 연결을 만드는 경로가 없다.

**대안**:
- **지금 최소한의 방어라도 미리 넣어두기**(예: private IP 문자열 패턴
  거부만이라도) — 기각. 서버가 애초에 그 URL로 접속하지 않는 이번
  Phase에서는 이 방어 로직이 실행될 경로 자체가 없어 죽은 코드(dead code)가
  된다. 실행되지 않는 보안 코드는 검증도 안 되고("접속 안 하는 코드가
  막아야 할 시나리오"를 테스트로 만들 수 없음) 거짓 안전감만 준다 — 실제
  outbound 호출 경로가 생긴 뒤에야 진짜 방어가 필요하고 진짜 검증도
  가능하다.
- **URL 형식 검증에 SSRF 방어까지 포함시키기**(scheme 제한을 SSRF 방어의
  일부로 취급) — 기각(개념 구분). `http`/`https` scheme 제한은 SSRF
  방어가 아니라 일반적인 입력 정합성 검사다(`javascript:`/`file:`처럼
  향후 다른 기능에서 오작동을 일으킬 수 있는 값을 저장하지 않기 위함).
  SSRF는 "서버 자신이 속아서 내부망에 접속하는 것"을 막는 문제이고, 지금은
  서버가 애초에 접속을 안 하므로 이 문제가 발생할 수조차 없다. 두 개념을
  섞으면 나중에 "URL 형식 검증만 있으면 SSRF는 이미 막혀 있다"는 잘못된
  전제가 남을 위험이 있어 명시적으로 분리해 기록한다.

**이유**: "최신/유행이라 미리 만든다"가 아니라 "지금 존재하는 공격 표면에
정확히 비례하는 방어만 만든다"는 원칙(`docs/ARCHITECTURE.md` "과도한
추상화·불필요한 패턴을 피한다")에 부합한다. 공격 표면이 생기는 시점(서버가
실제로 outbound HTTP 요청을 만드는 기능이 추가되는 시점)에 이 ADR을
참조해 방어 계층을 설계하는 것이 유효한 순서다.

**영향**: 이번 Phase에서 사용자가 악성/내부망 가리키는 URL 문자열을
`sourceUrl`로 입력해도 그 자체로는 서버에 안전하다(형식 검증 후 저장만
될 뿐, 서버가 접속하지 않으므로). 다만 이 ADR은 SSRF만 다룬다 — 저장된
악성 URL을 사람이나 다른 시스템(예: 향후 Frontend)이 그대로 열어보게
만드는 문제(예: 악성 링크를 사용자에게 노출하는 문제)는 별개이며, 그
기능을 설계하는 Task에서 필요 시 다시 검토한다.

**향후 "URL에서 자동으로 정보를 추출하는 기능"(서버가 실제로 outbound
HTTP 요청을 만드는 시점, 이번 Task 범위 아님)을 설계할 때 반드시
구현해야 할 것 — 이 목록 자체가 이 ADR의 핵심 산출물이다**:

---

## ADR-0009: 사람인 API를 제거하고 ALIO 단일 Provider로 MVP 진행 + `JobPosting` 필드 확장(경력구분/고용형태 분리)

- 날짜: 2026-08-15
- 상태: 확정
- 관련 Task: COLLECT-002

**문제 1 — Provider 범위**: 애초 계획은 대기업/공기업/금융권을 아우르는
여러 채용 Provider(사람인 포함) 연동이었으나, 사람인 API는 활용신청 승인을
아직 받지 못했다. 승인을 기다리며 MVP 전체가 blocking되는 상황을 피해야
한다.

**결정 1**: 사람인을 포함한 다른 Provider에 대한 mock/우회/임시 구현을
만들지 않는다. 대신 CareerOps MVP의 범위 자체를 **공공기관 채용 특화
서비스**로 좁히고, 외부 채용정보 Provider는 **ALIO(이미 COLLECT-001에서
연동 완료) 단일 소스**만 사용한다. `외부 Provider → Adapter →
공통 JobPosting 도메인 → DB` 경계는 그대로 유지해(`collector/alio/`
패키지가 이미 이 구조), 향후 사람인이 승인되거나 다른 Provider가 필요해지면
같은 패턴으로 `collector/<provider>/` 패키지를 추가하는 것으로 확장한다.
공기업만으로 데이터를 제한하지 않고 ALIO가 제공하는 공공기관 전체 채용정보를
수집한다.

**대안**: 사람인 승인 대기 중 mock 데이터/우회 구현으로 개발 계속 —
기각. 실제로 존재하지 않는 API 계약을 가정한 코드는 승인 후 반드시
재작업이 필요하고, "존재하지 않는 필드를 추측하지 않는다"는 프로젝트
원칙과도 맞지 않는다.

**이유**: ALIO는 이미 실제 서비스키로 E2E 검증까지 끝난 유일한 Provider다.
막혀 있는 외부 요인(타사 승인)에 전체 진행을 종속시키지 않고, 이미 확보한
데이터 소스의 활용도를 높이는 쪽이 실질적 진행이다.

**영향**: `docs/PROJECT.md`의 "대기업/공기업/금융권" 목표는 유지하되,
Provider 확장은 사람인 승인 등 외부 조건이 갖춰진 뒤 별도 Task로 재개한다.

---

**문제 2 — `JobPosting.employmentType` 필드 의미 오류**: COLLECT-001
구현 당시 ALIO 응답의 `recrutSeNm`(값 예: `"신입"`/`"경력"` — 실제로는
**경력구분**)을 `JobPosting.employmentType`(고용형태)에 매핑했다. 이후
COLLECT-002에서 실제 ALIO 응답을 직접 재검증한 결과, 진짜 고용형태(정규직/
비정규직 등)에 해당하는 별도 필드 `hireTypeNmLst`가 존재함이 확인됐다 —
즉 기존 `employmentType` 필드는 이름과 실제로 담긴 값의 의미가 어긋나
있었다.

**결정 2**: `JobPosting`에 `careerLevel`(경력구분, `recrutSeNm` 매핑) 필드를
신설하고, 기존 `employmentType` 필드는 이름 그대로 진짜 고용형태
(`hireTypeNmLst` 매핑)를 담도록 매퍼(`AlioJobMapper`)만 수정한다. 컬럼
자체의 rename이나 기존 데이터 백필(backfill) 마이그레이션은 하지 않는다 —
로컬 개발 DB에 있는 기존 레코드(COLLECT-001 수동 검증 중 저장된 데이터)는
새 컬럼이 `NULL`, `employmentType`에는 예전 의미(경력구분 값)가 남는다.
이번 Task는 재수집 시 `status`(진행/마감) 필드만 갱신하는 최소 전략만
포함하므로, 기존 행의 `employmentType`/`careerLevel`은 자동으로
바로잡히지 않는다 — 필요하면 사용자가 직접 로컬 DB를 정리하고 재수집하면
된다.

**대안**: 전체 필드 백필 마이그레이션 작성 — 기각. 실사용자가 없는 개인
프로젝트의 로컬 개발 DB이고, 이미 알고 있는 값(과거의 `employmentType`
값을 `careerLevel`로 옮기는 것)만 옮길 수 있을 뿐 진짜 `employmentType`
값(`hireTypeNmLst`)은 애초에 저장된 적이 없어 완전한 백필이 불가능하다.
불완전한 백필 로직을 만드는 비용이 "재수집하면 그만"인 이득보다 크다.

**이유**: MVP 단계에서 과도한 마이그레이션 로직보다 단순함을 우선한다는
기존 원칙(`docs/ARCHITECTURE.md`)에 부합하고, 실제 운영 데이터가 아직
없으므로 손실 위험이 없다.

**영향**: 이 Task 이후 배포된 운영 DB에 실사용자 데이터가 쌓이기 시작하면,
이런 무백필 방식은 더 이상 기본값으로 쓸 수 없다 — 그 시점부터는 스키마
변경마다 데이터 마이그레이션 여부를 반드시 재검토해야 한다.

1. **scheme allow-list**: `http`/`https`만 허용(다른 scheme은 요청 자체를
   시도하지 않고 거부).
2. **private/loopback/link-local 및 예약 IP 대역 차단**: IPv4
   `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.0/8`,
   `169.254.0.0/16`(cloud metadata endpoint `169.254.169.254` 포함),
   IPv6 `::1`, `fc00::/7`, `fe80::/10` 등.
3. **DNS rebinding 방지**: hostname을 검증한 시점의 IP와 실제 connect
   시점에 재조회된 IP가 다를 수 있다 — 검증에 사용한 IP로 직접 connect
   하거나, redirect/재조회마다 매번 재검증해야 한다.
4. **Redirect 제한**: 자동 redirect follow를 금지하거나, 허용하더라도
   매 홉마다 위 1~3번 검증을 다시 적용하고 총 홉 수를 제한한다.
5. **Response size 제한**: 대용량 응답으로 인한 메모리 고갈 방지.
6. **Timeout**: connect/read timeout을 명시적으로 설정(무한 대기 방지).
7. **Content-Type 검증**: 예상한 타입(HTML/JSON 등)만 파싱, 그 외 거부.
8. **이용약관/robots.txt 준수**: `docs/PROJECT.md` 스코프 밖 항목("채용공고
   출처 사이트의 이용약관을 위반하는 수집 방식")과 직결된다 — 자동 추출
   기능을 설계하는 시점에 이 제약을 다시 확인해야 한다.

---

## ADR-0010: 자동 테스트 전용 PostgreSQL DB(`careerops_test`) 분리 — Testcontainers 도입 대신 같은 컨테이너에 DB 추가

- 날짜: 2026-08-15
- 상태: 확정
- 관련 Task: JOB-002

**문제**: JOB-002(`GET /api/jobs` 필터/정렬/pagination)의 신규 테스트는 처음으로
"필터 없이 전체 목록"이나 "정확한 개수/순서"를 검증했다. 그런데
`JobPostingRepositoryTest`(`@DataJpaTest`)와 `JobPostingControllerTest`
(`@SpringBootTest`)는 CORE-001부터 `@AutoConfigureTestDatabase(replace = NONE)`로
로컬 dev Docker Compose PostgreSQL(`careerops` DB)을 그대로 재사용해왔다.
이 dev DB에는 COLLECT-001/002 `[수동]` 실키 검증 등으로 이미 저장된 실제
레코드가 65건 남아 있었고, 새 테스트의 정확한 개수/순서 assertion이 이
기존 데이터와 섞여 실패했다(예: 21건 저장 후 `totalElements`가 21이 아니라
86으로 나옴). 각 테스트 메서드는 트랜잭션 롤백으로 격리되지만, 애초에
"빈 테이블"을 전제한 assertion 자체가 이 프로젝트에서 처음 등장했다.

**결정**: 별도 테스트 인프라(Testcontainers)를 새로 들이지 않고, 이미 떠
있는 같은 docker-compose PostgreSQL 컨테이너 안에 **`careerops_test`**
데이터베이스를 하나 더 만든다. `docker-compose.yml`의 postgres 서비스에
`/docker-entrypoint-initdb.d` init script(`docker/postgres-init/`)를 추가해
앞으로 새로 뜨는 볼륨은 자동으로 `careerops_test`까지 생성되게 하고,
이미 데이터가 있는 기존 볼륨(init script는 최초 초기화 시에만 실행됨)에는
`CREATE DATABASE careerops_test;`를 1회 수동 실행해 추가한다(기존
`careerops` DB는 전혀 건드리지 않는 추가적(additive) DDL). 테스트 쪽은
**`backend/build.gradle`의 `tasks.named('test') { ... }`에
`environment 'SPRING_DATASOURCE_URL', 'jdbc:postgresql://localhost:5432/careerops_test'`
1줄을 추가**해 테스트 JVM의 `SPRING_DATASOURCE_URL`만 강제로 재정의한다.
(최초에는 `backend/src/test/resources/application.properties`로
`spring.datasource.url`을 재정의하려 했으나, Spring Boot의
`PropertySource` 우선순위상 OS 환경변수가 어떤 설정 파일보다 항상 위에
있어 `.env`로 source된 `SPRING_DATASOURCE_URL` 환경변수가 이 override를
무시하는 것을 실제 테스트 실행으로 확인했다 — 이 파일은 삭제하고
`build.gradle` 방식으로 교체했다. `SPRING_DATASOURCE_URL`이 필수 환경변수인
main `application.yml` 구조상, 파일 기반 override는 애초에 이 값을
이길 수 없다.) 나머지 설정(ddl-auto, redis, actuator, collector 등)은
그대로 main `application.yml`의 값을 물려받는다. Flyway가 테스트 컨텍스트
기동 시마다 `careerops_test`를 자동 마이그레이션하므로 별도 스키마 준비
스텝도 필요 없다. 테스트 코드에 `deleteAll()` 같은 방어적 정리 로직이나,
프로덕션 쿼리에 테스트 전용 식별자(UUID prefix 등)를 끼워 넣는 방식은
채택하지 않는다.

**대안**:
- **Testcontainers로 전환** — 기각(이번 시점). `CORE-001`/`JOB-001`에서
  이미 "1인 프로젝트, CI 파이프라인 없음" 단계에서는 Testcontainers가
  선제적 확장이라고 명시적으로 결정했고, "CI를 실제로 구성하는 시점에
  재검토"하기로 남겨뒀다(`.ai/tasks/JOB-001.md`). 지금 발견된 문제는
  "로컬에 Postgres가 아예 없다"가 아니라 "기존 dev DB와 테스트가 상태를
  공유한다"이므로, 같은 컨테이너에 DB 하나를 추가하는 쪽이 문제 정의에
  더 정확히 비례한다. CI 파이프라인을 실제로 만드는 시점(그때는 CI
  환경에 상시 Postgres가 없을 수 있음)에는 이 ADR 자체를 재검토한다.
- **테스트 메서드 안에서 `repository.deleteAll()` 후 진행(트랜잭션
  롤백으로 dev 데이터는 복원됨)** — 기각. 이론적으로는 각 테스트
  트랜잭션이 롤백되므로 dev 데이터가 실제로 손실되지는 않지만, "실제
  운영성 데이터가 있는 DB에 대해 삭제 오퍼레이션을 실행하는 테스트 코드"를
  만드는 것 자체가 사고 위험(롤백 로직이 어디선가 깨지면 실제 데이터
  손실)과 심리적 부담이 크다는 사용자 피드백에 따라 채택하지 않는다.
- **테스트 데이터에 UUID/prefix를 부여하고 쿼리에서 그 prefix로 필터링** —
  기각. 프로덕션 코드(Repository 쿼리)에 테스트 전용 조건이 스며들게
  되어 "테스트를 위해 프로덕션 코드를 왜곡하지 않는다"는 원칙과 충돌한다.

**이유**: 새 dependency 없이(Testcontainers 미도입), 프로덕션 코드
변경 없이, dev DB의 기존 데이터를 전혀 위험에 노출하지 않으면서 테스트
격리 문제를 근본적으로 해결한다. 기존 CORE-001/JOB-001 결정("로컬 Docker
Compose Postgres 사용, Testcontainers는 아직 아님")과도 상충하지 않는다.

**영향**: 로컬 개발 환경을 처음 세팅하는 사람은 `docker compose up -d`
실행 시 `careerops`와 `careerops_test` 2개 DB가 자동으로 생성된다(신규
볼륨 기준). 이미 볼륨이 있는 환경(이번 프로젝트의 현재 로컬 환경 포함)은
`careerops_test`를 1회 수동으로 만들어야 한다 — 이 사실을
`docs/ARCHITECTURE.md` 또는 README 성격의 문서에 남겨 향후 혼란을
방지한다. `backend/build.gradle`의 `test` task에 datasource 환경변수
override가 생기므로, 이후 테스트 관련 설정을 바꾸는 사람은 이 override가
`.env`의 `SPRING_DATASOURCE_URL`보다 우선한다는 점을 인지해야 한다(설정
파일이 아니라 Gradle test task 환경변수이기 때문에 가능한 override다).

---

## ADR-0011: ALIO 자동 수집 Scheduler — `fixedDelay` + 별도 metric 네임스페이스, 분산 락 미도입

- 날짜: 2026-08-15
- 상태: 확정
- 관련 Task: COLLECT-003

**문제**: COLLECT-001/002로 완성된 ALIO 수집(`AlioCollectorService.collect(int)`)은
`POST /api/collect/alio`로 사람이 직접 호출해야만 실행된다. 이를 주기적으로
자동 실행하되, (1) 기존 수집 로직/수동 API를 변경·중복 구현하지 않고,
(2) 단일 인스턴스 MVP에 맞는 최소한의 동시 실행 방지만 두고, (3) 외부 API
장애가 애플리케이션 전체나 다음 스케줄 실행에 영향을 주지 않으며, (4)
사람 개입 없이도 실행 상태(횟수/성공·실패/fetched/saved/skipped/updated/
실행시간)를 관측할 수 있어야 한다.

**결정**:

1. **`fixedDelay` 채택(`fixedRate`/cron 대신)** — 신규 `AlioCollectionScheduler`가
   `@Scheduled(initialDelayString="${careerops.scheduler.alio.initial-delay:PT1M}",
   fixedDelayString="${careerops.scheduler.alio.fixed-delay:PT6H}")`로
   `alioCollectorService.collect(numOfRows)`를 그대로 호출한다.
   `AlioCollectorService`/`CollectController`/`CollectResult`는 전혀
   수정하지 않는다.
2. **주기 기본값 6시간**, 설정(`careerops.scheduler.alio.fixed-delay`)으로
   변경 가능. 공공기관 채용공고는 게시/마감 상태 변경이 하루 단위로도
   드물고, 아직 알림 기능이 없어 실시간성 요구가 낮다. 급한 재수집은
   기존 수동 API로 가능하므로 자동 주기는 보수적으로 잡아 외부 API 부담을
   줄인다.
3. **동시 실행 방지에 별도 락을 두지 않는다** — `fixedDelay`는 "이전 실행이
   끝난 뒤 delay만큼 지나야 다음 실행을 스케줄"하는 것이 Spring의 기본
   동작이라, 단일 인스턴스에서는 이것만으로 겹침이 발생하지 않는다.
4. **예외를 Scheduler 내부에서 흡수**한다 — `AlioApiException`/예상 못한
   `RuntimeException`을 catch해 WARN 로그 + `careerops.scheduler.alio.run
   {result=failure}` metric만 남기고 밖으로 던지지 않는다(재시도 프레임워크
   없이, 다음 스케줄 실행이 사실상 재시도 역할을 한다).
5. **신규 metric은 `careerops.scheduler.alio.*` 별도 네임스페이스**로
   추가한다(`run`/`duration`/`fetched`/`saved`/`skipped`/`updated`/`failed`).
   기존 `careerops.collector.*`(COLLECT-001)에 `trigger` 같은 태그를
   덧붙이는 대신 완전히 분리된 Counter/Timer를 새로 만든다.

**대안**:
- **`fixedRate` 또는 cron 표현식** — 기각. `fixedRate`는 이전 실행이 API
  응답 지연 등으로 오래 걸리면 다음 실행과 겹칠 수 있어 별도 락이
  필요해진다. cron은 특정 시각 정렬(예: 매일 새벽 실행)이 필요할 때
  유리하지만, ALIO는 시간대별 트래픽 제약이 없는 공공 API라 그 이점이
  없고 `fixedDelay`보다 개념이 복잡하다.
- **ShedLock 등 분산 락 도입** — 기각(이번 시점). 현재 단일 인스턴스
  MVP이고, `fixedDelay` 자체로 단일 인스턴스 내 겹침 방지가 충분하다.
  다중 인스턴스로 확장하는 시점에는 인스턴스 간 겹침을 막을 별도 조율
  수단(ShedLock, DB advisory lock 등)이 반드시 필요하다 — **이번 Task
  범위에서는 구현하지 않고 이 ADR에 향후 고려사항으로만 남긴다.**
- **Spring Retry 등 재시도 프레임워크 도입** — 기각. 개별 실행 실패는
  다음 스케줄 실행(최대 6시간 뒤)이 사실상 재시도 역할을 하고, ALIO
  장애가 몇 분 내 복구되는 일시적 문제라면 다음 실행에서 자연히
  해소된다. 별도 backoff/재시도 정책을 지금 설계할 근거(장애 패턴 데이터)가
  없다.
- **기존 `careerops.collector.run`에 `trigger=manual|scheduled` 태그
  추가** — 기각. 기존 Counter의 태그 집합을 바꾸면 이미 있는
  `CollectControllerTest`의 metric assertion이 깨지고(태그 집합이 다른
  Counter는 Micrometer에서 별개 meter로 취급된다), 수동 API 관측 요구와
  무관한 변경이 기존 코드에 섞인다. 완전히 분리된 `careerops.scheduler.alio.*`
  네임스페이스를 추가하는 쪽이 기존 코드/테스트에 영향을 주지 않으면서도
  "자동 실행이 실제로 동작하고 있는가"를 더 명확하게 관측할 수 있다.

**이유**: "현재 단일 인스턴스 MVP에 필요한 최소한의 보호만 구현한다"는
사용자 요구에 정확히 비례하는 선택이다. `fixedDelay`는 새 코드나
dependency 없이 Spring 기본 동작만으로 동시 실행 방지를 얻고, 기존
수집·저장 로직을 전혀 건드리지 않아 회귀 위험이 없다.

**영향**: 다중 인스턴스로 확장할 경우 이 ADR의 "동시 실행 방지" 결정을
재검토해야 한다 — 그 시점에 필요한 것: (1) 인스턴스 간 상호 배제를 위한
분산 락(ShedLock 등, 신규 dependency), (2) 락 획득 실패 시의 로깅/metric
구분(정상적으로 스킵된 것인지 실제 장애인지), (3) 여러 인스턴스가 서로
다른 시각에 뜨는 경우의 초기 실행 정렬 문제. 지금은 이 세 가지 중 아무것도
구현하지 않는다.

---

## ADR-0012: `./gradlew bootRun` dev 실행 시 `.env` 자동 로드 —
## `spring.config.import`(Boot 내장 기능) 채택, 수동 `source .env` 대신

- 날짜: 2026-08-15
- 상태: 확정
- 관련 Task: 없음 (COLLECT-003 이후 발견된 dev 환경설정 문제)

**문제**: `application.yml`은 `SPRING_DATASOURCE_URL` 등을 OS 환경변수로만
주입받는다. `backend/build.gradle`의 `test` task는 이 값을 하드코딩
override하므로 테스트는 항상 정상 동작하지만(ADR-0010), `./gradlew bootRun`에는
그런 장치가 없다 — 셸에서 `.env`를 미리 `source`하지 않고 바로 실행하면
`SPRING_DATASOURCE_URL`이 빈 문자열로 해석되어 `'url' must start with
"jdbc"` 오류로 기동에 실패한다(실제 재현 확인).

**결정**: `application.yml`에 `spring.config.import:
optional:file:../.env[.properties]` 1줄을 추가한다. `.env`가 `KEY=VALUE`
형식(Java `.properties`와 동일한 문법)이라는 점을 이용해, dotenv류 신규
dependency 없이 Spring Boot가 이미 제공하는 "임의 파일을 `.properties`
파서로 강제 로드"하는 표준 기능(`[...]` 확장자 힌트)만으로 해결한다.
`optional:`이므로 `.env`가 없는 환경(예: 운영에서 진짜 OS 환경변수만
쓰는 경우)에서도 조용히 건너뛴다.

**대안**:
- **셸에서 `.env`를 `source`한 뒤 `bootRun`을 실행하는 관례로 남기고
  문서에만 안내** — 기각. 이번 문제가 바로 "사람이 매번 기억해야 하는
  수동 단계"가 누락되어 발생했다. 코드/설정 변경 없이 문서만 추가하는
  쪽이 더 보수적이지만, 같은 실수가 반복될 가능성이 높다고 판단해 기각.
- **`dotenv-java` 등 라이브러리 도입** — 기각. Spring Boot가 이미
  `spring.config.import`로 동일한 효과를 제공하므로 신규 dependency를
  추가할 이유가 없다("최신/편해 보인다고 무조건 추가하지 않는다" 원칙).
- **Spring profile(`application-dev.yml`)에 dev 전용 접속 정보를 평문으로
  하드코딩** — 기각. `POSTGRES_PASSWORD`/`JOB_ALIO_API_KEY` 같은 값이
  Git에 커밋되는 설정 파일에 들어가게 되어 "Secret은 절대 Git에 commit하지
  않는다" 원칙과 정면으로 충돌한다.

**이유**: OS 환경변수가 항상 `spring.config.import`로 로드된 값보다
우선한다는 점(ADR-0010에서 이미 실측 확인)이 그대로 유지되므로,
`build.gradle`의 `test` task override는 전혀 영향받지 않는다 — 즉 기존
테스트 DB 격리 구조를 한 글자도 바꾸지 않고, dev 실행 편의성만 추가로
얻는다.

**영향**: `./gradlew bootRun`(또는 IDE에서 `BackendApplication` 직접 실행,
작업 디렉터리가 `backend/`인 경우)은 이제 셸에서 `.env`를 미리 `source`하지
않아도 정상 기동한다. 저장소 루트가 아닌 다른 위치에서 실행하거나 `backend/`
기준 상대 경로(`../.env`)가 맞지 않는 특수한 실행 방식(예: 다른 작업
디렉터리)에서는 여전히 값을 못 찾을 수 있음 — 그 경우 기존처럼 OS
환경변수를 직접 설정하면 된다.

---

## ADR-0013: ALIO 상세조회(`/detail.do`) 보강 — 목록 수집 inline 실행,
## 소급 백필 없음, 조회 API 미노출

- 날짜: 2026-08-16
- 상태: 확정
- 관련 Task: COLLECT-004

**문제**: ALIO 목록 API의 `steps`(전형단계)/`files`(첨부파일)는 항상 빈
배열이라 상세조회(`/detail.do`)가 필요하다는 것은 COLLECT-001부터 알고
있었지만, (1) 언제 상세조회를 실행할지(모든 공고마다? 신규만? 별도
Scheduler?), (2) 이미 dev DB에 쌓인 과거 공고(COLLECT-001~003, steps/files
없음)를 이번 Phase에서 소급 보강할지, (3) 보강된 데이터를 `GET /api/jobs`
응답에 바로 노출할지가 정해지지 않은 채였다. 세 질문 모두 "다른 선택이
실제로 결과를 바꾸는" 지점이라 임의로 정하지 않고 사용자에게 제시했다.

**결정**:

1. **실행 시점 — 목록 수집 inline, 별도 Scheduler 없음**: `AlioCollectorService.collect()`가
   목록을 처리하는 기존 3개 분기(신규 저장/status 갱신/skip) 전부에서,
   그 자리의 `JobPosting`이 아직 `detailFetchedAt == null`이면 즉시
   `AlioDetailEnrichmentService.enrich()`를 호출한다. `AlioCollectionScheduler`
   (COLLECT-003)는 수정하지 않고 그대로 재사용 — 이 Scheduler가 정기적으로
   목록을 재수집할 때마다 자연히 상세 보강도 같이 진행된다.
2. **소급 백필 없음, "재발견 시에만" 보강**: dev DB에 이미 있는 과거 공고
   전체를 순회하며 상세를 채우는 별도 스크립트/API는 만들지 않는다. 대신
   목록 수집 중 그 공고가 다시 나타나면(신규가 아니어도, status 갱신이든
   변화 없는 skip이든) 미보강 상태라면 그 자리에서 보강한다. 결과적으로
   ALIO 목록 API의 현재 조회 range(`numOfRows`, 기본 50, page 1) 안에
   남아 있는 공고만 점진적으로 채워지고, 이미 이 range 밖으로 밀려난
   오래된 공고는 이번 Phase에서 보강되지 않는다 — 의도된 제약이다.
3. **`detailFetchedAt`(nullable Instant)으로 보강 완료 여부 추적**: 성공
   시에만 설정, 실패 시에는 건드리지 않아 다음 재발견 때 자동 재시도된다.
   실패 횟수/backoff를 추적하는 별도 상태 머신은 만들지 않는다 — 같은
   공고가 계속 목록 range 안에 있는 동안만 최대 6시간(Scheduler 주기)마다
   1회씩만 재시도되므로 호출량이 자연히 bound된다.
4. **`GET /api/jobs`/`GET /api/jobs/{id}` 응답 미노출**: `RecruitmentStep`/
   `Attachment`는 DB에는 저장되지만 이번 Phase는 `JobPostingResponse`/
   `JobPostingController`를 변경하지 않는다 — 저장까지만 다룬다.

**대안**:
- **신규 저장 시에만 1회 상세조회**(원래 초안) — 기각(사용자 요청으로
  변경). 이미 COLLECT-001~003으로 dev DB에 쌓인 105건은 전부 신규가
  아니므로 영원히 미보강 상태로 남는다는 문제가 있었다.
- **전체 페이지네이션 순회 + 백필 전용 API/스크립트** — 기각. 사용자가
  명시적으로 "기존 dev DB 전체를 한 번에 순회하거나 대량 외부 API 호출하는
  백필 기능은 이번 Phase에 만들지 마"라고 지정했다. 외부 API를 한 번에
  대량 호출하는 위험(문서화된 rate limit을 찾지 못한 상태) 대비 이번
  Phase 범위를 넘어선다고 판단.
- **status 갱신/신규 저장 시에만 보강, skip 시에는 보강 안 함** — 기각.
  사용자가 "재발견됐고 아직 보강이 안 됐다면" 보강하라고 명시했고, skip은
  가장 흔한 재발견 경로(대부분의 재수집 결과)라 이 경로를 빼면 사실상
  "거의 보강되지 않는" 결과가 된다.
- **별도 "상세 보강 전용" Scheduler** — 기각. 목록을 다시 순회해야
  "재발견"을 판단할 수 있는데, 이는 이미 `AlioCollectionScheduler`가
  하는 일과 같아서 별도 Scheduler를 만들면 같은 목록 조회를 두 번(기존
  Scheduler + 신규 Scheduler) 하게 된다.
- **응답 DTO에 즉시 노출** — 기각(이번 Phase). Controller/DTO/테스트가
  추가로 필요해 Task 범위가 "수집 보강"에서 "조회 API 확장"으로 넘어간다
  — Phase 목표와 어긋나 별도 Task로 분리.

**이유**: 기존 Collector/Scheduler 코드를 거의 건드리지 않으면서(3개
분기에 1줄씩 추가) "이미 있는 데이터도 점진적으로 채워진다"는 실용적
요구를 만족시킨다. 대량 외부 API 호출이나 새로운 상태 머신 없이,
`detailFetchedAt` 단일 nullable 필드만으로 "최초 1회만 보강, 실패하면
자연 재시도"가 성립한다.

**영향**: ALIO 목록 API의 현재 range 밖으로 이미 밀려난 과거 공고(예:
마감된 지 오래된 공고)는 steps/files가 영구히 비어 있을 수 있다 —
필요해지면 별도 백필 Task로 다룬다(`docs/ROADMAP.md` "Phase 5 이후
후보"). `RecruitmentStep`/`Attachment` 데이터는 이번 Phase 이후에도
API로 조회할 수 없다 — 조회 API 확장은 별도 Task.

---

## ADR-0014: ALIO 목록 API pagination — run 내 페이지 크기 고정 + 클라이언트
## 슬라이싱, Scheduler 기본 범위는 5,000건(전수 순회 아님)

- 날짜: 2026-08-16
- 상태: 확정
- 관련 Task: COLLECT-005

**문제**: `AlioCollectorService.collect(int numOfRows)`는 COLLECT-001부터
`list.do`를 `pageNo=1`로만 고정 호출해, `numOfRows`(수동 API/Scheduler
기본값 50)가 곧 그 실행에서 볼 수 있는 전체 범위였다. 그 밖의 공고는
COLLECT-004(ADR-0013)에서 이미 "재발견 시에만 보강"이라는 의도된 제약으로
문서화했지만, page 1 범위 자체가 너무 좁아 신규/상태 갱신조차 놓치는
경우가 실제로 발생할 수 있었다. 이번 Task는 여러 페이지를 정확히
순회하도록 만들되, (1) 실제 ALIO `list.do`의 pagination 계약이 무엇인지
추측 없이 검증하고, (2) Scheduler가 매 6시간마다 전체(112,920건)를
순회할지 아니면 더 넓지만 유한한 범위만 훑을지 결정해야 했다.

**조사 결과 — 실 서비스키로 직접 검증(2026-08-16, secret 미노출)**:

- `pageNo`는 정상 동작(인접 페이지 간 중복 없음, sn 내림차순 고정 정렬로
  보임).
- `numOfRows`는 서버가 **1000으로 조용히 캡**한다(1001~10000을 요청해도
  전부 정확히 1000건 반환, 에러 없음 — 문서화되지 않은 값, 실측으로만
  확인).
- `totalCount`가 응답에 항상 존재하고 신뢰 가능하다(세션 내 모든 호출에서
  일관값, `numOfRows=50` 기준 마지막 페이지 계산과 정확히 일치).
- 마지막 페이지 이후/`pageNo=0`·음수 호출도 에러가 아니라
  `resultCode=200` + 빈 배열.
- 필터 없는 기본 조회는 `ongoingYn=Y`(진행)/`N`(마감) 둘 다 반환한다(기존
  동작 유지 확인).
- 문서화된 호출 제한(rate limit) 없음(COLLECT-004와 동일 결론).
- **신규 발견(가장 중요)**: 같은 collection run 안에서 서버에 보내는
  `numOfRows`(페이지 크기)를 바꾸면 오프셋이 깨진다. 서버는
  `offset = (pageNo-1) × 이번_요청의_numOfRows`로 계산하는 것으로 보인다.
  실측: `pageNo=1,numOfRows=1000` 다음 `pageNo=2,numOfRows=1000`(크기
  고정)은 정상 연속이지만, `pageNo=1,numOfRows=1000` 다음
  `pageNo=2,numOfRows=500`(크기 변경)은 page1에 이어지는 항목이 아니라
  전혀 다른(더 앞쪽) 구간을 반환한다.

**결정 1 — pagination 구현 방식**: 한 번의 `collect(numOfRows)` run 안에서
서버로 보내는 페이지 크기(`min(numOfRows, 1000)`)를 **절대 바꾸지 않고
고정**한다. 호출자가 지정한 총 상한(`numOfRows`)에 페이지 중간에서
도달하면, 서버에 더 작은 페이지를 재요청하지 않고 **그 페이지의 응답
리스트를 클라이언트 측에서 슬라이싱**해 처리를 멈춘다. 종료 조건은
우선순위 순으로 (a) 누적 처리 건수가 `numOfRows` 도달, (b) 응답이
비어있거나 페이지 크기보다 작음(마지막 페이지, 최종 신뢰 기준 — `totalCount`가
run 도중 신규 공고로 늘어나 stale할 수 있으므로 이 신호를 항상 우선),
(c) 안전장치(`maxPages = ceil(첫 페이지 totalCount / pageSize) + 5`,
정상 상황에서는 결코 발동하지 않는 값). `numOfRows`의 기존 API 의미
("이 호출에서 처리할 최대 총 건수")는 그대로 유지해 수동 API 하위호환을
지킨다 — `numOfRows=50`(기존 기본값) 호출은 여전히 1페이지, 50건만
처리한다.

**결정 2 — Scheduler 기본 수집 범위는 5,000건, 전체 112,920건 전수 순회
아님**: `AlioCollectionScheduler` 코드는 전혀 수정하지 않고,
`careerops.scheduler.alio.num-of-rows` 설정 기본값만 `50` → `5000`으로
바꿨다. 전체 전수 순회(약 113페이지)를 매 6시간마다 반복하지 않기로
한 이유는, COLLECT-004부터 detail enrichment(`enrichIfNeeded()`)가 신규
저장/미보강 공고 발견 즉시 그 자리에서 실행되는 구조이기 때문이다 — 전수
순회를 하면 한 번에 대량의 신규 저장과 `detail.do` 호출이 발생할 수
있어, 외부 공공 API에 대한 부담(ADR-0011이 6시간 주기를 보수적으로 잡은
것과 같은 원칙)과 실행 시간이 통제 밖으로 커질 위험이 있다.

**대안**:
- **Scheduler가 매 실행마다 전체(112,920건) 전수 순회** — 기각(사용자
  선택). "page/numOfRows 범위 밖 공고를 놓치지 않는다"는 목표를 문자
  그대로는 가장 확실히 만족시키지만, 매 6시간 ~113페이지를 순차 호출하고
  대부분 skip으로 끝나는 대량의 외부 API 호출을 반복하게 되며, `totalCount`가
  계속 늘어나는 만큼 비용도 영구히 증가한다. 전체 히스토리 백필은 이번
  Phase 범위가 아니라 별도 운영 작업으로 분리하기로 했다(`docs/ROADMAP.md`
  "Phase 6 이후 후보").
- **`numOfRows`를 "페이지 크기"로 의미를 바꾸고 항상 전체를 순회** — 기각.
  기존 수동 API(`POST /api/collect/alio?numOfRows=50`) 호출자가 50건만
  받을 것으로 기대하는 계약을 조용히 깨고, 같은 파라미터로 갑자기 10만 건
  이상을 반환하게 만드는 것은 명세 없는 API 하위호환성 파괴다.
- **마지막 페이지를 "남은 개수만큼" 작게 재요청** — 기각(API 계약상 불가능).
  위 "신규 발견"에서 실측한 대로 페이지 크기를 바꾸면 오프셋이 깨져
  데이터를 놓치거나 중복 처리하게 된다.
- **Scheduler에 `Integer.MAX_VALUE` 등 "무제한" sentinel 값 주입** — 기각
  (사용자 명시 지정). 코드 일반화(임의의 `numOfRows`에 대해 정확히
  pagination)는 그대로 두되, Scheduler가 실제로 요청하는 값 자체는 항상
  유한하고 사람이 읽을 수 있는 숫자(5,000)로 설정에 명시한다.

**이유**: pagination 메커니즘 자체(정확성)와 Scheduler의 운영 범위(비용)를
분리해서 판단했다. 메커니즘은 "임의의 `numOfRows`에 대해 정확히 동작"해야
하므로 일반화하고, 실제로 얼마나 넓게 볼지는 순수한 운영 판단이라 사용자
승인을 받았다. 이렇게 분리하면 향후 전체 백필이 필요해져도 코드 변경 없이
호출 시 더 큰 `numOfRows`를 넘기기만 하면 된다(단, 그것도 별도 운영
작업으로 신중히 트리거해야 함 — 자동 Scheduler에 넣지 않음).

**영향**: Scheduler는 여전히 최신 5,000건보다 오래된(대략 page 6 이상,
`numOfRows=1000` 기준) 과거 공고는 자동으로 갱신/보강하지 않는다(ADR-0013의
"page 1 밖 공고는 영구히 미보강" 제약이 "5,000건 밖 공고"로 완화됐을
뿐, 완전히 해소되지는 않음). 전체 히스토리를 다루려면 별도 운영 작업
(예: 수동으로 큰 `numOfRows`를 넘겨 `POST /api/collect/alio` 1회성 호출)이
필요하다 — 자동화하지 않는다(`docs/ROADMAP.md` "Phase 6 이후 후보").

---

## ADR-0015: `JobPosting` 동시 수집 race 방지 — DB UNIQUE + exception
## catch/re-read, JVM in-process 단일 run lock 병행 채택

- 날짜: 2026-08-16
- 상태: 확정
- 관련 Task: COLLECT-006

**문제**: COLLECT-005 실 API 검증 중 수동 수집 API(`POST /api/collect/alio`)와
`AlioCollectionScheduler`가 동시에 실행되면서 동일한 `(source, external_id)`의
`JobPosting`이 실제로 중복 저장되는 race condition이 재현됐다(dev DB에 1,370개
중복 그룹 생성, 이후 사용자가 정리). 코드 확인 결과 이 프로젝트 전체에
`@Transactional`이 하나도 없어(`AlioCollectorService.collect()`를 감싸는
상위 트랜잭션 없음), `repository.findFirstBySourceAndExternalId(...)`(존재
확인)와 그 뒤 `repository.save(...)`(INSERT)가 Spring Data JPA가 각각
독립적으로 여는 별개의 짧은 트랜잭션이라 그 사이 창(window)에 다른 run이
끼어들면 둘 다 "없음"을 보고 둘 다 INSERT에 성공했다. DB 수준의 무결성
없이 애플리케이션의 사전 find만으로는 이 race를 막을 수 없었다.

**결정**:

1. **`job_postings(source, external_id)`에 plain UNIQUE 제약** 추가
   (`uk_job_postings_source_external_id`, `V4__add_job_postings_source_external_id_unique.sql`).
   `external_id`는 nullable이고(`ManualImportService`는 항상 `NULL`을
   저장) PostgreSQL이 UNIQUE 제약에서 NULL끼리는 서로 다른 값으로 취급하므로
   partial index 없이 plain UNIQUE로 충분함을 실측(`\d job_postings`) +
   dev DB 조회로 확인했다. migration에 데이터 정리 SQL을 넣지 않았다 —
   실제 중복이 있는 환경에서는 migration이 실패해야 한다(조용히 삭제 금지).
2. **conflict 발생 시 exception catch/re-read로 canonical row에 합류**한다
   (`JobPostingService.createOrGetExisting()`). `JobPosting.id`가
   `GenerationType.IDENTITY`라 `repository.save()`가 `persist()` 즉시 INSERT를
   실행하고, 그 INSERT는 `save()` 자신이 여는 독립적인 짧은 트랜잭션
   안에서 일어난다 — 이를 감싸는 상위 트랜잭션이 없으므로, 예외가 호출부에
   전파되는 시점에는 이미 그 트랜잭션이 rollback되고 닫혀 있다(상위
   트랜잭션이 rollback-only로 오염되는 문제 없음). 그래서 안전하게 catch하고
   `findFirstBySourceAndExternalId`로 재조회해 합류할 수 있다. `INSERT ...
   ON CONFLICT`(native SQL)는 검토했으나 기각 — 이 프로젝트가 지금까지 native
   SQL을 쓴 적이 없고, 결과 row를 다시 로드하는 후속 조회가 어차피 필요해
   왕복이 줄지도 않으며, `@CreationTimestamp` 같은 Hibernate 콜백이 native
   insert 경로에서 동작하지 않아 별도 처리가 필요해진다.
3. **JVM in-process 단일 run lock 병행 채택**(`ReentrantLock.tryLock()`,
   non-blocking, `AlioCollectorService.collect()` 전체를 감쌈). DB UNIQUE만
   으로도 데이터 무결성은 완전히 보장되므로 이 lock은 correctness의 필수
   조건이 **아니다** — 외부 ALIO API(`list.do`/`detail.do`) 중복 호출과
   부하를 줄이는 optimization이며, `collect()` 전체(목록 순회 + inline
   detail enrichment 포함)를 직렬화해 아래 4번의 detail enrichment race
   창을 사실상 닫는 부수효과도 있다. 락 경합 시(즉 이미 다른 run이 실행
   중일 때) 수동 API는 **즉시 HTTP 409**를 반환하고(`AlioCollectionInProgressException`),
   Scheduler는 이를 `failure`가 아니라 별도 `skipped` 결과로 집계한다
   (정상적인 경쟁 상황이지 장애가 아니므로).
4. **`AlioDetailEnrichmentService`의 동시성 race는 이번 Task에서 다루지
   않는다.** 같은 미보강 공고를 두 run이 동시에 발견하면 `detail.do` 중복
   호출 + `persistDetail()` 트랜잭션 전체 롤백으로 `detailFetchedAt` 갱신이
   지연될 수 있다(기존 COLLECT-004 UNIQUE 제약 덕분에 데이터 손상 자체는
   없음). 진짜 고치려면 `persistDetail()`의 트랜잭션 경계를 step/file
   단위로 재구조화해야 하는데, PostgreSQL은 트랜잭션 안에서 한 statement가
   실패하면 그 트랜잭션 전체가 aborted 상태가 되어 같은 트랜잭션 안에서
   catch-and-continue가 불가능하다 — 즉 "작은 수정"이 아니라
   `AlioDetailEnrichmentService`의 트랜잭션 구조 자체를 바꾸는 별도 범위다.
   후속 Task 후보로 `docs/ROADMAP.md`에 남긴다.

**대안**:
- **DB UNIQUE만, run lock 없음** — 기각(부분적, 사용자 선택). correctness는
  동일하게 보장되지만 겹침이 실제로 발생하면 `list.do`/`detail.do` 중복
  호출과 detail enrichment race 창이 그대로 남는다. 사용자가 optimization
  가치를 인정해 run lock을 함께 채택하기로 결정.
- **분산 락(ShedLock, Redis 등)** — 기각. 현재 단일 인스턴스 MVP(ADR-0011과
  동일 근거)이고, `ReentrantLock`으로 단일 인스턴스 내 상호 배제는 충분하다.
- **수동 API가 락 경합 시 대기(블로킹)하거나 "실행 중" 상태를 응답에
  명시** — 기각(사용자 선택, 즉시 거절/409 채택). 대기는 5,000건 처리 중
  HTTP 요청이 수십 초~수 분 걸릴 수 있고, 응답에 상태 필드를 추가하는
  것은 기존 `CollectResult` 계약을 변경해야 해서 즉시 거절이 가장 단순하고
  명확하다고 판단했다.

**이유**: correctness(DB UNIQUE)와 optimization(run lock)을 명확히 분리해서
설계했다 — 하나가 실패해도 다른 하나가 데이터 무결성을 지킨다. 실제
transaction boundary를 추측 없이 코드로 직접 확인한 뒤 exception catch/
re-read를 선택해 기존 100% JPA 기반 아키텍처와 native SQL 도입 없이
문제를 해결했다.

**영향**: `POST /api/collect/alio`는 이제 다른 collection run이 진행 중이면
HTTP 409를 반환할 수 있다(신규 API 계약, 기존 200/400/502는 변경 없음).
`AlioDetailEnrichmentService`의 동시성 취약점은 데이터 손상 위험 없이
남아 있으며, 트랜잭션 재구조화가 필요한 후속 Task로 `docs/ROADMAP.md`에
기록한다.

---

## ADR-0016: `JobApplication` 도메인 — status를 실제 Java enum으로,
## 중복 등록은 idempotent 흡수 대신 409 Conflict로 거부

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: APPLICATION-001

**문제 1 — `status` 표현**: `JobPosting.status`는 지금까지 plain `String`으로
ALIO 원본 값을 그대로 저장해왔다(`"OPEN"`/`"CLOSED"` 등, 외부 API가 실제로
주는 값을 신뢰). `JobApplication.status`도 같은 컨벤션을 그대로 따를지,
아니면 다른 표현을 쓸지 정해야 했다.

**결정 1**: `JobApplication.status`는 `String`이 아니라 실제 Java
`enum ApplicationStatus`(`@Enumerated(EnumType.STRING)`)로 만든다. 값은
`INTERESTED`/`PLANNED`/`SUBMITTED`/`OFFERED`/`REJECTED`/`WITHDRAWN` 6개로
최소화한다. `IN_PROGRESS`(의미가 모호하고 향후 확장과 겹침), `DOCUMENT`/
`WRITTEN`/`INTERVIEW`(지원 상태가 아니라 전형 단계)는 포함하지 않는다 —
`status`는 "이 지원이 전체적으로 어느 국면인가"라는 요약값 역할만 하고,
전형 단계별 일정/결과(서류/필기/면접 각각의 `scheduledAt`/`result`)는
향후 APPLICATION-002의 별도 `ApplicationStage` 모델이 담당하도록 역할을
분리해둔다.

**대안**: `JobPosting.status`와 동일하게 plain `String` — 기각.
`JobPosting.status`가 `String`인 이유는 "외부(ALIO) 원본 값을 그대로
반영해야 하는 신뢰 경계"이기 때문이지, 이 프로젝트의 일반 컨벤션이
아니다. `JobApplication.status`는 전적으로 내부 통제 어휘라 이 근거가
적용되지 않는다 — 오히려 enum을 쓰면 컴파일 타임 안정성과 잘못된 값의
자동 400 처리(Jackson deserialization 실패)를 얻을 수 있어 String을
고수할 이유가 없다.

---

**문제 2 — 동일 `JobPosting`에 대한 중복 `JobApplication` 등록**: 사용자가
같은 공고를 실수로(또는 의도적으로) 두 번 "지원 관리에 추가"하려는 요청을
어떻게 처리할지 정해야 했다. COLLECT-006(ADR-0015)에는 이미 비슷한 상황
— 동일 `(source, external_id)` 중복 저장 시도 — 에 대한 선례
(`JobPostingService.createOrGetExisting()`, 예외 catch 후 기존 row로
조용히 합류, idempotent)가 있었다.

**결정 2**: `JobApplication` 생성은 **COLLECT-006 선례를 그대로 따르지
않는다.** 대신 애플리케이션 사전 체크(`existsByJobPostingId`)와 DB
`UNIQUE(job_posting_id)` 제약 위반(`DataIntegrityViolationException`)
둘 다에서 **기존 row를 반환하지 않고 HTTP 409 Conflict로 명시적으로
거부**한다.

**대안**: `createOrGetExisting()`과 동일하게 200 + 기존 row 반환(idempotent)
— 기각(사용자 선택). COLLECT-006의 중복은 **시스템(ALIO 수집기)이 동시
실행되며 만든 데이터 레벨 아티팩트**라 사용자가 인지할 필요 없이 조용히
정리되는 것이 맞았다. `JobApplication` 생성은 **사용자가 직접 트리거하는
명시적 액션**(예: "지원 관리에 추가" 버튼)이라 성격이 다르다 — 두 번째
시도가 "이미 등록돼 있다"는 사실 자체가 사용자에게 유의미한 정보이고,
이를 성공(200)으로 숨기면 클라이언트/사용자가 자신의 실수(중복 클릭 등)를
인지할 기회를 잃는다. COLLECT-006의 수동 수집 API가 락 경합 시 409를
반환하는 기존 컨벤션과도 일관된다.

---

**문제 3 — FK cascade**: `job_applications.job_posting_id`가 삭제되는
`JobPosting`을 어떻게 처리할지(`ON DELETE CASCADE`/`RESTRICT`/미지정)
정해야 했다. 현재 `JobPosting` 삭제 API 자체가 없어 당장 실행 경로는
없지만, migration/entity 설계 시점에 결정해두지 않으면 향후 삭제 API가
추가될 때 조용히 잘못된 기본값(무제약 CASCADE 등)이 들어갈 위험이 있다.

**결정 3**: `job_posting_id` FK에 `ON DELETE` 절을 넣지 않는다(Postgres
기본 `NO ACTION` — 자식 row가 있으면 부모 삭제가 거부됨). Entity에도
`CascadeType.ALL` 등을 붙이지 않는다. `recruitment_steps`/`attachments`
(V3, COLLECT-004)가 이미 같은 방식으로 `JobPosting` FK를 다루고 있어
컨벤션을 그대로 재사용한다.

**대안**: `ON DELETE CASCADE` — 기각. `JobPosting`을 지우면 사용자의
지원 이력(`status`/`memo`/`appliedAt`)이 조용히 함께 사라지는 것은
"AI가 사용자 데이터를 임의로 손실시키지 않는다"는 프로젝트 원칙과
충돌한다. 향후 `JobPosting` 삭제 API가 필요해지면, 연결된
`JobApplication`이 있는 경우 어떻게 할지(삭제 차단/사용자에게 명시적
확인/soft delete 등)를 그 Task에서 별도로 설계해야 한다 — 지금 결정은
"기본값으로 사용자 데이터를 잃지 않는다"는 것뿐이다.

**이유(종합)**: 세 결정 모두 "기존 선례를 무비판적으로 복제하지 않고,
지금 다루는 데이터/액션의 실제 성격(외부 신뢰 경계 vs 내부 통제 어휘,
시스템 자동 프로세스 vs 사용자 명시적 액션, 수집 데이터 vs 사용자 소유
기록)에 맞춰 판단한다"는 같은 원칙에서 나왔다.

**영향**: `POST /api/applications`는 `POST /api/jobs`나
`JobPostingService.createOrGetExisting()`과 달리 중복 시 항상 409를
반환하는 새로운 API 계약이 생긴다. `ApplicationStatus`에 새 값이
필요해지면(예: APPLICATION-002 확장) 이 ADR의 "역할 분리" 원칙을 먼저
재확인해야 한다 — 전형 단계 정보를 `status`에 다시 밀어넣지 않는다.

---

## ADR-0017: `ApplicationStage` — `JobApplication` 삭제 시 `ON DELETE CASCADE`
## (ADR-0016의 `JobPosting`→`JobApplication` NO ACTION 컨벤션과 의도적으로 다름)

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: APPLICATION-002

**문제**: `application_stages.job_application_id` FK가 삭제되는
`JobApplication`을 어떻게 처리할지 정해야 한다. ADR-0016(문제 3)은
`job_applications.job_posting_id`에 `ON DELETE` 절을 넣지 않기로(NO ACTION)
결정했었다 — 이 선례를 `ApplicationStage`에도 기계적으로 재적용할지가
쟁점이었다.

**결정**: `application_stages.job_application_id` FK에는 **`ON DELETE
CASCADE`**를 사용한다. `JobApplication`을 삭제하면 그 안의 모든
`ApplicationStage`도 함께 삭제된다. Entity에는 `CascadeType`/양방향
`@OneToMany` 컬렉션을 추가하지 않는다 — cascade는 DB 제약(FK) 수준에서만
처리하고, `JobApplication`은 `ApplicationStage`에 대한 역참조 필드를
갖지 않는다(`RecruitmentStep`/`Attachment`가 `JobPosting`을 역참조하지
않는 기존 패턴과 동일).

**대안**: ADR-0016과 동일하게 `ON DELETE` 절 없음(NO ACTION) — 기각.
`JobPosting`→`JobApplication` 관계와 `JobApplication`→`ApplicationStage`
관계는 겉보기엔 같은 부모-자식 FK 구조지만 실제 성격이 다르다:
- `JobPosting`은 외부(ALIO)가 소유한 수집 데이터이고, `JobApplication`은
  사용자가 그 공고에 대해 만든 독립적인 기록(지원 여부/상태/메모)이다 —
  `JobPosting`이 없어져도 "내가 그 공고에 지원했었다"는 사용자 기록은
  그 자체로 의미가 있어 보존해야 한다(ADR-0016 결정 3의 근거).
- 반면 `ApplicationStage`는 `JobApplication` 없이는 어떤 독립적 의미도
  갖지 않는다 — "서류 전형 일정"이라는 데이터는 그것이 어느 지원 건에
  속하는지를 전제로만 존재한다. 진짜 aggregate 내부 구성요소이지, 그
  자체로 사용자가 보존하고 싶어할 별도 레코드가 아니다. `JobApplication`을
  삭제하면서 그 안의 `ApplicationStage`가 고아로 남으면(`ON DELETE
  RESTRICT`처럼 삭제 자체를 막거나, 방치해 orphan row로 남기거나) 오히려
  "지원을 삭제하려면 먼저 모든 전형 단계를 하나씩 수동으로 지워야 한다"는
  불필요한 마찰만 생긴다.

**이유**: "이전 Task의 FK 정책을 무비판적으로 복제하지 않고, 지금 다루는
관계의 실제 성격(사용자 소유 기록 간의 참조 vs 진짜 단일 aggregate 내부
구성요소)에 맞춰 판단한다"는 ADR-0016의 종합 원칙을 그대로 재적용한
결과다. `ApplicationStage`는 `JobApplication`의 일부이지 별개의 사용자
기록이 아니므로 CASCADE가 데이터 손실 우려 없이 자연스럽다.

**영향**: `DELETE /api/applications/{id}`(APPLICATION-001에서 이미 존재하는
엔드포인트)는 이제 그 `JobApplication`에 속한 모든 `ApplicationStage`도
암묵적으로 함께 삭제한다 — 애플리케이션 코드에서 별도로 자식을 먼저 지우는
로직을 작성할 필요가 없다. 향후 `ApplicationStage`가 다른 도메인(예:
Calendar 이벤트)의 참조 대상이 되면, 그 시점에 이 CASCADE가 그 도메인에도
연쇄적으로 영향을 주는지 재검토해야 한다.

---

## ADR-0018: PKB 데이터 모델 — 범용 `CareerItem` 대신 경험 중심
## `CareerExperience`(Certification/Education/Award 제외)

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-001

**문제**: PKB(Personal Knowledge Base) 핵심 도메인을 시작하며, 사용자의
프로젝트/활동/업무/연구 경험을 저장할 스키마를 두 방식 중 하나로 정해야
했다 — (A) 자격증/학력/수상까지 포함하는 범용 `CareerItem`(단일 테이블 +
`type` discriminator) 또는 (B) 서사형 경험(PROJECT/ACTIVITY/WORK/RESEARCH/
OTHER)만 다루는 `CareerExperience`(자격증/학력/수상은 이번 Phase에 만들지
않고 필요 시점에 별도 엔티티로 추가). 이번 Phase의 유일한 목표는 "사람이
입력한 경험 데이터의 안정적 저장/조회"이고, PKB의 다음 소비자는 "공고↔경험
매칭"과 "자소서 경험 추천"이다.

**결정**: **`CareerExperience`(경험 중심, 좁은 모델)**를 채택한다.
`ExperienceType` enum은 `PROJECT/ACTIVITY/WORK/RESEARCH/OTHER` 5종만
갖는다. `Certification`/`Education`/`Award`는 이번 Phase에서 어떤
테이블/필드도 만들지 않는다.

**대안**:
- **범용 `CareerItem`**(자격증/학력/수상까지 `type`으로 통합) — 기각. (1)
  자격증/학력/수상은 evidence(불릿)/skill 태그가 사실상 항상 비어 있는
  이질적 데이터라 같은 테이블에 밀어 넣으면 검증 로직이 결국 `type`별로
  갈라진다. (2) 다음 단계인 "공고↔경험 매칭"/"자소서 경험 추천"은 결국
  서사형 경험만 필요해 `type IN (PROJECT, ACTIVITY, WORK, RESEARCH)` 필터가
  필요하므로 통합의 이점이 사라진다. (3) 지금 소비자가 없는
  자격증/학력/수상 스키마(발급기관/자격번호/등급/수상순위 등)를 미리
  설계하는 것은 "과도한 추상화·불필요한 패턴을 피한다"(AGENTS.md) 원칙과
  충돌한다.

**이유**: MVP 단순성 우선 + 다음 단계 소비자(매칭/추천)가 실제로 필요로
하는 데이터 형태에 정확히 맞춘 선택이다. 자격증/학력/수상이 실제로
필요해지는 시점(적합도 판단/자소서 Phase)에 additive migration으로 별도
엔티티를 추가하는 비용이, 지금 안 쓰는 필드를 미리 설계/검증하는 비용보다
작다.

**영향**: 향후 자격증/학력/수상을 PKB에 편입하려면 `CareerExperience`와는
독립된 새 엔티티(들)를 추가해야 한다 — 기존 `CareerExperience` 스키마
변경은 필요 없다. 공고 매칭/자소서 추천 설계 시 `CareerExperience`만이
매칭 대상 데이터임을 전제로 삼는다.

---

## ADR-0020: PKB `Certification`/`Education`/`Award` — 독립 entity 3개,
## 상속·generic CRUD 배제 + GPA/degree·status 구조화 + Award-CareerExperience FK 보류

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-002, PKB-003, PKB-004

**문제**: ADR-0018에서 미룬 자격증/학력/수상을 PKB-001 완료 후 구조화하는
Phase(PKB-002)를 시작하며 네 가지 설계 판단이 필요했다. (1) 세 도메인이
구조적으로 비슷한 CRUD(생성/목록/단건/PATCH/삭제)라는 이유로 상속 구조나
generic `Repository`/`Service`/`Controller`를 도입할지, 아니면 명시적으로
독립된 entity 3개로 둘지. (2) `Education`의 학점을 `BigDecimal`로 구조화할지
자유 텍스트로 둘지. (3) `Education.degree`/`status`를 enum으로 강제할지
String으로 둘지. (4) `Award`와 `CareerExperience`(우수상↔수상 프로젝트) 사이에
지금 FK를 만들지.

**결정**:
1. `Certification`/`Education`/`Award`를 각각 독립된 entity + Repository +
   Service + Controller + DTO 세트로 만든다. 공통 부모 클래스, generic
   `Repository<T, ID>` 확장, 공용 CRUD 추상화를 도입하지 않는다.
2. `Education.gpa`/`gpaScale`을 `BigDecimal` 2컬럼(둘 다 nullable)으로
   구조화한다.
3. `Education.degree`(`HIGH_SCHOOL/ASSOCIATE/BACHELOR/MASTER/DOCTORATE/OTHER`)와
   `status`(`ENROLLED/ON_LEAVE/GRADUATED/EXPECTED_GRADUATION/WITHDRAWN`)를
   `EnumType.STRING` enum으로 강제한다. 둘 다 nullable — `OTHER`는
   고정 enum이 못 담는 값을 위한 escape hatch일 뿐, 값 자체를 필수로
   강제하지 않는다.
4. `Award`와 `CareerExperience` 사이에 FK를 만들지 않는다 — 독립 entity로
   유지한다.

**대안**:
- **상속 구조 / generic CRUD**(공통 부모 엔티티, `GenericPkbRepository<T>`
  등) — 기각. 세 도메인의 필드가 근본적으로 다르고(`credentialId`는
  Certification 전용, `gpa`/`gpaScale`은 Education 전용), validation
  규칙도 도메인마다 다르다(날짜 비교 vs GPA 비교 vs 없음). 공통 추상화를
  만들면 오히려 도메인별 특수 규칙을 표현하기 위한 우회가 필요해지고,
  `JobApplication`/`ApplicationStage`/`CareerExperience`가 이미 유지해온
  "명시적으로 작은 도메인" 컨벤션과도 어긋난다.
- **GPA를 String 자유 표기**(`"3.8/4.5"`) — 기각. 검증 로직이 필요 없어
  더 단순하지만, 향후 매칭(MATCH-001 후보)에서 숫자 비교가 필요해질
  가능성이 있고 지금 구조화하는 비용이 낮다.
- **degree/status를 String으로 둠** — 기각(ADR-0016의 "내부 통제 어휘는
  실제 Java enum, 외부 원본 신뢰 데이터는 String" 원칙 재적용). 다만
  해외 학위·특수 과정 등 고정 enum이 못 담는 값을 위해 `OTHER`를 둔다.
- **`Award.careerExperienceId` nullable FK를 지금 추가** — 기각. 이
  관계를 소비할 로직(UI/매칭)이 이번 Phase에 전혀 없고, 방향성(단순
  FK/역방향/조인 테이블) 자체도 아직 불확실해 지금 정하면 나중에 틀릴
  위험이 크다. "이 프로젝트로 받은 상"이라는 서사는 이미 `Award.description`
  자유 텍스트로 표현 가능하다. Additive migration으로 나중에 추가하는
  비용은 낮다.

**이유**: PKB-001부터 유지해온 "명시적인 작은 도메인 우선, 과도한
추상화·불필요한 패턴 회피"(AGENTS.md) 원칙을 반복 적용했다. GPA/enum
구조화는 향후 매칭 소비자가 비교적 명확히 예견되는 필드에 한해서만
선제 투자하고, 소비자가 불확실한 FK는 만들지 않았다.

**영향**: 앞으로 구조적으로 비슷한 CRUD 도메인이 더 늘어나도(예: 향후
`ProfileFact` 후보) 상속/generic 구조를 기본값으로 삼지 않는다 — 매번
"정말 공통인가"를 먼저 판단한다. `Certification`/`Education`/`Award`
스키마는 매칭(MATCH-001 후보) 설계 시 `JobPosting`과의 FK 없이 참조되는
것을 전제로 한다. `Award`↔`CareerExperience` 관계가 실제로 필요해지면
별도 Task에서 additive migration으로 판단한다.

---

## ADR-0019: `CareerExperience` 저장에 이 프로젝트 최초로
## Service-level `@Transactional` 도입 (ADR-0015 "no @Transactional" 전제의 예외)

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-001

**문제**: ADR-0015에서 확인했듯 이 프로젝트는 지금까지
`@Transactional`을 전혀 쓰지 않았다 — 모든 요청이 "read 여러 번 + 최종
write 1회"로 끝나, `repository.save()`가 여는 독립적인 짧은 트랜잭션만으로
충분했다. 그런데 `CareerExperience` 생성/수정은 부모 1행(`career_experiences`)
+ 자식 N행(`experience_bullets`) + 자식 M행(`experience_tags`)을 한 요청
안에서 함께 쓰는 **진짜 다중-row 원자적 쓰기**다. 이 전제가 처음으로
깨진다.

**결정**: `CareerExperienceService.create()`/`update()`/`delete()`에
Service-level `@Transactional`을 적용한다. 부모 저장 + 자식(bullets/tags)
전체 교체(delete-then-insert)가 하나의 트랜잭션 안에서 원자적으로
커밋/롤백되도록 한다.

**대안**:
- **기존처럼 `@Transactional` 없이 각 `save()`를 독립 트랜잭션으로 실행** —
  기각. 자식 저장 중 하나가 실패(예상치 못한 제약 위반 등)하면 부모만
  저장되고 자식은 일부만 저장된 깨진 상태가 사용자 눈에 보이지 않게
  남을 수 있다. 백그라운드 수집기(부분 실패를 다음 스케줄 재시도로 흡수
  가능, ADR-0011)와 달리, 사용자가 직접 입력하는 CRUD 폼에서는 부분 실패가
  곧 눈에 보이는 데이터 손상으로 인식된다 — 재시도로 자연 치유되지 않는다.
- **자식을 별도 API로 분리해 `@Transactional` 자체를 회피**(APPLICATION-002의
  `ApplicationStage`처럼 부모와 자식을 별개 요청으로 생성) — 기각(설계
  단계에서 검토, PKB-001/PKB-002 분리 여부로 사용자에게 제시했으나 한
  Task로 유지하기로 결정). bullets/tags 없는 `CareerExperience`는 "자소서
  경험 검색"이라는 PKB 이번 Phase의 존재 이유를 사실상 충족하지 못해,
  분리는 문제를 회피할 뿐 근본적으로 해결하지 않는다고 판단했다.

**이유**: ADR-0015/APPLICATION-002가 유지해온 "read 여러 번 + write
1회"라는 안전 전제가 이번에는 실제로 성립하지 않으므로, 그 전제 위에서
유효했던 무-트랜잭션 컨벤션을 기계적으로 재적용하지 않고 실제 쓰기
패턴에 맞춰 판단했다. `sortOrder`/`UNIQUE` 기반 race 방지(ADR-0015/0016
계열)와는 별개 문제다 — 이번은 동시성 race가 아니라 단일 요청 내 다중
row 쓰기의 원자성 문제다.

**영향**: 이 프로젝트에서 `@Transactional`이 등장하는 첫 사례다. 앞으로
"부모+자식을 한 요청에서 함께 쓰는" 유사한 도메인이 생기면 이 ADR을
전례로 참고해 `@Transactional` 도입 여부를 판단한다 — 모든 Service에
기본으로 붙이는 것은 아니며, 여전히 "read만 하거나 단일 write로 끝나는"
기존 Service(예: `JobApplicationService`, `ApplicationStageService`)는
무-트랜잭션 컨벤션을 유지한다.

---

## ADR-0021: PKB 문서 Import 파이프라인 v0 — 3계층 provenance 모델
## (`SourceDocument`/`ImportBatch`/`ImportCandidate`) + 승인 전 저장 금지

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-005, PKB-006(예정)

**문제(총괄)**: PKB(`CareerExperience`/`Certification`/`Education`/`Award`,
PKB-001~004)는 지금까지 사람이 API로 직접 입력한 데이터만 저장해왔다.
이번 Phase부터 "사용자가 붙여넣은 문서 원문 → (향후 AI가 추출한) 후보
데이터 → 사람이 검토/승인 → PKB에 반영"이라는 import 파이프라인을
설계해야 한다. AGENTS.md의 핵심 제약("AI가 사용자가 하지 않은 경험/수치를
만들어내지 못하게 막는다")상, 이 파이프라인은 **AI/사람이 만든 후보를
검토 없이 곧바로 PKB의 확정 사실로 저장하는 경로를 하나도 가져서는
안 된다.** 이를 위해 서로 연결된 7개의 하위 설계 결정이 필요했다.

**결정**:

1. **Provenance를 3개 테이블로 분리한다**(`SourceDocument`/`ImportBatch`/
   `ImportCandidate`) — 병합하지 않는다. `SourceDocument`는 사용자가
   등록한 원문(문서)의 정체성, `ImportBatch`는 그 문서에 대한 "1회
   검토/추출 시도" 단위, `ImportCandidate`는 그 시도에서 나온 개별 검토
   대상이다. 같은 문서를 다시 분석(재import)하는 경우 새
   `ImportBatch`만 추가되고 `SourceDocument`는 재사용된다.
2. **`ImportCandidate.payload`는 JSON을 담은 `TEXT` 컬럼**으로 저장한다
   (`CareerExperience`/`Certification`/`Education`/`Award`를 각각 그대로
   복제한 4개의 정형 candidate 테이블을 만들지 않는다). 서비스 레이어가
   Jackson으로 직렬화/역직렬화하고, 승인 시점에 대상 도메인의 기존
   `*CreateRequest` record로 재역직렬화 + `@Valid` 재검증한다.
3. **`career` 패키지는 `pkbimport` 패키지를 알지 못하는 단방향 의존만
   허용**한다. Provenance는 `career_experiences`/`career_certifications`/
   `career_educations`/`career_awards`에 각각 `source_type`(enum,
   MANUAL/IMPORT)과 `source_import_candidate_id`(plain `Long` 컬럼, DB
   FK는 있지만 JPA `@ManyToOne` 관계는 만들지 않음)를 직접 추가해 기록한다.
   별도 polymorphic provenance 관계 테이블은 만들지 않는다.
4. **파일 업로드(multipart)/PDF·DOCX 텍스트 추출/LLM 기반 구조화 추출은
   이번 Phase(PKB-005/PKB-006)에서 명시적으로 배제**한다. `docs/ROADMAP.md`에
   PKB-007(파일 업로드+텍스트 추출)/PKB-008(LLM 구조화 추출) 후보로만
   남기고, 이번엔 사용자가 원문 텍스트를 직접 붙여넣는 경로만 만든다.
5. **동일 `ImportCandidate`의 재승인/재거부 방지는 DB UNIQUE 제약이 아니라
   애플리케이션 상태 체크**(`status`가 `PENDING`일 때만 승인/거부 가능,
   아니면 409)로 구현한다.
6. **문서 재import는 막지 않는다.** `SourceDocument.contentHash`(원문의
   SHA-256)는 dedup을 강제하는 DB UNIQUE 제약이 아니라 참고용 정보로만
   저장한다.
7. **기존 PKB row(PKB-006에서 provenance 컬럼이 실제로 추가될 때)는
   전부 `source_type = 'MANUAL'`로 채운다.** 이 값은 "임의로 지어낸
   기본값"이 아니라 **사실 그 자체**다 — 이번 Phase 이전에는 import
   기능 자체가 존재하지 않았으므로, 지금까지 저장된 모든 row는 실제로
   전부 사람이 API로 직접 입력한 것이다. `source_import_candidate_id`는
   기존 row와 향후 수동 생성 row 모두 `NULL`로 유지하고, 존재하지 않는
   candidate 참조를 임의로 만들지 않는다.

**대안**:
- (1) **단일 테이블로 병합**(`import_batches`에 `fileName`/`documentType`/
  `contentHash`까지 통합) — 기각. "같은 문서를 다시 분석"이 1:1이 되어,
  실제로 필요해지는 시점(PKB-008)에 테이블을 다시 쪼개는 마이그레이션이
  필요해진다.
- (2) **4개 정형 candidate 테이블**(각 대상 entity의 필드를 그대로 복제) —
  기각. entity/migration/DTO가 4배로 늘어나는데, 검토 워크플로우(제안→
  목록→승인/거부) 자체는 4개 모두 동일해 구조적 이득이 적다(ADR-0020의
  "정말 공통인가를 먼저 판단한다" 원칙 재적용). payload 내부를 SQL로
  질의할 요구도 없다.
- (2) **native `jsonb` 컬럼 타입 매핑** — 기각(이번 시점). 이 프로젝트
  첫 semi-structured 컬럼인데, payload 내부를 질의할 필요가 전혀 없어
  이점이 없고, Boot 4.1/Hibernate 7의 또 다른 미검증 영역(`ARCHITECTURE.md`의
  누적되는 Boot 4.1 함정 목록)을 새로 열 이유가 없다.
- (3) **별도 polymorphic provenance 관계 테이블**(target_type+target_id
  조합으로 4개 테이블을 참조) — 기각. 4개의 서로 다른 테이블에 대한 진짜
  참조 무결성을 가질 수 없는 polymorphic FK이고, MVP 규모에 과한
  추상화다.
- (4) **PDF/DOCX 파싱, LLM 추출까지 한 Task/Phase에 포함** — 기각. 파싱은
  새 프로덕션 dependency(PDFBox/POI/Tika 등) 선택이 필요하고, LLM
  추출은 이 프로젝트에 아직 전혀 없는 product-facing AI provider 도입
  결정(provider 선택/구조화 출력/할루시네이션 방지)이 필요해 그 자체로
  각각 별도 ADR급 판단이다. 이 둘을 provenance+검토/승인 게이트와 묶으면,
  가장 안정적으로 검증되어야 할 안전장치(검토/승인)가 가장 불안정한
  부분(prompt 튜닝, provider 이슈)에 종속된다.
- (5) **재승인/재거부 방지를 DB 제약으로 표현** — 기각. "PENDING에서만
  전이 가능"은 uniqueness 문제가 아니라 상태 머신 문제라 DB UNIQUE로
  자연스럽게 표현되지 않는다. `@Transactional` 메서드 안에서 조회 후
  상태를 확인하고 쓰는 것으로 충분하다(ADR-0016의 409 선례와 동일한
  수준의 보호, 이 프로젝트의 낮은 동시성 전제에서 충분).
- (6) **동일 `contentHash` 재등록을 DB UNIQUE로 차단** — 기각. 같은 원문을
  다른 `documentType`으로 다시 등록하는 것도 정당한 사용일 수 있고,
  semantic dedup(비슷하지만 다른 문서)은 이번 범위 밖이라 hash 기반
  강제 차단만 도입하는 것은 어중간하다.
- (7) **기존 row의 `source_type`을 비워두거나(`nullable`) `UNKNOWN`으로
  채움** — 기각. 실제로 아는 사실(전부 수동 입력이었다)을 두고 불확실한
  값을 쓰는 것은 오히려 부정확하다.

**이유(종합)**: 이 프로젝트가 지켜온 "명시적으로 작은 도메인 우선,
소비자 불확실한 필드/관계는 만들지 않는다"(ADR-0018/0020) 원칙과, 이번
Phase의 진짜 핵심 제약("AI 추출 결과를 검토 없이 확정 사실로 저장하지
않는다")을 동시에 만족시키는 조합이다. provenance/candidate 모델은
파일 업로드나 LLM 없이도 그 자체로 완결되게 설계해, 검토/승인 게이트가
아직 존재하지 않는 자동 추출 기능과 무관하게 먼저 검증될 수 있게 했다.

**영향**: PKB-005는 `SourceDocument`/`ImportBatch`(사용자가 원문 텍스트를
직접 등록)만 다루고, `ImportCandidate`와 4개 PKB entity의 provenance
컬럼은 PKB-006에서 추가된다. PKB-007(파일 업로드+텍스트 추출)/PKB-008(LLM
구조화 추출)이 실제로 착수될 때, 이번 ADR의 (1)(SourceDocument/ImportBatch
재사용), (6)(contentHash 참고용 유지) 결정을 전제로 설계해야 한다 — 이때도
LLM이 만든 candidate가 승인 없이 곧바로 PKB에 쓰이는 경로를 추가해서는
안 된다(이 ADR의 핵심 제약은 향후 Task에도 그대로 적용된다).

---

## ADR-0022: `ImportCandidate` 검토/승인 흐름 — terminal 3-state,
## conditional UPDATE 기반 concurrency, `career` Service 오버로드 재사용,
## `ImportBatch` 명시적 complete + invariant 강제

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-006

**문제**: ADR-0021이 `ImportCandidate`의 큰 그림(payload를 JSON TEXT로,
provenance를 career entity에 직접 컬럼으로, 재승인/재거부 방지를 DB
UNIQUE가 아니라 애플리케이션 상태 체크로)을 이미 확정했지만, PKB-006을
실제로 구현하려면 그 "애플리케이션 상태 체크"를 정확히 어떻게 구현해야
동시 요청에서도 안전한지, candidate 상태 전이를 어디까지 허용할지,
기존 `career` 4개 Service의 생성 로직을 어떻게 재사용할지, 그리고
`ImportBatch.status`를 언제 `COMPLETED`로 전이시킬지가 남아 있었다.

**결정**:

1. **`ImportCandidateStatus`는 `PENDING`/`APPROVED`/`REJECTED` terminal
   3-state**다. `PENDING→APPROVED`, `PENDING→REJECTED`만 허용하고
   `APPROVED`/`REJECTED` 양쪽 다 어떤 상태로도 되돌릴 수 없다(재전이 요청은
   전부 409). 같은 내용을 다시 검토하고 싶으면 같은 payload로 새
   `ImportCandidate`를 다시 생성한다.
2. **Approve/Reject concurrency는 "먼저 원자적 conditional UPDATE, 실패 시
   진단"** 패턴으로 해결한다 — `UPDATE import_candidates SET status=:new,
   reviewed_at=:now WHERE id=:id AND import_batch_id=:batchId AND
   status='PENDING'`을 트랜잭션의 첫 statement로 실행하고 영향 row 수를
   확인한다. 0건이면 그제서야 원인(404/409)을 구분하는 조회를 한다. 이
   UPDATE 자체가 Postgres에서 대상 row에 암묵적 row-level lock을 걸기
   때문에, 동시에 들어온 두 approve 요청 중 하나는 반드시 다른 하나의
   커밋을 기다린 뒤 최신 상태로 WHERE 절을 재평가하게 되어 PKB row가 두 개
   생성되는 경우가 구조적으로 불가능하다. 별도 `@Lock(PESSIMISTIC_WRITE)`
   애노테이션 없이 이 보장을 얻는다. Approve는 이 UPDATE가 성공한 뒤
   같은 트랜잭션 안에서 payload 재검증 → 대상 `career` Service 호출 → PKB
   row 생성 → `createdEntityId` 기록까지 이어간다. 중간 어디서든 예외가
   나면 `@Transactional`이 1번의 상태 변경까지 포함해 전부 롤백한다.
3. **`career` 4개 Service에 provenance 인자를 받는 오버로드를 추가**한다
   (`create(request)` → `create(request, SourceType.MANUAL, null)`로
   위임, `pkbimport`가 `create(request, SourceType.IMPORT,
   candidateId)`를 호출). 기존 HTTP Controller/DTO/validation/business
   rule은 전혀 복제하지 않고 그대로 재사용한다. `SourceType` enum은
   provenance 컬럼을 소유하는 `career` 패키지에 둔다(`pkbimport`가
   `career`를 아는 단방향 의존은 ADR-0021이 이미 허용).
4. **`ImportBatch.status`는 명시적 `POST
   .../batches/{id}/complete` API로만 `OPEN→COMPLETED` 전이**한다(자동
   완료 없음). `COMPLETED`는 "그 batch의 검토 작업이 끝났다"는 의미의
   terminal 상태로 정의하고, 다음 불변식을 코드/DB 양쪽에서 강제한다:
   - complete 요청 시점에 그 batch에 `PENDING` candidate가 하나라도
     있으면 409(강제로 미완료 처리하지 않음).
   - 이미 `COMPLETED`인 batch를 다시 complete 요청하면 409.
   - `COMPLETED`인 batch에는 새 candidate를 생성할 수 없다 — 시도하면
     409.
   - reopen(`COMPLETED→OPEN`) API는 이번 Phase에 만들지 않는다. 같은
     `SourceDocument`를 다시 분석하거나 candidate를 더 추가하고 싶으면
     새 `ImportBatch`를 만든다 — `ImportBatch`는 "1회의 import/review
     시도"라는 의미를 계속 유지한다.

   이 불변식("COMPLETED batch는 PENDING candidate를 가질 수 없다")을 두
   경로(사람이 동시에 complete 요청 + candidate 생성 요청을 보내는 경우)
   양쪽에서 깨지지 않게 하려고, `complete()`는 `UPDATE import_batches SET
   status='COMPLETED', completed_at=:now WHERE id=:id AND status='OPEN'
   AND NOT EXISTS (SELECT 1 FROM import_candidates WHERE
   import_batch_id=:id AND status='PENDING')` 원자적 UPDATE 하나로
   구현하고, candidate 생성 경로는 그 직전에 부모 `ImportBatch` row를
   `SELECT ... FOR UPDATE`(pessimistic write lock, 짧은 구간만)로 잠근 뒤
   `status==OPEN`을 확인하고 삽입한다. 두 경로가 같은 `import_batches` row
   lock을 두고 경쟁하므로, 어느 쪽이 먼저 커밋되든 나중 쪽은 항상 최신
   상태를 보고 올바르게 성공/409를 결정한다 — 이 lock 덕분에
   approve/reject는 "COMPLETED batch에는 PENDING candidate가 없다"는
   불변식이 이미 구조적으로 보장되어, 별도로 batch 상태를 다시 확인하는
   코드를 추가하지 않아도 된다(2번의 candidate-level 상태 체크만으로
   충분).
5. **schema 변경 없음** — `import_batches.status`/`completed_at` 컬럼은
   PKB-005(V12)에서 이미 만들어져 있다(`COMPLETED`는 그때 예약만 해둔
   값). 이번 Task는 `ImportBatchService`/`ImportBatchController`에 로직만
   추가한다.

**대안**:
- **`REJECTED→APPROVED` 허용**(재검토 경로 제공) — 기각. PKB에 이미
  반영된 `APPROVED`와 달리 `REJECTED`는 되돌릴 이유가 있어 보이지만, 이를
  허용하면 "언제 그 candidate가 실제로 검토 완료됐다고 볼 것인가"가
  모호해지고 `ImportBatch.complete()`의 "PENDING candidate 없음" 불변식과도
  상호작용이 복잡해진다. 같은 payload로 새 candidate를 만드는 비용이
  거의 0이라 이 복잡도를 감수할 이유가 없다.
- **Approve concurrency를 `SELECT`(상태 확인) 후 `UPDATE`로 구현** — 기각.
  COLLECT-006에서 이미 겪은 check-then-act race를 반복한다. 조건부
  UPDATE를 먼저 실행하는 순서만 바꾸면 별도 락 없이 같은 보장을 얻을 수
  있어 채택하지 않을 이유가 없다.
- **`ImportBatch`도 모든 candidate가 terminal이 되면 자동 COMPLETED** —
  기각(사용자 결정). candidate는 이번 Phase에 사람이 API로 하나씩
  추가하므로, "지금 0개가 PENDING"과 "사용자가 더 이상 추가할 계획이
  없다"는 서로 다른 사실이다. 자동 완료는 후자를 시스템이 추론하는
  것인데 이번 Phase는 그 신호가 없다. 명시적 API가 이 모호함을
  없앤다.
- **`COMPLETED` batch에도 candidate 추가를 허용**(complete는 단순
  마킹일 뿐 강제 없음) — 기각(사용자 결정). "COMPLETED = 그 batch의
  검토 작업이 끝났다"는 의미를 실제로 강제하지 않으면 상태값이 정보성
  라벨에 지나지 않게 되고, `candidate가 새로 생겼는데 이미 검토가
  끝났다고 표시된 batch`라는 모순된 상태가 허용된다.
- **candidate 생성 시 배치 상태 확인을 `SELECT`(락 없음)로 처리** — 기각.
  `complete()`의 conditional UPDATE와 경쟁하는 유일한 다른 쓰기 경로라,
  락 없이 확인하면 두 요청이 동시에 서로 다른 스냅샷을 보고 둘 다
  성공해버리는 race가 남는다. 단일 row에 대한 짧은 pessimistic lock은
  단일 사용자 MVP 규모에 비례하는 최소한의 방어다.

**이유**: ADR-0021이 정한 큰 원칙(검토 없이 확정 저장 금지, 4-테이블
분리 대신 JSON payload, `career`←`pkbimport` 단방향 의존)을 그대로 두고,
그 안에서 실제 동시성/재사용/lifecycle 세부사항만 이번 ADR로 구체화했다.
모든 결정이 "단일 사용자 MVP에 비례하는 최소 방어 + DB의 기본 원자성
보장을 최대한 활용, 별도 분산 락/상태 머신 프레임워크 도입 없음"이라는
기존 프로젝트 컨벤션(ADR-0011/0015/0016/0019)을 그대로 따른다.

**영향**: `ImportBatch`는 이제 실질적인 상태 머신을 갖는다(`OPEN`→
`COMPLETED`만, reopen 없음) — 향후 PKB-007/008이 이 API를 소비할 때도
"COMPLETED batch에는 아무것도 추가할 수 없다"는 불변식을 전제로 설계해야
한다. LLM 기반 candidate 자동 생성(PKB-008)이 실제로 batch를 채우는
시점에는, 그 자동 생성 로직도 이번에 만든 candidate 생성 경로(배치 잠금
포함)를 그대로 재사용하게 된다.

---

## ADR-0023: PKB 문서 파일 업로드 — PDFBox/POI 직접 의존(Tika 배제),
## 원본 binary 영구 미저장

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-007

**문제**: ADR-0021 결정 4가 PKB-005/006에서 명시적으로 배제했던 "파일
업로드(multipart) + PDF/DOCX 텍스트 추출"을 PKB-007에서 구현해야 한다.
두 가지 판단이 필요했다 — (1) PDF/DOCX 파싱에 어떤 라이브러리를 쓸지(새
production dependency 도입), (2) 업로드된 원본 파일(binary)을 서버에
영구 보관할지 여부.

**결정**:

1. **PDF는 Apache PDFBox 3.0.8, DOCX는 Apache POI(`poi-ooxml`) 5.5.1을
   각각 직접 의존성으로 추가한다.** Maven Central 확인 결과(조사
   시점 2026-08-18) 둘 다 각 라인의 최신 안정(GA) 버전이고, 둘 다 Java
   21과 호환된다(PDFBox 3.x는 Java 8+ 요구, POI 5.x도 Java 8+ 요구).
   Spring Boot 4.1 dependency management(BOM)가 두 라이브러리 버전을
   관리하지 않으므로 `build.gradle`에 버전을 직접 명시한다.
2. **Apache Tika(전체 프레임워크)는 도입하지 않는다.** 이번 Phase가
   지원하는 형식은 PDF/DOCX 2종뿐이라, 수십 종 포맷 자동 감지와 그에
   따르는 무거운 transitive dependency(포맷별 파서, 선택적 OCR 연동
   등)를 끌어올 이유가 없다. `DocumentTextExtractor` 인터페이스 뒤에
   `PdfTextExtractor`/`DocxTextExtractor` 두 구현체만 둔다.
3. **업로드된 원본 PDF/DOCX binary는 서버에 영구 저장하지 않는다.**
   흐름은 `MultipartFile`(메모리) → parser → `rawText` 추출 →
   `SourceDocument.rawText`로 저장 → binary 폐기다. 로컬 filesystem
   경로, PostgreSQL bytea 컬럼, object storage 어디에도 원본을 남기지
   않는다.
4. **zip bomb 등 악성 압축 파일 방어는 Apache POI의 기본 내장 보호를
   그대로 사용한다.** POI 공식 API(`ZipSecureFile`) 확인 결과, OOXML
   (XWPF 포함) 파싱 시 압축 해제 비율(`minInflateRatio`, 기본 1%)과
   엔트리 크기 상한을 자동으로 검사해 zip bomb으로 판단되면 예외를
   던지는 보호가 **기본 활성화**돼 있다. 별도 라이브러리나 설정을
   추가하지 않고, 이 기본값을 완화하는 코드도 작성하지 않는다.

**대안**:
- **Apache Tika 도입** — 기각. "가능하면 직접적인 parser dependency만
  추가하고 무거운 document framework는 도입하지 않는다"는 이번 Task
  요구와, 지원 형식이 2종뿐이라는 현재 범위에 비례하지 않는 선택.
  향후 지원 포맷이 크게 늘어나 포맷별 파서를 하나씩 추가하는 비용이
  Tika 도입 비용을 넘어서는 시점에 재검토한다.
- **원본 binary도 함께 영구 저장(파일시스템 또는 object storage)** —
  기각(이번 시점). 이번 Phase 목표는 "파일 → text → `SourceDocument`"이고
  원본 보관은 별도 요구(재다운로드, 재파싱, 감사 등)가 있을 때 의미가
  있는 기능이다. 지금 저장 경로를 만들면 보관 정책(만료/삭제/암호화/
  접근 제어)까지 함께 설계해야 하는데 아직 그 요구가 확인되지 않았다.
  필요성이 확인되면 별도 object storage Phase에서 설계한다(PKB-005/006과
  동일하게 "지금 확실히 필요한 것만 만든다" 원칙).
- **`ZipSecureFile.setMinInflateRatio()` 등을 프로젝트가 직접 재설정** —
  기각. 기본값이 이미 합리적인 방어를 제공하고, 이번 Task가 다루는
  이력서/포트폴리오 DOCX는 일반적인 압축률을 가지므로 기본값을 낮출
  (완화할) 이유도, 올릴(더 엄격하게 할) 근거도 없다.

**이유**: "최신/유명하다고 무조건 쓰지 않는다"와 "지금 확실히 필요한
범위에 비례하는 구현만 한다"는 기존 원칙(ADR-0021과 동일선상)을 그대로
따른다. PDFBox/POI 직접 의존은 이번 Task의 실제 요구(PDF/DOCX 텍스트만
추출)에 정확히 대응하고, 원본 미저장 결정은 PKB-005/006이 이미 확립한
"불확실한 미래 요구를 위해 지금 인프라를 만들지 않는다" 패턴을 파일
업로드 기능에도 동일하게 적용한 것이다.

**영향**: 향후 지원 포맷이 늘어나거나(HWP/이미지 OCR 등) 원본 파일 보관이
실제로 필요해지면(예: 사용자가 나중에 원본을 다시 내려받고 싶어하는
요구가 확인되면) 이 ADR을 재검토해야 한다 — 그 시점에 Tika 전환이나
object storage 도입을 별도 Task/ADR로 다시 판단한다. 지금은
`SourceDocument.rawText`가 업로드된 문서의 유일하게 보존되는 표현이므로,
파싱 과정에서 유실된 정보(이미지, 레이아웃, 서식)는 복구할 수 없다.

---

## ADR-0024: PKB LLM 구조화 추출 — Anthropic 공식 Java SDK 직접 의존,
## 재실행 batch당 1회, candidate 생성 all-or-nothing

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: PKB-008

**문제**: ADR-0021이 명시적으로 배제하고 PKB-007이 다시 후보로 남긴
"`SourceDocument.rawText` → LLM 구조화 추출 → `ImportCandidate` 자동
생성"을 PKB-008에서 구현해야 한다. 이 프로젝트가 처음으로 도입하는
"제품 런타임이 직접 호출하는 유료 외부 AI provider"라, provider 선택/
SDK 방식/재실행 정책/candidate 생성 원자성 4가지를 함께 결정해야 했다.

**결정**:

1. **AI provider는 Anthropic(Claude)을 사용한다.** OpenAI도 structured
   output(JSON schema, constrained decoding)을 대등하게 지원하지만,
   Claude Platform의 structured output이 공식 문서상 명시적으로 GA임을
   확인했고([Structured outputs — Claude Platform
   Docs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs)),
   이 프로젝트가 devtool로 이미 Anthropic 생태계를 사용 중이라는 정성적
   근거를 더해 사용자가 최종 선택했다. **애플리케이션 런타임의 API
   key는 로컬 Claude Code/Codex CLI 로그인과 완전히 별개로 발급한다**
   (하나를 다른 하나의 credential로 재사용하지 않는다).
2. **공식 Java SDK(`com.anthropic:anthropic-java`)를 직접 의존성으로
   추가한다.** ALIO 선례(ADR-0007)처럼 `RestClient` 직접 호출도
   검토했으나 기각 — JSON schema 요청 구성/retry/timeout을 직접
   구현해야 하고, ALIO 클라이언트에는 애초에 명시적 timeout이 전혀
   없다는 사실이 확인되어(나쁜 선례) 그대로 복제할 수 없다. Spring AI
   같은 멀티 provider abstraction framework는 provider 1개·use case
   1개뿐인 이번 규모에 비례하지 않아 기각한다.
3. **`DocumentExtractionClient` 최소 abstraction 하나만 둔다**
   (`extract(rawText, documentType) -> StructuredExtractionResult`).
   구현체는 `AnthropicDocumentExtractionClient` 하나뿐이고, provider
   registry/factory는 만들지 않는다. 테스트는 이 인터페이스의 fake
   구현체를 주입한다(실제 API 호출 없음).
4. **structured output schema는 새 DTO를 만들지 않고 기존
   `CareerExperienceCreateRequest`/`CertificationCreateRequest`/
   `EducationCreateRequest`/`AwardCreateRequest`를 그대로 리스트
   원소로 감싸는 `StructuredExtractionResult` 하나만 신설한다.** LLM
   편의를 위한 새 사실 필드는 추가하지 않는다.
5. **추출 실행은 `ImportBatch` 단위로, batch당 1회만 허용한다.**
   `import_batches`에 nullable `extracted_at`(성공 커밋 시에만 설정)을
   추가해 이미 값이 있으면 재호출 시 409. 재시도가 필요하면 같은
   `SourceDocument`로 새 `ImportBatch`를 만든다 — ADR-0021이 이미 정한
   "같은 문서를 다시 분석하려면 새 `ImportBatch`" 설계를 그대로
   확장한 것으로, 새 개념을 도입하지 않는다. `ImportBatch.status`
   (`OPEN`/`COMPLETED`)에는 손대지 않는다 — review lifecycle과
   execution 사실을 한 enum에 섞지 않는다. `COMPLETED` batch에는
   extraction을 허용하지 않는다(ADR-0022의 "COMPLETED에는 아무것도
   추가할 수 없다" 불변식과 동일선상).
6. **한 번의 extraction에서 나온 candidate 전체를 단일 트랜잭션으로
   all-or-nothing 생성한다.** 하나라도 business validation(`@Valid`
   재검증, `ImportCandidateService.parseAndValidate()`)에 실패하면
   전체를 롤백하고 candidate 0개 + 400을 반환한다. partial success(유효한
   항목만 저장)는 채택하지 않는다 — 개인 PKB의 정확성/투명성이
   핵심 제약인 이 기능에서 "왜 일부만 후보로 보이는지"를 사용자가 추론해야
   하는 조용한 정보 손실을 피한다. 이 정책은 추가 구현 비용 없이
   달성된다: `ImportCandidateService.create()`가 이미 `@Transactional`이고
   내부 `findByIdForUpdate()` row lock이 같은 트랜잭션 내 재진입
   가능하므로, 새 orchestration 서비스가 이를 순차 반복 호출하기만
   해도 예외 발생 시 트랜잭션 전체가 자연히 롤백된다. `repository.save()`를
   직접 호출하지 않고 기존 `ImportCandidateService`를 그대로 재사용한다
   (리팩터링 불필요).
7. **LLM 호출 실패 시 재시도는 SDK 기본 내장 retry(429/5xx/네트워크
   timeout 한정, 소수 횟수)를 그대로 사용한다.** 커스텀 backoff/DLQ는
   만들지 않는다. connect timeout 10초, read/request timeout 60초를
   명시적으로 설정한다(무한 대기 금지).
8. **raw LLM request/response는 DB에 저장하지 않는다.** 최종 구조화
   결과는 이미 `ImportCandidate.payload`로 보존되어 추적 가능하고,
   원문 재포함 위험(privacy)과 미확인 미래 요구(디버깅용 보관)를 위해
   지금 인프라를 만들지 않는다는 ADR-0023과 동일한 원칙을 적용한다.
   대신 `import_batches`에 nullable `extraction_provider`/
   `extraction_model`/`extraction_prompt_version`(문자열) 3개 컬럼만
   추가해 향후 재추출/문제 결과 추적의 최소 단서로 남긴다.
9. **API key 환경변수명은 `CAREEROPS_ANTHROPIC_API_KEY`로 provider를
   명시한다.** 기존 `.env`/`spring.config.import` 로딩 경로를 그대로
   따르되(ADR-0012), provider SDK의 `.fromEnv()`(OS 환경변수 직접
   읽기)는 `.env`가 Spring `PropertySource`일 뿐 OS 환경변수가 아니므로
   사용하지 않는다 — 기존 ALIO 패턴(`@Value` 생성자 주입 후 SDK 빌더에
   명시 전달)을 그대로 따른다.

**대안**:
- **OpenAI API 채택** — 기각(사용자 선택). 기능적으로 대등하나 GA 라벨이
  Anthropic만큼 명시적이지 않고, devtool 생태계 중복 이점이 없다.
- **`ImportBatchStatus`에 `EXTRACTING`/`EXTRACTION_FAILED` 상태 추가** —
  기각. review 상태와 execution 상태를 한 축에 섞는 것이고, 이번 MVP는
  단일 동기 HTTP 요청 안에서 끝나 백그라운드 잡/큐가 없어 "진행중" 상태를
  영속화할 이유가 없다.
- **재실행 시 기존 PENDING candidate 존재 여부로 차단(옵션 B)/서버가
  자동으로 새 batch 생성(옵션 C)/별도 `ExtractionRun` entity(옵션 D)** —
  모두 기각. B는 전부 승인/거부된 뒤 재호출이 다시 가능해지는 이유가
  불명확하고, C는 "batch에 대해 실행한다"는 기존 API 계약과 어긋나며,
  D는 이번 규모에 과한 audit 구조다.
- **partial success(유효 candidate만 저장)** — 기각. 조용한 정보 손실
  위험이 이 기능의 핵심 제약(정확성)과 상충하고, all-or-nothing이
  기존 `ImportCandidateService`의 트랜잭션 경계를 그대로 재사용하는 것만으로
  공짜로 달성돼 추가 구현 비용도 없다.
- **retry 완전 비활성화(`maxRetries(0)`)** — 검토했으나 기각(사용자 선택).
  SDK가 이미 검증된 최소 재시도(400/인증 실패/스키마 invalid는 대상 아님)를
  제공해, 이를 그대로 쓰는 것이 "무조건적 retry를 새로 만들지 않는다"는
  원칙에 가장 부합한다.
- **raw LLM response를 DB에 저장** — 기각. 디버깅 효용은 상세 로그
  레벨(민감정보 제외, 메타데이터만)로 대체 가능하고, 저장하려면 보관
  기간/접근 제어까지 새로 설계해야 하는데 아직 그 요구가 없다(ADR-0023과
  동일 판단 근거).
- **`chunking`(문서를 여러 조각으로 나눠 각각 추출 후 병합)** — 기각(이번
  MVP). `rawText` 상한 50,000자는 선택한 모델의 context window(128K~1M
  토큰) 대비 일부만 사용하는 규모로 확인됐고, 목적이 "요약"이 아니라
  "사실 추출"이라 chunk 경계에서 관련 사실이 서로 다른 조각으로 갈라져
  부정확해질 위험이 오히려 커진다. Map-Reduce 구조는 도입하지 않는다.

**이유**: ADR-0021이 미리 설계해둔 3계층 provenance + 승인 게이트가
LLM 추출 경로에도 리팩터링 없이 그대로 들어맞는다는 것이 이번 조사로
확인됐다 — "LLM이 만든 candidate가 검토 없이 PKB에 반영되는 경로"는
설계상 존재하지 않는다(수동 생성이든 LLM 생성이든 `approve()`까지
완전히 동일한 코드 경로). 나머지 결정들(재실행 1회/all-or-nothing/SDK
기본 retry)은 모두 "이 프로젝트 규모에 비례하는 최소 방어 + 기존 구조/
DB 기본 원자성을 최대한 재사용, 별도 상태 머신/audit framework 도입
없음"이라는 기존 컨벤션(ADR-0011/0015/0016/0019/0022/0023)을 그대로
따른다.

**영향**: 이 프로젝트 최초로 런타임에 유료 외부 AI provider를 호출한다
— provider 콘솔에서 사용량/비용 알림을 걸어두는 것을 권장한다(코드
범위 밖). prompt/schema 기반 hallucination 방어는 위험을 줄일 뿐
제거하지 못하므로, 사람의 최종 승인이 여전히 유일한 진짜 안전판이라는
점을 Task 명세와 향후 UX 안내에 계속 명시해야 한다. 향후 다른 provider로
교체하거나 멀티 provider를 지원해야 하는 시점이 오면 이번 최소
abstraction(`DocumentExtractionClient`)을 그대로 확장하되, 지금은 그
필요가 없다.

---

## ADR-0027: PKB-008.1 — nullable 구조화 스키마, prompt v2,
## output token 예산 재조정, 좁은 조건의 chunking

- 날짜: 2026-08-22
- 상태: 확정
- 관련 Task: PKB-008.1

**문제**: Anthropic Java SDK 2.54.0의 자동 schema 유도는 모든 record
필드를 `required`로 만들고, 런타임에 인식되는 `Nullable` annotation이
붙은 필드만 null을 허용한다. 기존 생성 DTO에는 이 annotation이 없어
모델이 원문에 없는 날짜를 채우거나 optional 필드가 많은 객체 전체를
누락할 유인이 있었다. 긴 구조화 응답은 8,192 output token 한도에서
잘릴 수 있고, 최대 50,000자인 문서를 한 요청으로만 처리하면 누락 및
절단 위험도 커진다. 구현 후 실제 API 호출에서 Anthropic structured
output은 union type(`type` 배열 또는 `anyOf`) 파라미터를 schema당 최대
16개만 허용한다는 추가 제약도 확인했다. SDK의 로컬 schema validation은
이 제한을 검사하지 않아 로컬 schema 생성과 테스트만으로는 발견되지 않았다.

**결정**:

1. 기존 5개 생성 DTO의 optional 필드 중 LocalDate/enum/BigDecimal 12개에만
   `org.jspecify.annotations.Nullable`을 적용한다. plain String 선택 필드
   11개는 union 예산을 쓰지 않고 required non-null String으로 유지하며,
   원문에서 알 수 없으면 모델이 빈 문자열을 반환하도록 prompt로 지시한 뒤
   sanitizer가 null로 정규화한다. SDK가 runtime reflection으로 annotation을
   읽으므로 이미 전이 의존성이던
   `org.jspecify:jspecify:1.0.0`을 `implementation`으로 명시한다.
2. prompt를 v2로 올려 네 targetType을 각각 독립적으로 탐색하고, 정확한
   일자가 없는 날짜/enum/숫자는 null, 알 수 없는 선택 String은 빈 문자열로
   두며, 원문에 명시된 기술만 tag로 만들도록 구체화한다. sanitizer는
   빈 문자열과 공백만 있는 문자열도 기존 placeholder처럼 null로 바꾼다.
   기존 문서 태그 격리와 prompt injection 방어는 유지한다.
3. output 상한을 16,000 token으로 조정한다. request timeout은 최초
   120초로 설정했으나, 실제 사용자 문서(경험정리, 약 1만자)로 라이브
   E2E 검증 중 매 시도가 120초를 넘겨 SDK 기본 재시도(네트워크 timeout
   한정)까지 소진되며 약 6분 뒤에야 최종 timeout으로 실패하는 현상을
   확인했다 — 16,000 token 분량의 구조화 출력을 실제로 생성하는 데
   드는 시간이 120초보다 일관되게 길었던 것이지 일시적 네트워크
   문제가 아니었으므로, 재시도는 실패를 지연시킬 뿐 해결하지 못했다.
   이에 request timeout을 **300초**로 다시 올렸다(연결 timeout 10초는
   변경 없음). `stopReason`이 `MAX_TOKENS` 또는 context window
   초과이면 민감한 원문이나 응답을 기록하지 않고 전용 cause 타입과
   enum 값으로 구분한다. 외부 오류 분류는 기존 `MALFORMED_RESPONSE`를
   유지한다.
4. 20,000자 미만은 기존 단일 요청을 유지한다. 그 이상만 문단 경계에서
   목표 12,000자로 겹침 없이 나누어 순차 추출하고, 완전히 같은 record만
   제거한다. 한 chunk라도 실패하면 즉시 같은 예외를 전파해 기존
   all-or-nothing 정책을 유지한다. API와 orchestration 계층은 변경하지
   않고 `@Primary` decorator 뒤에 이 동작을 숨긴다.

**대안**:

- **별도 extraction 전용 DTO 도입** — 기각. 기존 create DTO와 필드 및
  validation을 이중 관리해 drift가 생기며, 확인된 원인은 기존 optional
  필드에 runtime nullable 메타데이터를 주는 최소 변경으로 직접 해결된다.
- **모든 문서 chunking 또는 overlap/Map-Reduce** — 기각. 짧은 문서의
  기존 동작을 불필요하게 바꾸고 중복·경계 병합 복잡도를 만든다. 실제
  위험이 커지는 20,000자 이상에만 단일 depth chunking을 적용한다.
- **semantic dedup/embedding 또는 모델 변경** — 기각. 이번 원인은
  schema·prompt·output budget으로 확인됐으며 별도 품질 시스템을 추가할
  근거가 없다.

**이유와 영향**: 빈 문자열을 허용하는 String과 달리 LocalDate/enum/숫자는
타입을 깨지 않고 "모름"을 표현하려면 진짜 nullable union이 필요하다.
따라서 union은 이 12개에 집중해 API 제한 16개보다 4개 여유를 남긴다.
prompt와 sanitizer가 String의 빈 문자열 관례를 책임지고, wrapper 전체
schema의 union 수를 세는 자동 테스트가 SDK 로컬 validation이 놓치는 실제
provider 제한의 재발을 막는다. prompt와 token 예산은 누락 및 절단의 직접
원인을 보완하며, 좁은 chunking은 50,000자 상한을 지원하면서 짧은 문서와
기존 API 계약에 미치는 영향을 제한한다. SDK schema 유도 결과와 chunk
병합/실패 동작은 네트워크 없는 자동 테스트로 고정한다.

---

## ADR-0025: ADR-0022 결정 4 정정 — `ImportBatch.complete()`는
## conditional UPDATE가 아니라 `findByIdForUpdate` 잠금 + 별도 조회로
## "PENDING candidate 없음"을 확인해야 한다

- 날짜: 2026-08-18
- 상태: 확정
- 관련 Task: FIX-002 (원 결정: PKB-006/ADR-0022)

**문제**: `ImportCandidateConcurrencyTest#concurrentCompleteAndCreatePreserveBatchInvariant`가
clean main HEAD에서 반복 실행 시 약 60%(35회 중 22회) 확률로
`assertThat(pending).isFalse()`에서 실패했다. `batchService.complete()`가
200을 반환했는데도 그 batch에 실제로 `PENDING` candidate가 존재하는,
"COMPLETED batch는 PENDING candidate를 가질 수 없다"(ADR-0022 결정 4)
불변식 위반 상태가 재현됐다.

**원인 진단(재현 근거)**: 애플리케이션/Hibernate를 배제하고 두 개의 raw
psql 세션으로 직접 재현했다 — 세션 A가 `SELECT ... FOR UPDATE`로
`import_batches` row를 잠근 뒤 `import_candidates`에 `PENDING` row를
INSERT하고 COMMIT, 세션 B는 A가 lock을 쥔 동안
`UPDATE import_batches ... WHERE status='OPEN' AND NOT EXISTS (SELECT 1
FROM import_candidates WHERE status='PENDING') ...`를 실행해 A의 lock을
기다렸다. A가 커밋한 뒤 B의 UPDATE가 unblock됐을 때, **B는 여전히
성공해서 batch를 COMPLETED로 만들었다** — A가 막 커밋한 PENDING
candidate가 존재하는데도.

이는 PostgreSQL READ COMMITTED의 문서화된 caveat이다: UPDATE가 대상
row의 lock을 기다리다가(EvalPlanQual) lock을 쥔 트랜잭션이 커밋되면,
**그 row 자신**은 최신 커밋 버전으로 재평가되지만 **그 UPDATE 문의
subquery가 참조하는 다른 테이블**(`import_candidates`)은 원래 그
UPDATE 문이 시작될 때 잡힌 snapshot으로 평가된다. 즉
`completeIfNoPending`의 `NOT EXISTS (SELECT ... FROM import_candidates
...)`는 lock 대기 도중 커밋된 INSERT를 보지 못한다.

ADR-0022 결정 4는 "두 경로가 같은 `import_batches` row lock을 두고
경쟁하므로 나중 커밋은 항상 최신 상태를 보고 올바르게 성공/409를
결정한다"고 가정했는데, 이 가정은 **lock의 대상이 되는 row 자신에는**
맞지만 **그 row를 잠근 SQL 문의 subquery가 참조하는 다른 테이블에는
적용되지 않는다** — 실제로는 틀린 가정이었다. `ImportCandidateService`의
approve/reject concurrency(`transitionIfPending`, ADR-0022 결정 2)는
이 문제가 없다 — 그 UPDATE의 조건(`status='PENDING'`)이 잠그는 row
자신의 컬럼만 재확인하고 다른 테이블을 참조하지 않기 때문이다. 두
동시성 보호가 겉보기엔 같은 패턴("조건부 UPDATE 하나")이었지만,
"조건이 자기 자신의 컬럼만 보는가, 다른 테이블을 subquery로 보는가"에
따라 안전성이 갈린다는 점이 이번에 새로 확인됐다.

**결정**:

1. `ImportBatch.complete()`는 `completeIfNoPending` 단일 conditional
   UPDATE를 버리고, candidate 생성 경로(`ImportCandidateService.create()`)와
   **동일한** `findByIdForUpdate()`(`@Lock(PESSIMISTIC_WRITE)`)로 batch
   row를 먼저 잠근 뒤, **별도의 새 SELECT 문**(`existsByImportBatchIdAndStatus`,
   이미 `ImportCandidateRepository`에 존재)으로 PENDING candidate 존재
   여부를 확인하고, 문제 없으면 `ImportBatch.markCompleted()`로 상태를
   바꾼다.
   - 잠금 대상 row 자신은 `findByIdForUpdate()`가 SELECT FOR UPDATE의
     본래 목적대로 항상 최신 커밋 버전을 반환하므로 `status` 확인은
     그대로 안전하다.
   - PENDING 존재 확인은 잠금을 획득한 **이후에** 실행되는 완전히 새로운
     SQL 문이므로, READ COMMITTED에서 그 문 자신의 시작 시점 snapshot을
     새로 받는다 — lock 대기 중 커밋된 변경을 정확히 반영한다.
   - candidate 생성 경로도 이미 같은 `findByIdForUpdate()`를 거치므로,
     두 경로 모두 batch row에 대해 완전히 직렬화된다(한쪽이 lock을
     쥐고 있으면 다른 쪽은 그 트랜잭션이 끝날 때까지 대기).
2. **새로운 locking 전략이나 분산 락은 도입하지 않는다** — ADR-0022가
   이미 candidate 생성 경로에 도입해둔 `PESSIMISTIC_WRITE` 잠금을
   complete() 경로에도 동일하게 적용해 원래 ADR-0022가 의도했던(그러나
   실제로는 candidate 생성 쪽에만 적용되고 complete() 쪽은 적용되지
   않았던) 대칭적 보호를 완성하는 것으로, 기존에 이미 승인된 primitive를
   재사용한다.
3. `completeIfNoPending`(단일 conditional UPDATE)은 삭제한다 — 안전하지
   않은 패턴을 코드베이스에 남겨 다른 곳에서 같은 실수가 재현되는 것을
   막는다.

**대안**:
- **subquery 대상(`import_candidates`)에도 `FOR UPDATE`/`FOR SHARE` lock
  힌트 추가** — 기각. JPQL `@Modifying UPDATE`의 subquery에는 `FOR
  UPDATE`를 표현할 방법이 없고(네이티브 SQL이 필요), 설령 가능해도
  "그 batch의 모든 PENDING candidate row를 잠그는" phantom-row 문제
  (아직 없는 row는 잠글 수 없음)가 별도로 남아 근본 해결이 아니다.
- **트랜잭션 isolation을 SERIALIZABLE로 올림** — 기각. 이 하나의
  메서드만을 위해 isolation level을 바꾸면 serialization failure(재시도
  필요)가 새로 발생할 수 있는데, 이번 FIX 범위는 "retry 추가 금지"이고
  격리 수준 변경은 이 프로젝트의 나머지 모든 `@Transactional` 경로와
  일관성이 깨지는 더 큰 변경이다. 이번 결정 1의 "잠금 후 별도 문으로
  재조회"가 이 프로젝트 규모에서 훨씬 최소한의 수정이다.
- **`import_candidates`에 partial UNIQUE index나 DB CHECK 제약으로
  강제** — 기각. "이 batch에 PENDING candidate가 없어야 COMPLETED"라는
  조건은 두 테이블에 걸친 불변식이라 단일 테이블 제약으로 표현할 수
  없다(Postgres는 cross-table CHECK 제약을 지원하지 않는다). 별도
  트리거가 필요한데, 이는 이 프로젝트가 계속 피해온 "필요 이상의
  DB 레벨 로직"이다.

**이유**: "필요한 것보다 새로운 locking primitive를 추가하지 않는다"는
기존 원칙(ADR-0022)을 그대로 지키면서, 이미 존재하는 `PESSIMISTIC_WRITE`
lock을 두 경쟁 경로(생성/완료) 모두에 실제로 적용하는 것으로 버그를
없앤다 — 새 lock 종류, 분산 락, retry, isolation level 변경 전부
불필요했다.

**영향**: `ImportBatch.complete()`가 이제 candidate 생성과 완전히 같은
잠금 순서를 거치므로, 두 요청이 동시에 들어오면 한쪽이 다른 쪽의 전체
트랜잭션이 끝날 때까지 대기한다 — 단일 사용자 MVP 규모에서 지연은
무시할 수준이다. `ImportCandidateConcurrencyTest`를 포함한 기존 동시성
테스트/Acceptance Criteria는 코드 변경 없이 그대로 유지되며, 이번
수정으로 반복 실행 시 항상 통과함을 확인했다(FIX-002 검증 기록 참고).
"조건부 UPDATE가 곧 안전한 concurrency 보장"이라는 가정은 앞으로 이
프로젝트에서 **그 조건이 자기 자신의 컬럼만 참조할 때만** 유효하다는
점을 다른 Task/Task 리뷰에서도 전제로 삼는다.

---

## ADR-0026: `JobPosting` ↔ PKB 매칭(MATCH-001) — deterministic
## 토큰/substring 채점, relevance/eligibility 분리, on-demand 계산

- 날짜: 2026-08-19
- 상태: 확정
- 관련 Task: MATCH-001

**문제**: PKB-008까지 완료되어 PKB(`CareerExperience`/`Certification`/
`Education`/`Award`)가 문서 기반/수동 입력 모두 구조화된 상태로 쌓이기
시작했다. `docs/ROADMAP.md` "Phase 13 이후 후보"에 남겨둔 `JobPosting`
↔ PKB 적합도 매칭을 실제로 설계해야 하는데, 그 전에 (1) 매칭 판단에
LLM/embedding을 쓸지, (2) 계산 결과를 저장할지, (3) 카테고리별 채점
가중치를 어떻게 둘지, (4) 점수 하나가 "지원자가 이 공고에 얼마나
관련 있는 이력을 가졌는가"와 "이 공고에 합격할 가능성이 있는가"라는
서로 다른 두 질문 중 무엇에 답하는지가 불명확한 채로는 API 계약도, Codex
Task 명세도 확정할 수 없었다. 조사 결과 `JobPosting`에는 사람이 읽는
자유 서술형 자격요건 텍스트가 사실상 없다(`careerLevel`/
`educationRequirement`는 ALIO API가 제공하는 짧은 분류값이고,
`jobCategory`도 쉼표로 구분된 직무 분류 문자열일 뿐 상세 요건 문장이
아니다 — COLLECT-002/ADR-0009 참고). 이 사실이 아래 결정 전체의
근거가 된다.

**결정**:

1. **Deterministic 토큰/substring 채점만 사용한다. LLM/embedding은
   전혀 쓰지 않는다.** `JobPosting`에 애초에 세부 자격요건 텍스트가
   없으므로, LLM에게 "이 지원자가 이 공고 요건을 충족하는가"를 판단하게
   맡기면 모델이 존재하지 않는 요건을 그럴듯하게 창작해 채울 위험이
   실제로 크다(짧은 분류값만 주고 풍부한 판단을 요구하면 환각이
   나오기 쉬운 전형적인 상황). AGENTS.md의 "AI가 사용자가 하지 않은
   경험/수치를 만들어내지 못하게 막는다"는 원칙은 자기소개서 생성뿐
   아니라 매칭 판단에도 동일하게 적용된다 — 매칭 결과가 "이 공고와
   관련 있다"고 말하는 근거(evidence)도 실제 PKB 필드 값이어야지, 모델이
   추론으로 채운 것이어서는 안 된다. 정규화(소문자화, 공백 정리) 후
   양방향 substring containment로 채점하고, 매칭된 필드를 evidence로
   그대로 노출한다. 향후 reranking을 위한 최소 추상화(인터페이스 등)도
   이번엔 미리 만들지 않는다 — PKB-008의 `DocumentExtractionClient`
   패턴(`pkbimport/extraction/llm/`)이 이미 존재해 필요해지면 그때
   같은 패턴으로 추가하는 진입장벽이 낮으므로, YAGNI를 그대로 적용한다.
2. **`overallScore`는 "관련도(relevance)"만 의미하고, "합격
   가능성(eligibility)"은 점수에 전혀 관여하지 않는다.** `careerLevel`
   (경력구분: 신입/경력 등)과 `educationRequirement`는 응답에 참고 정보로
   그대로 echo하되, 채점 로직에는 들어가지 않는다. 이 둘을 점수에
   섞으면 "학력요건 불충족 = 낮은 점수"처럼 마치 합격/불합격 판정처럼
   읽히는 응답이 나오는데, `JobPosting`의 `educationRequirement`는
   ALIO가 제공하는 단순 분류값이라 실제 자격요건의 뉘앙스(우대/필수,
   예외 조건 등)를 담지 못한다 — 이 정보로 합격 가능성을 판정하는 것
   자체가 근거 부족한 과대 해석이다. 점수는 "PKB와 공고 텍스트 간 정보상
   관련도"로만 좁혀 정의하고, 나머지 참고 정보는 사람이 직접 판단하도록
   투명하게 분리해 노출한다.
3. **계산 결과를 저장하지 않는다(on-demand 계산만).** 신규 엔티티/테이블/
   migration을 만들지 않는다. `JobPosting` 또는 PKB가 바뀔 때마다 캐시
   무효화를 관리하는 비용이, 아직 매칭 요청 빈도도 트래픽도 없는 이
   단계의 이득보다 크다.
4. **채점 가중치는 Experience 70% / Certification 15% / Education 10% /
   Award 5%로 확정한다.** 애초 사용자 초안은 60/20/15/5였으나, 위
   문제 정의와 같은 이유(`JobPosting`에 자격증/학력 세부 요건 텍스트가
   사실상 없어 그 카테고리의 매칭 신호가 구조적으로 약함)로 Experience
   비중을 더 높인 안을 채택한다. 카테고리 안에 여러 PKB 항목이 있으면
   평균이 아니라 **최고 점수**를 그 카테고리 점수로 사용한다(지원자가
   가진 여러 경험 중 하나라도 강하게 관련 있으면 그 카테고리는 이미
   관련도가 높다고 보는 것이 자연스럽고, 평균은 관련 없는 항목이 많을수록
   점수를 부당하게 깎는다).
5. **하드코딩 동의어 사전을 두지 않는다.** `jobCategory`는 쉼표로만
   분리하고(점(`.`) 등 추가 구분자로 더 쪼개지 않음) 각 조각을
   소문자화·공백정규화한 뒤 PKB 필드 값과 양방향 substring containment로
   비교한다. IT/전산 도메인 동의어(예: "백엔드"↔"서버 개발") 사전을
   수기로 만들면 유지보수 비용이 계속 늘고 커버리지도 항상 불완전하다 —
   지금은 정확 문자열 포함 관계만으로 baseline을 잡고, 실제 사용해보며
   놓치는 사례가 쌓이면 그때 재검토한다.
6. **알려진 한계로 한국어-영어 교차 매칭을 지원하지 않는다.** substring
   containment는 같은 언어 표기끼리만 매칭된다(예: PKB에 "Backend"로
   적혀 있고 공고엔 "백엔드"로만 적혀 있으면 매칭되지 않는다). 이 한계를
   숨기지 않고 문서화해, 실사용 중 실제로 문제가 되는지 관찰한 뒤 필요하면
   최소한의 사전 또는 다른 접근을 재검토한다.
7. **카테고리별 top N을 고정한다** — `CareerExperience` 5개,
   `Certification`/`Education`/`Award` 각 3개, 점수 0인 항목은 top N에서
   제외한다. 이 규모의 PKB에서 "관련도 상위 몇 개를 근거로 보여줄지"는
   UI 소비 목적에 맞춘 실용적 상수이며, 전체 목록을 다 보여주는 것보다
   사용자가 근거를 빠르게 훑어보기에 낫다.

**대안**:
- **LLM 기반 적합도 판단 또는 LLM reranking** — 기각. 위 1번 이유(존재하지
  않는 요건을 창작할 위험)와, `docs/DECISIONS.md`가 반복해온 "필요성이
  실제로 드러날 때 재검토한다"는 원칙에 따라 이번 Phase에서는 넣지 않는다.
- **embedding/pgvector 기반 유사도 매칭** — 기각. `JobPosting` 텍스트가
  짧고 정형화돼 있어 semantic 유사도가 substring 매칭 대비 실질적
  이득을 준다는 근거가 아직 없고, 새 인프라(pgvector) 도입 비용도 크다.
  PKB-008에서 이미 이 도입 여부를 이번 Task로 미뤄둔 바 있다.
- **`MatchResult` 엔티티로 결과 영속화(캐싱)** — 기각. 위 3번 이유. 트래픽/
  요청 빈도 데이터가 쌓이면 재검토한다.
- **사용자 초안 가중치 60/20/15/5** — 기각(사용자 승인 하에 조정). 위
  4번 이유로 Experience 비중을 70%까지 올린 안을 채택했다.
- **IT 도메인 동의어 사전 하드코딩** — 기각. 위 5번 이유.
- **카테고리 점수를 평균으로 계산** — 기각. 위 4번 이유.

**이유**: `JobPosting`에 실제로 존재하는 데이터의 정보량에 정확히
비례하는 방법을 선택했다 — 짧고 정형화된 텍스트에는 deterministic
매칭이 LLM보다 더 정직하고(창작 위험 없음), 더 검증 가능하며
(evidence가 항상 실제 필드 값), 더 저렴하다. "관련도"와 "합격 가능성"을
분리한 것은 이 프로젝트의 핵심 제약(근거 기반 검증, AGENTS.md)을
매칭 기능에도 일관되게 적용한 것이다.

**영향**: `GET /api/jobs/{jobId}/match` 응답의 `overallScore`는 앞으로도
"합격 가능성 점수"로 오인되지 않도록 API 문서/DTO 주석에 명시적으로
"relevance score"라고 표기해야 한다. LLM/embedding을 실제로 도입하는
시점이 오면 이 ADR을 참조해 재검토하고, 그때는 "존재하지 않는 요건을
만들어내지 않는다"는 이번 결정의 제약을 신규 접근에도 유지해야 한다.
한국어-영어 교차 매칭 미지원은 다국어 이력서/공고가 늘어나면 실사용
불편으로 이어질 수 있어, 실제 불편 사례가 관찰되면 최소 대응(예: 자주
쓰이는 IT 용어 한정 소규모 사전)부터 검토한다.

---

## ADR-0028: `JobPosting` ↔ PKB semantic 매칭(MATCH-002) — Claude
## structured output, ID 기반 hallucination 검증, MATCH-001과 독립 계산

- 날짜: 2026-08-22
- 상태: 확정
- 관련 Task: MATCH-002

**문제**: MATCH-001(ADR-0026) 완료 후 실사용 데이터 재검증(VALIDATE-001)에서
deterministic 토큰/substring 채점의 구조적 한계가 실측으로 드러났다.
실제 dev DB PKB(CareerExperience 6/Certification 10/Education 2/Award 1/
ExperienceTag 12, Java/Spring Boot/AI/RAG/Redis 등 백엔드+AI 프로필)로
OPEN ALIO 공고 7건을 테스트한 결과, "한국교통안전공단 AI서비스개발"과
"한전KDN AI기반 로봇플랫폼 개발 연구과제"(둘 다 `jobCategory`=`정보통신`)가
`overallScore=0.0`으로 나왔다 — "정보통신"처럼 광범위한 직군 라벨이
"Java"/"Spring Boot"/"정보처리기사" 같은 PKB의 구체적 기술 어휘와 문자열
수준에서 절대 겹치지 않기 때문이다. 반대로 "한국문화관광연구원"/
"국방과학연구소"는 `jobCategory`에 우연히 포함된 "연구"라는 조각이 PKB의
RAG 연구 경험과 substring으로 매칭돼 실제 관련성과 무관하게 `0.42`라는
상대적으로 높은 점수를 받았다. ADR-0026이 LLM/embedding을 배제한
근거("`JobPosting`에 자유 서술형 자격요건 텍스트가 사실상 없어 LLM에게
풍부한 판단을 맡기면 환각 위험이 크다")는 여전히 유효하지만, 이번 실측은
그 반대급부 — deterministic 방식 자체도 "광범위 직군 taxonomy vs 구체적
기술/경험" 사이의 semantic gap을 근본적으로 못 메운다는 것을 보여준다.
`JobPosting.title`을 전혀 쓰지 않은 것도 원인 중 하나였다. 이 gap을
메우려면 의미 기반 판단이 필요하지만, "존재하지 않는 요건을 만들어내지
않는다"는 ADR-0026의 핵심 제약은 그대로 유지해야 한다.

**결정**:

1. **`JobPosting`의 실제 필드(특히 `title`)와 승인된 전체 PKB를 Claude
   structured output으로 의미 비교하는 신규 병렬 API를 추가한다.**
   기존 `com.anthropic:anthropic-java` SDK(ADR-0024)를 그대로 재사용하고
   새 provider는 도입하지 않는다. `pkbimport/extraction/llm/`(interface +
   단일 구현체 + prompt builder + exception) 패턴을 `match/semantic/`에
   그대로 미러링한다. 현재 PKB 규모(6/10/2/1)에서는 전체를 한 요청에
   직접 입력해도 매우 작으므로 embedding/pgvector/candidate retrieval/
   chunking은 도입하지 않는다(PKB 규모가 크게 늘어나면 재검토).
2. **MATCH-001과 완전히 독립적으로 계산하되, 응답에 두 점수를 함께
   노출한다(`deterministicScore` + `semanticScore`).** 검토한 대안: (A)
   MATCH-001 점수를 semantic 판단의 추가 feature로 LLM에 제공 — 기각.
   deterministic 0점(VALIDATE-001에서 실측된 잘못된 0점)이 LLM 판단을
   부당하게 끌어내릴 위험이 있고, 이번 ADR이 해결하려는 문제 자체가
   "deterministic 채점의 구조적 한계"이므로 그 결과를 다시 LLM 입력에
   섞는 것은 문제를 절반만 해결한다. (B) 완전 독립 계산 + 응답에서도
   semantic 결과만 노출 — 기각(부분). deterministic 결과를 아예 숨기면
   두 방식의 차이를 사용자가 관찰할 수 없어 향후 검증(어느 쪽이 실사용에
   더 유용한지)이 어려워진다. 최종 채택은 "계산은 B처럼 독립, 노출은
   병기"로 A/B를 조합한 형태다 — `SemanticJobMatchService`는
   `JobMatchService.match()`를 호출하지 않고 `CareerMatchEngine`을 직접
   호출해 `deterministicScore`를 얻는다(MATCH-001 전용 metric이 semantic
   요청으로 오염되지 않도록).
3. **ID 기반 검증으로 hallucination을 방지한다.** LLM은 새 경험/자격증/
   학력/수상을 생성할 수 없고, 프롬프트에 실제로 포함시킨 PKB item id
   중에서만 선택해야 한다. 응답에 포함되지 않은 id가 하나라도 나오면
   전체 응답을 실패 처리한다(all-or-nothing, ADR-0024 결정 6과 동일
   사상 — partial success 미채택). `score`가 `[0,1]` 범위를 벗어나도
   동일하게 전체 실패(clamp 안 함). 중복 id는 hallucination이 아니므로
   최고 score만 남기고 계속 진행한다. 응답의 `title` 필드는 LLM 출력이
   아니라 검증된 id로 서버가 실제 엔티티를 조회해 채운다 — LLM이 실제와
   다른 제목을 지어낼 표면 자체를 없앤다.
4. **`POST /api/jobs/{jobId}/semantic-match`로 신규 endpoint를 추가한다
   (`GET` 아님).** 기존 `GET /api/jobs/{jobId}/match`(MATCH-001, 순수
   로컬 계산)는 그대로 둔다. 대안 GET 유지는 기각 — 이 프로젝트의 기존
   컨벤션(`POST /api/collect/alio`, `POST /api/import/jobs/manual`,
   `POST /api/jobs`)은 부수효과나 외부 비용이 있는 동작에 일관되게
   POST를 쓴다. LLM 호출은 유료 외부 API 호출이라는 명백한 부수효과가
   있어 GET의 "안전하고 캐시 가능한 조회" 시맨틱과 맞지 않는다.
5. **provider(Claude) 실패 시 명시적으로 `502`를 반환한다. Silent
   fallback을 만들지 않는다.** 대안 — provider 실패 시 MATCH-001
   deterministic 결과로 조용히 대체(fallback A) — 기각. 응답 형태가
   semantic 응답과 동일해 보이면 사용자가 이를 실제 semantic 판단
   결과로 착각할 위험이 크고, 이는 "AI가 사용자가 하지 않은 판단을
   한 것처럼 보이게 하지 않는다"는 이 프로젝트의 근거 기반 검증 원칙과
   충돌한다. fallback을 쓰더라도 `matchMethod=DETERMINISTIC_FALLBACK`
   같은 필드로 명시해야 한다는 조건을 붙이면 결국 구현 복잡도(두 응답
   스키마, 두 코드 경로)만 커지고, 클라이언트는 이미 존재하는
   `GET /api/jobs/{jobId}/match`를 별도로 호출해 deterministic 결과를
   얻을 수 있으므로 서버가 대신 fallback해줄 필요성이 낮다. MVP 단계는
   명시적 실패가 더 단순하다.
6. **Evidence는 자유 텍스트 원문 인용이 아니라 닫힌 enum 목록으로
   표현한다.** `EvidenceSource`(`JOB_TITLE`/`JOB_CATEGORY`/`CAREER_LEVEL`/
   `EDUCATION_REQUIREMENT`/`EXPERIENCE_TAG`/`EXPERIENCE_TITLE`/
   `EXPERIENCE_SUMMARY`/`EXPERIENCE_DETAIL`/`CERTIFICATION_NAME`/
   `CERTIFICATION_DESCRIPTION`/`EDUCATION_MAJOR`/`EDUCATION_DESCRIPTION`/
   `AWARD_TITLE`/`AWARD_DESCRIPTION`) 목록 + 짧은(최대 200자) 자연어
   `reason` 1문장을 함께 반환한다. 대안 — LLM이 실제 원문을 정확히
   인용(quote)하게 하고 서버가 substring으로 검증 — 기각. LLM이 원문을
   토씨 하나 틀리지 않고 재인용한다는 보장이 없고, 이를 검증하려면 또
   다른 취약한 문자열 매칭 로직이 필요해진다. enum은 "어떤 입력 필드
   *카테고리*에 근거했는가"를 구조적으로 검증 가능하게 강제하면서, 원문
   재인용 실패로 인한 불필요한 검증 실패를 없앤다. `reason`의 자연어
   내용 자체는 서버가 검증하지 않는다(자연어 검증은 신뢰할 수 없다는
   ADR-0026 이래의 원칙과 동일).
7. **eligibility(경력구분/학력요건 충족 여부) 판정은 이번 라운드에서
   제외한다.** `SemanticJobMatchResponse`에 관련 필드를 두지 않는다.
   PKB에는 지원자의 "경력 연차"나 "재직 여부"를 판단할 명시적 필드가
   없어(`CareerExperience`는 `startDate`/`endDate`만 있고 연차 합산 로직이
   없음) 새로운 deterministic 계산이나 추가 LLM 추론이 필요해 범위가
   커지고, `JobPosting.careerLevel`/`educationRequirement` 자체가 ALIO의
   단순 분류값이라 모호하다는 점은 VALIDATE-001에서 이미 확인된 문제다
   (ADR-0026 결정 2와 같은 이유). 필요성이 실사용에서 명확히 드러나면
   별도 Task(가칭 MATCH-003)에서 좁게 재검토한다.

**대안**: 각 결정 항목에서 기각한 대안은 위 결정 2/4/5/6에 함께 기술했다.
추가로 검토했으나 기각한 것: **MATCH-001을 semantic 방식으로 완전히
대체(삭제)** — 기각. ADR-0026의 deterministic 채점은 여전히 저렴하고
100% 재현 가능하며 evidence가 항상 실제 필드 값이라는 장점이 있어,
LLM 호출 실패/비용 상황에서도 항상 쓸 수 있는 baseline으로 유지할
가치가 있다. **매칭 결과 영속화(캐싱)** — 기각(이번 시점). MATCH-001과
동일 이유(ADR-0026 결정 3) — 트래픽/요청 빈도 데이터가 없다.

**이유**: MATCH-001이 증명한 "deterministic 방식이 짧고 정형화된
텍스트에는 정직하고 저렴하다"는 장점과, 이번 VALIDATE-001이 증명한
"광범위 직군 taxonomy는 deterministic 방식으로 못 메운다"는 한계를
동시에 인정하고, 두 방식을 경쟁시키지 않고 **병존**시키는 것이 가장
단순하면서도 실질적인 개선이다. hallucination 방지는 ID 기반 검증(결정
3)과 evidence enum(결정 6)이라는, 자연어를 신뢰하지 않고 구조적으로
검증 가능한 것만 검증한다는 일관된 원칙으로 달성한다 — 이는 AGENTS.md의
"AI가 사용자가 하지 않은 경험/수치를 만들어내지 못하게 막는다"는 핵심
제약을 매칭 기능에도 계속 일관되게 적용한 것이다.

**영향**: `POST /api/jobs/{jobId}/semantic-match`는 유료 외부 API를
호출하는 새 endpoint이므로, provider 콘솔의 사용량/비용 알림 설정을
계속 권장한다(ADR-0024와 동일). `deterministicScore`/`semanticScore`
두 점수가 응답에 함께 노출되므로, 향후 frontend/AGENT를 설계할 때 어느
점수를 대표값으로 쓸지 혼동하지 않도록 "semanticScore가 이 API의 대표
점수"라는 점을 API 문서/DTO 주석에 계속 명시해야 한다. 이번 Task의
`[수동]` E2E 검증(Case A~D)에서 prompt/evidence 품질 문제가 드러나면
PKB-008 → PKB-008.1(ADR-0027)과 같은 후속 보정 Task가 필요할 수 있다.
eligibility를 나중에 추가하는 시점에는 이번 ADR의 evidence/ID 검증
원칙을 그대로 유지해야 한다.

---

## ADR-0029: 지원 전략 분석(AGENT-001) — MATCH-002 후보 풀 재사용, score는
## MATCH-002 단일 소유, reason 미전달, 별도 evidence enum, PKB empty=409

- 날짜: 2026-08-22
- 상태: 확정
- 관련 Task: AGENT-001

**문제**: MATCH-001(ADR-0026)/MATCH-002(ADR-0028)는 "이 경험이 이 공고와
얼마나 관련 있는가?"에 답한다. `docs/PROJECT.md`/`docs/ROADMAP.md`의 다음
단계는 그 다음 질문 — "그렇다면 무엇을 강조하고 어떤 경험을 어떤 관점에서
활용해야 하는가?"다. 이를 위해 두 번째 LLM 호출(채용공고 + PKB를 보고
전략을 정리)이 필요한데, 순진하게 설계하면 세 가지 위험이 생긴다: (1)
전체 PKB를 다시 LLM에 넣으면 프롬프트가 커지고 관련성 낮은 공고에서도
LLM이 억지로 그럴듯한 전략을 만들어낼 여지가 생긴다, (2) 이 두 번째 LLM이
"얼마나 관련 있는가"를 다시 판단하게 하면 MATCH-002와 서로 다른(심지어
모순되는) 두 관련도 점수가 API 전체에 공존하게 되어 "어느 점수가
맞는가"라는 새로운 혼란을 만든다, (3) MATCH-002가 이미 LLM으로 생성한
자연어 `reason`을 두 번째 LLM에게 사실처럼 전달하면, 첫 번째 LLM의 판단
오류나 과장이 두 번째 LLM에서 한 번 더 증폭(hallucination의 chain)될
위험이 있다. 1차 설계 리뷰에서 사용자가 (2)를 명시적으로 지적해 "AGENT는
새로운 관련도 점수를 만들지 않는다"로 설계를 수정했다.

**결정**:

1. **후보 풀 재사용, 전체 PKB 재입력 금지.** `AgentAnalysisService`는
   먼저 `SemanticJobMatchService.match(jobId)`(기존 `@Autowired` 빈)를
   그대로 호출해 `SemanticJobMatchResponse`를 얻고, 그 응답의 4개 match
   배열(`experienceMatches` 등, 최대 14개)에 있는 id만으로 두 번째 LLM의
   입력을 구성한다. MATCH-002가 이미 전체 PKB를 훑어 관련도 상위를
   골라뒀으므로 이를 재사용하면 프롬프트가 작아지고, MATCH-002가 애초에
   매치를 거의 못 찾은 공고(후보 풀이 작거나 비어 있음)에서는 두 번째
   LLM의 재료 자체가 부족해져 억지 positioning이 구조적으로 억제된다.
   `SemanticJobMatchService.match()`를 그대로 호출하므로
   `careerops.semantic-match.*` 지표가 함께 증가하는 것은 의도된 정상
   동작이다(별도 우회/복제 코드를 만들지 않는다 — 만들면 두 코드 경로가
   미묘하게 갈라질 위험과 유지보수 비용만 늘어난다).
2. **score는 MATCH-002 단일 소유(single source of truth), 두 번째 LLM은
   score를 생성하지 않는다.** `RawAgentAnalysisResult`/
   `RawExperienceRecommendation`/`RawPkbRecommendation`(LLM structured
   output)에는 `score`/`relevanceScore` 필드를 아예 두지 않는다. LLM은
   추천 배열의 **순서**로만 "전략적으로 무엇을 더 우선할지"를 표현하고,
   서버가 이 순서를 그대로 보존해(재정렬하지 않음) 경험 추천에 한해
   `priority = 1..N`을 부여한다. 최종 응답에 노출하는 점수 필드
   (`semanticMatchScore`)는 서버가 MATCH-002 응답에서 해당 id의 원래
   `score`를 그대로 찾아 채운 값이다. 필드명을 `relevanceScore`처럼
   모호하게 짓지 않고 `semanticMatchScore`로 명명해, 이 값의 출처가
   MATCH-002라는 사실이 이름에서 드러나게 한다. 이 구조적 분리 덕분에
   "관련도"와 "전략적 우선순위"라는 서로 다른 두 질문에 각각 정확히 한
   LLM 호출만 책임지게 되어, 두 점수가 서로 다른 값을 주장하며 충돌하는
   상황 자체가 설계상 발생할 수 없다.
3. **MATCH-002의 자연어 `reason`은 두 번째 LLM 프롬프트에 절대 포함하지
   않는다.** 대신 실제 PKB 원본 필드(title/summary/detail/tags/bullets
   등)와 MATCH-002의 evidence enum(카테고리 힌트로만), 숫자 score만
   전달한다. `reason`은 첫 번째 LLM이 만든 자연어 판단이라 그 자체로
   이미 어느 정도 해석/요약이 섞여 있다 — 이를 사실처럼 두 번째 LLM에
   넘기면, 첫 번째 LLM의 사소한 과장이나 오류가 "이미 검증된 사실"인 것
   처럼 두 번째 LLM 출력에 스며들어 hallucination이 한 단계 더 증폭될
   위험이 있다. 두 번째 LLM은 반드시 원본 데이터에서 스스로 reason/
   emphasisPoints를 새로 생성해야 하며, 그래야 각 LLM 호출의 근거를
   독립적으로 원본 데이터까지 추적할 수 있다.
4. **hallucination 방지는 여전히 ID 기반 all-or-nothing이되, tie-break는
   score가 아니라 배열 순서다.** LLM은 프롬프트에 실제로 포함된 후보 풀
   id 중에서만 선택할 수 있고, 카테고리 후보 풀에 없는 id가 하나라도
   나오면 응답 전체를 실패 처리한다(MATCH-002/PKB-008과 동일 all-or-nothing
   사상). 다만 MATCH-002는 "중복 id는 최고 score 유지", "상한 초과는 score
   내림차순 truncate"였는데, 이번엔 LLM이 score를 만들지 않으므로 이
   기준을 쓸 수 없다 — 대신 "중복 id는 배열에서 먼저 등장한 것만 유지",
   "상한 초과는 배열 순서상 앞쪽 N개만 유지"로 tie-break 기준만 바꾸고,
   "이것들은 hallucination이 아니라 사소한 이탈이라 전체 실패로 처리하지
   않는다"는 사상은 그대로 유지한다.
5. **`match.dto.EvidenceSource`를 확장하지 않고 별도
   `agent.dto.AgentEvidenceSource` enum을 신설한다.** MATCH-002는 이미 3
   라운드 리뷰를 거쳐 PASS된 production API/DTO 계약이다. 이번 Task가
   필요로 하는 값은 기존 값 전부 + `EXPERIENCE_BULLET` 1개뿐이라 "기존
   enum에 값 하나 추가"가 얼핏 최소 변경처럼 보이지만, 그렇게 하면
   "MATCH-002 파일은 한 글자도 수정하지 않는다"는 이번 설계 전제와
   충돌하고, 이후 AGENT 쪽 요구로 evidence 값이 더 늘어나거나 의미가
   갈릴 때 MATCH-002의 production 계약까지 함께 흔들리게 된다. 가벼운
   enum 복제 비용(가장 값 대부분 중복)을 감수하고 두 API의 evidence 계약을
   독립적으로 진화 가능하게 분리하는 쪽을 택한다.
6. **PKB(`CareerExperience`/`Certification`/`Education`/`Award`) 4종이
   전부 0건이면 200이 아니라 명시적 409를 반환하고, LLM을 아예 호출하지
   않는다.** MATCH-002는 PKB가 비어 있어도 "계산할 것이 없다"는 사실
   자체가 유효한 답이라 `semanticScore=0.0`인 200을 반환했지만, AGENT의
   질문("무엇을 강조해야 하는가")은 강조할 대상 PKB가 하나도 없으면
   질문 자체가 성립하지 않는다 — 이 경우 200과 함께 빈 배열을 주면
   클라이언트가 "분석했지만 추천할 게 없다"와 "분석 자체가 불가능했다"를
   구분할 수 없다. `ImportBatchService`/`ImportCandidateService`가 "현재
   상태가 이 동작을 지원하지 않는다"는 사실을 전역 예외 처리 계층 없이
   서비스가 직접 `ResponseStatusException(HttpStatus.CONFLICT, message)`로
   던지는 기존 컨벤션과 같은 성격의 문제로 보고 동일한 패턴(409, 서비스가
   직접 던짐, `AgentAnalysisException`을 거치지 않음)을 그대로 적용한다.
   이 체크는 `semanticJobMatchService.match()` 호출 이전에 수행해, PKB가
   비어 있는 요청에서는 semantic match 호출조차 발생하지 않게 한다(불필요한
   LLM 호출 비용을 두 단계 모두에서 절약).
7. **timeout은 MATCH-002/PKB-008.1과 별개 네임스페이스
   (`careerops.ai.agent.*`, connect 10/request 60)로 신설한다.** MATCH-002의
   45초는 이번 스키마(최대 14개 후보에 대한 reason/emphasisPoints, 자연어
   요약 몇 개)와 출력 규모가 다르고, PKB-008.1의 300초는 16,000 output
   token 구조화 추출이라는 훨씬 큰 작업 기준이라 재사용하지 않는다. "45s +
   60s = 105s가 이 API의 hard maximum"이라는 표현은 쓰지 않는다 — 이는
   provider별 request timeout일 뿐이며, SDK 기본 재시도(429/5xx/네트워크
   timeout 한정)로 인해 실제 wall-clock은 더 길어질 수 있다. 이번 Task에서
   별도 end-to-end HTTP timeout은 추가하지 않는다.

**대안**:
- **두 번째 LLM이 관련도를 재판단하고 그 값을 최종 응답의 대표 점수로
  사용** — 기각(사용자 리뷰로 수정). MATCH-002와 별도로 또 다른 관련도
  판단이 생기면 두 점수가 다른 근거(다른 프롬프트, 다른 후보 풀 노출)로
  계산되어 서로 모순될 수 있고, 사용자가 "어느 점수를 믿어야 하는가"를
  또 판단해야 하는 부담이 생긴다.
- **MATCH-002 `reason`을 그대로 두 번째 LLM에 전달(맥락 제공 목적)** —
  기각. hallucination chain 위험이 사실 기반 판단이라는 이 프로젝트의
  핵심 제약과 직접 충돌한다. 실제 원본 데이터가 이미 프롬프트에 포함돼
  있으므로 `reason`을 추가로 줄 실익도 크지 않다.
- **전체 승인 PKB를 두 번째 LLM에도 다시 입력** — 기각. 프롬프트가
  커지고, 후보 풀 좁히기가 주는 "관련성 낮은 공고에서 억지 positioning
  억제" 효과를 잃는다. PKB 규모가 크게 늘어나 MATCH-002의 후보 풀
  선별만으로 충분하지 않다는 근거가 생기면 재검토한다.
- **`match.dto.EvidenceSource`에 `EXPERIENCE_BULLET` 추가 후 재사용** —
  기각. 위 결정 5 이유. MATCH-002 production DTO를 backward-compatible하게
  수정하는 것 자체는 기술적으로 가능하지만, 이번 Task의 전제("MATCH-002
  전 파일 무변경")와 "두 API의 evidence 계약을 독립적으로 진화시킨다"는
  판단에 따라 채택하지 않는다.
- **PKB empty를 MATCH-002처럼 200 + 빈 배열로 처리** — 기각. 위 결정 6
  이유. "추천할 게 없다"와 "분석이 애초에 불가능하다"를 구분해야 하는
  이 API의 특성상 명시적 실패가 더 정직하다.
- **`careerops.ai.match.*` 또는 PKB-008.1 timeout 재사용** — 기각. 위
  결정 7 이유(출력 규모가 다름).

**이유**: 이 결정 전체를 관통하는 원칙은 "각 LLM 호출은 정확히 하나의
질문에만 답하고, 그 질문에 필요한 최소한의 근거만 본다"는 것이다.
MATCH-002는 "관련도"를, AGENT는 "우선순위/포지셔닝"을 답한다 — 이
경계를 흐리면(두 번째 LLM이 관련도도 다시 판단하면) 책임 소재가
모호해지고 사용자가 두 점수 중 무엇을 신뢰해야 할지 판단해야 하는 부담이
생긴다. reason 미전달과 ID 기반 all-or-nothing 검증은 AGENTS.md의 "AI가
사용자가 하지 않은 경험/수치를 만들어내지 못하게 막는다"는 핵심 제약을
두 번째 LLM 호출에도 동일하게, 그리고 첫 번째 LLM의 판단이 두 번째로
전파되는 새로운 경로까지 포함해 일관되게 적용한 것이다.

**영향**: `AgentAnalysisResponse`의 `semanticMatchScore` 필드가
MATCH-002의 `score`와 항상 동일한 값을 가져야 한다는 불변식이 생긴다 —
향후 MATCH-002의 점수 계산 방식이 바뀌면 AGENT 응답도 자동으로 그 변경을
반영하게 된다(재계산 로직을 따로 두지 않았으므로 자연히 따라간다). 두
개의 독립적인 Anthropic 호출(semantic match + agent analysis)이 순차로
발생하므로 요청당 지연/비용이 MATCH-002 단독 대비 늘어난다 — 이는
`careerops.agent-analysis.duration`/`careerops.semantic-match.duration`
두 지표를 함께 관찰해야 실제 병목을 파악할 수 있다는 뜻이다. 별도
`AgentEvidenceSource` enum을 유지하는 한, 두 evidence enum 값 목록이
우연히 갈리지 않도록(둘 다 같은 JobPosting/PKB 필드 집합을 가리키므로)
향후 한쪽에 값을 추가할 때 다른 쪽도 함께 검토해야 한다는 수동 동기화
부담이 생긴다 — 실제로 값 집합이 크게 벌어지는 사례가 반복되면 공유
타입으로 통합하는 재검토가 필요할 수 있다.

---

## ADR-0030: 자기소개서 초안 생성(AGENT-002) — 승인 PKB 전체 노출(AGENT-001
## 후보 풀 원칙 의도적 이탈), 문항 공동 분석 단일 호출, 글자수 repair는
## best-effort 예외, questionId/PKB id 이중 all-or-nothing, 영속화 없음

- 날짜: 2026-08-23
- 상태: 확정
- 관련 Task: AGENT-002

**문제**: AGENT-001(ADR-0029)은 "이 공고에서 무엇을 강조할 것인가"에
답한다. `docs/PROJECT.md`의 다음 단계는 "실제 자기소개서 문항에 그 전략을
어떻게 배치하고 초안까지 쓸 것인가"다. 순진하게 설계하면 두 가지 위험이
있다: (1) 문항을 하나씩 독립적으로 LLM에 던지면 각 호출이 서로의 선택을
몰라 같은 경험이 여러 문항에 반복 등장한다 — 이는 이번 기능의 핵심 가치
("여러 문항을 함께 보고 경험 중복을 최소화한다")와 정면으로 충돌한다.
(2) AGENT-001이 확립한 "MATCH-002 후보 풀만 재사용"(ADR-0029 결정 1)
원칙을 AGENT-002에도 그대로 적용하면, "협업"/"갈등"/"성장" 같이 직무
relevance는 낮지만 문항 자체에는 반드시 필요한 경험이 애초에 후보 풀에
없어 선택 자체가 불가능해진다. 사용자가 설계 조사 단계에서 이 시나리오를
명시적 acceptance criteria로 지정했다: "AI서비스개발 공고에서 AGENT-001이
FinSight/RAG 위주로 추천해도, 협업 문항에는 실제 승인된 PKB 안의 협업
경험을 선택할 수 있어야 한다. 억지로 AGENT-001 후보만 고집하면 FAIL."

**결정**:

1. **승인 PKB 전체를 후보로 노출한다 — ADR-0029 결정 1(후보 풀만 재사용)을
   AGENT-002에서는 의도적으로 깬다.** `ApplicationDraftService`는
   `AgentAnalysisService.analyze(jobId)`가 반환한 `recommendedExperiences`
   등 4개 배열의 id로 후보를 좁히지 않고, `CareerExperienceRepository`/
   `CertificationRepository`/`EducationRepository`/`AwardRepository`의
   `findAll()`로 승인 PKB 4종 전체를 재조회해 LLM 입력에 포함한다.
   AGENT-001의 `recommendedExperiences`/`primaryMessage`/
   `positioningSummary`/`avoidOrBeCareful`/`gaps`는 프롬프트에 "전략
   참고 자료"로만 포함될 뿐, 후보를 필터링하는 데는 쓰지 않는다.
   역할을 명확히 분리한다 — **MATCH-002/AGENT-001 = 직무 relevance
   우선순위, AGENT-002 = 문항 적합성 + 지원서 전체 구성**. 현재 실측
   PKB 규모(경험 6~8건/자격 8~11건/학력 4~5건/수상 1건, AGENT-001 리뷰
   기준)에서는 전체를 프롬프트에 넣어도 크기 문제가 없다(ADR-0028의
   "PKB가 수백 건으로 커지면 재검토" caveat과 동일 전제를 이어받는다).
2. **문항 전체를 단일 LLM 호출로 공동 분석한다(Q1→LLM, Q2→LLM 반복
   호출 금지).** `RawApplicationDraftResult`가 `overallStrategy`와 문항
   수만큼의 `questionResults[]`를 한 번의 structured output 호출로
   동시에 반환한다. 문항을 독립 호출로 나누면 각 호출이 서로 다른 문항의
   선택을 알 수 없어 "경험 중복 최소화"라는 목표를 구조적으로 달성할 수
   없다. outline과 draft도 별도 2단계 호출로 나누지 않고 같은 호출에서
   함께 생성해 호출 횟수를 불필요하게 늘리지 않는다.
3. **AGENT-001은 매 요청마다 내부 서비스로 재호출하고, 결과를 캐싱·영속화
   하지 않는다.** `ApplicationDraftService`가 `AgentAnalysisService`를
   Spring bean으로 직접 주입받아 호출한다(Controller→Controller 호출
   아님, ADR-0029와 동일 패턴). 클라이언트가 AGENT-001 결과를 request
   body로 넘기게 하는 방식은 채택하지 않는다 — API 계약이 "PKB/공고
   상태가 바뀌면 항상 최신 전략을 반영한다"는 단순한 불변식을 유지하게
   한다. 이 결정의 대가로 성공 경로 기준 Anthropic 호출이 요청당 최대
   3회(semantic match 1 + agent analysis 1 + draft plan 1, 글자수 repair
   발생 시 4회)가 되고, AGENT-001 실측 63.2~85.7초(ADR-0029 Task 결과)에
   draft 생성 호출이 더해져 총 응답 시간이 **90~180초, repair 발생 시 그
   이상**이 될 수 있다 — 사용자가 설계 승인 단계에서 이 latency를 인지한
   상태로 동기(synchronous) 응답 방식을 명시적으로 선택했다(비동기
   Job/polling 구조는 이번 Phase에 도입하지 않는다).
4. **ID 검증은 이중 all-or-nothing(questionId 축 + PKB id 축)이며, 유효
   집합의 기준이 서로 다르다.** PKB id(`primaryExperienceId`/
   `supportingExperienceIds`/`certificationIds`/`educationIds`/
   `awardIds`)의 유효 집합은 이번 요청이 조회한 **승인 PKB 전체**다(결정
   1과 일관). 어느 문항의 어느 필드에서든 unknown id가 하나라도 있으면
   응답 전체 실패. `supportingExperienceIds` 등 배열 **내부** 중복은
   ADR-0029와 동일하게 사소한 이탈로 처리(첫 등장만 유지, dedup, WARN
   로그, 실패 아님). 서로 다른 **문항 간** 동일 `primaryExperienceId`
   재사용은 "중복 최소화" 지시를 system prompt 레벨의 소프트 정책으로만
   두고 런타임 실패 조건으로 강제하지 않는다 — PKB가 작아 정말로 겹칠
   수밖에 없는 정당한 경우(관련 경험이 1개뿐인데 문항이 2개)까지 실패
   처리하면 이 기능의 실사용성을 해친다.
   questionId 축은 별도 규칙이다: request 내 중복 `questionId`는 LLM
   호출 이전 **400**(입력 정합성 문제). LLM 응답이 request에 없는
   `questionId`를 반환하거나, request의 어떤 `questionId`에 대한 결과가
   빠져 있거나, 같은 `questionId`를 결과에서 두 번 반환하면 셋 다
   **응답 전체 실패(502)** — PKB id 축과 달리 questionId 중복은 dedup
   대상이 아니라 provider validation failure로 취급한다(문항-결과
   매칭이 index가 아니라 questionId 기준 Map이므로, 중복 반환은 "어느
   쪽이 진짜 답인지" 판단할 근거가 없는 구조적 오류이기 때문이다).
5. **글자수 repair는 최대 1회, 배치, best-effort — "provider 실패 =
   전체 실패" 원칙의 유일한 의도적 예외다.** `maxLength`가 지정된 문항
   중 1차 생성 결과가 초과한 것이 있으면, 초과한 문항들만 모아 배치로
   1회 repair 호출을 보낸다(문항별 개별 재시도 없음 — 호출 횟수 폭발
   방지). repair 프롬프트는 "새 사실/id/context 추가 금지, 기존 근거로만
   축약"을 명시한다. repair 후에도 초과하면 재시도하지 않고
   `limitExceeded=true`로 표시할 뿐 실패시키지 않는다. **repair 호출
   자체가 provider 에러(timeout 등)로 실패해도 전체 요청은 실패하지
   않는다** — 1차 draft를 `limitExceeded=true`와 함께 그대로 반환한다.
   semantic match/agent analysis/1차 draft 호출 실패는 지금까지와 동일하게
   전체 실패(502, silent fallback 없음, partial 200 없음)이며, 이 비대칭은
   repair가 필수 정확성 요소가 아니라 best-effort 개선이기 때문에 의도적
   으로 둔 것이다.
6. **저장하지 않는다.** `POST` → 계산 → response, 신규 엔티티/테이블/
   migration 없음. MATCH-001/MATCH-002/AGENT-001이 유지해온 무상태
   원칙과 일관되고(ADR-0026/ADR-0028), 실제 트래픽/수정 이력 요구가
   아직 없다.
7. **문항 개수 상한 10개, timeout은 connect 10초/request 150초를
   초기값으로 두되 실측 후 재조정 조건부.** `ApplicationDraftRequest
   .questions`가 10개를 초과하면 400. 150초는 AGENT-001의 60초(§9,
   ADR-0029)를 그대로 재사용하지 않고(draft 텍스트 생성이라는 더 큰
   출력 규모), PKB-008.1의 300초(ADR-0027)도 그대로 재사용하지 않는다
   (작업 성격이 다름) — 대신 PKB-008.1이 120초 추정을 300초로 재조정한
   선례(ADR-0027)를 반복하지 않기 위해, Task 명세에 "실제 dev DB +
   Anthropic API E2E로 실측 후 필요시 조정"을 명시적 조건으로 남긴다.
   repair 호출도 같은 timeout을 재사용한다(별도 3번째 네임스페이스는
   과도).
8. **`missingCompanyContext`(boolean, 문항 단위)를 둔다.** ADR-0029
   시점에 이미 확인된 사실 — `JobPosting`에는 `companyName`/`title`/
   `careerLevel`/`educationRequirement`/`jobCategory` 외에 회사 사업/
   문화/인재상을 서술하는 필드가 전혀 없다 — 이 그대로 AGENT-002에도
   적용된다. 지원동기 문항처럼 기업 정보가 필요한 문항에서 이 근거
   부족을 문자열 매칭 없이 구조적으로 드러내기 위한 필드다. system
   prompt는 이 5개 필드 외의 회사 정보를 절대 추측하지 말라고 명시한다.

**대안**:
- **AGENT-002도 AGENT-001 추천 후보 풀만 사용** — 기각(§29 acceptance
  criteria와 정면 충돌). 직무 relevance와 문항 적합성이라는 서로 다른
  두 질문을 하나의 후보 풀 제한으로 뭉뚱그리면, relevance가 낮지만
  문항에 반드시 필요한 경험(협업/갈등/성장 등)을 구조적으로 선택할 수
  없게 된다.
- **문항별 독립 LLM 호출(Q1→LLM, Q2→LLM, ...)** — 기각. 각 호출이 서로의
  선택 결과를 모르므로 경험 중복을 사후에 조정하는 별도 조율 단계가
  필요해지고, 이는 오히려 구조를 더 복잡하게 만들며 "공동 분석"이라는
  핵심 가치를 우회하는 셈이 된다.
- **클라이언트가 AGENT-001 결과를 request body로 전달** — 기각(사용자
  요구사항에서 명시적으로 배제). PKB/공고가 바뀌어도 클라이언트가 오래된
  전략을 계속 재사용할 위험이 생기고, "전략 산출은 항상 서버가 최신
  상태로 계산한다"는 기존 API들의 불변식과 어긋난다.
- **AGENT-001 결과 persistence/cache 도입** — 기각(이번 범위 과함). 이번
  Phase에서 캐시 무효화 정책(공고/PKB 변경 시 언제 무효화할지)까지
  설계하는 것은 과도하다. latency가 실사용에서 실제로 문제가 되면 별도
  Task로 재검토한다.
- **글자수 초과를 validation failure로 처리(설계안 A)** — 기각. 사용자가
  실제 제출에 쓸 초안을 만드는 기능에서, 근거는 정확한데 글자수만 살짝
  넘었다는 이유로 전체를 실패시키는 것은 사용성을 심하게 해친다.
- **글자수 초과를 그대로 반환만 하고 repair 없음(설계안 C)** — 기각(이번
  시점). repair 1회·배치·best-effort 정책의 복잡도가 과도하지 않다고
  판단해 설계안 B를 선택했다. 구현 중 실제로 repair 로직이 예상보다
  복잡해지면(예: repair 프롬프트가 hallucination을 유발) 이 결정을
  재검토하고 C로 후퇴할 수 있다.
- **questionId 중복 반환도 PKB id처럼 dedup 처리(첫 등장 유지)** — 기각.
  PKB id 중복은 "같은 사실을 두 번 언급"이라는 사소한 이탈이지만,
  questionId 중복은 "이 문항에 대한 답이 두 개 존재하는데 어느 쪽이
  진짜인지 구조적으로 알 수 없다"는 다른 성격의 문제라 사용자 요구사항이
  명시적으로 "provider validation failure"를 요구했다.
- **비동기 Job(202 + polling) 구조 도입** — 기각(이번 Phase). AGENT-001이
  이미 60~86초 동기 응답을 실사용 가능한 선례로 남겼고, 현재 인프라에
  Job 상태 저장소가 없다. 사용자가 latency를 인지한 채로 동기 방식을
  명시적으로 선택했다. 실사용에서 타임아웃/이탈이 실제로 문제가 되면
  별도 Task로 도입을 재검토한다.

**이유**: 이 결정을 관통하는 원칙은 ADR-0029와 동일하되 경계가 다르다 —
"각 LLM 호출/서비스는 정확히 하나의 질문에만 답한다"는 원칙 아래
MATCH-002/AGENT-001은 "무엇이 직무와 관련 있고 무엇을 강조할지"를,
AGENT-002는 "실제 문항에 무엇을 어떻게 배치하고 쓸지"를 답한다. 이
경계를 지키려면 AGENT-002는 AGENT-001이 좁혀놓은 후보에 갇히지 않고
승인 PKB 전체를 볼 수 있어야 한다. hallucination 방지(ID 기반
all-or-nothing, 실제 PKB 원본을 사실 source로 재조회)와 "AI가 사용자가
하지 않은 경험/수치를 만들어내지 못하게 막는다"(AGENTS.md)는 원칙은
AGENT-001과 동일하게 유지하되, 이번엔 questionId라는 두 번째 축의
정합성까지 함께 지켜야 하는 것이 새로운 지점이다.

**영향**: `ApplicationDraftService`는 `career/*` 4개 Repository의
`findAll()`을 직접 호출하는 첫 사례가 된다(MATCH-002/AGENT-001은 모두
좁혀진 id 목록으로 `findAllById`만 호출했다) — PKB 규모가 수백 건으로
커지면 이 전체 조회와 프롬프트 크기를 재검토해야 한다. AGENT-001을 매
요청 재호출하므로 `careerops.semantic-match.*`/`careerops.agent-analysis.*`
지표가 AGENT-002 호출로도 함께 증가하는 것이 의도된 연쇄다(ADR-0029와
동일 원칙, `docs/METRICS.md`에 명시). `application-draft` timeout
150초는 확정값이 아니라 실측 후 조정 대상이라는 조건이 Task 명세에
남으므로, E2E 검증 단계에서 실제 소요 시간을 반드시 기록해야 한다.
글자수 repair의 best-effort 예외는 "provider 실패 = 항상 전체 실패"라는
지금까지의 단순한 규칙에 처음으로 예외를 만든다 — 향후 다른 기능에서
유사한 best-effort 후처리 단계를 추가할 때 이 비대칭을 참고 선례로 삼을
수 있다.

---

## ADR-0031: 다건 채용공고 추천(RECOMMEND-001) — Batch Semantic Ranking
## 단일 호출, OPEN 전체 cap 없이 candidate화, MATCH-001 hard filter 금지
## 재확인, matchedThemes 미도입, recommendationScore는 semanticScore와
## 별개 척도

- 날짜: 2026-08-24
- 상태: 확정
- 관련 Task: RECOMMEND-001

**문제**: MATCH-001(ADR-0026)/MATCH-002(ADR-0028)/AGENT-001(ADR-0029)/
AGENT-002(ADR-0030)는 모두 "공고 1건"을 전제로 설계됐다. 실측 latency는
MATCH-002 수십 초(공고당 Anthropic 1회), AGENT-001 63~86초(2회),
AGENT-002 최대 356초(3~4회)다. `docs/ROADMAP.md`의 다음 단계는 "여러
공고 중 지금 먼저 볼 공고를 어떻게 추릴 것인가"인데, 이 기존 구조를 공고
N건에 그대로 반복 적용하면(N번 공고 → N번 Anthropic 호출) 비용/latency가
자동 추천에 쓸 수 없는 수준이 된다 — 예를 들어 실측 OPEN 420건에 MATCH-002를
반복하면 순차 기준 최소 2.9시간이 걸린다. 반대로 "MATCH-001 점수로 먼저
후보를 좁힌 뒤 상위만 MATCH-002를 돌리자"는 직관적인 대안은
VALIDATE-001/ADR-0026에서 이미 반증됐다 — 실제 PKB(Java/Spring/AI/RAG
프로필)로 "한국교통안전공단 AI서비스개발", "한전KDN AI 로봇플랫폼"
(둘 다 jobCategory="정보통신")이 MATCH-001 `overallScore=0.0`으로 나오는
반면, "연구"라는 우연한 substring 때문에 무관한 공고가 0.42로 높게 나온
사례가 실측으로 확인되어 있다. 즉 MATCH-001은 이 두 공고를 candidate
풀에서 아예 제거해버리는 false negative를 실제로 일으킨다.

**결정**:

1. **candidate는 `status='OPEN'` 전체를 cap 없이 사용한다 (mechanical
   truncate 없음).** 조사 시점 실측 OPEN 420건, compact 표현 기준 예상
   input token은 약 45,000~60,000 수준으로 Claude Sonnet 5 context
   window 안에 여유 있게 들어간다. "50건 초과 시 마감 임박순 truncate"
   같은 mechanical cap도 검토했으나, relevance 판단이 개입하지 않는
   기계적 기준이라도 실질적으로 후보의 상당수(약 370건)를 이번 요청에서
   원천 배제하는 recall 손실이 생긴다. 결정 3(MATCH-001 hard filter
   금지)과 같은 원칙 — "관련 있는 공고를 후보 단계에서 놓치지 않는다" —
   을 candidate 크기 문제에도 동일하게 적용해 cap을 두지 않는 쪽을
   택했다. candidate 규모가 향후 수천 건으로 커져 이 가정이 깨지면(예:
   OPEN 공고가 수천 건이 되어 token/latency가 실측으로 문제가 되면)
   그때 chunk/broad-prefilter를 재검토한다 — 이번 Task 범위에서는
   미리 만들지 않는다.
2. **Batch Semantic Ranking: candidate 전체 + compact PKB profile을
   Claude 구조화 출력 1회로 처리한다.** "공고마다 MATCH-002 반복 호출"
   (결정 문제의 근거로 기각)과 "MATCH-001 hard filter 후 상위만
   MATCH-002"(같은 이유로 기각) 대신, N건이든 항상 Anthropic 호출
   1회로 고정되는 구조를 택한다. 이는 AGENT-002(최대 3~4회 연쇄 호출)
   보다도 단순하다.
3. **compact PKB/JobPosting 표현을 쓰되, PKB는 detail/bullets를
   제외하고 JobPosting은 실제 존재가 확인된 필드만 포함한다.** PKB
   실측 규모(CareerExperience 6 / Certification 10 / Education 2 /
   Award 1)는 candidate 420건 대비 프롬프트 크기에 거의 영향을 주지
   않으므로 과도한 축약보다 signal 유지를 우선했다 — CareerExperience는
   id/title/organization/role/summary/tags까지 포함하고 detail/bullets만
   제외한다(AGENT-001/AGENT-002만큼 상세할 필요는 없는 coarse ranking
   목적이며, summary가 이미 요약 정보를 담고 있다). JobPosting은
   id/companyName/title/jobCategory/careerLevel/educationRequirement/
   applicationEndAt만 포함한다 — location/employmentType은 MATCH-002/
   AGENT-001도 프롬프트에서 제외했던 필드라 이번에도 배제했고, status는
   candidate가 이미 전부 OPEN이라 불필요하다.
4. **`recommendationScore`는 MATCH-002의 `semanticScore`와 이름과
   의미를 모두 분리한다.** `recommendationScore`는 "이 candidate 집합
   안에서 이 공고를 우선 검토할 가치가 있는 상대적 정도"이고,
   `semanticScore`는 "이 공고 1건에 대한 심층 관련도"다. 계산 맥락
   (수백 건을 한 번에 상대 비교 vs 단일 공고를 깊게 판단)이 다르므로
   같은 척도라고 가정하지 않는다. 두 값 다 "합격 가능성/서류 통과
   가능성/취업 성공 확률"을 의미하지 않는다는 원칙(ADR-0026/ADR-0028과
   동일)도 그대로 유지한다.
5. **`matchedThemes` 같은 고정 taxonomy 필드는 도입하지 않는다.**
   `reason` + matched PKB id 목록(careerExperienceIds/certificationIds/
   educationIds/awardIds)만으로 추천 근거를 충분히 전달할 수 있다고
   판단했다. ADR-0026 결정 5("하드코딩된 세밀한 기술 동의어/도메인
   taxonomy 사전을 두지 않는다")와 같은 원칙 — 닫힌 목록은 그 목록에
   없는 관련성을 구조적으로 놓치고, 유지보수 부담만 늘린다 — 을 이번
   theme taxonomy 문제에도 동일하게 적용했다.
6. **ID 검증은 MATCH-002 컨벤션을 그대로 따른다: 반환된 jobId 및 PKB
   id가 이번 요청 input 집합 밖이면 응답 전체 실패, 중복 jobId는
   최고 score만 유지, score가 [0.0, 1.0] 범위를 벗어나면 clamp 없이
   전체 실패.** AGENT-001/AGENT-002가 "배열 순서 유지"로 tie-break한
   것은 그 LLM들이 score를 만들지 않기 때문에 나온 대안일 뿐이다.
   RECOMMEND-001은 LLM이 score를 직접 생성하므로, 이미 검증된
   MATCH-002의 "최고 score 유지" 패턴을 그대로 재사용하는 것이 더
   단순하고 새로운 tie-break 규칙을 만들 필요가 없다.
7. **LLM이 반환한 배열 순서를 신뢰하지 않고, 서버가
   `recommendationScore` 내림차순(동점 시 jobId 오름차순)으로
   재정렬한 뒤 Top N을 truncate한다.** MATCH-001/MATCH-002의 정렬
   컨벤션과 동일 — LLM이 score를 만드는 이상 순서까지 LLM에 맡길
   이유가 없고, "score와 배열 순서가 불일치하면 어느 쪽이 맞는가"라는
   모호함을 원천적으로 없앤다.
8. **API는 `POST /api/jobs/recommendations?limit=5`(query param, 기본
   5, 최대 20, 범위 밖은 400)로 신설한다.** 기존
   `/api/jobs/{jobId}/match|semantic-match|agent-analysis`와 같은
   `job` 네임스페이스 아래 두되, `{jobId}` 경로 변수 없이 컬렉션
   레벨 액션으로 둔다 — 이 프로젝트에 "여러 공고를 한 번에 다루는"
   API 선례가 아직 없어 새 최상위 리소스(`/api/recommendations`)를
   만들기보다, 기존 네임스페이스 안에서 가장 단순하게 확장했다.
9. **승인 PKB 4종이 전부 0건이면 200+빈 배열이 아니라 409를 반환하고
   LLM을 호출하지 않는다.** AGENT-001(ADR-0029 결정 6)과 동일 판단 —
   "여러 공고 중 우선순위를 매긴다"는 이 Task의 질문도 강조/추천할
   PKB 근거가 하나도 없으면 성립하지 않는다. MATCH-002가 PKB empty를
   `score=0.0`인 200으로 처리한 것은 "계산할 것이 없다"는 사실 자체가
   유효한 단일 공고 답이기 때문이며, 여러 공고를 골라내야 하는
   RECOMMEND-001의 성격은 AGENT-001에 더 가깝다고 판단했다.
10. **전용 timeout 네임스페이스(`careerops.ai.recommendation.*`,
    connect 10초/request 90초)를 신설하고 기존 값을 재사용하지
    않는다.** MATCH-002(45초)/AGENT-001(60초)/AGENT-002(150초) 전부
    "입력이 작고 출력도 작은" 또는 "입력이 작고 출력이 큰" 조합인
    반면, RECOMMEND-001은 **입력이 매우 크고(candidate 420건) 출력은
    작은(Top N ≤ 20개 짧은 항목)** 지금까지 없던 조합이라 기존 어떤
    값도 근거가 되지 않는다. 90초는 사용자가 제시한 범위(60~90초)의
    상단을 초기값으로 채택했고, PKB-008.1(120초 추정 → 실측 후 300초로
    재조정)과 같은 조건 — 확정값이 아니라 실제 E2E 실측 후 조정
    대상 — 을 그대로 적용한다.
11. **Persistence를 추가하지 않는다 (on-demand response만, migration
    없음).** "이미 알림한 공고인지" 판단은 NOTIFY-001이 이력 저장을
    필요로 할 때 별도로 설계한다 — 이번 Task에서 미리 만들면 실제
    NOTIFY-001 요구사항과 맞지 않는 스키마를 먼저 확정해버릴 위험이
    있다. 같은 이유로 "최근 신규 공고만 추천"(B안)도 이번 범위에
    넣지 않는다 — reliable한 "마지막 추천/알림 시점" 필드가 아직
    없고, 이를 만들려면 결국 persistence가 필요해 이번 결정과
    충돌한다.

**대안**:
- **공고마다 MATCH-002 반복 호출** — 기각. 위 문제 정의 근거(순차 기준
  최소 2.9시간)로 자동 추천에 쓸 수 없다.
- **MATCH-001 score로 hard filter 후 상위만 MATCH-002** — 기각.
  VALIDATE-001/ADR-0026에서 실측된 false negative(정보통신 공고
  0.0점) 때문에 관련 있는 공고가 후보 단계에서 사라진다.
- **candidate에 mechanical cap(예: 50건, 마감 임박순 truncate) 적용**
  — 기각(결정 1). 실측 OPEN 420건이 token 예산 안에 들어오는 상황에서
  370건을 원천 배제할 근거가 부족하다. candidate 규모가 실제로 수천
  건 단위로 커지는 시점에 재검토한다.
- **chunk(25~50건) 단위로 나눠 여러 번 ranking 후 merge/rerank** —
  기각(이번 Task 범위). 현재 실측 규모(420건)가 chunk 없이 single
  batch로 처리 가능한 수준이라 판단했고, chunk를 도입하면 서로 다른
  context에서 계산된 score를 비교해야 하는 문제(절대 척도 비교 불가)가
  새로 생긴다. 이 문제가 실제로 발생하는 candidate 규모(500건대 이상)에
  도달하면 별도 Task로 재검토한다.
- **`matchedThemes` 고정 taxonomy 도입** — 기각(결정 5). ADR-0026
  결정 5와 같은 이유로, 닫힌 목록이 목록 밖 관련성을 놓치는 위험이
  UI 활용 이점보다 크다고 판단했다.
- **PKB empty를 MATCH-002처럼 200+빈 배열로 처리** — 기각(결정 9).
  ADR-0029 결정 6과 같은 이유.
- **`/api/recommendations`를 새 최상위 리소스로 신설** — 기각(결정
  8). 현재로선 `job` 네임스페이스 확장만으로 충분하고, 새 리소스를
  만들 만한 별도 도메인 경계가 아직 없다고 판단했다.

**이유**: 이 결정 전체를 관통하는 원칙은 두 가지다. 첫째, "각 LLM 호출은
정확히 하나의 질문에만 답한다"(ADR-0029/ADR-0030과 동일) — RECOMMEND-001의
질문은 "무엇을 먼저 볼 가치가 있는가"뿐이며, 합격 가능성이나 심층 분석은
답하지 않는다. 둘째, "관련 있는 공고를 후보 단계에서 놓치지 않는다"
(ADR-0026 이후 VALIDATE-001로 실측 확인된 원칙) — 이번 결정에서는
MATCH-001 hard filter뿐 아니라 mechanical candidate cap과 고정 theme
taxonomy에도 같은 원칙을 일관되게 적용했다. AGENTS.md의 "AI가 사용자가
하지 않은 경험/수치를 만들어내지 못하게 막는다"는 제약은 ID 기반
all-or-nothing 검증(결정 6)과 서버가 title/company/deadline을 DB에서
재조회하는 방식(LLM은 jobId만 반환)으로 동일하게 적용된다.

**영향**: RECOMMEND-001은 이 프로젝트에서 처음으로 "공고 1건이 아니라
공고 N건"을 한 LLM 호출에 넣는 사례가 된다 — 향후 candidate 규모가
실제로 수천 건으로 늘어나면 이번에 기각한 chunk 설계를 별도 Task로
재검토해야 한다(그 시점 chunk score 비교 문제도 함께 해결해야 한다).
`recommendationScore`와 `semanticScore`가 이름과 척도 모두 다르다는
사실이 API 문서/DTO 주석에 명시되지 않으면 클라이언트가 두 값을
동일하게 취급하는 혼란이 생길 수 있다 — 구현 시 필드 주석으로 반드시
구분한다. Persistence를 두지 않기로 한 결정은 NOTIFY-001이 "이미
알림 보낸 공고" 판단을 위한 이력 저장을 처음부터 새로 설계해야 함을
의미한다.

---

## ADR-0032: 채용공고 추천 알림 준비(NOTIFY-001) — 전용 entity, LLM 호출
## 위에 추가 트랜잭션 금지, `job_posting_id` UNIQUE 기반 dedupe(`ON
## CONFLICT` 재기각), unseen pool 순차 소진, score/reason만 snapshot

- 날짜: 2026-08-24
- 상태: 확정
- 관련 Task: NOTIFY-001

**문제**: RECOMMEND-001(ADR-0031)은 OPEN JobPosting 전체를 candidate로
Claude 1회 호출로 batch ranking하지만 결과를 저장하지 않는다(on-demand
계산, 무상태). 실제 E2E에서 47~76초가 걸린다. 저장이 없으므로 수집→추천→
알림을 반복 자동화하면 "이 공고를 이미 사용자에게 알려줬는가?"를 판단할
방법이 없다 — 매 호출마다 이미 알린 공고를 다시 알림 대상으로 만들거나,
알림 이력 자체를 추적할 수 없다.

설계 중 중요한 사실을 발견했다: `JobRecommendationService.recommend()`는
이미 `@Transactional(readOnly=true)`이고, 그 안에서 Anthropic 호출(실측
47~76초)이 그대로 실행된다 — 즉 RECOMMEND-001은 이미 그 시간 동안 DB
커넥션을 하나 점유한 채로 LLM 응답을 기다린다. 이는 AGENT-002/RECOMMEND-001
리뷰가 관찰한 "산발적 DB 커넥션 풀 경합 flake"의 더 직접적인 원인
후보다. RECOMMEND-001 코드 변경은 이번 Task 범위 밖이므로, NOTIFY-001은
이 기존 커넥션 점유를 없앨 수 없다 — 대신 그 위에 **추가** 트랜잭션을
쌓지 않는 것으로 문제를 더 키우지 않는 쪽을 택했다.

**결정**:

1. **`NotificationPreparationService`는 `JobRecommendationService` 빈을
   생성자 주입받아 직접 메서드 호출한다(`recommend(20)`). HTTP로
   `/api/jobs/recommendations`를 다시 호출하지 않는다.** Controller→
   Controller HTTP 재호출 금지 원칙(AGENT-001/AGENT-002가 이미 지켜온
   "Service→Service 내부 재사용" 패턴, ADR-0029/ADR-0030과 동일)을
   그대로 따른다. `recommend()`는 always `limit=20`(RECOMMEND-001의
   max)으로 고정 호출해 가능한 넓은 unseen pool을 확보한다 — 이 값은
   NOTIFY 쪽 `limit` 파라미터(아래 결정 7)와 별개다.
2. **`NotificationPreparationService.prepare()`를 감싸는
   `@Transactional`을 두지 않는다.** 3단계로 명확히 분리한다: (1)
   `recommend(20)` 호출 — RECOMMEND-001 자신의 트랜잭션은 그 내부에서
   독립적으로 열리고 닫히며 NOTIFY 쪽이 이를 감싸지 않는다, (2) 반환된
   최대 20건을 메모리에서 가공(dedupe pre-check, OPEN 재확인 — 각각
   배치 1쿼리, 밀리초 수준의 개별 트랜잭션), (3) 최종 후보를 **행마다
   독립적으로** `save()`한다. 이렇게 하면 47~76초짜리 LLM 대기와 DB
   쓰기가 하나의 트랜잭션으로 묶이는 최악의 경우(커넥션을 분 단위로
   점유)를 원천적으로 만들지 않는다.
3. **전용 `JobRecommendationNotification` entity를 신설한다(generic
   `Notification` 추상화를 만들지 않는다).** 이 프로젝트의 `JobApplication`/
   `ApplicationStage`/`ImportCandidate`는 전부 도메인별 전용 entity이고,
   `type`/nullable FK/JSON payload로 여러 알림 종류를 한 테이블에 담는
   generic 패턴의 선례가 전혀 없다. NOTIFY-002(마감/전형 알림)가 실제로
   설계되는 시점에는 dedupe key 자체가 다를 가능성이 높다(예: `(job_application_id,
   reminder_type)` 복합 키 — `JobPosting` 단일 FK가 아님) — 지금 두
   알림 종류의 공통점을 추측해 미리 통합하면 두 요구가 실제로 갈릴 때
   더 큰 리팩터링 비용이 든다. ADR-0026 결정 5/ADR-0031 결정 5와 같은
   YAGNI 원칙을 entity 설계에도 동일하게 적용했다.
4. **dedupe key는 `job_posting_id` 단일 컬럼이고, DB에
   `UNIQUE(job_posting_id)` 제약을 반드시 둔다.** 애플리케이션 레벨
   `existsByJobPostingId()` 사전 체크만으로는 동시 요청 레이스를 막을
   수 없다(두 요청이 동시에 체크를 통과하고 둘 다 INSERT를 시도할 수
   있음) — 최종 정합성은 DB constraint로 보장한다. 정확히
   `job_applications.uk_job_applications_job_posting_id UNIQUE
   (job_posting_id)`(V5 migration)와 같은 스타일을 재사용한다.
5. **`ON CONFLICT DO NOTHING` 같은 native SQL을 다시 검토했으나
   기각한다 — COLLECT-006에서 이미 같은 대안을 검토·기각한 이유가
   그대로 적용된다.** native insert 경로에서는 `@CreationTimestamp`
   콜백이 동작하지 않고, 결과 row 재조회가 어차피 필요해 왕복이
   줄어들지도 않는다. 대신 `JobApplicationService.create()`가 이미
   쓰는 패턴 — `existsByJobPostingId()` 사전 체크(대부분의 경우 불필요한
   INSERT 시도를 줄임) + `save()`를 개별 `try/catch(DataIntegrityViolationException)`로
   감싸 레이스로 인한 잔여 위반만 "이미 알림됨"으로 재분류 — 를 그대로
   재사용한다. Postgres는 트랜잭션 안에서 한 statement가 실패하면
   트랜잭션 전체가 aborted 상태가 되므로(COLLECT-006에서 이미 확인된
   제약), 이 catch는 반드시 row별 독립 트랜잭션(결정 2) 안에서만
   유효하다 — 여러 INSERT를 하나의 트랜잭션으로 묶으면 이 패턴 자체가
   깨진다.
6. **snapshot은 `recommendationScore`/`reason`만 저장하고,
   `companyName`/`title`/`applicationEndAt`은 저장하지 않고 `JobPosting`
   FK로 응답 시점에 재조회한다.** `JobApplicationRepository.search()`가
   이미 `JOIN a.jobPosting p`로 매번 재조회하는 동일 패턴을 쓰고
   있다. reason은 `JobRecommendationService`가 이미 응답 단계에서
   200자로 truncate하지만(RECOMMEND-001 review-1/2에서 실측 확인),
   저장 직전에도 방어적으로 재truncate해 컬럼 크기 초과로 인한 DB
   에러를 원천 차단한다. matched PKB IDs(`careerExperienceIds` 등)는
   이번엔 저장하지 않는다 — KAKAO 메시지 본문에 실제로 "관련 경험: X"
   같은 표시가 필요하다고 확인되기 전까지 JSON/join table을 미리
   추가하지 않는다(YAGNI, 결정 3과 같은 원칙).
7. **`NotificationStatus`는 `PENDING/SENT/FAILED` 3-state enum을 지금
   정의하되, 이번 Task의 production 경로는 PENDING만 생성한다.**
   `ImportCandidateStatus`가 이미 `@Enumerated(EnumType.STRING)` +
   `@Column(length=20)`으로 3-state를 정의해둔 선례가 있고, `VARCHAR`
   컬럼은 enum 값이 늘어나도 migration이 필요 없어 지금 다 정의해도
   비용이 거의 없다. 다만 `sent_at`/재시도 횟수/에러 사유 등 SENT/FAILED
   전이에 실제로 필요한 컬럼과 `updateStatus()` 같은 전이 메서드는
   이번 migration/entity에 추가하지 않는다 — KAKAO-001이 실제로 무엇이
   필요한지(카카오 메시지 id? 에러 메시지 원문?) 확정된 뒤 그때
   추가하는 것이 더 정확하다.
8. **prepare API의 `limit`은 "이번 요청에서 새로 생성할 notification
   최대 개수"로 정의한다(RECOMMEND-001의 `limit`과 의미가 다름).**
   `POST /api/notifications/job-recommendations?limit=5`가 내부적으로
   항상 `recommend(20)`을 호출한 뒤, 이미 알림된 jobId를 skip하며 score
   내림차순 순서로 최대 `limit`개까지만 새로 생성한다. unseen이
   `limit`보다 적으면 있는 만큼만 생성하고 강제로 채우지 않는다.
9. **already-notified를 skip한 뒤에도 Top20 pool에서 다음 unseen까지
   순차적으로 채운다("현재 Top5에 새로 진입한 공고만"이 아니다).**
   대안(Top5 진입분만 알림)은 Top5 밖에 머무르는 관련 공고를 영원히
   알리지 못하는 문제가 있다 — "관련 있는 공고를 후보 단계에서 놓치지
   않는다"는 ADR-0026/ADR-0031이 이미 일관되게 지켜온 원칙과 정면으로
   충돌한다. 순차 소진 방식은 매 회차 호출마다 Top20 pool에서 자연스럽게
   다음 unseen 묶음을 긁어오므로 이 원칙을 그대로 계승한다.
10. **insert 직전 `JobPosting.status`를 배치로 재조회해 `"OPEN"`이
    아니면 해당 건은 생성하지 않는다.** RECOMMEND 호출 시점엔 OPEN이었어도
    insert 시점엔 CLOSED로 바뀌었을 수 있다(수십 초의 LLM 대기 시간
    동안). 과도한 locking 없이 단순 배치 재조회 1회로 충분하다고
    판단했다 — race를 완전히 제거하려는 시도(예: 비관적 락)는 이번
    MVP 규모에 비해 과하다.
11. **`GET /api/notifications/job-recommendations?status=PENDING`
    (status optional, `@PageableDefault(size=20) Pageable`)을 이번
    Task에 포함한다.** `JobPostingController`의 pagination 컨벤션과
    `JobApplicationListResponse`(content/totalElements/totalPages/page/
    size) 패턴을 그대로 재사용해 구현 비용이 낮고, 개발 중 persistence
    확인과 KAKAO-001의 조회 계약을 미리 명확히 하는 실질적 근거가
    있다. 단건 조회(`GET /{id}`)는 KAKAO-001이 배치로 PENDING을
    조회해 처리할 가능성이 높아 이번엔 생략한다.
12. **별도 `CREATE INDEX`를 추가하지 않는다.** `UNIQUE(job_posting_id)`
    제약이 자동으로 btree index를 만들어 dedupe 조회를 이미 커버한다.
    `(status, created_at)` 복합 index는 후보로 검토했으나, 현재 규모
    (요청당 최대 20건 삽입, 조회도 KAKAO-001 전까지는 개발 확인 목적)에서
    이 index로 해결할 실측 성능 문제가 없다 — "불필요한 index 추가
    금지" 원칙에 따라 KAKAO-001이 실제 polling 패턴을 확정한 뒤 필요성을
    재판단한다.

**대안**:
- **Generic `Notification`(type/nullable FK/payload) entity** — 기각
  (결정 3). 이 프로젝트에 선례가 없고, NOTIFY-002의 실제 요구가
  확정되기 전에 미리 통합하면 두 요구가 갈릴 때 더 큰 비용이 든다.
- **`ON CONFLICT DO NOTHING` native SQL insert** — 기각(결정 5).
  COLLECT-006과 동일한 이유(`@CreationTimestamp` 미동작, 왕복 절감
  효과 없음)로 재확인.
- **prepare 전체를 하나의 `@Transactional`로 묶어 원자적으로 처리** —
  기각(결정 2). RECOMMEND-001의 47~76초 LLM 대기를 트랜잭션에 포함시키면
  DB 커넥션을 분 단위로 점유하게 되어, 이미 관찰된 커넥션 풀 경합
  문제를 악화시킨다. Notification 생성은 dedupe key가 있어 멱등이므로,
  일부만 커밋되고 나머지가 실패해도 다음 prepare 호출이 안전하게
  이어서 처리할 수 있다 — 원자성을 포기해도 안전하다.
- **"현재 Top5에 새로 진입한 공고만" 알림** — 기각(결정 9). Top5 밖에
  머무르는 관련 공고를 영원히 알리지 못하는 구조적 결함이 있다.
- **matched PKB IDs를 JSON/join table로 저장** — 기각(결정 6과 같은
  이유). 실제 필요성이 확인되기 전까지 미리 만들지 않는다.
- **SENT/FAILED 전이에 필요한 컬럼(`sent_at` 등)을 지금 미리 추가** —
  기각(결정 7). KAKAO-001이 실제로 무엇을 저장해야 하는지 알기 전에
  스키마를 먼저 확정하면 다시 바꿔야 할 위험이 있다.

**이유**: 이 결정 전체를 관통하는 원칙은 두 가지다. 첫째, "외부 API
대기 시간 동안 DB connection/transaction을 점유하지 않는다" — 이미
관찰된 DB 커넥션 풀 경합 flake의 실제 근본 원인(RECOMMEND-001 자신의
장기 읽기 트랜잭션)을 이번 조사로 처음 명확히 특정했고, NOTIFY-001은
이 위에 쓰기 트랜잭션까지 얹어 문제를 악화시키지 않는 것을 최우선
설계 제약으로 삼았다. 둘째, "관련 있는 공고를 후보 단계에서 놓치지
않는다"(ADR-0026 이후 일관된 원칙) — unseen pool 순차 소진 방식(결정
9)과 OPEN 재확인(결정 10)에도 이 원칙을 동일하게 적용했다. YAGNI
원칙(entity 설계, matched PKB IDs, SENT/FAILED 컬럼)은 ADR-0026 결정
5/ADR-0031 결정 5의 연장선이다.

**영향**: `job_recommendation_notifications` 테이블이 이 프로젝트에서
"LLM 호출을 포함한 기존 서비스를 재사용하되 그 위에 쓰기 트랜잭션을
얹지 않는" 첫 사례가 된다 — 향후 유사한 "무거운 계산 서비스 재사용 +
경량 persistence" 패턴에 이번 3단계 경계 설계가 선례로 참고될 수 있다.
`RecommendationScore`/`reason`이 snapshot이므로, 이후 PKB가 바뀌거나
RECOMMEND-001의 랭킹 로직이 바뀌어도 이미 생성된 notification의 값은
갱신되지 않는다 — 이는 의도된 것이지만, "알림에 표시된 점수/사유가
현재 재계산 결과와 다를 수 있다"는 사실을 KAKAO-001과 향후 UI가
인지해야 한다. `NotificationStatus`에 SENT/FAILED 값이 이미 존재하므로
KAKAO-001은 새 enum을 만들지 않고 값만 사용하게 되지만, 그 전이에
필요한 컬럼은 KAKAO-001이 새 migration으로 추가해야 한다.

---

## ADR-0033: 다건 채용공고 추천 안정화(RECOMMEND-001.1) — transaction
## boundary 분리, immutable candidate snapshot, provider output 상한
## 지시, 좁은 repair retry(최대 1회), timeout 재검토 보류

- 날짜: 2026-08-25
- 상태: 확정
- 관련 Task: RECOMMEND-001.1 (원 Task: RECOMMEND-001/ADR-0031,
  회귀 대상: NOTIFY-001/ADR-0032)

**문제**: NOTIFY-001 실제 E2E(`.ai/tasks/NOTIFY-001.md` "실제 E2E 결과")에서
candidate 452~461건 규모의 `POST /api/jobs/recommendations` 호출 4회 중
3회가 각각 `MALFORMED_RESPONSE`/`UNKNOWN_JOB_ID`/`NETWORK_TIMEOUT`로
실패했다(각 50~81초). RECOMMEND-001(ADR-0031)은 OPEN candidate가 420건
수준일 때 설계·검증됐고, candidate 규모가 계속 커지는 지금(ALIO
scheduler가 계속 신규 공고를 수집) 이 실패율은 재현 가능한 안정성
문제로 확인됐다. 이번 조사로 다음 세 가지 근본 원인 후보를 코드
직접 확인으로 좁혔다:

1. **장기 트랜잭션**: `JobRecommendationService.recommend()`가
   `@Transactional(readOnly=true)`이고, 그 트랜잭션 안에서 (a) 7개
   repository로 PKB/OPEN candidate를 JPA Entity로 읽고 (b) Anthropic
   호출(실측 47~81초)을 기다린 뒤 (c) `convert()` 내부에서
   `jobs.findAllById(...)`로 DB를 다시 조회한다 — DB 커넥션 하나가
   Anthropic 응답을 기다리는 동안 통째로 점유된다. ADR-0032가 이미
   "이 위에 추가 트랜잭션을 얹지 않는다"로 대응했지만, RECOMMEND-001
   자신의 이 장기 트랜잭션 자체는 그대로 남아 있었다.
2. **provider output 상한 부재**: 구조화 출력 schema
   (`RawRecommendationResult`/`RawJobRecommendation`)에 배열 크기 제약이
   전혀 없고, prompt도 "요청한 개수(`limit`, 최대 20) 이하만 반환"이라는
   느슨한 지시뿐이다. `MAX_TOKENS=8_192`는 고정인데 candidate가
   400건대를 넘어가면 모델이 더 많은 후보를 평가·서술하려는 경향이
   생겨 JSON이 도중에 잘릴 위험이 커진다 — `MALFORMED_RESPONSE`의
   유력 원인이다.
3. **넓은 candidate 풀에서 ID hallucination 위험 증가**: 4자리 jobId
   400개 이상이 긴 `<jobs>` 목록에 흩어져 있고, "허용된 ID만 쓰라"는
   자연어 지시 외에 별도 강조 장치가 없다 — `UNKNOWN_JOB_ID`의 유력
   원인이다.

`NETWORK_TIMEOUT`은 이번 조사에서 원인을 확정하지 못했다. 실패
duration(50~81초)이 90초 request timeout보다 짧고 성공 케이스(47~76초)와
겹쳐, 90초 벽을 실제로 넘긴 증거가 없다. Anthropic Java SDK 2.54.0의
공개 문서/GitHub 소스를 조사한 결과, 이 SDK는 기본적으로 **연결 오류/
408/409/429/5xx를 지수 백오프로 최대 2회 자동 재시도**하며(총 3회
시도), **timeout은 "재시도를 제외한" 개별 시도 단위**로 적용된다 —
ADR-0027에서 실측된 "120초 timeout 설정 시 SDK 재시도가 겹쳐 약 6분
뒤에야 최종 실패"(120초×3회 시도와 정확히 일치)가 이 동작과 일치함을
이번에 문서로도 재확인했다. 이 사실을 적용하면, 90초 timeout이 실제로
소진됐다면 실패까지 최대 약 270초(90초×3회)가 걸려야 하는데 실측은
50~81초에 그쳤다 — 즉 `NETWORK_TIMEOUT`으로 분류된 실패가 진짜
"응답을 기다리다 시간 초과"가 아니라, `classify()`가
`AnthropicIoException`/`SocketTimeoutException`/`IOException`/클래스명에
`Timeout` 포함을 전부 하나로 묶어 판단하기 때문에 실제로는 다른 종류의
`IOException`(예: 큰 요청/응답 도중의 연결 재설정)이 `NETWORK_TIMEOUT`
으로 잘못 이름 붙었을 가능성이 있다. 이 가설은 실제 예외 클래스를 로그로
남긴 뒤에야 확정할 수 있어, 이번 ADR은 timeout 값 자체를 바꾸지 않고
진단 가능성만 먼저 개선하기로 결정한다(아래 결정 6).

**결정**:

1. **`RecommendationCandidateReader`를 신설해 DB 읽기와 DTO
   materialize만 `@Transactional(readOnly=true)`로 감싸고,
   `JobRecommendationService.recommend()`/`calculate()`에서는
   `@Transactional`을 제거한다.** 흐름을 `reader.read()`(짧은
   read-only 트랜잭션, 밀리초 단위) → (트랜잭션 종료) →
   `client.recommend(...)`(Anthropic 호출, DB 커넥션 미점유) →
   (필요 시 repair retry, 여전히 DB 커넥션 미점유) → 결과 조립 순서로
   재구성한다. `JobPosting`/`CareerExperience`/`Certification`/
   `Education`/`Award`는 모두 plain `@Column`만 있고 연관관계가
   없으며(`ExperienceTag.careerExperience`만 예외지만 서비스는
   `.getId()`만 사용), Entity를 DTO로 변환해 트랜잭션 밖으로 내보내도
   `LazyInitializationException` 위험이 없음을 코드로 직접 확인했다.
   `convert()` 내부의 재조회(`jobs.findAllById(unique.keySet())`)는
   유지하되 이 역시 짧은 개별 read-only 트랜잭션으로 남는다(ID 검증
   목적의 짧은 조회이며 LLM 대기와 묶이지 않는다).
2. **`RecommendationInput`(및 `RecommendationJobCandidate`/
   `RecommendationExperience`/`RecommendationCertification`/
   `RecommendationEducation`/`RecommendationAward`) immutable record를
   신설하고, `JobRecommendationClient` 인터페이스가 JPA Entity 대신
   이 record를 받도록 변경한다.** `JobRecommendationPromptBuilder`도
   Entity 직접 접근 대신 이 record로 프롬프트를 만든다. 이렇게 하면
   (a) 트랜잭션 경계가 코드 구조로 강제되고(Entity가 애초에 트랜잭션
   밖으로 나갈 수 없음), (b) repair retry(결정 5)가 동일 snapshot을
   재사용함을 타입으로 보장한다(재시도 시 DB를 다시 읽을 방법 자체가
   코드에 없다).
3. **provider output 상한을 schema가 아니라 prompt 지시로만 강제한다.**
   Anthropic structured output(JSON Schema 기반)이 `maxItems`/
   `minItems`/`minimum`/`maximum`/`minLength` 같은 배열·수치 제약
   키워드를 지원하지 않고 포함 시 400으로 거부한다는 사실을 공식
   문서로 확인했다(PKB-008.1/ADR-0027에서 확인된 "union 파라미터
   16개 상한"과는 별개의 제약). 따라서 `providerTopK = max(limit*2,
   20)`을 계산해 prompt에 "recommendations 배열은 최대
   `providerTopK`개까지만 포함하라. 나머지 후보는 평가 대상에는
   포함하되 출력하지 않는다"처럼 명시적 상한 문장으로 지시한다(기존
   "요청한 개수 이하만 반환"이라는 느슨한 문장보다 훨씬 명확한 상한
   숫자를 준다). 모델이 이 지시를 어기고 `providerTopK`보다 많이
   반환해도 서버가 이를 이유로 즉시 실패시키지 않는다 — 기존 dedup
   (최고 score 유지) + 정렬 + `limit` truncate 로직이 초과분을 그대로
   흡수할 수 있으므로 별도 hard-cap 검증 코드를 새로 만들지 않는다
   (YAGNI, ADR-0026/ADR-0031이 지켜온 최소 구현 원칙과 동일).
   `providerTopK`는 최종 API `limit`(1~20)과 다른 값이며, `limit*2` 여유는
   ID 검증 실패나 중복 jobId로 일부가 버려져도 최종 `limit`개를
   채울 여지를 남기기 위함이다.
4. **candidate 자체에는 여전히 cap을 두지 않는다(ADR-0031 결정 1
   재확인).** candidate 크기(현재 420~461건) 문제는 결정 3(output
   상한 지시)으로 대응하며, candidate 입력 자체를 줄이면 ADR-0026/
   ADR-0031이 반증한 "관련 있는 공고를 후보 단계에서 놓친다"는
   false negative가 재발한다. Safety를 위한 절대 상한(예: candidate
   1,000건 초과 시 즉시 4xx)도 이번엔 도입하지 않는다 — 현재 규모
   (461건, 조사 시점 예상 input token 약 50,000~65,000 수준)에서
   근거가 되는 실측 실패가 없고, 미리 만든 hard cap은 그 값 자체가
   추측이 되어 나중에 실제 규모에 맞지 않을 위험이 있다. 대신
   `careerops.recommendation.candidates`(기존 지표)를 계속 관찰하다가
   candidate가 수천 건 단위로 커지는 시점에 별도 Task로
   chunk/prefilter 설계를 재검토한다(ADR-0031 결정 1의 유예 조건과
   동일).
5. **validation/malformed 실패(UNKNOWN_JOB_ID/UNKNOWN_PKB_ID/
   SCORE_OUT_OF_RANGE/MALFORMED_RESPONSE)에 한해 provider repair
   retry를 최대 1회 허용한다.** `JobRecommendationException`에
   `isRepairable()`(위 4개 reason만 true)을 신설해 기존 metric 태깅용
   `isValidationFailure()`(UNKNOWN_JOB_ID/UNKNOWN_PKB_ID/
   SCORE_OUT_OF_RANGE만 포함, `MALFORMED_RESPONSE`는 여전히
   `provider_error`로 집계)와 의도적으로 분리한다 — 두 메서드의 목적이
   다르다(하나는 "재시도할 가치가 있는가", 하나는 "어느 지표 버킷에
   집계하는가")는 사실을 이름으로도 구분한다. `NETWORK_TIMEOUT`/
   `PROVIDER_4XX`/`PROVIDER_RETRY_EXHAUSTED`는 repair retry 대상이
   아니다 — 이들은 애초에 Anthropic SDK 자체가 이미 내부적으로 최대
   2회 재시도(결정 근거 참고)를 소진한 뒤에야 우리 코드에 도달하는
   실패이므로, 그 위에 application-level 재시도를 또 얹으면 지연만
   커지고 성공 가능성은 거의 늘지 않는다(ADR-0027에서 실측된 "재시도가
   실패를 지연시킬 뿐 해결하지 못한" 선례와 같은 판단). retry는
   결정 2의 동일 `RecommendationInput` snapshot을 재사용하며 DB를
   다시 읽지 않는다 — candidate 집합이 하나의 request(및 그 repair
   retry) 안에서 항상 고정됨을 보장한다(scheduler가 그 사이 새 공고를
   추가해도 이번 request에는 반영되지 않음, 의도된 것).
6. **timeout(90초)은 이번에 바꾸지 않는다. 대신 실패 시 예외 클래스
   simple name만(원문/메시지 없이) 로그에 남겨 향후 진단을 가능하게
   한다.** 문제 정의에서 확인했듯 실측 실패 duration(50~81초)이 90초
   미만이라 timeout 값 자체가 원인이라는 증거가 없다 — 근거 없이
   150/180초로 올리는 것은 ADR-0027의 "확정된 원인 없이 값만 올리면
   재시도가 실패를 지연시킬 뿐"이라는 선례를 반복하는 것이다. 대신
   `AnthropicJobRecommendationClient.classify()`가 분류에 사용한
   예외의 `getClass().getSimpleName()`을 `NETWORK_TIMEOUT`/
   `MALFORMED_RESPONSE`/`PROVIDER_RETRY_EXHAUSTED` 로그 라인에 추가한다
   (jobId/score/duration만 남기던 기존 로그 원칙 그대로, 클래스 이름은
   PKB/공고 원문이 아니므로 privacy 제약과 무관). 이후 실제 E2E에서
   `NETWORK_TIMEOUT`이 재현되면 그 로그로 "진짜 timeout인지, 다른
   `IOException`이 잘못 분류된 것인지"를 실제 근거로 판별할 수 있고,
   그 결과에 따라 timeout 값 조정이나 `classify()` 분류 세분화를 후속
   Task로 결정한다 — 이번 Task 범위에서는 "무엇을 관찰해야 하는가"만
   확정하고 "얼마로 바꿀 것인가"는 결정하지 않는다.
7. **`careerops.recommendation.provider.retry`(Counter, `outcome`=
   `repaired`|`still_failed` 태그)와
   `careerops.recommendation.provider.validation_failure`(Counter,
   `reason`=UNKNOWN_JOB_ID|UNKNOWN_PKB_ID|SCORE_OUT_OF_RANGE|
   MALFORMED_RESPONSE 태그)를 신설한다.** 전자는 repair retry가 실제로
   얼마나 자주 필요한지와 그 성공률을, 후자는 attempt 단위로 어떤
   reason이 가장 빈번한지를 관찰한다 — 기존
   `careerops.recommendation.request{result=...}`는 최종 결과만 보므로
   "1차는 실패했지만 재시도로 복구된" 빈도를 볼 수 없다는 관측
   공백을 메운다. token usage(`StructuredMessage.usage()`) 지표는
   PKB-008.1이 `javap`으로 `stopReason()`/`usage()` 존재를 이미
   확인한 선례가 있으므로 구현 시점에 접근 가능성만 재확인해 가능하면
   추가하고(고정 label만 사용, job/PKB 내용 label 금지), 불가능하면
   이번 Task에서 강제하지 않는다(blocking 기준 아님).

**대안**:

- **timeout을 150초 또는 180초로 선제적으로 상향** — 기각(결정 6).
  실측 실패 duration이 현재 90초보다 짧아 timeout이 원인이라는 근거가
  없다. ADR-0027 선례(근거 없는 timeout 상향은 재시도만 늘리고 실패를
  지연시킬 뿐)를 반복하지 않는다.
- **candidate에 hard cap(예: 1,000건 초과 시 400) 도입** — 기각(결정
  4). 현재 461건 규모에서 근거가 되는 실측 실패가 없고, ADR-0031이
  이미 기각한 "mechanical candidate cap"과 같은 recall 손실 위험을
  다시 끌어들인다.
- **schema에 `maxItems` 등 JSON Schema 제약 추가로 provider output을
  강제** — 기각(결정 3). Anthropic API가 이 키워드들을 구조화 출력에서
  거부한다는 사실을 공식 문서로 확인했다(요청 자체가 400으로 실패).
- **candidate를 chunk(25~50건)로 나눠 여러 번 ranking 후 merge** —
  기각(이번 Task 범위, ADR-0031 결정 재확인). chunk 간 절대 척도 비교
  불가 문제가 여전히 해결되지 않았고, 현재 실패 원인이 output 상한
  부재/장기 트랜잭션으로 이미 설명되므로 더 큰 구조 변경 없이 먼저
  해결을 시도하는 것이 우선이다.
- **validation/malformed 실패뿐 아니라 NETWORK_TIMEOUT/PROVIDER_4XX/
  PROVIDER_RETRY_EXHAUSTED까지 application-level retry 대상으로 포함**
  — 기각(결정 5). 이 실패들은 이미 SDK 자체 재시도(최대 2회)를 소진한
  뒤 도달하므로, 그 위에 추가 재시도를 얹으면 실패 확정까지의 지연만
  늘어난다(최악의 경우 90초×3(SDK)×2(application) = 540초에 근접).
- **`JobRecommendationClient` 인터페이스는 그대로 두고
  `AnthropicJobRecommendationClient` 내부에서만 Entity→DTO 변환** —
  기각(결정 2). 인터페이스가 Entity를 계속 받으면 Fake
  구현체(테스트)를 포함한 모든 구현이 Entity를 계속 다뤄야 해
  트랜잭션 경계가 타입으로 강제되지 않는다 — "실수로 Entity를 다시
  트랜잭션 밖으로 흘려보내는" 회귀를 컴파일 타임에 막을 수 없다.

**이유**: 이번 결정을 관통하는 원칙은 "외부 API 대기 시간 동안 DB
connection/transaction을 점유하지 않는다"(ADR-0032가 NOTIFY-001에
적용한 원칙을 RECOMMEND-001 자신에게도 동일하게 적용) — NOTIFY-001은
이 문제를 "위에 얹지 않는 것"으로 회피했을 뿐 근본 원인은 그대로
RECOMMEND-001에 남아 있었다. 둘째, "관련 있는 공고를 후보 단계에서
놓치지 않는다"(ADR-0026/ADR-0031) — provider output 상한(결정 3)과
candidate cap 미도입(결정 4) 모두 이 원칙을 그대로 유지한 채 실패율만
낮추는 방향을 택했다. 셋째, "근거 없이 값을 바꾸지 않는다"(ADR-0027
선례) — timeout(결정 6)과 candidate cap(결정 4) 모두 실측 증거가
timeout 값 자체를 가리키지 않는 상황에서 추측성 변경을 하지 않고,
대신 다음에 근거를 확보할 수 있는 관측 장치(진단 로그, 신규 metric)를
먼저 놓았다.

**영향**: `JobRecommendationClient` 인터페이스 시그니처 변경은 이
인터페이스를 구현/mock하는 모든 코드(`AnthropicJobRecommendationClient`,
테스트의 Fake 구현체)에 영향을 준다 — 단`POST
/api/jobs/recommendations?limit=N` 공개 API와
`JobRecommendationService.recommend(int)` 시그니처(NOTIFY-001이
의존하는 계약)는 그대로이므로 NOTIFY-001 production 코드는 무변경으로
유지된다. `RecommendationInput`류 record가 이 프로젝트에서 "LLM
클라이언트 인터페이스가 JPA Entity 대신 immutable snapshot DTO를
받는" 첫 사례가 되며, 향후 유사하게 무거운 LLM 호출을 감싸는 서비스
(예: 이후 chunk/batch 재설계)가 이 경계 패턴을 선례로 참고할 수 있다.
`isRepairable()`/`isValidationFailure()`가 서로 다른 reason 집합을
반환하는 것이 코드만 보면 헷갈릴 수 있어, 두 메서드 모두 javadoc으로
목적을 명시해야 한다(구현 시 필수). timeout을 이번에 바꾸지 않기로
했으므로, `NETWORK_TIMEOUT`이 실제 E2E에서 다시 재현되면 후속 Task가
이번에 추가한 진단 로그를 근거로 값을 조정하거나 `classify()` 분류
자체를 세분화해야 한다 — 그 결정을 미리 내리지 않은 것은 의도된
유예다.

---

## ADR-0034: 채용공고 추천 알림 카카오톡 발송(KAKAO-001) — refresh_token만
## 영속화(access_token 미저장), SENDING atomic claim, idempotency
## 부재로 인한 자동 retry 금지, Default Text 템플릿, provider 실패는
## FAILED commit 후 502

- 날짜: 2026-08-25
- 상태: 확정
- 관련 Task: KAKAO-001 (전제: NOTIFY-001/ADR-0032)

**문제**: NOTIFY-001(ADR-0032)은 알림 대상을 `JobRecommendationNotification`
(status=PENDING)으로 저장하는 것까지만 했고, 실제 메시지 전송은 의도적으로
후속 Phase로 미뤄졌다. KAKAO-001은 이 PENDING notification을 사용자 본인의
카카오톡 "나와의 채팅방"(Send-to-me)으로 전송해야 한다. 세 가지 독립적인
어려움이 있다: (1) Kakao Login access_token은 수명이 짧고 refresh_token으로
갱신해야 하는데, 이 프로젝트는 단일 사용자 개인 MVP로 기존 인증/암호화
인프라가 전혀 없다. (2) 동일 notification에 대한 동시 send 요청이 중복
카카오톡 메시지를 발송할 위험이 있다. (3) 설계 조사(2026-08-25,
developers.kakao.com 공식 문서 확인) 결과 Send-to-me API에는 공식
idempotency 메커니즘이 없어, network timeout에 대한 자동 재전송이 중복
발송으로 이어질 수 있다.

**결정**:

1. **app credential은 `.env`, user OAuth token은 `refresh_token`만 DB에
   저장하고 access_token은 저장하지 않는다.** `CAREEROPS_KAKAO_REST_API_KEY`/
   `CAREEROPS_KAKAO_CLIENT_SECRET`은 기존 `.env`/`application.yml
   (careerops.*)` 컨벤션을 그대로 따른다. `kakao_oauth_token`(singleton,
   0~1행)에는 `refresh_token`+`refresh_token_expires_at`(nullable)만
   저장하고, **access_token은 매 전송 요청마다 `grant_type=refresh_token`
   으로 즉시 새로 발급받아 그 자리에서만 쓰고 버린다.** 개인용 저빈도(하루
   몇 건) 발송이므로 access_token 캐싱/만료 추적 로직 자체를 없애는 것이
   순수 캐싱 로직을 추가하는 것보다 단순하고, "401(만료된 access_token)"
   시나리오를 상시 경로에서 사실상 제거한다.
2. **plaintext 저장을 그대로 채택하고, 별도 암호화 계층을 추가하지
   않는다.** 이 프로젝트에 KMS/Vault 등 암호화 인프라가 없고, PKB 개인
   이력 데이터도 이미 같은 DB에 평문으로 저장되는 기존 보안 모델과 동일
   수준이다. 토큰 하나 때문에 새 encryption-at-rest 계층/dependency를
   추가하는 것은 이번 MVP 규모에 대한 과잉설계로 판단했다 — DB 접근 자체가
   신뢰 경계라는 것을 known limitation으로 문서화하고, 로그 미노출로
   위험을 상쇄한다.
3. **최초 OAuth 연결은 OAuth connect endpoint(authorize redirect/callback)
   를 구현하지 않고, 사용자가 Kakao Developers 콘솔에서 수동으로
   refresh_token을 1회 발급받아 `.env`(`CAREEROPS_KAKAO_INITIAL_REFRESH_TOKEN`)
   에 입력하면 앱이 DB로 1회 이관(seed)한다.** 이번 Task 목적은 메시지
   delivery이지 OAuth UI가 아니다. authorize/callback endpoint는 이 저장소에
   전혀 없는 인증 계층(state/CSRF 방어, session 등)을 새로 요구해 스코프가
   크게 늘어난다 — 개인용 backend MVP에서 최초 1회의 수동 콘솔 조작 비용이
   이 복잡도보다 명백히 낮다.
4. **refresh 응답 rotation 규칙을 그대로 반영한다: 응답에 새
   `refresh_token`이 있으면 DB 값을 교체하고, 없으면 기존 값을
   유지한다.** 공식 문서 확인 결과 Kakao는 남은 유효기간이 1개월 미만일
   때만 새 refresh_token을 반환한다 — "항상 교체" 또는 "절대 교체 안 함"
   둘 다 틀린 가정이므로, 응답에 필드가 있는지 여부로만 분기한다.
5. **동시 send 방지를 위해 `NotificationStatus`에 `SENDING`을 추가하고,
   atomic conditional UPDATE로 claim한다** (`WHERE id=:id AND status IN
   ('PENDING','FAILED')`, `affectedRows==1`인 요청만 provider 호출).
   대안(기존 3-state 유지 + `SELECT FOR UPDATE` 같은 DB lock)은 ADR-0032가
   확립한 "외부 API 대기 시간 동안 DB connection/transaction을 점유하지
   않는다"는 원칙과 정면 충돌해 기각했다 — Kakao 호출은 초 단위가 걸릴 수
   있는 외부 I/O이므로 그 시간 동안 row lock을 잡는 것은 이 프로젝트가
   RECOMMEND-001.1(ADR-0033)에서 이미 명시적으로 경계한 실수를 반복하는
   것이다. `FAILED`도 claim 대상에 포함해 전용 `/retry` endpoint 없이
   `/send` 재호출만으로 재시도가 가능하게 했다(결정 8과 연결).
6. **Kakao 메시지/토큰 HTTP 호출은 어떤 DB 트랜잭션도 열려있지 않은
   상태에서 실행하고, claim과 최종 상태 전이(SENDING→SENT/FAILED)는 각각
   독립된 짧은 트랜잭션으로 분리한다.** ADR-0032/ADR-0033이 이미 이
   프로젝트에서 두 번 확립한 "external I/O와 DB transaction 경계 분리"
   원칙을 세 번째로 동일하게 적용한다. 최종 전이도 조건부 UPDATE
   (`WHERE status='SENDING'`)로 수행해 예기치 않은 상태 불일치를 조용히
   무시하지 않는다.
7. **provider 실패로 인한 `FAILED` 전이는 반드시 commit된 뒤에 502
   예외가 컨트롤러로 전파된다.** `@Transactional`로 "상태 갱신 →
   예외 throw"를 하나로 묶으면 예외 전파가 트랜잭션을 롤백시켜 FAILED
   기록 자체가 유실되는 흔한 실수가 생긴다(§AGENTS.md 공통 원칙과 무관한
   구현 디테일이지만 실제 여러 프로젝트에서 반복되는 버그 패턴) — 상태
   전이용 repository 메서드가 각자 독립 커밋되므로 이 실수가 구조적으로
   발생하지 않는다. API 레벨에서 provider/token 실패는 일괄 502로
   통일한다(성공 경로의 200과만 명확히 구분하면 충분하고, 4xx/5xx/timeout을
   세분화한 응답 코드 체계를 클라이언트에 추가로 노출할 실질적 필요가
   없다는 것이 이번 MVP 단계의 판단이다 — 세분화가 필요해지면 `failureCode`
   필드로 이미 DB에 남는다).
8. **자동 network-timeout/5xx retry를 도입하지 않는다.** 공식 문서에서
   idempotency key 등 중복 방지 메커니즘을 확인하지 못했다 — 자동
   재전송은 "전송됐는지 알 수 없는데 또 보내는" 위험을 그대로 안는다.
   이 시스템의 delivery semantics를 **"요청 1회당 최대 1회의 능동적 전송
   시도"(at-most-one-active-attempt)**로 명시적으로 정의하고, exactly-once
   를 주장하지 않는다. timeout은 `failureCode=DELIVERY_UNKNOWN`으로 다른
   실패와 구분해 저장하고, 재시도 여부는 사람(단일 사용자 본인)이 `/send`
   재호출로 직접 판단한다(human-in-the-loop) — 개인용 저빈도 MVP에서는
   자동화보다 정직한 상태 표시를 우선했다.
9. **Kakao Default Text 템플릿만 사용한다(`object_type=text`, `text`≤200자,
   `link.web_url`=`JobPosting.sourceUrl` 필수).** Custom 템플릿은 Kakao
   콘솔에서 사전 등록이 필요해 개인용 MVP에 설정 비용을 추가하고, Feed
   템플릿은 `image_url`을 요구해 이번 목적(텍스트 알림)에 불필요한 이미지
   자산 관리를 강제한다. `link`가 API 필수 파라미터이므로 `sourceUrl`이
   null인 `JobPosting`(스키마상 nullable)은 provider를 호출하지 않고
   `failureCode=INVALID_MESSAGE_DATA`로 즉시 FAILED 처리한다 — 잘못된
   요청으로 Kakao를 호출해 불필요한 4xx를 만들지 않는다.
10. **메시지는 `KakaoRecommendationMessageFormatter`(pure/deterministic)
    가 notification snapshot과 `JobPosting` DB 실제값만으로 구성하고,
    LLM을 사용하지 않는다.** AGENTS.md의 "AI가 사용자가 하지 않은 경험/
    수치를 만들어내지 못하게 막는다"는 원칙을 그대로 승계 — reason은
    RECOMMEND snapshot을 그대로 쓰고 재요약하지 않는다. 총 200자 예산
    초과 시 `String.length()`(Java char) 기준 truncate만 적용한다(순수
    텍스트 템플릿이라 이모지/서로게이트 페어가 없어 UTF-8 byte 기준과의
    불일치 문제가 애초에 발생하지 않는다).

**대안**:
- **access_token도 DB에 캐싱하고 401 시에만 refresh** — 기각(결정 1).
  캐싱/만료 추적 로직이 추가되는 대신 얻는 이득(요청당 refresh 1회 절약)
  이 개인용 저빈도 사용 패턴에서는 작다. 단순함을 우선했다.
- **refresh_token까지 `.env`에 저장(자동 재작성)** — 기각(결정 1과 연결).
  rotation 시 앱이 `.env` 파일을 스스로 재작성해야 하는데, 이는
  "`.env`는 앱이 쓰는 대상이 아니다"라는 기존 컨벤션(`application.yml`
  주석)과 충돌하고 컨테이너 배포 환경에서 더 취약하다.
- **암호화 저장(KMS/Vault 등)** — 기각(결정 2). 이번 MVP 규모에 필요한
  근거가 없는 새 인프라/dependency 도입.
- **OAuth connect endpoint 구현** — 기각(결정 3). 메시지 delivery라는
  이번 Task 목적 대비 과한 스코프(state/CSRF/session 등 인증 계층 신규
  구현).
- **기존 3-state + `SELECT FOR UPDATE`** — 기각(결정 5). 외부 API 대기
  시간 동안 DB lock을 점유하는 구조적 결함.
- **자동 network-timeout retry (idempotency 없이)** — 기각(결정 8). 중복
  발송 위험을 감수할 근거가 없다.
- **Custom Kakao 템플릿(template_id 사전 등록)** — 기각(결정 9). 개인용
  MVP에 불필요한 외부 설정 의존성 추가.
- **provider 실패를 4xx/5xx/timeout별로 세분화한 HTTP 응답 코드** —
  기각(결정 7). 클라이언트가 이 구분을 실제로 소비할 필요가 아직 없고,
  필요해지면 이미 DB에 남는 `failureCode`로 확장 가능하다.

**이유**: 이 결정 전체를 관통하는 원칙은 세 가지다. 첫째, "외부 API 대기
시간 동안 DB connection/transaction을 점유하지 않는다"(ADR-0032/ADR-0033
에서 이미 두 번 확립, 결정 5/6에서 세 번째로 동일하게 적용). 둘째, "근거
없는 보장을 하지 않는다" — idempotency 메커니즘이 실제로 없다는 사실을
확인한 뒤에는 exactly-once나 안전한 자동 retry를 흉내 내지 않고, 그
한계를 `DELIVERY_UNKNOWN`이라는 명시적 상태로 정직하게 드러냈다(결정 8).
셋째, "이번 MVP 규모에 근거 없는 인프라를 미리 추가하지 않는다"(YAGNI,
ADR-0026/ADR-0031/ADR-0032가 일관되게 적용해온 원칙) — 암호화 계층(결정
2), OAuth connect endpoint(결정 3), custom 템플릿(결정 9), 세분화된 에러
응답 코드(결정 7) 모두 지금 근거가 없는 복잡도로 판단해 기각했다.

**영향**: `NotificationStatus`에 `SENDING`이 추가되면서 이 enum을
소비하는 모든 코드(검색 API의 `status` 필터 등)가 4-state를 인지해야
한다 — 기존 NOTIFY-001 API 계약(`GET .../job-recommendations?status=`)
자체는 변경되지 않지만, 실제 값 집합이 늘어난다. access_token을 저장하지
않기로 한 결정은 향후 AUTOMATION-001이 주기적으로 여러 건을 발송하게
되면 매 건마다 refresh 호출이 발생한다는 뜻이다 — 이번 조사에서는 이
호출 자체에 별도 quota 제약이 확인되지 않았으므로 문제가 아니라고
판단했지만, AUTOMATION-001에서 발송 빈도가 크게 늘어나면 재검토
대상이다. `failureCode`를 자유 문자열 컬럼으로 열어둔 것은 향후
KAKAO-002/AUTOMATION-001이 새로운 실패 유형을 추가할 때 migration 없이
확장 가능하게 하려는 의도다. SENDING crash에 대한 자동 복구를 만들지
않기로 한 것(결정 5의 연장)은 known limitation으로 남으며, 후속
DELIVERY-RETRY/AUTOMATION Task가 lease/sweeper 도입 여부를 다시
판단해야 한다.

---

## ADR-0035: 추천→알림 준비→Kakao 발송 자동화(AUTOMATION-001) — prepare/
## delivery 단계별 독립 flag(기본 false), cron 기반 매일 아침 스케줄,
## 단일 진입점 전제 overlap guard 생략, PENDING 전용 backlog,
## TOKEN_REFRESH_FAILED short-circuit

- 날짜: 2026-08-25
- 상태: 확정
- 관련 Task: AUTOMATION-001 (전제: NOTIFY-001/ADR-0032, KAKAO-001/ADR-0034)

**문제**: RECOMMEND-001/NOTIFY-001/KAKAO-001은 전부 수동 HTTP API로만
트리거된다. `docs/PROJECT.md`의 제품 목표("신규/추천/마감임박 공고를
매일 아침 카카오톡으로 전달한다")를 실제로 충족하려면 이 세 단계를
주기적으로 자동 실행해야 한다. 다만 이 프로젝트는 실제 유료 Anthropic
API 호출을 전면 금지하는 정책 하에 있고, KAKAO-001의 실제 Kakao E2E도
아직 사용자 승인을 받지 않은 상태다 — 따라서 자동화 스케줄러가 기본
상태에서 실수로라도 실제 외부 API를 호출하는 일이 없어야 한다는 것이
이번 설계의 최우선 제약이다.

**결정**:

1. **"각 기능을 다시 구현"하지 않고 기존 Service를 그대로 호출한다.**
   `NotificationPreparationService.prepare(int limit)`(내부에서 이미
   `JobRecommendationService.recommend(20)`을 재사용)와
   `NotificationSendService.send(long id)`를 신규 orchestration 계층이
   호출할 뿐, 두 Service의 production 코드는 전혀 수정하지 않는다.
   RECOMMEND-001을 AUTOMATION에서 별도로 다시 호출하지 않는다(중복 LLM
   호출 방지).
2. **prepare와 delivery를 완전히 독립된 두 pair(Scheduler+Service)로
   분리하고, 각각 독립된 feature flag(`careerops.automation.prepare.enabled`
   /`careerops.automation.delivery.enabled`, 둘 다 기본 false)로
   통제한다.** 단일 `automation.enabled` 하나로 묶는 대안도 검토했으나,
   "prepare는 자동 실행하되 delivery는 사람이 수동으로 `/send`를
   호출해 검토 후 보낸다"는 조합을 사용자가 명시적으로 요구했다 — 단일
   flag로는 이 조합을 표현할 수 없다. 두 flag 모두 `@ConditionalOnProperty
   (..., havingValue="true")`(matchIfMissing 미지정 → 기본 false)로 걸어,
   flag가 false인 동안은 해당 `@Scheduled` 메서드를 가진 Scheduler Bean
   자체가 Spring 컨텍스트에 생성되지 않는다 — Anthropic/Kakao credential이
   `.env`에 비어 있어도(현재 실제 상태) 이 사실과 무관하게 자동 호출
   경로 자체가 존재하지 않음을 보장하는 것이 핵심 안전장치다(코드로
   확인: `AnthropicJobRecommendationClient`/`KakaoTokenStore` 둘 다
   생성자가 아니라 실제 호출 메서드 안에서만 credential을 lazy하게
   검증하므로, Bean 생성 자체는 credential 유무와 무관하게 항상
   성공한다 — 따라서 "Bean이 아예 없다"만이 확실한 차단선이다).
3. **cron 기반 스케줄(Asia/Seoul, prepare 07:50 → delivery 08:00)을
   채택하고, `fixedDelay`는 채택하지 않는다.** `fixedDelay`는 "얼마나
   자주"만 보장하고 "언제"는 기동 시각에 종속되는데, 제품 목표가
   명시적으로 "매일 아침"이므로 특정 시각에 정렬되는 cron이 이 목표와
   정확히 일치한다. delivery를 prepare보다 10분 뒤로 둔 것은 그날의
   prepare 결과가 delivery 대상에 포함될 시간을 확보하기 위함이지만,
   두 스케줄러가 서로를 기다리도록 결합하지는 않는다 — delivery는
   자기 실행 시점의 PENDING backlog 전체를 대상으로 하므로 prepare가
   그날 아직 안 끝났거나 실패했어도 기존 backlog만으로 독립적으로
   정상 동작한다(결정 5와 연결).
4. **Overlap guard(JVM lock/DB advisory lock/Redis lock)를 만들지
   않는다.** COLLECT-006이 `ReentrantLock`을 추가한 이유는 "수동 API +
   scheduler"라는 **두 개의 서로 다른 진입점**이 동시에 같은 자원을
   건드릴 수 있었기 때문이다(코드로 확인). 이번 설계는 수동 실행
   API를 만들지 않기로 했으므로(결정 8) 각 stage의 진입점이 정확히
   그 stage 전용 `@Scheduled` 메서드 하나뿐이다 — Spring의 cron 트리거
   기본 동작(이전 실행이 끝나야 다음 트리거가 실질적으로 의미를 갖는
   일반적 케이스)만으로 충분하며, 이 프로젝트에 Redis/분산 락 실사용
   선례가 전혀 없다는 사실(`docker-compose`에만 떠 있는 미사용
   인프라, 코드 grep으로 확인)도 새 lock infra를 도입하지 않는 방향을
   뒷받침한다. **이 결정은 결정 8(수동 API 없음)에 종속적이다** — 이후
   수동 실행 API가 추가되면 그 시점에 COLLECT-006과 동일한 lock
   패턴을 반드시 재도입해야 한다.
5. **Delivery 대상은 "이번 run에서 새로 생성된 것"이 아니라, PENDING
   전체를 생성 시각 오래된 순으로 최대 `deliveryLimit`개 선택한다
   (backlog 방식).** "신규 생성분만"은 이전 run에서 실패/중단 등으로
   남은 PENDING이 영원히 전송되지 않는 구조적 결함이 있다 —
   ADR-0026/0031/0032가 일관되게 지켜온 "관련 있는 것을 후보 단계에서
   놓치지 않는다" 원칙을 여기도 동일하게 적용했다. 단, 기존
   `JobRecommendationNotificationRepository.search()`는 정렬이
   `ORDER BY n.createdAt DESC, n.id DESC`로 하드코딩돼 있어(코드로
   확인) 그대로 재사용할 수 없어, 신규 조회 메서드
   (`findIdsByStatusOrderByCreatedAtAsc`, `status`는 항상 PENDING)를
   추가한다. FAILED는 이 조회에 포함하지 않는다 — AUTOMATION은
   PENDING만 자동 재시도 대상으로 삼고, 사람이 `/send`를 수동으로
   다시 호출해 FAILED를 재시도하는 기존 경로(ADR-0034의 atomic claim
   쿼리가 이미 허용)는 그대로 유지된다. Migration은 필요 없다(기존
   컬럼/index로 충분, 이 규모에서 신규 index 근거 없음).
6. **Prepare 실패(409/502)는 로그/metric만 남기고 예외를 삼켜, delivery
   stage 실행에 영향을 주지 않는다.** 두 stage가 완전히 독립이므로
   이는 자연스럽게 보장된다 — prepare가 실패해도 이전 run들이 쌓아둔
   PENDING backlog는 delivery stage가 자기 스케줄에 따라 계속 발송을
   시도한다.
7. **Delivery 부분 실패는 best-effort로 나머지 항목을 계속 시도하되,
   `TOKEN_REFRESH_FAILED`만 예외적으로 그 run의 남은 시도를
   short-circuit한다.** `PROVIDER_ERROR`/`PROVIDER_5XX`/`DELIVERY_UNKNOWN`
   은 개별 메시지/일시적 성격이라 한 항목의 실패가 다른 알림 발송을
   막을 이유가 없다. 그러나 `TOKEN_REFRESH_FAILED`는 같은 DB
   row/`.env` 설정을 다시 읽을 뿐이므로 같은 run의 나머지 모든 항목도
   결정론적으로 동일하게 실패할 것이 사실상 확실하다 — 이미 실패가
   확정된 시도를 반복해 불필요한 실제 Kakao 호출을 여러 번 더
   일으키는 것을 막기 위해 이 경우만 명시적으로 중단한다(`if (reason
   == TOKEN_REFRESH_FAILED) break;` 수준의 단순 분기, 별도 circuit
   breaker 인프라 아님).
8. **수동 실행 API(`POST /api/automation/...`)를 만들지 않는다.** 기존
   `POST /api/notifications/job-recommendations`와 `POST
   .../{id}/send`가 이미 더 세밀한 수동 제어 수단을 제공하므로 얇은
   wrapper endpoint는 추가 가치가 없고, 검증은 Service를 테스트에서
   직접 호출하는 것으로 충분하다. 이 결정이 결정 4(overlap guard
   생략)를 성립시키는 전제이기도 하다.
9. **`AutomationRun` 같은 실행 이력 entity/migration을 만들지 않는다.**
   `AlioCollectionScheduler`/`JobRecommendationService`/
   `NotificationPreparationService` 모두 실행 결과를 별도 테이블이
   아니라 Micrometer metric + 구조화 로그 한 줄로만 남기는 것이 이
   프로젝트의 일관된 패턴이다 — 같은 패턴이면 "이 run이 무엇을
   했는지" 재구성에 충분하다.

**대안**:
- **단일 `automation.enabled` flag** — 기각(결정 2). "prepare 자동/
  delivery 수동" 조합을 표현할 수 없다.
- **`AutomationService.runOnce()` 하나가 prepare→delivery를 순서대로
  처리하는 단일 orchestrator** — 기각(결정 2와 연결). 단계별 flag를
  채택하면 두 stage가 서로 다른 시각에 서로 다른 on/off 상태로
  동작해야 하므로, 하나의 공유 orchestrator보다 두 개의 독립된
  컴포넌트가 실제 요구를 더 정확히 반영한다.
- **`fixedDelay` 기반 스케줄** — 기각(결정 3). "매일 아침"이라는 제품
  목표와 어긋난다.
- **JVM `AtomicBoolean`/DB advisory lock/Redis distributed lock** —
  기각(결정 4). 진입점이 stage당 하나뿐인 상황에서는 해결할 문제가
  없는 인프라다.
- **이번 run에서 새로 생성된 notification만 delivery** — 기각(결정
  5). 백로그가 쌓이면 영원히 전송되지 않는 공고가 생긴다.
- **delivery 실패를 전부 fail-fast로 중단** — 기각(결정 7). 개별
  메시지 실패가 다른 알림 발송을 막을 이유가 없다.
- **`TOKEN_REFRESH_FAILED`도 예외 없이 best-effort로 반복 시도** —
  기각(결정 7). credential 오류 상황에서 매 항목마다 불필요한 실제
  Kakao 호출이 반복된다.

**이유**: 이 결정 전체를 관통하는 원칙은 세 가지다. 첫째, "외부 API 대기
시간 동안 DB connection/transaction을 점유하지 않는다"(ADR-0032/0033/0034가
이미 세 번 확립, orchestration 계층에도 네 번째로 동일하게 적용 —
`AutomationPrepareService`/`AutomationDeliveryService` 어디에도
`@Transactional`을 두지 않는다). 둘째, "실제로 존재하는 문제만 해결한다"
— overlap guard는 "수동 API + scheduler"라는 실제 코드 구조(COLLECT-006)
에서만 필요했던 것이지, 진입점이 하나뿐인 이번 설계에는 해당하지 않는
문제를 관성적으로 복제하지 않았다(코드를 실제로 읽어 COLLECT-006의 lock이
왜 필요했는지 확인한 뒤에야 내릴 수 있었던 판단). 셋째, "관련 있는 것을
후보 단계에서 놓치지 않는다"(ADR-0026/0031/0032) — PENDING backlog 방식
(결정 5)과 best-effort delivery(결정 7)가 이 원칙을 유지한다.

**영향**: `JobRecommendationNotificationRepository`에 신규 조회 메서드가
추가되지만 기존 `search()`/`claimForSending()`/엔티티/컬럼은 전혀 바뀌지
않는다 — NOTIFY-001/KAKAO-001의 API 계약과 트랜잭션 경계는 이번 Task로
전혀 영향받지 않는다. 향후 수동 실행 API가 추가되면(예: 운영 편의를 위해)
그 시점에 반드시 COLLECT-006과 동일한 lock 패턴을 재도입해야 한다는
조건부 의존성이 남는다 — 이 조건을 놓치면 결정 4가 더 이상 안전하지
않다. `TOKEN_REFRESH_FAILED` short-circuit은 이 reason이 정말로
"systemic"하다는 전제(같은 프로세스 안에서 같은 refresh_token/설정을
다시 읽는다는 것)에 의존한다 — 향후 `KakaoTokenStore`가 per-notification
으로 다른 credential을 쓰게 되는 변화가 생기면 이 가정을 재검토해야
한다.
