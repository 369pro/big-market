# Local LLM-market

Run commands from the `big-market` repository root. Docker Desktop must be running.
The sibling directories `chatgpt-data`, `chatgpt-web`, `agent-service`, and `big-market-erp` must exist.
Start the existing `local-mysql` container on port 3306 before this stack.
MySQL is reused, not created or managed by this compose file. Existing marketing
databases are retained; the missing `openai` database was initialized separately.

## Start and stop

```sh
docker compose -f docs/dev-ops/docker-compose-local.yml up -d
docker compose -f docs/dev-ops/docker-compose-local.yml ps -a
docker compose -f docs/dev-ops/docker-compose-local.yml logs --tail=100 big-market chatgpt-data
docker compose -f docs/dev-ops/docker-compose-local.yml stop
```

Missing images are pulled automatically. Redis, Elasticsearch, RocketMQ,
and frontend dependencies use separate named volumes. Do not use `down -v`
unless you intend to erase this local environment's data.

## Addresses

| Service | Address |
| --- | --- |
| Chat UI | http://localhost:3002 |
| ERP UI | http://127.0.0.1:8000 |
| Chat API | http://127.0.0.1:8091 |
| Python chat service health | http://127.0.0.1:8094/health |
| Market API | http://127.0.0.1:8092 (also 8098 for the existing frontends) |
| Nacos | http://127.0.0.1:8848/nacos |
| Elasticsearch | http://127.0.0.1:9200 |
| MySQL (existing local-mysql) | 127.0.0.1:3306 |
| Redis | 127.0.0.1:16379 |

This compose stack publishes ports only on loopback. The existing MySQL container
retains its own port bindings and credentials. `mysql.env` (git-ignored) supplies
`LOCAL_MYSQL_USERNAME` and `LOCAL_MYSQL_PASSWORD`; the current local account is
root with an empty password. Redis uses password 123456. These settings are for
local development only; do not expose an empty-password MySQL account publicly.
The user frontend supports account/password registration and login. Passwords
are stored as BCrypt hashes; the old `1234` test-code bypass is disabled.
The ERP mock login remains `admin` / `ant.design` and is not production authentication.

Both backends require the same `APP_JWT_SECRET` in `mysql.env` (at least 32 UTF-8
bytes; generate a random value with `openssl rand -hex 32`). Never commit it.
Apply `auth-account.sql` to the existing MySQL instance before first registration;
it creates only the missing `openai.auth_account` table and preserves existing data.
Sessions use the shared `auth_session_` Redis namespace with a string codec and
expire after seven days. Logout revokes the session for both services. Sessions
created before this migration require a new login.

## Rebuild backend artifacts

The compose file expects the market JAR at
`big-market-app/target/big-market-app.jar` and the chat JAR at
`docs/dev-ops/local/artifacts/chatgpt-data-app.jar`.
Use JDK 17. Both backend projects compile with `--release 17` and run on
`eclipse-temurin:17-jre`. XStream is upgraded to 1.4.21 for the chat XML path.

```sh
mvn -Pdev -Dmaven.test.skip=true package
build_dir=$(mktemp -d /tmp/llm-market-chat-build.XXXXXX)
rsync -a --exclude=.git --exclude=target --exclude=/data/ --exclude=.idea ../chatgpt-data/ "$build_dir/"
mvn -f "$build_dir/pom.xml" -Dmaven.test.skip=true clean package
mkdir -p docs/dev-ops/local/artifacts
cp "$build_dir/chatgpt-data-app/target/chatgpt-data-app.jar" docs/dev-ops/local/artifacts/
docker compose -f docs/dev-ops/docker-compose-local.yml up -d --force-recreate big-market chatgpt-data
```

Frontend sources are mounted read-only and copied into Docker volumes at startup.
The chat UI uses a production standalone build to reduce Docker memory usage.
After changing it, run `NEXT_PUBLIC_API_HOST_URL=http://127.0.0.1:8091 npm run build`
from `../chatgpt-web`, then restart `chatgpt-web`. Public API addresses are embedded
at build time. Set `FRONTEND_MODE=development` in the compose service for hot development.
The ERP runs built assets through Umi preview (512 MiB Node heap), retaining
the existing local mock login APIs without a resident webpack dev compiler.
After changing ERP source, rebuild it explicitly, then restart:

