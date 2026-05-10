# ─── Stage 1: Build ──────────────────────────────────────────────────────────
# Full JDK needed for Maven compilation; Alpine keeps the layer small.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Make Maven wrapper executable
COPY mvnw .
COPY .mvn/ .mvn/
RUN chmod +x mvnw

# ── Copy all pom.xml files first so Docker caches the dependency-download
#    layer independently of source changes ──────────────────────────────────
COPY pom.xml .
COPY shared/kernel/pom.xml      shared/kernel/
COPY shared/contracts/pom.xml   shared/contracts/
COPY modules/claims/pom.xml     modules/claims/
COPY modules/customers/pom.xml  modules/customers/
COPY modules/documents/pom.xml  modules/documents/
COPY modules/workflow/pom.xml   modules/workflow/
COPY modules/notifications/pom.xml modules/notifications/
COPY modules/workshops/pom.xml  modules/workshops/
COPY modules/payments/pom.xml   modules/payments/
COPY modules/reporting/pom.xml  modules/reporting/
COPY modules/rentals/pom.xml    modules/rentals/
COPY app/eclaims-api/pom.xml    app/eclaims-api/

# Download all dependencies (cached unless any pom.xml changes)
RUN ./mvnw dependency:go-offline -pl app/eclaims-api -am -B -q

# ── Copy sources and build ────────────────────────────────────────────────
COPY shared/   shared/
COPY modules/  modules/
COPY app/      app/

# Tests are run by CI before this image is built (see ci.yml), so skip here.
RUN ./mvnw -pl app/eclaims-api -am package -DskipTests -B -q

# ─── Stage 2: Runtime ────────────────────────────────────────────────────────
# Minimal JRE image — no compiler, no Maven, ~100 MB smaller.
FROM eclipse-temurin:21-jre-alpine

# Non-root user — principle of least privilege
RUN addgroup -S eclaims && adduser -S eclaims -G eclaims

WORKDIR /app

# Copy the fat jar produced by eclaims-api
COPY --from=build /build/app/eclaims-api/target/eclaims-api-*.jar app.jar

# Create uploads dir (used when ECLAIMS_STORAGE_PATH=/app/uploads)
RUN mkdir -p uploads && chown -R eclaims:eclaims /app

USER eclaims

EXPOSE 8090

# Tune JVM for containers:
#   -XX:+UseContainerSupport   — respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75    — use 75% of container RAM for heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
