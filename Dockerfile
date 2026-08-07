# Multi-stage build for the Spring Boot API.
# Stage 1: build the JAR with Maven
# Stage 2: run the JAR with a minimal JRE

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy Maven wrapper and POM first for dependency caching
COPY mvnw .
COPY mvnw.cmd .
COPY .mvn/ .mvn/
COPY pom.xml .

# Download dependencies (cached layer unless POM changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN ./mvnw -B -o package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine

# Non-root user for security
RUN addgroup -S contentfilter && adduser -S contentfilter -G contentfilter

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /app/target/contentfilter-*.jar app.jar

# Create a temp directory for file uploads (writable by non-root)
RUN mkdir -p /tmp/contentfilter && chown contentfilter:contentfilter /tmp/contentfilter

USER contentfilter

# JVM flags for container-aware operation
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.io.tmpdir=/tmp/contentfilter"

# Default Spring profile; override with SPRING_PROFILES_ACTIVE
ENV SPRING_PROFILES_ACTIVE=""

# The FastAPI model service URL when running in Docker Compose
ENV CONTENT_FILTER_MODEL_SERVICE_URL="http://contentfilter-model:8000"

EXPOSE 8080

# Health check: liveness probe
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
