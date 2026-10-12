# 인프라

EC2 한 대에 프론트(정적)와 백엔드(컨테이너)가 같이 있습니다. 배포는 GitHub Actions 가
자동으로 합니다.

> 리전 `ap-northeast-2`(서울) · **Bedrock 만 `us-east-1`**

---

## 1. 한눈에

```
GitHub Actions ─OIDC─▶ NewVentDeploy ──┬─ S3 newvent-deploy   (프론트 번들 올리기)
                                        ├─ ECR newvent-backend (백엔드 이미지 올리기)
                                        └─ SSM SendCommand     ("받아서 설치해라")
                                                  │
                                                  ▼
EC2 i-{0123456789} ◀── NewVentEc2Role ──┬─ S3 · ECR  (받기만)
  nginx(호스트) + Docker(앱·DB)              ├─ SSM       (에이전트 접속)
                                              └─ Bedrock us-east-1
```

**Actions 는 올리기만, 서버는 받기만 합니다.** 양쪽이 같은 자원에 양방향 권한을 갖지
않습니다 — 이유는 §7.

---

## 2. EC2 호스트

| | |
| --- | --- |
| 인스턴스 | `i-{0123456789}` · `t3.small` · `x86_64` · `ap-northeast-2a` |
| OS | Ubuntu 24.04 LTS |
| 메모리 | 1,906MB + **swap 2,048MB** (`/swapfile`, `/etc/fstab` 등록) |
| 디스크 | 19GB |
| 주소 | Elastic IP → `newvent.duckdns.org` |
| 보안그룹 | 인바운드 **80 · 443 만** |

### 부팅 시 자동 복구

```
docker · nginx · amazon-ssm-agent · certbot.timer   전부 enabled
newvent-app · newvent-postgres                      restart=unless-stopped
```

**인스턴스를 꺼도 Elastic IP 가 유지되므로** 다시 켤 때 DuckDNS 를 손볼 필요가 없고 전부
자동으로 올라옵니다. 중지하면 인스턴스 요금만 멈추고 **EIP·디스크 요금은 계속 나갑니다.**

### swap 이 있는 이유

`Dockerfile` 1단계가 Gradle 을 돌리는데, 가용 1.5GB 에 swap 이 0 이면 OOM killer 가
`Killed` 한 줄만 남기고 빌드를 죽입니다. **지금은 Actions 가 빌드하니 배포에는 필요
없지만**, 런타임 여유로 남겨둡니다.

---

## 3. 접속 — SSM (22번을 열지 않습니다)

### 결정과 이유

보안그룹에 **22번이 없습니다.** SSH 가 아예 붙지 않습니다. SSM 에이전트가 **바깥으로
443 을 걸어** 명령을 받아오므로 인바운드가 필요 없습니다.

| | SSH | SSM |
| --- | --- | --- |
| 인바운드 포트 | 22 필요 | **없음** |
| 접근 제어 | 키 파일 배포·회수 | IAM 정책 |
| 누가 무엇을 했나 | 서버 로그 | CloudTrail |
| 권한 회수 | 서버에서 직접 제거 | IAM 에서 한 번 |

8명이 쓰는데 **키를 나눠주고 회수하는 일이 없어집니다.**

### 접속

```bash
AWS_PROFILE=newvent-admin aws ssm start-session --region ap-northeast-2 \
  --target i-{0123456789}
```

> 시작 디렉터리가 홈이 아니라 에이전트 작업 디렉터리(`/var/snap/amazon-ssm-agent/<pid>`)
> 입니다. 접속하면 `cd /opt/newvent` 부터 하세요.

### DB 를 로컬 도구로 보기 — 포트를 열지 않고

```bash
AWS_PROFILE=newvent-admin aws ssm start-session --region ap-northeast-2 \
  --target i-{0123456789} \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["5432"],"localPortNumber":["15432"]}'
```

DataGrip 에서 `localhost:15432` · DB `newvent` · 사용자 `newvent` 로 붙습니다.
비밀번호는 서버의 `/opt/newvent/.env` 에 있습니다. **창을 닫으면 터널이 끊깁니다.**
운영 DB 이므로 연결 설정에서 **읽기 전용**을 켜두길 권합니다.

