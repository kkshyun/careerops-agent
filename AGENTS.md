# AGENTS.md — CareerOps Agent 공통 규칙

이 문서는 이 저장소에서 작업하는 모든 AI(Claude Code, Codex)와 사람이 공유하는
최소한의 규칙이다. 도구별 상세 절차는 별도 문서/Skill로 분리되어 있으니
이 파일을 계속 늘리지 말 것.

## 프로젝트 한 줄 요약

CareerOps Agent: 대기업/공기업/금융권 IT·전산·AX 신입 채용공고를 자동 수집하고,
지원자의 이력(학력/자격증/프로젝트/경험)에 맞는 적합도를 판단해 카카오톡으로
알림을 보내며, 근거 기반 검증을 거쳐 자기소개서 작성을 돕는 개인 프로젝트.
제품 목표 상세는 [docs/PROJECT.md](docs/PROJECT.md), 기술 스택/구조는
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 참고.

## 역할 분리

- **Claude Code = Tech Lead / Planner / Reviewer.** 요구사항 분석, 기술 조사,
  아키텍처 설계, 작업 분해, Acceptance Criteria 작성, Codex 결과물 리뷰,
  설계 의사결정 기록을 담당한다. 상세는 [CLAUDE.md](CLAUDE.md) 참고.
- **Codex = Developer.** Claude가 작성한 Task 명세(`.ai/tasks/`)를 기반으로
  실제 애플리케이션 코드와 테스트 코드를 구현하고, 로컬 테스트를 실행한 뒤
  변경 내용을 반환한다.
- 애플리케이션 코드 구현은 원칙적으로 Codex가 담당한다. Claude가 직접
  프로덕션 코드를 작성하는 것은 Codex를 쓸 수 없는 예외 상황에서만, 그리고
  그 사실을 명시적으로 알린 뒤에만 허용된다.
- Codex MCP 연결이 끊긴 경우, 임의로 다른 방식으로 대체하지 말고 먼저
  연결 상태와 문제를 사용자에게 보고한다.

## Task 진행 순서

모든 작업은 Task ID(`CO-XXXX`)를 발급받고 다음 순서를 따른다:

**계획 → 구현 → 리뷰 → 검증**

- 계획: Task 명세를 `.ai/tasks/CO-XXXX-<slug>.md`에 작성 (Acceptance Criteria 포함)
- 구현: Codex가 명세 기반으로 구현 (같은 대화 thread를 재사용)
- 리뷰: Claude가 Acceptance Criteria 대비 결과를 검토, 미충족 시 같은 Codex
  thread에 수정 요청
- 검증: 가능한 범위에서 테스트 실행 결과 확인 후 종료

절차의 세부 실행 방법은 `codex-implement` Skill과 `.ai/tasks/_TEMPLATE.md`,
`.ai/reviews/_TEMPLATE.md`에 있다.

## 공통 개발 원칙

- 최신 기술이라고 무조건 쓰지 않는다. 기술을 추가할 때는 해결하려는 문제를
  먼저 명확히 한다.
- 과도한 추상화와 불필요한 패턴을 피한다. MVP에서는 단순한 구조를 우선한다.
- 모든 중요한 아키텍처 결정에는 이유와 대안을 [docs/DECISIONS.md](docs/DECISIONS.md)에
  기록한다.
- **Secret/API Key는 절대 Git에 commit하지 않는다.** `.env`, 인증서, 토큰 등은
  `.gitignore`에 등록하고 예시 파일(`*.example`)로만 형태를 남긴다.
- 새로운 production dependency를 추가할 때는 이유를 기록한다 (Task 명세 또는
  DECISIONS.md).
- 구현 후 반드시 가능한 범위에서 테스트한다. 테스트를 못 돌린 경우 그 사실과
  이유를 명시적으로 보고한다.
- AI가 사용자가 하지 않은 경험이나 수치를 생성하지 못하도록, 자기소개서 관련
  기능은 항상 근거(Evidence) 기반으로 검증하는 구조를 유지한다.

## 측정

모든 주요 Task는 `.ai/metrics/metrics.jsonl`에 진행 기록을 남긴다. 필드 정의는
[docs/METRICS.md](docs/METRICS.md) 참고. 측정 시스템 자체를 과도하게 만들지 않는다
(JSONL 한 줄 append면 충분).

## 기술 스택 (요약)

Backend: Java 21 / Spring Boot / Spring Data JPA / PostgreSQL / Redis
Frontend: Next.js / TypeScript
Infra: Docker / Docker Compose (추후 Cloud)
AI: Claude / Codex / MCP (추후 Agent SDK 검토)
Monitoring: Micrometer / Prometheus / Grafana

상세 및 채택 이유는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)와
[docs/DECISIONS.md](docs/DECISIONS.md) 참고.
