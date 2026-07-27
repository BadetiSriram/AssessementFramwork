# =============================================================================
# Stage 1: builder
# Populates Maven local cache as a separate layer (dependencies change rarely),
# then compiles and packages the fat jar, and extracts layered-jar content.
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace

# --- dependency cache layer --------------------------------------------------
# Copy all POM files first so that `dependency:go-offline` can be cached by
# Docker independently of source code changes.
COPY pom.xml                                  ./pom.xml
COPY framework-core/pom.xml                   ./framework-core/pom.xml
COPY framework-camunda-starter/pom.xml        ./framework-camunda-starter/pom.xml
COPY framework-data-starter/pom.xml           ./framework-data-starter/pom.xml
COPY framework-observability-starter/pom.xml  ./framework-observability-starter/pom.xml
COPY framework-security-starter/pom.xml       ./framework-security-starter/pom.xml
COPY framework-test/pom.xml                   ./framework-test/pom.xml
COPY framework-web-starter/pom.xml            ./framework-web-starter/pom.xml
COPY service-template/pom.xml                 ./service-template/pom.xml

RUN mvn -B -q dependency:go-offline

# --- source build layer ------------------------------------------------------
COPY framework-core/src                   ./framework-core/src
COPY framework-camunda-starter/src        ./framework-camunda-starter/src
COPY framework-data-starter/src          ./framework-data-starter/src
COPY framework-observability-starter/src ./framework-observability-starter/src
COPY framework-security-starter/src      ./framework-security-starter/src
COPY framework-test/src                  ./framework-test/src
COPY framework-web-starter/src           ./framework-web-starter/src
COPY service-template/src                ./service-template/src

RUN mvn -B -q package -DskipTests -pl service-template -am

# --- layered-jar extraction --------------------------------------------------
# Boot 4 layertools splits the fat jar into cache-friendly layers:
#   dependencies  → third-party JARs (changes least often)
#   spring-boot-loader → Boot launcher classes
#   snapshot-dependencies → -SNAPSHOT JARs (changes on snapshot bumps)
#   application   → your compiled classes / resources (changes most often)
RUN java -Djarmode=layertools \
         -jar service-template/target/service-template-*.jar \
         extract --destination /extracted

# =============================================================================
# Stage 2: runtime
# Minimal JRE image with a non-root user and extracted layers copied in
# cache-optimal order so only the "application" layer is invalidated on
# typical code changes.
# =============================================================================
FROM eclipse-temurin:21-jre AS runtime

LABEL org.opencontainers.image.source="https://github.com/aaseya/camunda-process-framework" \
      org.opencontainers.image.title="service-template" \
      org.opencontainers.image.description="Scaffold service on camunda-process-framework"

# Install curl for the HEALTHCHECK (not present in the slim JRE base).
# The apt cache is purged in the same layer to keep image size minimal.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root user / group (UID 1000).
# The eclipse-temurin:21-jre base (Ubuntu 24.04) already ships an "ubuntu" account at
# UID/GID 1000, so groupadd fails with "GID '1000' already exists". Remove it first —
# UID 1000 must be preserved because deploy/helm-chart/values.yaml pins
# runAsUser: 1000 and fsGroup: 1000.
RUN (userdel -r ubuntu 2>/dev/null || true) \
    && groupadd --gid 1000 spring \
    && useradd --uid 1000 --gid spring --shell /bin/bash --create-home spring

WORKDIR /app

# Copy layers in ascending volatility order so Docker reuses cached layers on
# code-only changes (only the final "application" layer is invalidated).
COPY --from=builder --chown=spring:spring /extracted/dependencies/          ./
COPY --from=builder --chown=spring:spring /extracted/spring-boot-loader/    ./
COPY --from=builder --chown=spring:spring /extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /extracted/application/           ./

# JVM tuning: exit immediately on OOM instead of hanging; cap heap at 75 % of
# the container memory limit (avoids manual -Xmx that diverges from the limit).
ENV JAVA_OPTS="-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

USER 1000

# Boot 4 launcher class (org.springframework.boot.loader.launch.JarLauncher).
# JAVA_OPTS is expanded via the shell so the env var is honoured at runtime.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
