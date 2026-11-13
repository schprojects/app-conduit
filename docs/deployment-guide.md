# Setup and deployment guide

How to set up a development environment for a new integration application built on the framework, how to build the deliverables and
how to deploy them. Every command in this guide was run on a clean example project (`orders-integration`, shortname `ORDX`, called system
`orders`) against the sample APP-B stand-in of this repository; replace the example names with yours. Steps that depend on tools that were not
available when this was written are marked **not verified**.

For the design see [architecture.md](../architecture.md); for running this repository see the [README](../README.md).

## 1. Prerequisites

- JDK 21 (Temurin) and git
- Docker, only to build and run the container image
- Maven is not needed: the Maven wrapper (`mvnw`, `.mvn/`) of this repository is copied into each project
- Access to the framework: this repository, or the artifact `com.appconduit:conduit-framework` in your organisation's Maven repository

## 2. Make the framework available to Maven

From a clone of this repository:

```bash
./mvnw -pl conduit-framework -am install -DskipTests    # installs the framework and the parent POM into ~/.m2
```

If your organisation hosts the framework in an internal Maven repository, add that repository to `~/.m2/settings.xml` and skip this step
(**not verified**: this repository has no such repository).

## 3. Create the project

An integration application is a small Maven project that depends on the framework. Layout:

```text
orders-integration/
  mvnw, .mvn/                          copied from this repository (Maven wrapper)
  pom.xml
  Dockerfile, .dockerignore            Dockerfile from this repository; .dockerignore holds "*" and "!target/*.jar"
  .gitignore                           .env, *.env, logs/, target/  (credentials and logs are never committed)
  .env.example                         variable names with "change-me" values (the real .env is not committed)
  README.md                            the run procedure of this application
  src/main/java/com/example/orders/OrdersIntegrationApplication.java
  src/main/resources/application.yml
  src/main/resources/openapi/integration-api.yaml            the contract: every inbound operation
  src/main/resources/camel/rest.yaml                         copied unchanged
  src/main/resources/camel/operations/get-order-data.yaml    operation routes
  src/main/resources/camel/connectors/orders/orders-get-order-data.yaml   connector capabilities
  src/test/resources/application.properties                  test credentials
  src/test/java/com/example/orders/...                       your tests and the convention tests
```

**`pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
  </parent>

  <groupId>com.example.orders</groupId>
  <artifactId>orders-integration</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <java.version>21</java.version>
    <camel.version>4.22.2</camel.version>
    <conduit-framework.version>0.1.0-SNAPSHOT</conduit-framework.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.apache.camel.springboot</groupId>
        <artifactId>camel-spring-boot-bom</artifactId>
        <version>${camel.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>com.appconduit</groupId>
      <artifactId>conduit-framework</artifactId>
      <version>${conduit-framework.version}</version>
    </dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-spring-boot-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-platform-http-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-rest-openapi-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-yaml-dsl-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-http-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-direct-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-micrometer-starter</artifactId></dependency>
    <dependency><groupId>org.apache.camel.springboot</groupId><artifactId>camel-mdc-starter</artifactId></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.tngtech.archunit</groupId>
      <artifactId>archunit-junit5</artifactId>
      <version>1.5.1</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

Keep the Spring Boot and Camel versions equal to those in this repository's parent `pom.xml`; that is how the pinned Camel LTS line is kept.

**Main class**: only `@SpringBootApplication` and `SpringApplication.run`, as in `conduit-sample`.

**`application.yml`**: copy `conduit-sample/src/main/resources/application.yml` and change only `spring.application.name`, the default
`INTEGRATION_APP_SHORT_NAME` (alphanumeric, no hyphens), and the `integration.connectors.<system>` block (one block per called system, with the
variables `INTEGRATION_<SYSTEM>_URL`, `_TIMEOUT_MS`, `_USER`, `_PASSWORD`). Logging, threads, Camel MDC, HTTP pool and actuator are shared
policy and stay as they are.

**Contract, routes and connector**: start from `conduit-sample` (`openapi/integration-api.yaml`, `camel/operations/get-some-data.yaml`,
`camel/connectors/appb/appb-get-some-data.yaml`). In the connector file rename the route ids, the `direct:` endpoints, `targetApp`, the
`logName` (`connector.<system>`) and the `integration.connectors.<system>.*` placeholders, and set the path and method. The operation
route calls only `direct:<system>.<capability>` endpoints.

**Tests**: `src/test/resources/application.properties` holds dummy credentials and an unreachable URL for each called system (copy
`conduit-sample`'s file and rename the connector keys); without them the context does not start. Copy `ArchitectureTest` and
`RouteConventionTest` from `conduit-sample/src/test/java/.../architecture/`, replace the package name `com.appconduit.sample` with yours
(in `ArchitectureTest` also `importPackages(...)`), and delete the rule `frameworkDoesNotDependOnApplicationCode`, which belongs to the
framework build. Add a test that starts the application and checks `/actuator/health` (see `ApplicationStartsTest`).

Verify:

```bash
./mvnw verify          # BUILD SUCCESS; produces target/orders-integration-1.0.0-SNAPSHOT.jar
```

The example project ran 10 tests (1 start-up test, 2 and 7 convention tests).

## 4. Development environment with STS (Spring Tools)

The Maven parts are verified; the STS menus follow the tool's standard behaviour and were **not verified** (STS was not installed).

1. Install JDK 21. In STS: Window > Preferences > Java > Installed JREs, add JDK 21 and make it the default; set the compiler compliance to 21.
2. File > Import > Maven > Existing Maven Projects, choose the project folder, Finish. The framework must already be in `~/.m2` (section 2),
   otherwise the dependency shows as unresolved.
3. Credentials: copy `.env.example` to `.env` **in the project folder** and set the values. The application reads `./.env` relative to its
   working directory, and STS starts it with the project folder as the working directory.
4. Run: right click the main class > Run As > Spring Boot App (or the Boot Dashboard). Route and contract files are not hot reloaded; restart
   after a change. Program argument `--server.port=<port>` changes the port (default 8080).
5. Tests: right click the project > Run As > JUnit Test, or `./mvnw verify` in a terminal; the terminal build is the one that counts.

Without an IDE: `./mvnw verify`, then `java -jar target/<artifact>.jar` in the folder that holds `.env` (verified).

## 5. Run against a stand-in of the called system

Until the real system is reachable, point `INTEGRATION_<SYSTEM>_URL` at a stand-in. This repository's `conduit-appb-stub` answers
`GET /api/data` with Basic authentication. Build it once in a clone of this repository (`./mvnw verify`), then:

```bash
APPB_USER=ordersu APPB_PASSWORD=ordersp \
  java -jar conduit-appb-stub/target/conduit-appb-stub-0.1.0-SNAPSHOT.jar --server.port=18091 &
