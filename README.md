# app-conduit

Lightweight, reusable integration framework (Spring Boot + Apache Camel) with a sample integration application.
The architecture document, the requirements and the numbered decision records (ADRs) are maintained separately, in a private repository (project names: ADR 0013).
New integration application (project setup, STS, build, deployment): [docs/deployment-guide.md](docs/deployment-guide.md).

## Status

Revision 2 (Camel-native, lean) is implemented: contract-first OpenAPI, Camel YAML routes with one connector per target system,
a shared error rule, request tracking and audit through Camel constructs, Spring Security, structured logging, a two-transport proof
(test-only `appx.getBom`), a container image and an end-to-end script. The structure is described in the sections below.

## Prerequisites

- JDK 21 (Temurin)
- Nothing else: Maven is provided by the wrapper (`./mvnw`)

Versions: Camel 4.22.2 (LTS) on Spring Boot 4.1.1, fixed in the parent `pom.xml`.

## Build and test

```bash
./mvnw verify                 # unit, integration and architecture tests, builds the jars
./scripts/e2e.sh              # end-to-end run of the built jars (starts both apps on :18080/:18081, throwaway credentials)
```

Convention tests run in the same build and enforce the architecture rules: `ArchitectureTest` (Java: only
connectors use HTTP clients or vendor SDKs, the framework never depends on application code, no controllers) and
`RouteConventionTest` (YAML routes: transport URIs only in `camel/connectors/`, capability endpoints set `targetApp`, operation routes call
only capability endpoints and have no error handling, no asynchronous steps). Developer-only routes with `http:` go under
`src/test/resources/camel/`; they are not packaged and not checked. The test-only `appx.getBom` connector there shows one capability
reachable over two transports (`integration.connectors.appx.transport`, per-capability override `integration.connectors.appx.getBom.transport`).

## Run

Credentials are required at startup (the applications refuse to start without them). Copy the example env file and set
real values (`.env` is git-ignored; never commit it). Use the same values for `INTEGRATION_APPB_*` and `APPB_*`:

```bash
cp .env.example .env     # then edit the values
java -jar conduit-appb-stub/target/conduit-appb-stub-0.1.0-SNAPSHOT.jar &            # APP-B stand-in on :8081
java -jar conduit-sample/target/conduit-sample-0.1.0-SNAPSHOT.jar    # integration application on :8080
curl localhost:8080/actuator/health        # {"status":"UP"...}
curl -i localhost:8080/actuator/health | grep -i x-request-id   # X-Request-Id: INTX-<12 alphanumeric>
```

## Configuration

| Variable | Default | Meaning |
|----------|---------|---------|
| `INTEGRATION_APP_SHORT_NAME` | `INTX` | Shortname of the integration application (alphanumeric, no hyphens), used in request ids and logs (`service`) |
| `INTEGRATION_INBOUND_USER` / `INTEGRATION_INBOUND_PASSWORD` | none (required) | Account callers (APP-A) use for inbound calls |
| `INTEGRATION_OPS_USER` / `INTEGRATION_OPS_PASSWORD` | none (required) | Separate operations account for `/actuator/info` and `/actuator/metrics` |
| `INTEGRATION_APPB_USER` / `INTEGRATION_APPB_PASSWORD` | none (required) | Credentials the APP-B connector uses towards APP-B (`integration.connectors.appb.auth.*`) |
| `INTEGRATION_APPB_URL` | `http://localhost:8081` | APP-B base URL (`integration.connectors.appb.base-url`) |
| `INTEGRATION_APPB_TIMEOUT_MS` | `10000` | Outbound timeout for APP-B in milliseconds (`integration.connectors.appb.timeout-ms`) |
| `APPB_USER` / `APPB_PASSWORD` | none (required, stub only) | Accounts the APP-B stub accepts |
| `INTEGRATION_LOG_DIR` | `logs` | Directory of the rolling log file `integration.log` |
| `INTEGRATION_LOG_MAX_FILE_SIZE` / `INTEGRATION_LOG_MAX_HISTORY` / `INTEGRATION_LOG_TOTAL_SIZE_CAP` | `20MB` / `14` / `500MB` | Log rotation limits (`logging.logback.rollingpolicy.*`); the retention is the audit retention |

