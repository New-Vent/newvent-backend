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

# ★ 되돌릴 대상을 먼저 기억한다. 교체한 뒤에는 알 수 없다.
PREV=$(docker inspect newvent-app --format '{{.Config.Image}}' 2>/dev/null || true)
echo "▸ 현재 이미지: ${PREV:-(없음)}"
echo "▸ 새 이미지  : $IMAGE"

echo "▸ ECR 로그인"
# 인스턴스 역할(NewVentEc2Role)의 EcrPull 로 토큰을 받는다. 키를 심지 않는다.
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

echo "▸ 이미지 받기"
# 레이어 중복 제거 — 소스만 바뀐 배포는 jar 층만 내려온다.
docker pull "$IMAGE"

echo "▸ 교체"
# ★ .env 는 .gitignore 라 호출자의 git checkout 이 건드리지 않는다.
#   git clean -fd 는 .env 를 지우니 배포 경로에서 절대 쓰지 말 것.
export IMAGE
docker compose $COMPOSE up -d

echo "▸ 헬스체크"
# 기동에 22초쯤 걸린다(실측). 3초 × 40 = 120초까지 기다린다.
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "✅ UP ($((i * 3))초)"

    # 디스크가 19GB 뿐이고 이미지 하나가 431MB 다. 최근 3개만 남긴다.
    docker images "$REGISTRY/newvent-backend" --format '{{.ID}} {{.CreatedAt}}' \
      | sort -k2 -r | tail -n +4 | awk '{print $1}' \
      | xargs -r docker rmi -f >/dev/null 2>&1 || true

    df -h / | tail -1
    exit 0
  fi
  sleep 3
done

echo "✗ 헬스체크 실패 — 앱 로그 마지막 60줄"
docker compose $COMPOSE logs --tail 60 app || true

if [ -n "$PREV" ] && [ "$PREV" != "$IMAGE" ]; then
  echo "▸ 이전 이미지로 되돌립니다 — $PREV"
  # ★ 되돌아가는 건 코드뿐이다. Flyway 가 이미 적용한 스키마는 그대로 남는다.
  #   마이그레이션이 뒤로 호환되지 않으면(컬럼 삭제·타입 변경) 되돌려도 뜨지 않는다.
  #   ddl-auto: validate 라 엔티티와 스키마가 어긋나면 기동이 멈춘다.
  IMAGE="$PREV" docker compose $COMPOSE up -d || true
fi

exit 1
