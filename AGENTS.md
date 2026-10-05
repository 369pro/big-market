# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build all modules
mvn clean install -Pdev

# Build skipping tests
mvn clean install -Pdev -DskipTests

# Run the app (from big-market-app)
mvn spring-boot:run -pl big-market-app -Pdev

# Run a single test class
mvn test -pl big-market-domain -Dtest=YourTestClassName -Pdev
```

The app starts on port 8092 (dev profile). Spring profiles: `dev`, `test`, `prod`. Active by default: `dev`.

## Architecture

This is a **DDD (Domain-Driven Design)** lottery/raffle system (大市场/大营销抽奖系统) built with Spring Boot 2.7.12, Java 17, MyBatis, Dubbo 3.0, Nacos, RocketMQ, Redis (Redisson), and Elasticsearch.

### Module dependency graph (top-down)

```
big-market-trigger  ──→  big-market-api  ──→  big-market-domain  ←──  big-market-infrastructure
       │                                                │
       └──────→  big-market-types  ←────────────────────┘
                     ↑
            big-market-querys
```

**big-market-types** — Shared types with zero dependencies. Enums (`ResponseCode`), annotations (`@DCCValue`, `@RateLimiterAccessInterceptor`), constants (`RedisKey` prefixes), base event class (`BaseEvent<T>`), and `AppException`.

**big-market-domain** — Pure domain logic, no framework dependencies beyond types. Contains 7 sub-domains: `strategy`, `activity`, `award`, `credit`, `rebate`, `auth`, `task`. Each sub-domain defines repository **interfaces** (e.g., `IStrategyRepository`, `IActivityRepository`), domain services, entities, value objects, aggregates, and event classes. No implementations here.

**big-market-infrastructure** — Implements domain repository interfaces using MyBatis DAOs (`dao/`), Redis (`redis/`), Elasticsearch (`elasticsearch/`), and Retrofit2 gateway (`gateway/`). Repository impls live in `adapter/repository/`. Port adapter `AwardPort` calls external OpenAI account service.

**big-market-api** — RPC/HTTP service interfaces and DTOs. All interfaces are in `com.pro.trigger.api`. Request/response DTOs use a generic `Request<T>` / `Response<T>` wrapper.

**big-market-trigger** — HTTP controllers (`http/`), Dubbo RPC handlers (`rpc/`), scheduled jobs (`job/`), and RocketMQ listeners (`listener/`). Controllers are annotated with **both** `@RestController` and `@DubboService(version = "1.0")` — they serve HTTP and Dubbo RPC simultaneously. The draw endpoint uses Hystrix circuit breaker + Guava RateLimiter AOP.

**big-market-app** — Spring Boot entry point (`Application.java` with `@EnableScheduling`, `@EnableDubbo`). Config classes for dual DataSources (MySQL + Elasticsearch), Redis/Redisson, ZooKeeper (conditional), Retrofit2, thread pools, Guava cache. MyBatis mapper XMLs live at `src/main/resources/mybatis/mapper/mysql/*.xml`.

**big-market-querys** — CQRS read side. Lightweight module that queries Elasticsearch for user raffle orders.

### Key patterns

- **Strategy raffle pipeline**: Responsibility chain (`rule/chain/` — blacklist → weight → default) → random raffle algorithm (O(1) or O(log n)) → decision tree (`rule/tree/` — lock → luck award → stock check).
- **Activity quota pipeline**: Chain of responsibility (`quota/rule/` — base check → SKU stock check) → trade policy (credit pay, rebate, etc.).
- **Database sharding**: `db-router-spring-boot-starter` routes by `userId` across 2 databases x 4 tables. The default config DB `big_market` stores reference data (strategies, awards, activities). Sharded DBs `big_market_01`/`02` store user-scoped data.
- **Dynamic config**: `@DCCValue` annotation + ZooKeeper nodes at `/big-market-dcc/config` for runtime hot-reload (e.g., degrade switch, rate limiter toggle). ZooKeeper DCC is disabled by default in dev (`zookeeper.sdk.config.enable: false`).
- **Event-driven**: Domain events published via RocketMQ. Listeners handle async award distribution, credit adjustment, SKU stock zero handling, and rebate processing.
- **Canal + Elasticsearch**: MySQL binlog → Canal → Elasticsearch for user raffle order queries (CQRS read model). Configs in `docs/dev-ops/canal-adapter/`.

### Where things live

| Concern | Location |
|---|---|
| New domain logic | `big-market-domain/src/main/java/com/pro/domain/<subdomain>/` |
| Repository impl + DAO | `big-market-infrastructure/src/main/java/com/pro/infrastructure/` |
| HTTP/RPC endpoint | `big-market-trigger/src/main/java/com/pro/trigger/` |
| API DTO / interface | `big-market-api/src/main/java/com/pro/trigger/api/` |
| MyBatis mapper XML | `big-market-app/src/main/resources/mybatis/mapper/mysql/` |
| Spring config / beans | `big-market-app/src/main/resources/` (YAML) and `spring-config.xml` |
| Shared constants/enums | `big-market-types/src/main/java/com/pro/types/` |
