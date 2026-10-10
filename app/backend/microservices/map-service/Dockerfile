# Build context is the REACTOR ROOT (app/backend/microservices), not this directory.
# The previous Dockerfile used the service directory as context and ran ./mvnw, but the
# parent pom and the wrapper both live one level up, so the image could never build.
#
#   docker build -f map-service/Dockerfile .
#
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Poms first, so the dependency layer is cached until a pom actually changes.
# Every module pom is copied because Maven refuses to read a parent whose <modules>
# point at directories that do not exist.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY semaphore-service/pom.xml semaphore-service/
COPY schedule-service/pom.xml schedule-service/
COPY map-service/pom.xml map-service/

RUN ./mvnw -B -ntp -pl map-service -am dependency:go-offline -DskipTests || true

COPY common/src common/src
COPY map-service/src map-service/src

# -am also builds `common`, which map-service depends on.
# Tests need Docker (Testcontainers) and do not run inside the image build; CI runs them.
RUN ./mvnw -B -ntp -pl map-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S kapp && adduser -S kapp -G kapp
COPY --from=builder /build/map-service/target/*.jar app.jar
USER kapp

EXPOSE 8085

# MaxRAMPercentage makes the JVM respect the container's mem_limit instead of sizing
# its heap from the host's total RAM. This matters: seven services share one laptop.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
