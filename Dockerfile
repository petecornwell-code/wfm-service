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

# The heap flag lives HERE, on the command line, not in a JAVA_OPTS environment variable.
# `java -jar` does not read JAVA_OPTS -- that is a convention of Spring Boot's launch script and
# of Tomcat, not of the JVM. The ECS task definition declared
# JAVA_OPTS=-XX:MaxRAMPercentage=75.0 for months and the JVM ignored it, falling back to the
# container default of 25%: a 1 GiB heap inside a 4 GiB task, with three quarters of the memory
# unused. A 287-agent solve then died with "Java heap space" (2026-09-23).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
