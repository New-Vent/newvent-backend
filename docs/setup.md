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

LLM 관련 환경변수는 선택 사항이며 11번 항목에 정리되어 있습니다.

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

## 11. LLM 환경변수 (선택)

설정하지 않으면 `mock`으로 동작합니다. **LLM을 쓰지 않는 작업에는 아무것도 필요하지 않습니다.**

```text
LLM_PROVIDER     mock(기본) | ollama | bedrock
LLM_MODEL        provider마다 형식이 다릅니다
BEDROCK_REGION   기본값 us-east-1
```

### Bedrock으로 실행

```text
Run → Edit Configurations → Environment variables
```

```text
LLM_PROVIDER=bedrock;LLM_MODEL=google.gemma-3-27b-it
```

`LLM_MODEL`을 설정하지 않으면 기본값 `qwen2.5:7b`가 들어가 **기동 시점에 실패**합니다.
Bedrock 모델 ID가 아니라는 메시지가 나옵니다.

AWS 자격증명은 **환경변수에 넣지 않습니다.** `aws configure`로 프로파일을 만들면
AWS SDK가 `~/.aws/credentials`에서 자동으로 읽습니다.

```bash
aws configure
```

모델 접근 권한은 AWS 콘솔에서 별도로 신청해야 합니다. 권한이 없으면 호출 시점에
실패하며, 어떤 관문이 남았는지는 오류 메시지에 나옵니다.

### 컨테이너로 Bedrock을 쓸 때 (예외)

위의 "환경변수에 넣지 않습니다"는 IDE나 `gradlew bootRun`으로 **호스트에서** 실행할 때의 이야기입니다.
컨테이너는 호스트의 `~/.aws`를 볼 수 없으므로 이 경우에만 환경변수로 전달합니다.

`.env`에 두 줄을 추가합니다. `.env`는 Git에 포함되지 않습니다.

```text
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

그리고 `docker-compose.bedrock.yml`을 겹쳐서 띄웁니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.bedrock.yml up --build
```

`mock`으로 쓰는 경우에는 이 파일이 필요하지 않습니다. 기존 명령이 그대로 동작합니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml up --build
```

내릴 때는 `docker-compose.bedrock.yml`을 **빼고** 실행합니다. 포함하면 AWS 키가 없는 셸에서
`down`조차 막힙니다(키를 필수로 검사하기 때문입니다).

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

`~/.aws`를 컨테이너에 마운트하는 방법도 있지만 쓰지 않습니다. 그 사람의 **모든** 프로파일이
컨테이너에 노출되기 때문입니다. Bedrock 전용 키만 넘기는 쪽이 최소권한에 맞습니다.

### 실제 모델로 스모크 테스트

애플리케이션을 띄우지 않고 모델만 확인합니다. DB도 필요하지 않습니다.

Windows:

```bash
set LLM_SMOKE=1 && set LLM_PROVIDER=bedrock && gradlew.bat test --rerun
```

macOS/Linux:

```bash
LLM_SMOKE=1 LLM_PROVIDER=bedrock ./gradlew test --rerun
```

실제 API를 호출하므로 비용이 발생합니다. 5회 호출에 약 1원입니다.