## Concurrency

Requirement NFR-13: concurrent requests are independent, so a slow target does not stall the others. Requests run on virtual
threads (JDK 21, `spring.threads.virtual.enabled=true`), so no pool needs sizing; a slow target only parks a thread, and a request
holds it at most for the per-target timeout (`INTEGRATION_APPB_TIMEOUT_MS`, default 10 s). Nothing in the tracing depends on the
thread: the request id and operation travel on the Camel exchange. `ParallelRequestsTest` proves 50 slow requests finish in about one
slow time with their own ids. The outbound HTTP connection pool of Camel's http component (`camel.component.http.*` in
`application.yml`, 100 connections per target) is sized so it is not the ceiling either; its default of 20 was.
Graceful shutdown is on; Tomcat's own limits (`server.tomcat.threads.max`, `server.tomcat.max-connections`, `server.tomcat.accept-count`)
bound the front door and can be set as usual Spring Boot properties.

## Modules

- `conduit-framework`: the reusable framework (library)
- `conduit-sample`: sample integration application built on the framework (APP-A calls it, it calls APP-B)
- `conduit-appb-stub`: stand-in for APP-B (called application) for demos and end-to-end runs

## Request tracking

Every inbound request gets a request id `<APP>-<12 random alphanumeric>` (e.g. `INTX-x7Gq2mK9pR4t`), always generated by the
integration application (a caller supplied `X-Request-Id` is ignored). It is returned in the `X-Request-Id` response header
and shown on every log line written while the request is processed (Camel's MDC service, key `requestId`).

## Audit

Every inbound request except `/actuator/**` writes exactly two structured log lines on the logger `audit`:
`request.started` (status `STARTED`) and `request.completed` (status `SUCCESS` for HTTP below 400, otherwise `FAILED`). Both carry
`requestId`, `service`, `operation` (the OpenAPI `operationId`, `UNKNOWN` when no operation was reached, for example a 401), `status`; the
completed line adds `httpStatus`, `durationMs` and, for failures, `errorCode`. There is no database: the retention of the log files is the
audit retention, and a request interrupted by a restart is a `request.started` line without a `request.completed` line. Camel writes them (`RequestAuditNotifier`); requests that never reach Camel (401, unknown path) get both lines from `ErrorResponseWriter`.

```bash
grep '"logger_name":"audit"' logs/integration.log | grep INTX-x7Gq2mK9pR4t
```

## Error responses

Failures return the standard body (status is always `FAILED`; the HTTP code tells the kind of failure) and the `request.completed`
audit line carries the same `errorCode`:

```json
{"requestId":"INTX-x7Gq2mK9pR4t","status":"FAILED","errorCode":"APPB_TIMEOUT","message":"APPB timed out"}
```

| HTTP | errorCode | Meaning |
|------|-----------|---------|
| 400 | `INVALID_REQUEST` | request does not satisfy the contract |
| 401 | `AUTH_FAILED` | authentication failure |
| 404 | `UNKNOWN_OPERATION` | no operation in the contract matches |
| 404 | `<TARGET>_NOT_FOUND` | the target reports the resource does not exist |
| 502 | `<TARGET>_UNAVAILABLE` | the target returned any other error or is unreachable |
| 504 | `<TARGET>_TIMEOUT` | the target timed out |
| 500 | `INTERNAL_ERROR` | internal error (details are logged, never returned) |

`ErrorMapper` is the one place that classifies a failure. Connectors only fail (with an exception); the shared Camel error rule
(`IntegrationErrors`) and `ErrorResponseWriter` build the response. Try: `curl -i -u <inbound user>:<password> localhost:8080/api/v1/unknown`
returns 404 `UNKNOWN_OPERATION`.

## Logging

One structured (JSON, Spring Boot `logstash` format) log stream per application: stdout and one rolling file
`logs/integration.log` (git-ignored). Every line carries `service` (the integration application shortname) and `logger_name`; every line
written while a request is processed also carries `requestId` and `operation`. Connector lines use the logger `connector.<system>`
(for example `connector.appb`), so lines can be filtered per connector from the one stream:

```bash
grep INTX-x7Gq2mK9pR4t logs/integration.log                         # follow one request across framework and connector
grep '"logger_name":"connector.appb"' logs/integration.log          # APP-B connector lines only
```

The file rotates by size and history and is bounded by a total size cap (`INTEGRATION_LOG_*`, see Configuration). In containers collect
stdout or mount `/logs`; without one of the two a container restart loses the audit trail. Passwords, tokens and request or response
bodies are never logged; Camel's message tracer is off.

## Security

Userid-password (HTTP Basic) with Spring Security, two filter chains (JWT bearer is future). Credentials come from the
environment or the `.env` file, never from the repository or the image.

| Endpoint | Access |
|----------|--------|
| `/actuator/health` | open (container health probes) |
| `/actuator/info`, `/actuator/metrics` | operations account |
| everything else (`/api/**`) | inbound account |

Other actuator endpoints are not exposed; there is no Prometheus endpoint in revision 2. Camel route metrics are part of
`/actuator/metrics`. Missing or invalid credentials return `401` with `AUTH_FAILED` (and audit lines). The caller's `Authorization` and
`X-Request-Id` headers never reach a target system. Passwords and tokens are never logged.

```bash
curl -i -u caller:<password> localhost:8080/api/v1/unknown          # 404 (authenticated)
curl -i localhost:8080/api/v1/unknown                               # 401 AUTH_FAILED
curl -u ops:<password> localhost:8080/actuator/metrics              # operations account
```

## Sample integration: getSomeData

APP-A -> `GET /api/v1/getSomeData` (inbound account) -> contract `openapi/integration-api.yaml` (served by Camel) -> operation route
`direct:getSomeData` (`camel/operations/get-some-data.yaml`) -> capability endpoint `direct:appb.getSomeData` of the APP-B connector
(`camel/connectors/appb/appb-get-some-data.yaml`) -> APP-B `GET /api/data`.

```bash
curl -i -u <inbound user>:<password> localhost:8080/api/v1/getSomeData
# 200 {"data":"hello from APP-B"} + X-Request-Id: INTX-...
# stop the APP-B stub and call again: 502 {"...","errorCode":"APPB_UNAVAILABLE",...}
```

- The same request id is sent to APP-B as `X-Request-Id` and appears in the connector log lines and in both audit lines
  (operation `getSomeData`).
- APP-B timeout returns `504 APPB_TIMEOUT` (set `INTEGRATION_APPB_TIMEOUT_MS`, e.g. `1000`, and call the stub with a delay).
- The APP-B stub simulates failures with query parameters on `/api/data` (`?delayMs=3000`, `?fail=true`), for direct checks:
  `curl -u <appb user>:<password> 'localhost:8081/api/data?fail=true'`.
- Operation routes and connectors have no error handling of their own (failures reach the shared error rule) and contain no audit or
  request-id code. Only the connector talks to APP-B.

To add another operation or application, follow [docs/deployment-guide.md](docs/deployment-guide.md) (project setup); the sample operation under "Sample integration" shows the contract, the operation route and the connector.

## Container and on-premise

The same configuration works from a plain JVM jar (on-premise, see Run) and from containers. The image runs as a non-root
user; the logs (and with them the audit lines) live in the `/logs` volume. Credentials are passed as environment
variables, never baked into the image. `/actuator/health` is open for health probes.

```bash
./mvnw verify                          # builds the jars the image copies
cp .env.example .env                   # set the values (same for INTEGRATION_APPB_* and APPB_*)
docker compose up --build -d           # conduit-sample on :8080, conduit-appb-stub internal
curl -u <inbound user>:<password> localhost:8080/api/v1/getSomeData
docker compose down                    # add -v to also delete the log volume
```

A single image is built per jar with `docker build --build-arg JAR=<path to jar> .` (see `Dockerfile`).
Revision 2 runs as a single instance; Kubernetes/OpenShift and multi-instance are out of scope.

## Not in revision 2 (future)

Prometheus export, JWT bearer tokens, vault-based secrets, retry/circuit-breaker, a queryable audit store, multi-instance
(kept out deliberately to stay lean).
