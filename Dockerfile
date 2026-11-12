# Runs one of the built jars: docker build --build-arg JAR=integration-app/target/integration-app-0.1.0-SNAPSHOT.jar .
FROM eclipse-temurin:21-jre
ARG JAR
RUN useradd --system --uid 10001 app && mkdir -p /logs && chown app /logs
COPY ${JAR} /app/app.jar
USER app
WORKDIR /app
# Logs (and with them the audit lines) are written to stdout and to the /logs volume; credentials come from the
# environment, never from the image.
ENV INTEGRATION_LOG_DIR=/logs
VOLUME ["/logs"]
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
