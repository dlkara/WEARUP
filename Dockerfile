# - JDK와 Gradle 캐시는 빌드 단계에서만 사용
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.gradle bash gradlew --no-daemon bootJar

# - 실행 이미지에는 JRE·앱 JAR·상태 확인 도구만 포함
FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 wearup \
    && useradd --uid 10001 --gid wearup --no-create-home --shell /usr/sbin/nologin wearup \
    && mkdir -p /app/upload/post /app/upload/item /app/upload/brand \
    && chown -R wearup:wearup /app
WORKDIR /app
COPY --from=build --chown=wearup:wearup /build/build/libs/*.jar /app/app.jar
USER wearup
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
