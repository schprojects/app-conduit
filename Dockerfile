# Runs one of the built jars: docker build --build-arg JAR=integration-app/target/integration-app-0.1.0-SNAPSHOT.jar .
FROM eclipse-temurin:21-jre
ARG JAR
RUN useradd --system --uid 10001 app && mkdir -p /data /logs && chown app /data /logs
COPY ${JAR} /app/app.jar
USER app
WORKDIR /app
# Audit database and logs live in volumes; credentials come from the environment, never from the image.
ENV INTEGRATION_DB_PATH=/data/integration INTEGRATION_LOG_DIR=/logs
VOLUME ["/data", "/logs"]
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
