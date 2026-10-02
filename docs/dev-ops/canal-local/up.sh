#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$SCRIPT_DIR/prepare.sh"
exec docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d "$@"
