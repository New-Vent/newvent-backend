# 로컬 개발 환경 설정

New-Vent 백엔드의 로컬 실행 방법을 안내합니다.

## 1. 사전 준비

* JDK 21
* Docker Desktop
* Git
* IntelliJ IDEA

Gradle은 별도로 설치하지 않고 프로젝트의 Gradle Wrapper를 사용합니다.

## 2. 저장소 복제

```bash
git clone https://github.com/New-Vent/newvent-backend.git
cd newvent-backend
git switch develop
```

## 3. DB 환경변수 설정

실제 DB 접속 정보는 저장소에 커밋하지 않습니다. 필요한 값은 팀 내부에서 별도로 공유합니다.

사용하는 환경변수는 다음과 같습니다.

```text
DB_NAME
DB_USERNAME
DB_PASSWORD
DB_URL
```

`DB_URL`을 설정하지 않으면 `application.yaml`의 기본 주소를 사용합니다.
`.env`의 `DB_NAME`을 기본값과 다르게 설정한 경우에는 `DB_URL`도 동일한 DB 이름으로 설정해야 합니다.

### Docker Compose 설정

프로젝트 최상단에 `.env` 파일을 생성합니다.

```dotenv
DB_NAME=<로컬 DB 이름>
DB_USERNAME=<로컬 DB 사용자명>
DB_PASSWORD=<로컬 DB 비밀번호>
```

`.env`는 Git에 포함하지 않습니다.

```gitignore
.env
.env.*
!.env.example
```

저장소에는 실제 값 대신 다음과 같은 `.env.example`만 포함할 수 있습니다.

```dotenv
DB_NAME=
DB_USERNAME=
DB_PASSWORD=
```

### IntelliJ 설정

Docker Compose는 `.env`를 자동으로 읽지만, IntelliJ에서 실행하는 Spring Boot 애플리케이션은 `.env`를 자동으로 읽지 않습니다.

다음 메뉴에서 환경변수를 설정합니다.

```text
Run → Edit Configurations → Environment variables
```

```text
DB_USERNAME=<로컬 DB 사용자명>;DB_PASSWORD=<로컬 DB 비밀번호>
```

필요한 경우 `DB_URL`도 함께 설정합니다.

```text
DB_URL=jdbc:postgresql://localhost:5432/<로컬 DB 이름>
```

## 4. PostgreSQL 실행

Docker Desktop을 실행한 후 프로젝트 최상단에서 다음 명령어를 실행합니다.

```bash
docker compose up -d
```

컨테이너 상태를 확인합니다.

```bash
docker compose ps
```

`newvent-postgres`가 `healthy` 상태이면 정상적으로 실행된 것입니다.

로그는 다음 명령어로 확인할 수 있습니다.

```bash
docker compose logs postgres
```

## 5. 애플리케이션 실행

### IntelliJ에서 실행

앞에서 설정한 Run Configuration의 환경변수를 사용해 Spring Boot 애플리케이션을 실행합니다.

```text
Run → Edit Configurations → Environment variables
```

### 터미널에서 실행

터미널에서 실행하는 경우에는 해당 터미널에 `DB_USERNAME`, `DB_PASSWORD`가 설정되어 있어야 합니다.

프로젝트 루트의 `.env` 파일은 Docker Compose에서만 자동으로 읽으며, Spring Boot는 자동으로 읽지 않습니다.

Windows:

```bash
gradlew.bat bootRun
```

macOS/Linux:

```bash
./gradlew bootRun
```

애플리케이션 실행 시 Flyway가 다음 경로의 마이그레이션 파일을 자동으로 적용합니다.

```text
src/main/resources/db/migration
```

## 6. Flyway 적용 확인

PostgreSQL 컨테이너에 접속합니다.

```bash
docker exec -it newvent-postgres psql -U <로컬 DB 사용자명> -d <로컬 DB 이름>
```

테이블 목록을 확인합니다.

```text
\dt
```

Flyway 적용 내역을 확인합니다.

```text
SELECT installed_rank, version, description, script, success
FROM flyway_schema_history;
```

접속을 종료합니다.

```text
\q
```

## 7. 테스트

Windows:

```bash
gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
```

## 8. PostgreSQL 종료

데이터를 유지하면서 컨테이너를 종료합니다.

```bash
docker compose down
```

다시 실행할 때는 다음 명령어를 사용합니다.

```bash
docker compose up -d
```

## 9. 로컬 DB 초기화

다음 명령어는 PostgreSQL 컨테이너와 로컬 DB 데이터를 모두 삭제합니다.

```bash
docker compose down -v
```

초기 설정 단계에서 마이그레이션을 다시 적용해야 할 때만 사용합니다.

## 10. Flyway 작성 규칙

마이그레이션 파일은 다음 형식을 사용합니다.

```text
V{버전}__{설명}.sql
```

예시:

```text
V1__init_schema.sql
V2__add_event_column.sql
V3__create_new_table.sql
```

공유 환경에 적용된 마이그레이션은 수정하지 않습니다. 스키마 변경이 필요하면 새로운 버전의 마이그레이션을 추가합니다.
