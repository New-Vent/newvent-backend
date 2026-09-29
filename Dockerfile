# newvent-backend 이미지
#
#   2단계로 나눈다 (멀티스테이지)
#   1단계  JDK + Gradle + 소스로 jar 을 만든다      → 무겁다 (~800MB)
#   2단계  그 jar 하나만 JRE 위에 얹는다            → 가볍다 (~200MB)
#   최종 이미지에 소스코드도 Gradle 도 안 들어간다.
#
# 빌드:  docker build -t newvent-backend .
# 실행:  docker compose -f docker-compose.yml -f docker-compose.app.yml up

# ══ 1단계 · 빌드 ══════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src

#   의존성 파일을 소스보다 먼저 복사한다.
#   소스만 고쳤을 때 이 레이어가 캐시돼서 재빌드가 빨라진다.
#   순서를 바꾸면 한 줄만 고쳐도 의존성을 매번 새로 받는다.
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

#   gradlew 에 실행 권한이 없을 수 있다 (실제로 이 저장소가 그렇다).
#   `sh gradlew` 로 우회해도 되지만 권한을 주는 쪽이 깔끔하다.
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src

#   테스트는 건너뛴다 — CI 가 이미 돌린다.
#   여기서 또 돌리면 이미지 빌드가 느려지고, DB 가 아직 없어서 어차피 5건이 실패한다.
RUN ./gradlew --no-daemon bootJar -x test

# ══ 2단계 · 실행 ══════════════════════════════════════════════════
#   alpine 을 쓴다 — jammy 대비 기반이 300MB 가볍다.
#   amd64·arm64 둘 다 제공하므로 t3(x86)·t4g(ARM) 어느 쪽에 올려도 된다.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

#   타임존·로캘을 고정한다. 이게 없으면 컨테이너가 UTC 로 돈다.
ENV TZ=Asia/Seoul
ENV LANG=C.UTF-8
# curl 은 healthcheck 용, tzdata 는 타임존용. jre 이미지엔 둘 다 없다
RUN apk add --no-cache curl tzdata \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

#   root 로 돌리지 않는다. 컨테이너가 뚫려도 권한이 제한된다.
RUN addgroup -S app && adduser -S app -G app

#   COPY 에서 바로 소유자를 준다.
COPY --chown=app:app --from=build /src/build/libs/*.jar app.jar
USER app

EXPOSE 8080

#   컨테이너 메모리에 맞춰 힙을 잡게 한다.
#   t4g.small 이 2GB 라 기본값(호스트 메모리의 1/4)으로 두면 넉넉하지 않다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
