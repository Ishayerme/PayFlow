# One Dockerfile for all three services. Build one with, e.g.:
#   docker build --build-arg SERVICE=account-service -t payflow/account-service .
# docker-compose.yml does this for all three.

# ---- build stage: compile with the Maven wrapper (tests run in `mvnw verify`, not here) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY account-service/pom.xml account-service/
COPY transaction-service/pom.xml transaction-service/
COPY notification-service/pom.xml notification-service/
COPY dev-db/pom.xml dev-db/
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline -pl common,account-service,transaction-service,notification-service || true
COPY . .
ARG SERVICE
RUN ./mvnw -q -B package -DskipTests -pl ${SERVICE} -am

# ---- run stage: only the JRE and the jar ----
FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
RUN useradd --system payflow
COPY --from=build /src/${SERVICE}/target/${SERVICE}-1.0.0.jar app.jar
USER payflow
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
