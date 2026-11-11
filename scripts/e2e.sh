#!/usr/bin/env bash
# End-to-end check of the running jars (build first: ./mvnw verify).
# Starts APP-B stub + integration application with throwaway credentials, database and logs in a temp dir.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
APP_PORT=18080
STUB_PORT=18081
PW="$(head -c 12 /dev/urandom | base64 | tr -dc 'A-Za-z0-9')"
FAILED=0
PIDS=()

cleanup() { for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null; done; rm -rf "$TMP"; }
trap cleanup EXIT

check() { # description expected actual
  if [ "$2" == "$3" ]; then echo "PASS  $1"; else echo "FAIL  $1 (expected $2, got $3)"; FAILED=1; fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
wait_for() { for _ in $(seq 1 60); do [ "$(code "$1")" == "200" ] && return 0; sleep 1; done; return 1; }

export APPB_USER=appb APPB_PASSWORD="$PW" SERVER_PORT=$STUB_PORT
( cd "$TMP" && exec java -jar "$ROOT"/app-b-stub/target/app-b-stub-*-SNAPSHOT.jar --server.port=$STUB_PORT >stub.log 2>&1 ) & PIDS+=($!)

export INTEGRATION_APP_SHORT_NAME=INTX INTEGRATION_INBOUND_USER=caller INTEGRATION_INBOUND_PASSWORD="$PW" \
  INTEGRATION_OPS_USER=ops INTEGRATION_OPS_PASSWORD="$PW" INTEGRATION_APPB_USER=appb INTEGRATION_APPB_PASSWORD="$PW" \
  INTEGRATION_APPB_URL=http://localhost:$STUB_PORT INTEGRATION_APPB_TIMEOUT=2s \
  INTEGRATION_DB_PATH="$TMP/data/integration" INTEGRATION_LOG_DIR="$TMP/logs"
( cd "$TMP" && exec java -jar "$ROOT"/integration-app/target/integration-app-*-SNAPSHOT.jar --server.port=$APP_PORT >app.log 2>&1 ) & PIDS+=($!)
APP_PID=${PIDS[1]}

wait_for "http://localhost:$APP_PORT/actuator/health" || { echo "FAIL  application did not start"; cat "$TMP/app.log" | tail -20; exit 1; }
BASE=http://localhost:$APP_PORT

check "health is UP and open"                       200 "$(code $BASE/actuator/health)"
check "api without credentials -> 401"              401 "$(code $BASE/api/v1/getSomeData)"
check "api with wrong credentials -> 401"           401 "$(code -u caller:wrong $BASE/api/v1/getSomeData)"
check "getSomeData with inbound account -> 200"     200 "$(code -u caller:$PW $BASE/api/v1/getSomeData)"
check "unknown operation -> 404"                    404 "$(code -u caller:$PW $BASE/api/v1/nope)"
check "metrics with inbound account -> 401"         401 "$(code -u caller:$PW $BASE/actuator/metrics)"
check "metrics with operations account -> 200"      200 "$(code -u ops:$PW $BASE/actuator/metrics)"
check "other actuator endpoints not exposed -> 404" 404 "$(code -u ops:$PW $BASE/actuator/env)"

RID="$(curl -si -u caller:$PW $BASE/api/v1/getSomeData | tr -d '\r' | awk -F': ' 'tolower($1)=="x-request-id"{print $2}')"
case "$RID" in INTX-????????????) check "request id format" ok ok ;; *) check "request id format" ok "$RID" ;; esac
sleep 1
check "request id in framework log"  1 "$(grep -c "$RID" "$TMP/logs/framework.log" | awk '{print ($1>0)?1:0}')"
check "request id in APP-B log"      1 "$(grep -c "$RID" "$TMP/logs/appb.log" | awk '{print ($1>0)?1:0}')"

kill "${PIDS[0]}"; sleep 1
BODY="$(curl -s -u caller:$PW $BASE/api/v1/getSomeData)"
check "APP-B down -> 502 APPB_UNAVAILABLE" 1 "$(echo "$BODY" | grep -c '"errorCode":"APPB_UNAVAILABLE"')"

kill "$APP_PID"; wait "$APP_PID" 2>/dev/null
[ -f "$TMP/data/integration.mv.db" ] && check "audit database file persisted" ok ok || check "audit database file persisted" ok missing

[ $FAILED -eq 0 ] && echo "E2E OK" || echo "E2E FAILED"
exit $FAILED
