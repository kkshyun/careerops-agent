# ROADMAP.md — CareerOps Agent

## Phase 0 — AI 개발팀 협업 구조 구축 (현재)

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
- [ ] 사용자 승인 (Phase 1 진행 여부 확정)

Phase 0에서는 CareerOps 기능 코드를 작성하지 않는다.

## Phase 1 후보 (제안 — 승인 대기, 우선순위 미확정)

아래는 다음으로 진행할 만한 후보들이다. 어떤 것부터 할지는 사용자 승인 후
Task로 쪼갠다. Phase 0 완료 보고 시 함께 제안한다.

1. **프로젝트 뼈대(Skeleton) 구축** — Spring Boot(Java 21) 백엔드 +
   PostgreSQL/Redis를 Docker Compose로 띄우는 최소 구조. 도메인 기능 없이
   헬스체크/설정/DB 연결까지만. 이후 모든 기능의 기반이 되므로 우선순위가
   가장 높은 후보.
2. **Personal Knowledge Base(PKB) v0** — 이력서/경험정리/기존 자소서를
   저장하고 조회하는 최소 기능. 자기소개서 파이프라인의 전제 조건.
3. **채용공고 수집 파이프라인 v0(단일 소스)** — 사이트 1곳만 대상으로 수집 →
   정규화 → 중복 제거까지의 최소 파이프라인. 이후 소스 확장.
4. **Metrics 계측 배선** — Micrometer/Prometheus/Grafana를 Phase 1 스켈레톤에
   바로 연결해, 이후 기능들이 처음부터 관측 가능하게 한다.

이 문서는 Phase가 진행되며 갱신된다. 완료된 Phase는 위 체크박스로 남기고,
다음 Phase 후보를 그 아래 갱신한다.
