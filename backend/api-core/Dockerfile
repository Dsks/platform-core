# ==========================================
# STAGE 1: BUILD (Gradle + JDK 21)
# ==========================================
FROM gradle:jdk21-alpine AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./

COPY src ./src

RUN gradle bootJar -x test --no-daemon

# ==========================================
# STAGE 2: RUNTIME (JRE 21 Alpine)
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]