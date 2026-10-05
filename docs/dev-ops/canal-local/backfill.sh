#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ADAPTER_URL=${ADAPTER_URL:-http://127.0.0.1:18082}
MAP_DIR="$SCRIPT_DIR/../canal-adapter/es7"

for file in "$MAP_DIR"/*user_raffle_order_*.yml; do
  [ -f "$file" ] || continue
  name=$(basename "$file")
  printf 'Backfilling %s\n' "$name"
  curl -fsS -X POST "$ADAPTER_URL/etl/es7/$name" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data ''
  printf '\n'
done
