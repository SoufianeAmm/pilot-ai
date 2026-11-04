# ---- Build stage: compile and package (tests run in CI / "mvnw verify", not here) ----
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/flowpilot.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage: JRE only, non-root user, layered for fast rebuilds ----
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system flowpilot && useradd --system --gid flowpilot --no-create-home flowpilot
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER flowpilot
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