### ⚠️ 두 실행 사용자가 다릅니다

```
Session Manager (사람)      ssm-user (uid 1001)
Run Command     (자동화)    root
```

`/opt/newvent` 가 `ssm-user` 소유라 root 로 도는 git 이 `dubious ownership` 으로 거부합니다.
**그런데 Run Command 에는 `HOME` 이 없어서** `git config --global` 이 `fatal: $HOME not set`
(exit 128) 으로 죽습니다. `docker login` 도 `$HOME/.docker/config.json` 에 씁니다.

### SSM 은 명령 채널이지 파일 채널이 아닙니다

보내는 것은 쉘 명령이 담긴 JSON 뿐이라, 파일을 옮기려면 명령 안에 녹여야 합니다.
처음에는 base64 로 조각내어 보냈습니다 (`frontend/deploy/publish-via-ssm.sh`).

**작동하지만 대가가 큽니다** — 파일 내용이 명령문이라 CloudTrail 에 통째로 남고, 중간에
끊기면 반쪽이 남고, 무결성 검사가 없고, 431MB 이미지는 아예 불가능합니다.

**그래서 데이터는 S3·ECR 로 보내고 SSM 은 "가서 받아라" 지시만 나릅니다.**

---

## 4. nginx — 호스트에서 직접 (컨테이너가 아닙니다)

`nginx/1.24.0 (Ubuntu)` · `systemctl enabled` · 80·443 IPv4/IPv6

```
/etc/nginx/conf.d/newvent.conf                     서버 블록 전부 (sites-enabled 는 비어 있음)
/etc/nginx/snippets/newvent-security-headers.conf  공통 보안 헤더
/etc/letsencrypt/live/newvent.duckdns.org/         인증서 (certbot.timer 자동 갱신)
```

### 라우팅

| 경로 | 대상 |
| --- | --- |
| `/api/` `/auth/` `/e/` | `proxy_pass http://127.0.0.1:8080` |
| `^~ /static/` `^~ /admin/static/` `^~ /assets/` | `try_files $uri =404` — 해시 파일명, 영구 캐시 |
| `= /admin` | `301 /admin/` |
| `^~ /admin/` | `try_files $uri $uri/ /admin/index.html` — 관리자 SPA 폴백 |
| `/` | `try_files $uri $uri/ /index.html` — 사용자 SPA 폴백 |

80 포트는 `/.well-known/acme-challenge/`(갱신용)만 남기고 전부 `301` 로 443 에 넘깁니다.

### 왜 컨테이너로 안 옮기나

인증서와 certbot 타이머가 호스트에 있어 마운트·갱신 훅이 복잡해집니다. 지금 구조에서는
**배포가 nginx 를 전혀 건드리지 않습니다** — 앱 컨테이너만 교체되고 nginx 는 `:8080` 이
다시 뜰 때까지 502 를 내다가 알아서 붙습니다. **그 약 25초가 배포 중단 시간입니다.**

### 알아둘 것 셋

**① `/api/` 블록만 SSE 설정이 다릅니다.** `proxy_buffering off` · `proxy_read_timeout 300s`.
버퍼링이 켜져 있으면 생성 진행 상태가 모였다 한 번에 나가 진행 표시가 무의미해집니다.
LLM 재시도가 기본 60초를 넘기므로 타임아웃도 300초입니다.

**② `/actuator/health` 는 프록시 대상이 아닙니다.** SPA 폴백에 걸려 **백엔드가 죽어도
`200 text/html`** 을 돌려줍니다. 배포 검증에 쓰면 거짓 통과합니다 — 외부 검증은
`/api/public/events` 의 **JSON 본문**으로 해야 합니다.

**③ `add_header` 는 상속이 "덮어쓰기"** 입니다. 하위 블록에 `add_header` 가 하나라도 있으면
상위 것이 전부 사라집니다. 그래서 자체 헤더를 쓰는 location 마다 스니펫을 다시 `include`
합니다. HSTS 는 주석 처리 상태 — 인증서 갱신을 한 번 확인한 뒤 켜기로 했습니다.

---

