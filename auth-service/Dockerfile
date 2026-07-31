FROM gradle:8.10-jdk21-alpine AS build

WORKDIR /app

COPY build.gradle settings.gradle ./
COPY gradle ./gradle

COPY src ./src

RUN gradle clean bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/build/libs/*.jar app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]