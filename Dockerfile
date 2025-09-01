# syntax=docker/dockerfile:1

# 1) Build stage: use JDK to build Spring Boot jar with Gradle Wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar -x test

# 2) Run stage: slim JRE image to run the built jar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENV TZ=Asia/Seoul
RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl ca-certificates \
	&& rm -rf /var/lib/apt/lists/* \
	&& useradd -m -u 10001 appuser \
	&& mkdir -p /app/uploads/posts \
	&& chown -R appuser:appuser /app \
	&& chmod -R 755 /app \
	&& chmod 750 /app/uploads /app/uploads/posts
USER appuser
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
	CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
