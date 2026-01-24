#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
PORT=${PORT:-9090}
OUT_FILE=${OUT_FILE:-"$ROOT_DIR/openapi.yaml"}
JAR_FILE=${JAR_FILE:-"$ROOT_DIR/target/cheappInv-0.0.1-SNAPSHOT.jar"}
LOG_FILE=${LOG_FILE:-"/tmp/cheappInv-openapi.log"}

# Normaliza OUT_FILE a ruta absoluta (evita escribir en un cwd inesperado)
if [[ "$OUT_FILE" != /* ]]; then
  OUT_FILE="$ROOT_DIR/$OUT_FILE"
fi

OUT_DIR=$(dirname "$OUT_FILE")
mkdir -p "$OUT_DIR"

if [[ ! -f "$JAR_FILE" ]]; then
  echo "Jar no encontrado en $JAR_FILE. Ejecuta: ./mvnw -DskipTests package" >&2
  exit 1
fi

# Arranca en background
java -jar "$JAR_FILE" --server.port="$PORT" >"$LOG_FILE" 2>&1 &
PID=$!

echo "Servicio arrancando (pid=$PID, port=$PORT)..."

tmp_out="${OUT_FILE}.tmp"

cleanup() {
  kill "$PID" >/dev/null 2>&1 || true
  rm -f "$tmp_out" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Espera a health
for i in {1..80}; do
  if curl -fsS "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
  if (( i == 80 )); then
    echo "No pude levantar el servicio en http://localhost:$PORT (ver log: $LOG_FILE)" >&2
    exit 1
  fi
done

# Exporta YAML (escritura atómica)
curl -fsS "http://localhost:$PORT/v3/api-docs.yaml" -o "$tmp_out"
mv -f "$tmp_out" "$OUT_FILE"

echo "OpenAPI exportado a: $OUT_FILE"
