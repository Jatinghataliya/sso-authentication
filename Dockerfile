# ─────────────────────────────────────────────────────────────
# Stage 1: Build
#   Uses Maven + JDK 17 to compile and package the fat JAR.
#   The Maven local repo is cached in a layer so rebuilds are fast.
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /app

# Copy dependency descriptors first — Docker caches this layer
# and only re-downloads when pom.xml changes.
COPY pom.xml .
COPY .mvn/ .mvn/
RUN test -f mvnw && chmod +x mvnw || true

# Download all dependencies (offline cache layer)
COPY pom.xml .
RUN apk add --no-cache maven \
 && mvn dependency:go-offline -q

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

# Pass all credentials via environment variables at runtime.
# Do NOT bake secrets into the image.
# See README.md → Local Configuration → Option B for the full list.
ENV AUTH0_CLIENT_ID=""
ENV AUTH0_CLIENT_SECRET=""
ENV AUTH0_DOMAIN=""
ENV GITHUB_CLIENT_ID=""
ENV GITHUB_CLIENT_SECRET=""
ENV GOOGLE_CLIENT_ID=""
ENV GOOGLE_CLIENT_SECRET=""
ENV APP_BASE_URL="http://localhost:8081"
ENV SERVER_PORT="8081"

ENTRYPOINT ["java", "-jar", "app.jar"]
