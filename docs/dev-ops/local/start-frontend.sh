#!/bin/sh
set -eu

# Keep platform-specific dependencies and generated files in Docker volumes.
tar -C /source --exclude='./node_modules' --exclude='./.git' --exclude='./.next' --exclude='./dist' --exclude='./src/.umi' --exclude='./src/.umi-production' -cf - . | tar -C /app -xf -
cd /app
lock_hash=$(sha256sum package-lock.json | cut -d ' ' -f 1)
if [ ! -f node_modules/.local-lock-hash ] || [ "$(cat node_modules/.local-lock-hash)" != "$lock_hash" ]; then
    npm ci --legacy-peer-deps --no-audit --no-fund
    printf '%s' "$lock_hash" > node_modules/.local-lock-hash
fi

if [ "$1" = chat ]; then
    exec npm run dev -- --hostname 0.0.0.0
fi
exec npm start