## 5. Docker Compose — 파일 네 개를 겹칩니다

`Docker 29.8.2` · `compose 5.6.0`

| 파일 | 내용 | 언제 |
| --- | --- | --- |
| `docker-compose.yml` | **postgres 만** | 항상 (기본) |
| `docker-compose.app.yml` | `app` — `build:` 로 소스에서 빌드 | 로컬에서 이미지 확인 |
| `docker-compose.deploy.yml` | `app.image` 를 ECR URI 로 덮고 **`build: !reset null`** | **배포** |
| `docker-compose.bedrock.yml` | LLM 환경변수 주입 (AWS 키 포함) | **팀원 노트북 전용** |

```bash
docker compose up -d                                           # 평소 개발 — DB 만
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.app.yml \
               -f docker-compose.deploy.yml up -d              # 배포
```

### ⚠️ `build: !reset null` 이 핵심입니다

`image:` 만 덮으면 compose 가 `build` 정의를 그대로 들고 있어서, **이미지가 로컬에 없을 때
박스에서 Gradle 을 돌립니다.** 운영 중인 앱(441MB)·Postgres 와 1.9GB 를 두고 경쟁합니다.
`!reset` 으로 지워야 "받아서 띄우기" 만 합니다.

### ⚠️ `docker-compose.bedrock.yml` 을 서버에서 쓰지 마세요

`AWS_ACCESS_KEY_ID` 를 주입합니다. EC2 는 인스턴스 역할로 받으니 불필요하고, `-f` 를 하나
더 붙이면 **고아 컨테이너** 위험만 늘어납니다.

### 컨테이너

| 이름 | 이미지 | 설정 |
| --- | --- | --- |
| `newvent-app` | `…/newvent-backend:{커밋SHA}` | `user=app`(비root) · `restart=unless-stopped` |
| `newvent-postgres` | `pgvector/pgvector:pg17` | `restart=unless-stopped` |

네트워크 `newvent_default`(bridge). 앱은 **컨테이너 이름으로** DB 에 붙습니다.

```
DB_URL=jdbc:postgresql://postgres:5432/newvent
```

`localhost` 가 아닙니다 — 컨테이너 안에서 `localhost` 는 자기 자신입니다.

비밀값(`DB_PASSWORD` · `JWT_SECRET` · `CORS_ALLOWED_ORIGINS`)은 `/opt/newvent/.env`(권한 600)
에만 있습니다. `.gitignore` 와 `.dockerignore` 양쪽에 있어 **git 에도 이미지에도 안
들어갑니다.**

---

## 6. PostgreSQL

| | |
| --- | --- |
| 버전 | `PostgreSQL 17` · 이미지 `pgvector/pgvector:pg17` |
| 확장 | `vector` · `pgcrypto` · `plpgsql` |
| 볼륨 | `newvent_newvent-postgres-data` |

확장은 **Flyway 가 직접 만듭니다** — `V7` 이 `pgcrypto`, `V10` 이 `vector`. 공식 이미지에서
`POSTGRES_USER` 는 슈퍼유저라 `CREATE EXTENSION` 권한이 있습니다.

```bash
docker compose exec -T postgres sh -c \
  'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\conninfo"'
```

컨테이너 **안의** 환경변수를 참조해 비밀번호가 명령줄·히스토리에 남지 않습니다.

### 이 볼륨이 유일한 영속 데이터입니다

나머지는 전부 재생성됩니다 — 컨테이너는 ECR 에서, `/srv/web` 은 S3 에서, `/opt/newvent` 는
git 에서. **백업 대상은 이 볼륨 하나**입니다.

---

## 7. IAM — 권한을 정확히 반대로 나눕니다

### OIDC 공급자

| | |
| --- | --- |
| URL | `token.actions.githubusercontent.com` |
| Audience | `sts.amazonaws.com` |

GitHub 이 워크플로마다 **몇 분짜리 서명 토큰**을 발급하고 AWS 가 검증합니다. 액세스 키를
GitHub Secret 에 저장하는 방식과 달리 **유출될 장기 자격증명이 없습니다.**

### `NewVentDeploy` — Actions 가 빌리는 역할

