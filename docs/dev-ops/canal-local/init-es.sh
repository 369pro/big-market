#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ES_URL=${ES_URL:-http://127.0.0.1:9200}
INDEX=big_market.user_raffle_order

if curl -fsS -o /dev/null "$ES_URL/$INDEX"; then
  printf 'Elasticsearch index already exists: %s\n' "$INDEX"
else
  curl -fsS -X PUT "$ES_URL/$INDEX" \
    -H 'Content-Type: application/json' \
    --data-binary "@$SCRIPT_DIR/es-mapping.json"
  printf '\nCreated Elasticsearch index: %s\n' "$INDEX"
fi

curl -fsS "$ES_URL/$INDEX/_mapping"
printf '\n'
