# Local MySQL → Canal → Elasticsearch

This directory is a self-contained local overlay for the order read model. It does not edit the shared `docker-compose-local.yml` or the existing dirty `docs/dev-ops/canal-adapter/application.yml`. The overlay reuses the tracked `docs/dev-ops/canal-adapter/es7` mapping files read-only; those eight files map the eight sharded `user_raffle_order_*` tables to the single `big_market.user_raffle_order` index and use `order_id` as the Elasticsearch document ID.

The overlay uses the official `canal/canal-server:v1.1.7` image and the project’s existing `coopersoft/canal-adapter:v1.1.6` image. Both containers reach the already running host-published MySQL and Elasticsearch through `host.docker.internal`. `prepare.sh` reads `docs/dev-ops/local/mysql.env` for the adapter and smoke-test connection, then creates the ignored, mode `0600` runtime Canal properties file. Canal Server 1.1.7 cannot authenticate the local MySQL `root` account’s `caching_sha2_password` protocol, so the runtime file reuses the existing `canal@%` replication account from the tracked local Canal properties; set `CANAL_MYSQL_USERNAME` and `CANAL_MYSQL_PASSWORD` when those local credentials differ. No runtime credentials are copied into this overlay.

## Start and backfill

```bash
cd /Users/tsy/WorkSpace/LLM-market/big-market
docs/dev-ops/canal-local/up.sh
docs/dev-ops/canal-local/init-es.sh
docs/dev-ops/canal-local/backfill.sh
docs/dev-ops/canal-local/verify.sh
```

The first backfill imports current rows from all eight sharded tables. It is idempotent because each mapping uses `order_id` as `_id`. The adapter endpoint is available at `http://127.0.0.1:18082`; Canal Server listens at `127.0.0.1:11111`.

Canal’s `conf/example/meta.dat` is kept in the named `canal-conf` volume. `down.sh` removes only the containers and network, so a later `up.sh` keeps the binlog checkpoint while copying the freshly rendered credentials into the same volume. For a clean local restart, recreate both services with `down.sh` followed by `up.sh`; this also lets the adapter establish a new TCP session against Canal Server.

## CDC smoke test

```bash
docs/dev-ops/canal-local/cdc-smoke.sh
```

The smoke test creates two generated `T<unix-time>A/B` rows in the local `big_market_01.user_raffle_order_000` table, waits for both inserts, updates one row and waits for the updated state, then deletes both rows and waits for both Elasticsearch documents to disappear. The `EXIT` trap removes only those generated IDs if an intermediate assertion fails.

## Stop

```bash
docs/dev-ops/canal-local/down.sh
```

The index is intentionally never deleted by these scripts. If an index already exists, `init-es.sh` only inspects it and leaves its mapping and data untouched.

The explicit mapping keeps identifiers exact (`keyword`), IDs numeric (`long`), order state exact (`keyword`), and timestamps as dates. This matches the SQL projection expected by `big-market-app` (`_user_id`, `_activity_id`, `_activity_name`, `_strategy_id`, `_order_id`, `_order_time`, `_order_state`, `_create_time`, `_update_time`).