```sh
docker compose -f docs/dev-ops/docker-compose-local.yml stop big-market-erp
docker compose -f docs/dev-ops/docker-compose-local.yml run --rm --no-deps -e NODE_OPTIONS=--max-old-space-size=1024 big-market-erp sh /startup/start-frontend.sh erp-build
docker compose -f docs/dev-ops/docker-compose-local.yml up -d --no-deps big-market-erp
```

The build lives in the existing `erp-work` volume. Building needs additional
temporary memory; pause optional tools such as Kibana if Docker is near its
memory budget, then restore them. If a build fails, fix/retry the build before
starting preview. To resume frontend development, set ERP `FRONTEND_MODE` to
`development`, restore `NODE_OPTIONS=--max-old-space-size=1536`, and recreate
only `big-market-erp`. Preview is for this loopback-only local environment;
its mock login is not production authentication.

The marketing backend uses `restart: unless-stopped` to recover from an
unexpected exit. This improves recovery but does not fix memory exhaustion.
Backend connection overrides are loaded through environment variables because
the existing application entry points do not forward command-line arguments to Spring.

## Python chat service

The `agent-service` API and `agent-worker` process are built from the sibling
`agent-service` directory. The browser uses the authenticated Java
`/api/v1/agent/` proxy. Python owns the `ai_agent` database; Java owns the wallet
and its billing ledger in `openai`. Model keys come from the existing ignored
`llm.env` file. Search remains unavailable until a search provider is configured.

Run `python3 docs/dev-ops/local/setup-agent.py` once before starting these
services. It preserves existing config, creates the isolated database account,
and writes two private, git-ignored configuration files. It does not seed
business accounts or change user balances. Keep both generated service keys
identical. Apply `chatgpt-data/docs/dev-ops/environment/sql/agent-billing.sql`
to the existing `openai` database before starting the rebuilt Java backend.

After rebuilding the Java artifact as described above:

```sh
docker compose -f docs/dev-ops/docker-compose-local.yml build agent-service
docker compose -f docs/dev-ops/docker-compose-local.yml up -d --no-deps --force-recreate chatgpt-data agent-service agent-worker
docker compose -f docs/dev-ops/docker-compose-local.yml restart chatgpt-web
curl --fail http://127.0.0.1:8094/health
```

Python tables have a reviewable initial DDL in
`agent-service/migrations/001_initial.sql`; first local startup
also creates missing tables. Closing the browser does not cancel work. Stop
is an explicit action; a worker crash produces an interrupted run after its
lease expires, and a retry creates a new run. Historical imports require user
selection and confirmation and do not create backdated billing records.

## Payment lifecycle events

Apply `chatgpt-data/docs/dev-ops/environment/sql/payment_event_outbox.sql` to
`openai` before starting the rebuilt chat backend. This additive migration does
not backfill historical completed orders. The local Compose stack enables
`payment.events.rocketmq` and topic initialization includes `payment_lifecycle`.

Create private `docs/dev-ops/local/marketing-rpc.env` from
`marketing-rpc.env.example`, using the credential registered for `chatgpt-data`
in the marketing provider's `appTokenMap`. This file is git-ignored. Never put
real credentials in the example file. Recreate `chatgpt-data` after changes.
The consumer uses the current `com.pro.trigger.api.IRebateService` interface.

Payment confirmation writes a PAID event in the order transaction; wallet
fulfillment writes DELIVERED in the wallet transaction. The relay checks broker
SEND_OK before recording SENT. Consumers mark DONE after business handling;
failed or unconfirmed rows remain retryable. Status 3 is a manual pause/review
state, not an automatic dead-letter policy. Inspect outbox failures, RocketMQ
retries/DLQ, and marketing task records together. Do not replay real historical
orders without checking existing grants and rebate business IDs.

## Scope and limitations

- Local startup does not validate paid model requests, WeChat integration, or payments.
- Local Alipay sandbox configuration is imported from the private sibling
  `chatgpt-data/.env.payment`; valid merchant credentials are required.
- Canal is not part of this compose stack; Elasticsearch order-query indexes are
  not populated automatically. Market activity queries use MySQL and work separately.
- Seed activity dates come from the repository SQL and may need adjustment for
  an actual raffle demonstration.
