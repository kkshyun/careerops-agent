---
task_id: CORE-001
title: 백엔드 실행 기반(Skeleton) 구축 — Spring Boot + PostgreSQL + Redis
phase: done
planned_by: claude
implemented_by: codex
status: passed
created_at: 2026-08-13T19:30:00+09:00
codex_thread_id: 019ffa88-73ac-7801-b3d9-e1049b27f239
---

## Context

`docs/ROADMAP.md`의 Phase 1 후보 1번 "프로젝트 뼈대(Skeleton) 구축"에 해당한다.
지금까지(Phase 0)는 Claude/Codex 협업 구조(AGENTS.md, docs/, `.ai/`, subagent,
skill)만 만들어졌고 애플리케이션 코드는 전혀 없다. 이후 모든 도메인 기능
(PKB, 채용공고 수집, Fit Scoring, 자기소개서 파이프라인 등)이 이 위에서
동작하므로, 도메인 로직 없이 "로컬에서 뜨고, DB/Redis에 붙고, 상태를 확인할
수 있는" 최소 실행 기반만 먼저 만든다.

이 Task는 CareerOps의 제품 기능을 전혀 구현하지 않는다. 순수 인프라/부트스트랩
Task다.

## Scope

1. Java 21 + Spring Boot 4.1.x(ADR-0004) 기반 Gradle(ADR-0005) 프로젝트를
   `backend/` 디렉토리에 생성한다.
2. PostgreSQL과 Redis를 Docker Compose로 로컬에 띄울 수 있게 한다
   (`docker-compose.yml`).
3. Spring Boot 애플리케이션이 기동 시 PostgreSQL(JPA/DataSource)과
   Redis(RedisConnectionFactory)에 정상 연결되도록 최소 설정을 구성한다.
4. Actuator `/actuator/health`, `/actuator/prometheus` endpoint를 노출한다.
5. Secret(DB/Redis 접속 정보 등)을 `.env.example` + 환경변수 방식으로
   관리하고, `application.yml`에는 하드코딩하지 않는다.
6. 최소 테스트(ApplicationContext 로딩 테스트) 1개 이상을 포함한다.
7. 처음 clone하는 개발자가 로컬에서 실행할 수 있도록 README에 절차를 기록한다.

## Out of Scope

아래는 이번 Task에서 명시적으로 하지 않는다 (다른 Phase/Task에서 다룬다):

- 채용공고 수집
- User/Profile, Experience, Personal Knowledge Base(PKB)
- Vector Search
- Claude API 연동
- Codex 제품 기능 연동 (이 Task는 Claude/Codex 개발팀 협업용 인프라가 아니라
  CareerOps 제품 자체의 실행 기반을 만드는 것이며, 그 반대로 제품이 Codex를
  호출하는 기능도 포함하지 않는다)
- Kakao API 연동
- 자기소개서 생성 기능
- 인증/인가 (Spring Security 등 추가하지 않음)
- Frontend(Next.js) — 이번 Task는 Backend만 다룬다
- Grafana Dashboard — Prometheus 서버 자체도 아직 세우지 않는다. Micrometer가
  Actuator를 통해 `/actuator/prometheus`로 metric을 "노출"하는 것까지만 한다.
  Prometheus가 그것을 실제로 scrape하는 것은 이후 Task.
- 실제 제품용 Agent 로직
- 도메인 패키지(`domain/`, `service/`, `repository/`, `controller/` 등 도메인
  기능을 위한 패키지) — 만들지 않는다
- DB 스키마/엔티티 — `@Entity` 클래스를 하나도 만들지 않는다
- 불필요한 추상화: `BaseEntity`, 공통 예외 처리(`@ControllerAdvice` 등),
  복잡한 레이어드 아키텍처, 커스텀 공통 응답 wrapper 등

## Acceptance Criteria

검증 방식을 `[자동]`(스크립트/명령으로 기계적으로 확인 가능) /
`[수동]`(사람이 직접 확인해야 함)으로 표시한다.

- [ ] `[자동]` **Java 21 빌드**: `backend/` 디렉토리에서 `./gradlew build`
      실행 시, Java 21 toolchain으로 빌드가 성공한다(BUILD SUCCESSFUL).
