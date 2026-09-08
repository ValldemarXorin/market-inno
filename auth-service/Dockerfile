FROM gradle:8.10-jdk23-alpine AS build

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle ./gradle

COPY src ./src

RUN gradle clean bootJar -x test --no-daemon

FROM eclipse-temurin:23-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs && chown -R appuser:appgroup /app/logs

COPY --from=build /app/build/libs/*.jar app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]