# .env of the application: INTEGRATION_ORDERS_USER=ordersu, INTEGRATION_ORDERS_PASSWORD=ordersp, INTEGRATION_ORDERS_URL=http://localhost:18091
java -jar target/orders-integration-1.0.0-SNAPSHOT.jar --server.port=18090
curl -i -u <inbound user>:<password> localhost:18090/api/v1/getOrderData    # 200, the data and X-Request-Id: ORDX-<12 characters>
curl -i localhost:18090/api/v1/getOrderData                                 # 401
curl -u <ops user>:<password> localhost:18090/actuator/metrics              # operations account
```

`logs/integration.log` then holds the audit lines (`request.started`, `request.completed`) and the connector line (`connector.orders`).

## 6. Build the deliverables

- `./mvnw verify` runs every test and builds the runnable jar `target/<artifactId>-<version>.jar`. Do not deliver when it fails.
- Set a release version (`./mvnw versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false`), build again and deliver exactly that jar. The jar
  contains no credentials and no `.env` (verified: the example produced `orders-integration-1.0.0.jar`).
- Build the container image from the verified jar, never from sources:

  ```bash
  docker build --build-arg JAR=target/orders-integration-1.0.0.jar -t orders-integration:1.0.0 .
  ```

  The image runs as the non-root user `app`, exposes 8080 and keeps the logs in the `/logs` volume (verified).
- Record the framework version (`conduit-framework.version` in the `pom.xml`) in the release notes.

## 7. Configuration of a deployment

- Everything is an environment variable (names in the README of the application): per-system URL, timeout and credentials, the inbound
  and operations accounts, the shortname, the log directory and the log rotation. Create the values per environment in its secret store or in an
  `.env` file; they never go into the jar, the image or git.
- The application refuses to start when a credential is missing. That is the first check of a deployment.

## 8. Deploy on a JVM

- Needs JDK 21 and a service account. Copy the jar and an `.env` (file mode 600, owned by the service account) into one folder and start it
  from that folder: `java -jar orders-integration-1.0.0.jar` (verified; the folder receives `logs/integration.log`).
- Without an `.env`, set the same variables in the environment of your service manager (**not verified**: no service definition is part of
  this repository).
- Set `INTEGRATION_LOG_DIR` to a directory with enough space; rotation and retention follow `INTEGRATION_LOG_*`. The retention is the audit retention.
- Stop with SIGTERM; graceful shutdown is configured (`server.shutdown=graceful`).

## 9. Deploy as a container

```bash
docker run -d --name orders-integration -p 8080:8080 --env-file .env -v orders-logs:/logs orders-integration:1.0.0
```

Verified with `--network host` and `--env-file`; the logs volume held `integration.log`. Keep the file for `--env-file` outside the image build
context and outside git; its lines are `NAME=value` without quotes. Several applications run side by side, each with its own image, `.env`, port
and logs volume. Pushing the image to a registry (`docker tag`, `docker push`) is **not verified**.

## 10. Verify a deployment

- `curl localhost:8080/actuator/health` returns `{"status":"UP"...}` (health is open).
- `curl -u <inbound user>:<password> localhost:8080/api/v1/<operation>` returns 200 and an `X-Request-Id`; without credentials it returns 401.
- One `request.started` and one `request.completed` line per call appear in `logs/integration.log` (`/logs/integration.log` in the container).
- `curl -u <ops user>:<password> localhost:8080/actuator/metrics` answers (operations account).
- Roll back by starting the previous jar or image tag with the same configuration; there is no database to migrate.

## 11. Common problems

| Symptom | Cause | Fix |
|---------|-------|-----|
| `docker build` fails: `target/<jar>: not found` | `.dockerignore` excludes everything except the jars of this repository | Use `*` followed by `!target/*.jar` in your project |
| Application refuses to start, credential missing | `.env` is not in the working directory, or a variable is empty | Put `.env` next to the jar (or in the project folder in STS), or set the variables in the environment |
| Application will not start, a contract operation has no route | Every `operationId` needs a `direct:<operationId>` route | Add the route or fix the name (camelCase, exact) |
| Dependency `conduit-framework` not found | The framework is not in `~/.m2` | Section 2 |
| Container cannot reach the called system | `localhost` inside the container is the container | Use a reachable host name or `--network host` |
