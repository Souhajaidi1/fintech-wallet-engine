# ---- build ----------------------------------------------------------------
# Dependencies are resolved in their own layer so that a source-only change
# does not re-download the world on every build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# The application never needs root. Running as an unprivileged user means a
# remote-code-execution bug does not immediately own the container.
RUN addgroup -S wallet && adduser -S wallet -G wallet
USER wallet

COPY --from=build --chown=wallet:wallet /build/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
