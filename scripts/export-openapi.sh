#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
PORT=${PORT:-9090}
OUT_FILE=${OUT_FILE:-"$ROOT_DIR/openapi.yaml"}
JAR_FILE=${JAR_FILE:-"$ROOT_DIR/target/cheappInv-0.0.1-SNAPSHOT.jar"}

if [[ ! -f "$JAR_FILE" ]]; then
  echo "Jar no encontrado en $JAR_FILE. Ejecuta: ./mvnw -DskipTests package" >&2
  exit 1
fi

LOG_FILE=${LOG_FILE:-/tmp/cheappInv-openapi.log}

# Arranca en background
java -jar "$JAR_FILE" --server.port="$PORT" >"$LOG_FILE" 2>&1 &
PID=$!

echo "Servicio arrancando (pid=$PID, port=$PORT)..."

cleanup() {
  kill "$PID" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Espera a health
for i in {1..80}; do
  if curl -fsS "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

# Exporta YAML
curl -fsS "http://localhost:$PORT/v3/api-docs.yaml" -o "$OUT_FILE"

echo "OpenAPI exportado a: $OUT_FILE"
