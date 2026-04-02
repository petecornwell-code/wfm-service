# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle wrapper and config first (layer caching)
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

# Download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app
USER app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["java", "-cp", "app.jar", "org.springframework.boot.loader.launch.PropertiesLauncher"] || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
