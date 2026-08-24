# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY vending-machine-service/pom.xml vending-machine-service/pom.xml
COPY vending-machine-service/src/ vending-machine-service/src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -pl vending-machine-service -am -Dmaven.test.skip=true package \
    && cp vending-machine-service/target/vending-machine-service-0.0.1-SNAPSHOT.jar /application.jar

FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

COPY --from=build --chown=10001:10001 /application.jar application.jar

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
