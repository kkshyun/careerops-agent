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

### Phase 3 이후 후보 (우선순위 미확정, 필요 시점에 별도 Task로 분리)

- **기관유형/기관분류 텍스트 매핑** — ALIO 목록 API 응답에는 기관 코드
  (`pblntInstCd`)만 있고 유형/분류명 필드가 없음이 확인됨(COLLECT-002 조사).
  참조 데이터(코드정의서/기관목록 API 등)를 확보해야 가능.
- **ALIO 상세조회(`/detail.do`) 연동** — 채용전형단계(`steps`)/첨부파일
  메타정보(`files`)는 목록 API에서 항상 빈 배열. 상세 API 연동 필요
  (COLLECT-001부터 반복적으로 Out of Scope 처리된 부분).
- **공고 전체 필드 갱신 전략** — 현재(COLLECT-002 기준)는 재수집 시 상태만
  갱신하고 나머지 필드는 최초 저장값 유지. 필드 전체를 최신화하려면 별도
  설계 필요.
- **조회/필터링 API 추가 확장** — `employmentType`/`educationRequirement`/
  게시일 범위 필터, 클라이언트 지정 정렬 등 JOB-002에서 의도적으로 제외한
  항목. 필요성이 실사용에서 확인되면 별도 Task.
- **Scheduler(정기 수집)**, **cross-source dedup**, **Personal Knowledge
  Base(PKB) v0**, **Metrics 계측 확장**, **알림(카카오톡)** 등 — 아직 착수
  전, 우선순위는 사용자 승인 후 결정.

이 문서는 Phase가 진행되며 갱신된다. 완료된 Phase는 체크박스로 남기고,
다음 Phase/후보를 그 아래 갱신한다.
