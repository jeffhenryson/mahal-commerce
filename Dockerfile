# syntax=docker/dockerfile:1

# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only pom.xml first so the dependency download layer is cached separately
# from source changes (re-downloads only when pom.xml changes)
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline -q

# Copy sources and build (tests run in CI, not here)
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre-alpine

ENV JAVA_OPTS=""
WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S -G spring spring
USER spring

COPY --from=build /app/target/security-spring-*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
