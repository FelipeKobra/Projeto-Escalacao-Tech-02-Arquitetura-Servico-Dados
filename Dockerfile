# syntax=docker/dockerfile:1
# =============================================================================
# Imagem única para os dois apps Quarkus. O docker-compose escolhe qual via
#   build.args.MODULO = fontes-operacionais | plataforma-dados
# Build multi-stage: compila com Maven (cache de ~/.m2 entre builds) e roda só
# com o JRE, como usuário sem privilégios.
# =============================================================================
ARG MODULO

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULO
WORKDIR /build
RUN test -n "${MODULO}" || (echo "Informe --build-arg MODULO=fontes-operacionais|plataforma-dados" && exit 1)
COPY pom.xml .
COPY oltp-schema oltp-schema
COPY fontes-operacionais fontes-operacionais
COPY plataforma-dados plataforma-dados
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "${MODULO}" -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULO
WORKDIR /app
RUN useradd --system --uid 1001 quarkus
COPY --from=build --chown=1001 /build/${MODULO}/target/quarkus-app/ /app/
USER 1001
ENV JAVA_OPTS_APPEND="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar /app/quarkus-run.jar"]
