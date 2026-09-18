# ─────────────────────────────────────────────────────────────
# Stage 1: Build
#   Installs Maven via apk, downloads dependencies, then packages
#   the fat JAR. pom.xml is copied first so the dependency layer
#   is cached and only re-downloaded when pom.xml changes.
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Install Maven (no Maven wrapper in this project)
RUN apk add --no-cache maven

# Copy pom.xml first — Docker caches this layer until pom.xml changes
COPY pom.xml .

# Download all dependencies (cached layer)
RUN mvn dependency:go-offline -q

# Copy source and build the fat JAR (skip tests — run them separately)
COPY src ./src
RUN mvn package -DskipTests -q

# ─────────────────────────────────────────────────────────────
# Stage 2: Runtime
#   Slim JRE-only image — no JDK, no Maven, no build tools.
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=build /app/target/sso-okta-*.jar app.jar

# Port the app listens on (matches server.port / SERVER_PORT default)
EXPOSE 8081

# Credentials and PORT are injected at runtime by Railway.
# Do NOT bake secrets or PORT into the image.
ENV APP_BASE_URL="http://localhost:8081"

ENTRYPOINT ["java", "-jar", "app.jar"]
