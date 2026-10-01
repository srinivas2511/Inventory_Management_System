# syntax=docker/dockerfile:1
# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---- runtime: slim JRE, non-root ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S ims && adduser -S ims -G ims
WORKDIR /app
COPY --from=build /build/target/ims-backend-*.jar /app/app.jar
USER ims
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
