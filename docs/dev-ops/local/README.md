# Local LLM-market

Run commands from the `big-market` repository root. Docker Desktop must be running.
The sibling directories `chatgpt-data`, `chatgpt-web`, and `big-market-erp` must exist.
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
| Chat UI | http://127.0.0.1:3002 |
| ERP UI | http://127.0.0.1:8000 |
| Chat API | http://127.0.0.1:8091 |
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
After changing frontend sources, restart that frontend container to copy them again.
Backend connection overrides are loaded through environment variables because
the existing application entry points do not forward command-line arguments to Spring.

## Scope and limitations

- Local startup does not validate paid model requests, WeChat integration, or payments.
- The chat backend has no payment channel configured in its development settings.
- The existing chat rebate RPC imports `cn.bugstack.trigger.api.IRebateService`,
  while the market provider exports `com.pro.trigger.api.IRebateService`.
  Payment-to-rebate integration requires a separate API alignment.
- Canal is not part of this compose stack; Elasticsearch order-query indexes are
  not populated automatically. Market activity queries use MySQL and work separately.
- Seed activity dates come from the repository SQL and may need adjustment for
  an actual raffle demonstration.
