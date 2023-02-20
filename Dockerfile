FROM maven:3-openjdk-18-slim AS builder
COPY src/main src/main
COPY pom.xml pom.xml
RUN mvn clean package -Dmaven.test.skip=true


FROM eclipse-temurin:11.0.18_10-jdk

COPY --from=builder target/anshar-*-SNAPSHOT.jar anshar.jar
COPY --from=builder src/main/resources/dummy_credentials.json dummy_credentials.json

EXPOSE 8012
ENV GOOGLE_APPLICATION_CREDENTIALS dummy_credentials.json
ENTRYPOINT ["java", "-jar", "/anshar.jar", "$JAVA_OPTIONS", "-Dspring.config.location optional:file:/application.properties"]
