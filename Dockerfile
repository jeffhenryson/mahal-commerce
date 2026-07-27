# syntax=docker/dockerfile:1

# ===== Build stage =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# O repositório Maven vive num cache do BuildKit, não numa camada da imagem. Builds
# seguintes reaproveitam o que já foi baixado e um download interrompido não obriga a
# refazer tudo — antes, cada build rebaixava o mundo do zero.
#
# `dependency:go-offline` foi removido: ele resolvia TODAS as dependências, inclusive as
# de escopo test (testcontainers sozinho tem 17 MB), que a imagem de runtime nunca usa.
# Era também o passo mais frágil do build, porque um único corte de conexão em qualquer
# artefato derrubava tudo. O cache mount cobre melhor o que ele tentava resolver.
#
# `maven.test.skip=true` (em vez de `skipTests`) pula também a COMPILAÇÃO dos testes,
# que é o que puxa as dependências de teste. Testes rodam no CI e via
# `docker run maven ... mvn test`, não aqui.
#
# retryHandler.count cobre cortes transitórios ("Premature end of Content-Length
# delimited message body"), comuns em artefatos grandes e links instáveis.
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,id=mahal-maven,target=/root/.m2 \
    mvn -B -ntp \
        -Dmaven.test.skip=true \
        -Dmaven.wagon.http.retryHandler.count=5 \
        package

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre-alpine

ENV JAVA_OPTS=""
WORKDIR /app

# Non-root user for security
RUN addgroup -S spring && adduser -S -G spring spring
RUN mkdir -p /app/uploads && chown -R spring:spring /app
USER spring

COPY --from=build /app/target/mahal-commerce-*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