신뢰 조건이 `main` 브랜치로 한정돼 있습니다.

```
token.actions.githubusercontent.com:sub = repo:New-Vent@{조직ID}/*:ref:refs/heads/main
```

조직 이름 뒤의 **숫자 ID** 는 GitHub 의 OIDC subject 커스터마이즈가 켜져 있어서 붙습니다.
ID 는 재사용되지 않으므로 **조직을 지우고 남이 같은 이름을 써도 막힙니다.**

> ⚠️ 이 형식 때문에 처음 설정이 실패했습니다. AWS 는 "역할 없음"·"신뢰 불일치"·"조건 불충족"
> 에 **전부 같은 `Not authorized to perform sts:AssumeRoleWithWebIdentity`** 를 돌려줍니다.
> 원인을 가리려면 **CloudTrail 의 `AssumeRoleWithWebIdentity` 이벤트에서 `userName`** 을
> 보세요. 실제로 보낸 `sub` 가 거기 있습니다.

| 정책 | 핵심 권한 |
| --- | --- |
| `NewVentDeployPolicy` | `s3:PutObject`·`ListBucket` → `newvent-deploy` / `ssm:SendCommand` → **인스턴스·문서 한정** / `ssm:Get·ListCommandInvocation` |
| `EcrPush` | `ecr:GetAuthorizationToken`(`*`) + `PutImage` 등 → `newvent-backend` 한정 |

**`ssm:SendCommand` 의 Resource 가 둘인 이유** — 인스턴스와 **문서**를 둘 다 좁혀야 합니다.
인스턴스만 좁히면 다른 SSM 문서로도 명령을 보낼 수 있고, 문서만 좁히면 계정의 **모든
인스턴스**에 보낼 수 있습니다.

**`GetAuthorizationToken` 만 `*` 인 이유** — ECR 로그인 토큰 발급이 레지스트리 단위 API 라
리포지토리로 좁힐 수 없습니다. 이 토큰만으로는 아무것도 못 하고, 실제 push 는 리포지토리
한정 권한이 따로 판정합니다.

**`s3:DeleteObject` 는 일부러 뺐습니다.** 배포가 과거 릴리스를 지울 이유가 없고, 지우면
롤백 자료가 사라집니다. 정리는 수명주기 규칙이 합니다.

### `NewVentEc2Role` — 인스턴스가 쓰는 역할

인스턴스 프로파일로 붙어 있고, EC2 안의 모든 프로세스가 **IMDSv2** 로 임시 자격증명을
받습니다. **`.env` 나 디스크에 AWS 키가 하나도 없습니다.**

| 정책 | 종류 | 용도 |
| --- | --- | --- |
| `AmazonSSMManagedInstanceCore` | AWS 관리형 | SSM 등록·접속·명령 수신 |
| `NewVentBedrockInvoke` | 고객 관리형 | `us-east-1` 의 gemma·titan-embed·cohere-embed 호출 |
| `ReadDeployBucket` | 인라인 | `s3:GetObject`·`ListBucket` |
| `EcrPull` | 인라인 | `BatchGetImage`·`GetDownloadUrlForLayer` |

> **Bedrock 만 `us-east-1`** 입니다. 모델 사용 승인(`agreementAvailability`)은 **계정·리전
> 단위**라 IAM 권한과 별개로 콘솔에서 따로 수락해야 합니다.

### 왜 push 와 pull 을 나누나

| 자원 | `NewVentDeploy` (Actions) | `NewVentEc2Role` (서버) |
| --- | --- | --- |
| S3 | `PutObject` ⬆️ | `GetObject` ⬇️ |
| ECR | `PutImage` ⬆️ | `BatchGetImage` ⬇️ |

**서버에 `ecr:PutImage` 를 주면** 앱 취약점으로 박스가 뚫렸을 때 공격자가 악성 이미지를
올려 **다음 배포부터 계속 실행**시킬 수 있습니다. "서버 한 대 침해" 가 "앞으로의 모든 배포
침해" 로 커집니다. 지금 구성에서는 박스가 완전히 뚫려도 **레지스트리를 오염시키지
못합니다.**

노출 수준도 정반대입니다.

