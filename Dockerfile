# syntax=docker/dockerfile:1

# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only pom.xml first to leverage Docker layer caching
COPY pom.xml .

# Copy sources
COPY src ./src

# Build application (skip tests here; CI covers tests)
RUN mvn -B -ntp -DskipTests package

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre

ENV JAVA_OPTS=""
WORKDIR /app

# Create non-root user for security
RUN useradd -r -u 10001 spring && chown -R spring:spring /app
USER spring

# Copy fat jar from build stage
COPY --from=build /app/target/security-spring-*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
