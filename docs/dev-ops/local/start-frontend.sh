#!/bin/sh
set -eu

if [ "$1" = chat ] && [ "${FRONTEND_MODE:-development}" = production ]; then
    # Never deploy the shared .next directory: other builds can use another API.
    node /source/scripts/verify-local-build.cjs /source/.next-local "${NEXT_PUBLIC_API_HOST_URL:?Missing chat API URL}"
    mkdir -p /app/production/.next-local/static /app/production/public
    tar -C /source/.next-local/standalone --exclude='.env*' -cf - . | tar -C /app/production -xf -
    tar -C /source/.next-local/static -cf - . | tar -C /app/production/.next-local/static -xf -
    tar -C /source/public -cf - . | tar -C /app/production/public -xf -
    cd /app/production
    PORT=3002 HOSTNAME=0.0.0.0 exec node server.js
fi

# Keep platform-specific dependencies and generated files in Docker volumes.
tar -C /source --exclude='./node_modules' --exclude='./.git' --exclude='./.next' --exclude='./.next-local' --exclude='./dist' --exclude='./src/.umi' --exclude='./src/.umi-production' -cf - . | tar -C /app -xf -
cd /app
lock_hash=$(sha256sum package-lock.json | cut -d ' ' -f 1)
if [ ! -f node_modules/.local-lock-hash ] || [ "$(cat node_modules/.local-lock-hash)" != "$lock_hash" ]; then
    npm ci --legacy-peer-deps --no-audit --no-fund
    printf '%s' "$lock_hash" > node_modules/.local-lock-hash
fi

if [ "$1" = chat ]; then
    exec npm run dev -- --hostname 0.0.0.0 --port 3002
fi
if [ "$1" = erp-build ]; then
    exec npm run build
fi
if [ "$1" = erp ] && [ "${FRONTEND_MODE:-development}" = production ]; then
    if [ ! -f dist/index.html ]; then
        echo "Build ERP first: docker compose -f docs/dev-ops/docker-compose-local.yml run --rm --no-deps -e NODE_OPTIONS=--max-old-space-size=1024 big-market-erp sh /startup/start-frontend.sh erp-build" >&2
        exit 1
    fi
    # Preview serves built assets and retains this local ERP's mock login APIs.
    # Do not use npm run preview: that script rebuilds on every start.
    exec node node_modules/@umijs/max/bin/max.js preview --host 0.0.0.0 --port 8000
fi
exec npm start
