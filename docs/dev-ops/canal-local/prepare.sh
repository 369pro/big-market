#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_DIR=$(CDPATH= cd -- "$SCRIPT_DIR/../../.." && pwd)
ENV_FILE="$REPO_DIR/docs/dev-ops/local/mysql.env"
TEMPLATE="$SCRIPT_DIR/canal/instance.properties.template"
TARGET_DIR="$SCRIPT_DIR/runtime/canal"
TARGET="$TARGET_DIR/instance.properties"

if [ ! -r "$ENV_FILE" ]; then
  printf 'Missing local credentials file: %s\n' "$ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a
: "${LOCAL_MYSQL_USERNAME:?LOCAL_MYSQL_USERNAME is missing from mysql.env}"
if [ "${LOCAL_MYSQL_PASSWORD+x}" != x ]; then
  printf 'LOCAL_MYSQL_PASSWORD is missing from mysql.env\n' >&2
  exit 1
fi

# Canal Server 1.1.7 bundles an old MySQL protocol client. The local MySQL
# account root uses caching_sha2_password, while the existing canal@% account
# is the replication account with a compatible authentication plugin. Reuse
# the existing local Canal credentials unless an override is supplied, while
# keeping them out of this overlay and its Git history.
CANAL_PROPERTIES="$REPO_DIR/docs/dev-ops/canal/instance.properties"
CANAL_USERNAME=${CANAL_MYSQL_USERNAME:-$(sed -n 's/^canal\.instance\.dbUsername=//p' "$CANAL_PROPERTIES" | head -n 1)}
CANAL_PASSWORD=${CANAL_MYSQL_PASSWORD:-$(sed -n 's/^canal\.instance\.dbPassword=//p' "$CANAL_PROPERTIES" | head -n 1)}
: "${CANAL_USERNAME:?Canal username is missing; set CANAL_MYSQL_USERNAME}"
if [ "${CANAL_PASSWORD+x}" != x ]; then
  printf 'Canal password is missing; set CANAL_MYSQL_PASSWORD\n' >&2
  exit 1
fi

mkdir -p "$TARGET_DIR"
umask 077
CANAL_USER="$CANAL_USERNAME" CANAL_PASS="$CANAL_PASSWORD" \
  perl -pe 's/__CANAL_USERNAME__/$ENV{CANAL_USER}/g; s/__CANAL_PASSWORD__/$ENV{CANAL_PASS}/g' \
  "$TEMPLATE" > "$TARGET"
chmod 600 "$TARGET"
printf 'Prepared local Canal instance configuration at %s\n' "$TARGET"
