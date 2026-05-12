# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy Maven configuration
COPY pom.xml ./

# Copy source code
COPY src ./src

# Build application
RUN mvn -q -DskipTests clean package

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy

# Install runtime dependencies
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        dumb-init \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 1000 appuser

# Set working directory
WORKDIR /app

# Copy application jar from build stage
COPY --from=build --chown=appuser:appuser /workspace/target/*.jar /app/app.jar

# Copy entrypoint script
RUN echo '#!/bin/sh' > /app/entrypoint.sh && \
    echo 'exec java ${JAVA_OPTS} -jar /app/app.jar' >> /app/entrypoint.sh && \
    chmod +x /app/entrypoint.sh

# Create cache directory for application
RUN mkdir -p /app/cache && chown -R appuser:appuser /app

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose application port
EXPOSE 8080

# Switch to non-root user
USER appuser

# Run application
ENTRYPOINT ["dumb-init", "--"]
CMD ["/app/entrypoint.sh"]