- [ ] `[자동]` **전체 테스트 통과**: `./gradlew test` 실행 시 실패한 테스트가
      0건이다. (사전조건: 아래 Docker Compose 서비스가 로컬에서 기동 중이어야
      한다 — 이 Task 범위에서는 Testcontainers 등 테스트 전용 격리 인프라를
      추가하지 않으므로, 이 사전조건을 README와 Test Plan에 명시한다.)
- [ ] `[수동]` **PostgreSQL 컨테이너 정상 실행**: 저장소 루트에서
      `docker compose up -d` 실행 후 `docker compose ps`에서 postgres
      서비스 상태가 `Up`(healthcheck 설정 시 `healthy`)이다.
- [ ] `[수동]` **Redis 컨테이너 정상 실행**: 동일하게 `docker compose ps`에서
      redis 서비스 상태가 `Up`(`healthy`)이다.
- [ ] `[수동]` **Spring Boot가 두 서비스에 연결된 상태로 실행**: PostgreSQL/
      Redis 컨테이너가 뜬 상태에서 `./gradlew bootRun`(또는 빌드된 jar 실행)
      시, 기동 로그에 DataSource/Redis 연결 실패·에러가 없고 프로세스가
      종료되지 않고 대기 상태를 유지한다.
- [ ] `[자동]` **`/actuator/health` 정상 응답**: 애플리케이션 기동 중
      `curl -s http://localhost:8080/actuator/health`의 응답 JSON에서
      `.status == "UP"`이고, `.components.db.status == "UP"`,
      `.components.redis.status == "UP"`이다 (Actuator의 DataSource/Redis
      HealthIndicator 자동 구성을 그대로 사용 — 커스텀 헬스체크 코드 작성 금지).
- [ ] `[자동]` **`/actuator/prometheus` metric 조회 가능**:
      `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/prometheus`
      가 `200`을 반환하고, 응답 본문에 Prometheus text exposition 포맷
      (`# HELP`, `# TYPE`로 시작하는 라인)이 포함되어 있다.
- [ ] `[자동]` **Git tracked file에 secret 없음**: `git ls-files`로 나오는
      파일 중 `.env`(및 `.env.<name>`, `application-local.yml`,
      `application-secrets.yml` 등 `.gitignore`에 등록된 패턴)가 하나도
      없다(`git check-ignore .env`가 성공해야 함/실제 `.env`는 애초에 tracked
      되지 않아야 함). `backend/src/main/resources/application.yml`에는
      실제 비밀번호/토큰 값이 아니라 `${ENV_VAR}` 형태의 placeholder만
      존재한다(하드코딩된 자격증명 문자열 없음).
- [ ] `[수동]` **README만으로 로컬 실행 가능**: 이 Task를 모르는 개발자가
      저장소를 처음 clone한 뒤 README에 적힌 순서(사전 요구사항 확인 →
      `.env` 준비 → `docker compose up -d` → 애플리케이션 실행 →
      `/actuator/health` 확인)를 그대로 따라 했을 때, 추가 질문 없이
      애플리케이션을 로컬에서 띄울 수 있다.

## Technical Notes

### 결정된 버전/도구 (근거는 `docs/DECISIONS.md` ADR-0004, ADR-0005)

- Java 21
- Spring Boot **4.1.x**(2026-08 기준 최신 GA: 4.1.0, Spring Framework 7 /
  Jakarta EE 11 기반). 3.x 라인은 2026-06-30부로 OSS 지원 종료(EOL)이므로
  절대 3.x로 새로 시작하지 않는다. 구현 시점에 4.1 라인의 더 최신 patch가
  있으면 그것을 사용한다.
- Build Tool: **Gradle**(Groovy DSL, Gradle Wrapper 커밋). Kotlin DSL은
  쓰지 않는다 — 백엔드가 Java이므로 build 스크립트에 별도 언어를 추가하지
  않기 위함.
- PostgreSQL: Docker 이미지 `postgres:18-alpine` (18.x 계열 최신, 메이저
  버전만 pin하고 patch는 이미지 재빌드 시 자동 갱신되도록 함).
