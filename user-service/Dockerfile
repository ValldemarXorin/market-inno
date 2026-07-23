FROM eclipse-temurin:23-jdk-alpine AS app-compiler
WORKDIR /source

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:23-jre-alpine
WORKDIR /runtime

RUN addgroup -S runtimegroup && adduser -S runtimeuser -G runtimegroup
USER runtimeuser

COPY --from=app-compiler /source/build/libs/*.jar application.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "application.jar"]

#final testing ci