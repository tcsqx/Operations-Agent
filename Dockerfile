# ==============================================================================
# Stage 1: Build & Package
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Cache maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and package
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Production Minimal Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner

LABEL maintainer="OpsPilot Team <dev@opspilot.org>"
LABEL description="OpsPilot 2.0 - Enterprise AI SRE & DevOps Copilot"

# Install lightweight diagnostic tools for sandbox probes (procps, coreutils, curl)
RUN apk add --no-cache \
    curl \
    procps \
    coreutils \
    iproute2 \
    tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

# Create non-root dedicated user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs /app/uploads \
    && chown -R appuser:appgroup /app

# Copy application JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/super-biz-agent-*.jar /app/app.jar

USER appuser

# Healthcheck configuration
HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
    CMD curl -f http://localhost:9900/api/v2/tools || exit 1

EXPOSE 9900

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
