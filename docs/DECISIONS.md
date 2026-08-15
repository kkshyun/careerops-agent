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
