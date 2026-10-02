#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ES_URL=${ES_URL:-http://127.0.0.1:9200}
INDEX=big_market.user_raffle_order

printf '%s\n' 'Canal container status:'
docker compose -f "$SCRIPT_DIR/docker-compose.yml" ps
printf '%s\n' 'Canal adapter destinations:'
curl -fsS http://127.0.0.1:18082/destinations
printf '\n%s\n' 'Elasticsearch cluster health:'
curl -fsS "$ES_URL/_cluster/health/$INDEX"
printf '\n%s\n' 'Elasticsearch document count:'
curl -fsS "$ES_URL/$INDEX/_count"
printf '\n%s\n' 'Elasticsearch mapping:'
curl -fsS "$ES_URL/$INDEX/_mapping"
printf '\n'
