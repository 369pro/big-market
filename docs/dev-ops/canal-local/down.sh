#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec docker compose -f "$SCRIPT_DIR/docker-compose.yml" down "$@"
