#!/usr/bin/env bash
#
# 박스에서 도는 배포 스크립트. ECR 이미지를 받아 앱 컨테이너만 교체한다.
#
#   SSM Run Command 가 root 로, 소스를 배포 SHA 에 맞춘 뒤 이걸 부른다:
#     cd /opt/newvent && git checkout --detach <SHA>
#     SHA=<SHA> IMAGE=<ECR URI:SHA> bash deploy/remote-install.sh
#
#   역할을 나눈다 — git 은 호출자가, docker 는 이 스크립트가.
#   그래야 "소스는 바꿨는데 이미지는 못 받았다" 같은 중간 상태를 구분할 수 있다.
#
#   왜 박스에서 빌드하지 않나
#   Dockerfile 1단계가 Gradle 을 돌린다. 운영 중인 앱(525MB)·Postgres 와
#   1.9GB 를 두고 경쟁하면 둘 다 느려진다. 빌드는 Actions 러너가 한다.

set -euo pipefail

: "${SHA:?SHA 가 필요합니다}"
: "${IMAGE:?IMAGE 가 필요합니다}"

REGION=${REGION:-ap-northeast-2}
REGISTRY=${REGISTRY:-673478370256.dkr.ecr.ap-northeast-2.amazonaws.com}
COMPOSE="-f docker-compose.yml -f docker-compose.app.yml -f docker-compose.deploy.yml"
LOCK=${LOCK:-/var/lock/newvent-deploy.lock}

# 한 번에 하나만 돈다.
#
#   Actions 가 폴링을 포기해도(executionTimeout 전에) 서버의 명령은 계속 돈다.
#   그 상태에서 다음 배포가 들어오면 docker compose up 둘이 같은 컨테이너를
#   동시에 건드린다. 겹치면 기다리지 않고 즉시 실패시킨다.
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "✗ 다른 배포가 진행 중입니다 ($LOCK). 이번 배포를 중단합니다."
  exit 1
fi

# 되돌릴 대상을 먼저 기억한다. 교체한 뒤에는 알 수 없다.
PREV=$(docker inspect newvent-app --format '{{.Config.Image}}' 2>/dev/null || true)
echo "▸ 현재 이미지: ${PREV:-(없음)}"
echo "▸ 새 이미지  : $IMAGE"

# 교체 실패와 헬스체크 실패가 같은 경로로 들어온다.
#
#   set -e 때문에 `docker compose up -d` 가 실패하면 스크립트가 거기서 끝나
#   아래 롤백이 영영 실행되지 않았다. up 은 기존 컨테이너를 지운 뒤
#   새 컨테이너 시작에서 실패할 수 있고, 그러면 서비스가 죽은 채로 남는다.
#   그래서 두 실패를 모두 이 함수로 보낸다.
rollback() {
  echo "✗ $1"
  echo "─── 앱 로그 마지막 60줄 ───"
  docker compose $COMPOSE logs --tail 60 app 2>&1 | tail -60 || true

  if [ -z "$PREV" ]; then
    echo "⚠ 되돌릴 이전 이미지가 없습니다 (첫 배포). 서비스가 내려가 있을 수 있습니다."
    exit 1
  fi
  if [ "$PREV" = "$IMAGE" ]; then
    echo "⚠ 이전 이미지가 이번 것과 같습니다. 되돌려도 달라지지 않습니다."
    exit 1
  fi

  echo "▸ 이전 이미지로 되돌립니다 — $PREV"
  #   되돌아가는 건 코드뿐이다. Flyway 가 이미 적용한 스키마는 그대로 남는다.
  #   마이그레이션이 뒤로 호환되지 않으면(컬럼 삭제·타입 변경) 되돌려도 뜨지 않는다.
  #   ddl-auto: validate 라 엔티티와 스키마가 어긋나면 기동이 멈춘다.
  if IMAGE="$PREV" docker compose $COMPOSE up -d; then
    echo "▸ 되돌리기 완료. 배포는 실패로 기록합니다."
  else
    echo "✗ 되돌리기도 실패했습니다. **수동 확인이 필요합니다.**"
  fi
  exit 1
}

echo "▸ ECR 로그인"
# 인스턴스 역할(NewVentEc2Role)의 EcrPull 로 토큰을 받는다. 키를 심지 않는다.
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

echo "▸ 이미지 받기"
# 레이어 중복 제거 — 소스만 바뀐 배포는 jar 층만 내려온다.
docker pull "$IMAGE"

echo "▸ 교체"
#   .env 는 .gitignore 라 호출자의 git checkout 이 건드리지 않는다.
#   git clean -fd 는 .env 를 지우니 배포 경로에서 절대 쓰지 말 것.
export IMAGE
# set -e 에 맡기지 않는다 — 실패를 직접 받아 롤백으로 보낸다.
docker compose $COMPOSE up -d || rollback "컨테이너 교체 실패 (docker compose up)"

echo "▸ 헬스체크"
# 기동에 22초쯤 걸린다(실측). 3초 × 40 = 120초까지 기다린다.
healthy=0
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "✅ UP ($((i * 3))초)"
    healthy=1
    break
  fi
  sleep 3
done

[ "$healthy" = 1 ] || rollback "헬스체크 실패 (120초 동안 UP 이 아님)"

# 디스크가 19GB 뿐이고 이미지 하나가 431MB 다. 최근 3개만 남긴다.
docker images "$REGISTRY/newvent-backend" --format '{{.ID}} {{.CreatedAt}}' \
  | sort -k2 -r | tail -n +4 | awk '{print $1}' \
  | xargs -r docker rmi -f >/dev/null 2>&1 || true

df -h / | tail -1
echo "▸ 완료 — $IMAGE"
