#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)
ENV_FILE="$REPO_DIR/docs/dev-ops/local/mysql.env"
ES_URL=${ES_URL:-http://127.0.0.1:9200}
INDEX=big_market.user_raffle_order
MYSQL_HOST=${MYSQL_HOST:-127.0.0.1}
MYSQL_PORT=${MYSQL_PORT:-3306}
MYSQL_DB=big_market_01
MYSQL_TABLE=user_raffle_order_000

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
: "${LOCAL_MYSQL_USERNAME:?LOCAL_MYSQL_USERNAME is missing from mysql.env}"
if [ "${LOCAL_MYSQL_PASSWORD+x}" != x ]; then
  printf 'LOCAL_MYSQL_PASSWORD is missing from mysql.env\n' >&2
  exit 1
fi

if ! curl -fsS -o /dev/null "$ES_URL/$INDEX"; then
  printf 'Missing index %s; run init-es.sh first.\n' "$INDEX" >&2
  exit 1
fi

stamp=$(date +%s)
order_a="T${stamp}A"
order_b="T${stamp}B"
user_id="canal_test_${stamp}"
cleanup() {
  MYSQL_PWD="$LOCAL_MYSQL_PASSWORD" mysql --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
    -u "$LOCAL_MYSQL_USERNAME" -NBe \
    "DELETE FROM ${MYSQL_DB}.${MYSQL_TABLE} WHERE order_id IN ('${order_a}','${order_b}');" \
    >/dev/null 2>&1 || true
}
trap cleanup EXIT

count=$(MYSQL_PWD="$LOCAL_MYSQL_PASSWORD" mysql --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
  -u "$LOCAL_MYSQL_USERNAME" -NBe \
  "SELECT COUNT(*) FROM ${MYSQL_DB}.${MYSQL_TABLE} WHERE order_id IN ('${order_a}','${order_b}');")
[ "$count" = 0 ] || { printf 'Generated test order id already exists.\n' >&2; exit 1; }

printf 'Inserting test orders %s and %s into %s.%s\n' "$order_a" "$order_b" "$MYSQL_DB" "$MYSQL_TABLE"
MYSQL_PWD="$LOCAL_MYSQL_PASSWORD" mysql --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
  -u "$LOCAL_MYSQL_USERNAME" -NBe \
  "INSERT INTO ${MYSQL_DB}.${MYSQL_TABLE}
   (user_id, activity_id, activity_name, strategy_id, order_id, order_time, order_state, create_time, update_time)
   VALUES
   ('${user_id}', 900001, 'Canal smoke test', 900001, '${order_a}', NOW(), 'create', NOW(), NOW()),
   ('${user_id}', 900001, 'Canal smoke test', 900001, '${order_b}', NOW(), 'create', NOW(), NOW());"

wait_source() {
  local id=$1 expected=$2 body
  for _ in $(seq 1 60); do
    body=$(curl -fsS "$ES_URL/$INDEX/_doc/$id" 2>/dev/null || true)
    if [ "$expected" = present ] && printf '%s' "$body" | jq -e --arg id "$id" '.found == true and ._source._order_id == $id' >/dev/null 2>&1; then
      return 0
    fi
    if [ "$expected" = used ] && printf '%s' "$body" | jq -e '.found == true and ._source._order_state == "used"' >/dev/null 2>&1; then
      return 0
    fi
    if [ "$expected" = absent ] && [ -z "$body" ]; then
      return 0
    fi
    if [ "$expected" = absent ] && printf '%s' "$body" | jq -e '.found == false' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  printf 'Timed out waiting for %s (%s)\n' "$id" "$expected" >&2
  return 1
}

wait_source "$order_a" present
wait_source "$order_b" present
printf '%s\n' 'Insert propagation verified.'

printf 'Updating %s\n' "$order_a"
MYSQL_PWD="$LOCAL_MYSQL_PASSWORD" mysql --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
  -u "$LOCAL_MYSQL_USERNAME" -NBe \
  "UPDATE ${MYSQL_DB}.${MYSQL_TABLE} SET order_state='used' WHERE order_id='${order_a}';"
wait_source "$order_a" used
printf '%s\n' 'Update propagation verified.'

printf 'Deleting test orders\n'
MYSQL_PWD="$LOCAL_MYSQL_PASSWORD" mysql --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
  -u "$LOCAL_MYSQL_USERNAME" -NBe \
  "DELETE FROM ${MYSQL_DB}.${MYSQL_TABLE} WHERE order_id IN ('${order_a}','${order_b}');"
wait_source "$order_a" absent
wait_source "$order_b" absent
printf '%s\n' 'Delete propagation verified; test rows are gone.'
