FROM maven:3-openjdk-18-slim AS builder
COPY src/main src/main
COPY pom.xml pom.xml
RUN mvn clean package -Dmaven.test.skip=true


FROM eclipse-temurin:11-jdk

COPY --from=builder target/anshar-*-SNAPSHOT.jar anshar.jar
COPY --from=builder src/main/resources/dummy_credentials.json dummy_credentials.json

EXPOSE 8012
ENV GOOGLE_APPLICATION_CREDENTIALS dummy_credentials.json
CMD java $JAVA_OPTIONS -jar /anshar.jar -Dspring.config.location optional:file:/application.properties