- Redis: Docker 이미지 `redis:8-alpine` (8.x 계열).
- Spring Boot 4.x는 Jakarta EE 11 / Jackson 3 기반으로 3.x와 세부 설정이
  다를 수 있다. 구현 시 3.x 시절 예제·튜토리얼을 그대로 베끼지 말고, 반드시
  Spring Boot 4.1.x 공식 문서 기준으로 Gradle 플러그인 조합(예:
  `org.springframework.boot` 4.1.0 plugin, dependency management 방식)을
  확인한다.

### 만들 파일/디렉토리 구조 (이 범위를 벗어나지 않을 것)

```
careerops-agent/                       (저장소 루트)
├── docker-compose.yml                 # PostgreSQL + Redis만 정의
├── .env.example                       # 커밋 대상. 실제 .env는 .gitignore에 이미 등록됨
├── README.md                          # 신규 생성 — 로컬 실행 절차
└── backend/
    ├── build.gradle
    ├── settings.gradle
    ├── gradlew, gradlew.bat, gradle/wrapper/...
    └── src/
        ├── main/
        │   ├── java/com/careerops/backend/
        │   │   └── BackendApplication.java   # @SpringBootApplication 하나만
        │   └── resources/
        │       └── application.yml           # 환경변수 참조만, 값 하드코딩 금지
        └── test/
            └── java/com/careerops/backend/
                └── BackendApplicationTests.java  # contextLoads() 수준
```

- `com.careerops.backend` 패키지 아래에 `domain/`, `service/`,
  `repository/`, `controller/` 등 하위 패키지를 미리 만들지 않는다. 지금
  필요한 클래스는 애플리케이션 진입점(`BackendApplication`) 하나뿐이다.
- `docker-compose.yml`은 `postgres`, `redis` 두 서비스만 정의한다. 포트는
  각각 표준 포트(5432, 6379)를 로컬에 그대로 매핑한다. PostgreSQL은 데이터
  영속을 위해 named volume 하나를 둔다. Redis는 이 단계에서 캐시 용도이므로
  영속 볼륨을 강제하지 않아도 된다(필요 없으면 생략).
- 서비스 healthcheck(`pg_isready`, `redis-cli ping` 등)를 Compose에 정의해
  `docker compose ps`로 상태를 바로 확인할 수 있게 한다.

### 최소 의존성 (Gradle)

다음 이외의 production dependency를 새로 추가하지 않는다. 추가가 필요하다고
판단되면 이유를 Task 진행 중 기록하고, 이 Task 범위를 벗어난다고 판단되면
별도 Task로 미룬다.

- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.postgresql:postgresql` (runtime)
- `io.micrometer:micrometer-registry-prometheus`
- `org.springframework.boot:spring-boot-starter-test` (test scope)

Lombok, MapStruct 등 편의 라이브러리는 이번 Task에서 추가하지 않는다(도메인
코드가 없으므로 필요 자체가 없음).

### Actuator / Micrometer 설정

- `management.endpoints.web.exposure.include=health,prometheus`로
  두 endpoint만 명시적으로 노출한다(`*`로 전체 노출 금지).
- `management.endpoint.health.show-details=always`로 `db`, `redis` 컴포넌트
  상태가 응답에 보이게 한다. 이 값은 로컬 개발 편의를 위한 것이며, 인증 없이
  운영에 노출하는 문제는 이후 인증/인가 Task에서 다룬다(지금은 out of scope).
- `spring-boot-starter-actuator` + `spring-data-jpa`/`spring-data-redis`가
  클래스패스에 있으면 Spring Boot가 DataSource/Redis용 HealthIndicator를
  자동 구성한다. **커스텀 HealthIndicator 코드를 작성하지 않는다** — 자동
  구성을 그대로 사용하는 것이 이 Task의 의도다.
- `@Entity`가 하나도 없으므로 `spring.jpa.hibernate.ddl-auto=none`으로
  설정해, Hibernate가 존재하지 않는 스키마를 만들려고 시도하지 않게 한다.

### Secret 관리

- `.env.example`에 최소한 다음 키를 정의한다(값은 로컬 기본값/placeholder만,
  실제 비밀 값 금지): `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`,
  `DB_URL` 또는 `SPRING_DATASOURCE_URL`, `REDIS_HOST`, `REDIS_PORT`.
- `application.yml`은 `${POSTGRES_PASSWORD}`처럼 환경변수만 참조한다.
  기본값(`:default`)은 비밀이 아닌 값(호스트명, 포트 등)에만 허용하고,
  비밀번호류는 기본값을 두지 않아 값이 없으면 기동이 실패하도록 한다.
- `.env`는 `.gitignore`에 이미 등록되어 있다(`.env`, `.env.*`,
  `!.env.example`). 새로 만드는 어떤 파일도 이 규칙을 우회하지 않는다.
- `docker-compose.yml`은 `.env` 파일(Compose가 같은 디렉토리에서 자동으로
  읽는 파일)에 정의된 값을 사용하고, Compose 파일 자체에 비밀 값을
  하드코딩하지 않는다.

### 선제적 확장 금지

"나중에 필요할 것 같다"는 이유로 지금 만들지 않는다. 예: 공통 예외 처리,
공통 API 응답 wrapper, `BaseEntity`/`BaseRepository`, 멀티모듈 Gradle 구조,
프로파일 3종 이상 분리(local/dev/prod 등 — 지금은 local 실행만 되면 됨),
Testcontainers, CI 파이프라인(GitHub Actions 등). 이런 것들이 실제로
필요해지는 시점의 Task에서 별도로 설계한다.

### 지표(Metrics) 기록

`docs/METRICS.md` 스키마에 따라 `.ai/metrics/metrics.jsonl`에 `task_id:
"CORE-001"`로 plan/implement/review/verify 각 단계 완료 시 한 줄씩
append한다(`codex-implement` Skill 절차를 따른다). 이 Task 자체는 제품
지표(METRICS.md의 "제품 지표" 목록)와는 무관하다 — 아직 계측할 도메인
기능이 없다.

## Test Plan

- `[자동]` `cd backend && ./gradlew build` — Java 21 toolchain으로 컴파일
  성공.
- `[자동]` `docker compose up -d` (저장소 루트) 로 두 서비스 기동 후
  `cd backend && ./gradlew test` — `BackendApplicationTests#contextLoads`
  포함 전체 테스트 통과. 이 테스트가 통과한다는 것 자체가 ApplicationContext
  기동 시점에 DataSource/Redis 연결이 성립함을 의미한다(연결 실패 시
  context 기동 자체가 실패하므로).
- `[수동]` `curl -s http://localhost:8080/actuator/health | jq` — `status`,
  `components.db`, `components.redis` 확인.
- `[수동]` `curl -s http://localhost:8080/actuator/prometheus | head` —
  Prometheus 포맷 출력 확인.
- `[자동]` `git ls-files | grep -E '^\.env$|\.env\.[^e]|application-local\.yml|application-secrets\.yml'`
  — 아무것도 출력되지 않아야 한다(단, `.env.example`은 예외).
- `[수동]` 새 터미널/디렉토리에서 README만 보고 처음부터 재현.

## Codex Thread 기록

| round | 요청 요약 | 결과 요약 |
|---|---|---|
| 1 | CORE-001 명세 기반 backend 스켈레톤 구현: Gradle 프로젝트, docker-compose(postgres/redis), Actuator health/prometheus, .env.example, README. 빌드/테스트/컨테이너 기동까지 직접 실행 후 보고 지시 | Java 21 + Spring Boot 4.1.0. `./gradlew build`/`test` 성공(1/1 통과). Postgres 18 / Redis 8 healthy 확인. `/actuator/health`(db,redis UP), `/actuator/prometheus`(200) 직접 curl 확인. 명세된 의존성 7개만 사용 + BOM 관리용 `io.spring.dependency-management` 플러그인 추가(정당성 reviewer가 확인). Secret 미커밋 확인. 검증 후 컨테이너/앱 정리. → reviewer 1차 리뷰 PASS (`.ai/reviews/CORE-001-review-1.md`) |