```
NewVentDeploy    수명 몇 분 · main 브랜치 한정 · 저장된 비밀 없음
NewVentEc2Role   24시간 상시 · 80/443 이 인터넷에 열린 서버
```

**오래 살고 노출된 쪽이 더 좁은 권한**을 갖습니다.

---

## 8. 저장소 — S3 · ECR

### S3 `newvent-deploy` (프론트 산출물)

| | |
| --- | --- |
| 리전 | `ap-northeast-2` — EC2 와 같아야 전송료가 0 |
| 퍼블릭 액세스 | **4종 모두 차단** |
| 키 구조 | `releases/{커밋SHA}/user.tgz` · `admin.tgz` · `install.sh` |
| 수명주기 | `releases/` 접두사, 30일 후 만료 |

### ECR `newvent-backend` (백엔드 이미지)

| | |
| --- | --- |
| 태그 | 커밋 SHA |
| 태그 변경 | **MUTABLE** — 같은 SHA 재배포(`Re-run`)를 허용하려고 |
| 수명주기 | `tagStatus: any`, 10개 초과 시 만료 |

**ECR 을 고른 이유는 레이어 중복 제거입니다.** 이미지가 층 구조라 소스만 바뀐 배포는 jar
층만 오갑니다.

| | 첫 배포 | 소스만 고친 2회차 |
| --- | --- | --- |
| **ECR** | 142MB(압축) | **수십 MB** |
| S3 `docker save` | 142MB | **142MB 전체** |

프론트는 번들이 124KB 라 S3 로 충분하지만, 백엔드는 이 차이가 큽니다.

---

## 9. 배포

### 트리거

```
develop → main 머지(push)  →  ci.yml 의 build 통과  →  deploy 잡
```

`deploy` 는 `needs: build` 라 **테스트를 통과해야만** 돕니다. PR 에서는 돌지 않습니다
(`github.event_name == 'push'` 조건).

> 별도 `deploy.yml` 이 아니라 `ci.yml` 의 잡인 이유 — 기본 브랜치가 `develop` 이라 별도
> 파일에서 `workflow_run` 으로 CI 를 기다리게 하면 "기본 브랜치의 워크플로만 트리거된다" 는
> 제약에 걸립니다.

### 흐름

```
Actions   docker build → ECR push (태그=SHA)
          SSM SendCommand ─┐
                           └▶ EC2  git fetch && checkout <SHA>   (compose 파일용)
                                   deploy/remote-install.sh
                                     ECR 로그인 → docker pull → compose up -d
                                     /actuator/health 확인 (최대 120초)
                                     실패하면 이전 이미지로 복귀
Actions   외부에서 /api/public/events 의 JSON 본문 확인
```

**`.env` 는 `.gitignore` 라 `git checkout` 이 건드리지 않습니다.**
`git clean -fd` 는 지우니 **배포 경로에서 절대 쓰지 마세요.**

### 안전장치

| | |
| --- | --- |
| `flock` | 서버에서 한 번에 하나만. 겹치면 기다리지 않고 즉시 실패 |
| `executionTimeout: 600` | SSM 이 명령을 끊습니다. 워크플로 폴링(660초)보다 **짧게** 둬서 SSM 이 먼저 끊고 `TimedOut` 으로 보고하게 합니다 |
| `cancel-in-progress: false` (main) | 교체 도중 취소되면 컨테이너가 반쯤 바뀐 채 남습니다 |
| 롤백 | 교체 실패와 헬스체크 실패가 **같은 경로**로 들어옵니다 |

### 배포 중단 시간

**약 25초.** 컨테이너를 재생성하는 동안 `/api/`·`/e/` 가 502 입니다(앱 기동 22.5초 실측).
컨테이너를 둘 띄우는 무중단 배포는 1.9GB 박스에서 메모리가 안 됩니다.

### 지금 무엇이 떠 있나

```bash
git -C /opt/newvent log --oneline -1          # 배포된 커밋
docker inspect newvent-app --format '{{.Config.Image}}'
```

배포 뒤에는 **detached HEAD** 입니다. 서버에서 `git pull` 을 치지 마세요 — 배포본과
어긋납니다.
