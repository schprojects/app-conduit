# Integration

Lightweight, reusable integration framework (Spring Boot + Apache Camel) with a sample integration application.
Specs are in `specs/` (`requirements.md`, `high-level-architecture.md`, `integration-guide.md`).

## Status (by phase)

| Phase | Content | State |
|-------|---------|-------|
| 1 | Project skeleton, app starts, health UP | done |

## Prerequisites

- JDK 21 (Temurin)
- Nothing else: Maven is provided by the wrapper (`./mvnw`)

Versions: Camel 4.22.2 (LTS) on Spring Boot 4.1.1, fixed in the parent `pom.xml`.

## Build and test

```bash
./mvnw verify
```

## Run

```bash
java -jar integration-app/target/integration-app-0.1.0-SNAPSHOT.jar
curl localhost:8080/actuator/health        # {"status":"UP"...}
```

## Configuration

| Variable | Default | Meaning |
|----------|---------|---------|
| `INTEGRATION_APP_SHORT_NAME` | `INTX` | Shortname of the integration application (alphanumeric, no hyphens), used in request ids and logs |

## Modules

- `integration-framework`: the reusable framework (library)
- `integration-app`: sample integration application built on the framework
