# CareerOps Agent

CareerOps Agent의 최소 백엔드 실행 기반입니다. 현재는 제품 기능 없이 Spring Boot가 PostgreSQL과 Redis에 연결되고 상태 및 Prometheus 지표를 노출합니다.

## 사전 요구사항

- Java 21
- Docker Desktop 또는 Docker Engine + Docker Compose
- `curl` (상태 확인용)

Gradle은 별도로 설치할 필요가 없습니다. 저장소에 포함된 Gradle Wrapper를 사용합니다.

## 로컬 실행

저장소 루트에서 환경 파일을 준비하고 로컬 전용 비밀번호로 바꿉니다.

```bash
cp .env.example .env
```

환경변수를 현재 셸에 적용한 뒤 PostgreSQL과 Redis를 실행합니다. Compose는 `.env`를 자동으로 읽지만, Spring Boot 실행과 테스트에도 같은 값이 필요하므로 `source` 단계가 필요합니다.

```bash
set -a
source .env
set +a
docker compose up -d
docker compose ps
```

두 서비스가 `healthy`가 되면 애플리케이션을 실행합니다.

```bash
cd backend
./gradlew bootRun
```

다른 터미널에서 상태와 Prometheus 지표를 확인합니다.

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/prometheus | head
```

테스트도 PostgreSQL과 Redis가 실행 중이고 `.env`의 값이 현재 셸에 적용된 상태에서 수행합니다.

```bash
cd backend
./gradlew test
```

종료할 때 애플리케이션에서 `Ctrl+C`를 누르고 저장소 루트에서 컨테이너를 내립니다. PostgreSQL 데이터는 named volume에 보존됩니다.

```bash
docker compose down
```
