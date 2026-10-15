# big-market | 大市场抽奖服务

这是一个基于 DDD 分层的抽奖/营销服务。`big-market-app` 是 Spring Boot 启动模块，领域逻辑位于 `big-market-domain`，基础设施实现位于 `big-market-infrastructure`，HTTP/Dubbo 入口位于 `big-market-trigger`，读侧位于 `big-market-querys`。

## 依赖与服务

构建依赖 JDK 17、Maven 和可访问的 Maven 仓库。项目按 Java 17 编译，Spring Boot 为 2.7.12，使用 MyBatis、MySQL Connector 8.0.22、Dubbo/Nacos、Redisson、RocketMQ 5.3.2、Elasticsearch 7.17.14 和 XXL-JOB 2.4.1。

核心运行至少需要：

- MySQL 8：`big_market` 保存活动、策略、奖品等配置；`big_market_01`、`big_market_02` 保存用户分片数据。分片配置是 2 个库、每库 4 张表。
- Redis：缓存、分布式锁、会话和限流依赖 Redis。
- Nacos：Dubbo 注册中心。开发配置使用 Nacos，Spring Cloud ZooKeeper 在开发 profile 中关闭。
- RocketMQ：抽奖、库存、发奖、返利等异步事件依赖 NameServer 和 Broker。
- Elasticsearch 7：查询侧和 Canal 同步链路使用；当前本地 compose 会等待 Elasticsearch 健康后再启动应用。

按功能启用的服务：ZooKeeper（DCC 动态配置启用时）、XXL-JOB Admin（调度任务）、Canal Server/Adapter 和 Kibana（订单 CQRS 查询与监控）、Logstash（日志转发）。Node.js 20 只在同时启动 `chatgpt-web` 或 `big-market-erp` 前端时需要。

## 数据库初始化与迁移

生产执行前备份并在目标库逐个校验。以下基础 SQL 含 `DROP TABLE IF EXISTS` 和示例数据，只适用于新库或已确认可重建的环境：

```bash
mysql -h 127.0.0.1 -P 3306 -u db_admin -p < docs/dev-ops/mysql/sql/big_market.sql
mysql -h 127.0.0.1 -P 3306 -u db_admin -p < docs/dev-ops/mysql/sql/big_market_01.sql
mysql -h 127.0.0.1 -P 3306 -u db_admin -p < docs/dev-ops/mysql/sql/big_market_02.sql
```

`init.sql` 当前为空，不需要单独执行。已有库只执行对应的增量迁移，并在执行前确认列、索引和分片数量：

1. `strategy-award-stock-inbox.sql`：在 `big_market` 创建策略奖品库存消费幂等表；启用该消费者前执行。
2. `idempotency-request-id.sql`：在两个用户分片库的 4 张 `user_raffle_order_*` 表增加请求幂等约束；启用带 `requestId` 的抽奖重试前执行。
3. `award-feed-index.sql`：为两个分片库的中奖记录表增加中奖展示查询索引；使用中奖展示接口时执行。
4. `nacos.sql`：只有 Nacos 使用 MySQL 持久化时执行；本地 `docker-compose-local.yml` 使用独立模式，不需要它。
5. `xxl_job.sql`：部署 XXL-JOB Admin 前执行。
6. `canal_manager.sql`：部署 Canal Manager 时执行；仅启动 Canal Server/Adapter 不等于已经完成订单索引初始化。

`seed-erp-activity-test-data.sql` 是本地 ERP 活动 fixture，必须在基础库完成后按需执行；`prepare-balance-awards.sql`、`repair-sign-rebate.sql` 是有前置条件的专项脚本，不属于通用上线步骤，也不要在生产中直接试跑。

## 配置与环境变量

仓库没有 `big-market/.env.example`。默认 `application.yml` 激活 `dev`，`application-dev.yml` 使用固定的开发端点；生产请通过外部 Spring 配置、容器环境变量或密钥管理系统覆盖，不要把真实凭证写回跟踪文件。

| 变量 | 必需性 | 用途 |
| --- | --- | --- |
| `APP_JWT_SECRET` | 必需 | JWT 签名密钥，代码要求至少 32 个 UTF-8 字节；与 `chatgpt-data` 联调时必须相同 |
| `LOCAL_MYSQL_USERNAME` | 本地 compose | `docs/dev-ops/local/application-local.yml` 的数据库用户名 |
| `LOCAL_MYSQL_PASSWORD` | 本地 compose | 三个 `big_market*` 数据库的密码；通过被忽略的 `docs/dev-ops/local/mysql.env` 提供 |
| `ROCKETMQ_NAMESRV_ADDR` | 独立启动时按需 | RocketMQ NameServer，默认 `127.0.0.1:9876` |
| `SPRING_PROFILES_ACTIVE` | 容器启动时 | 本地 compose 设置为 `dev,local` |
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | 容器启动时 | 本地 compose 设置为 `file:/config/`，加载本地覆盖配置 |

Redis、MySQL、Nacos、Elasticsearch、XXL-JOB、Logstash 和网关地址在当前开发 YAML 中仍有静态配置；修改生产部署时必须逐项替换为外部配置并核对网络可达性。不要引用或复制仓库中现有的明文密码、Token、AppId、AppToken 或第三方地址。

## 启动方式

### 直接运行

```bash
export APP_JWT_SECRET='由密钥管理系统注入的随机值'
mvn clean install -Pdev
mvn spring-boot:run -pl big-market-app -Pdev
```

开发 HTTP 端口为 `8092`。也可以运行构建产物：

```bash
java -jar big-market-app/target/big-market-app.jar --spring.profiles.active=dev
```

### 本地 Docker Compose

`docs/dev-ops/docker-compose-local.yml` 不创建 MySQL，而是连接已经运行在宿主机 `3306` 的 `local-mysql`。它还要求相邻存在 `chatgpt-data`、`chatgpt-web`、`big-market-erp`，并要求 `docs/dev-ops/local/mysql.env`、市场 JAR 和聊天服务 JAR 已准备好。

```bash
mvn -Pdev -Dmaven.test.skip=true package
docker compose -f docs/dev-ops/docker-compose-local.yml up -d
docker compose -f docs/dev-ops/docker-compose-local.yml ps -a
docker compose -f docs/dev-ops/docker-compose-local.yml logs --tail=100 big-market
```

停止时保留数据卷：

```bash
docker compose -f docs/dev-ops/docker-compose-local.yml stop
```

不要随意使用 `down -v`。构建镜像脚本位于 `big-market-app/build.sh`，Dockerfile 使用 `eclipse-temurin:17-jre`；正式发布前应自行确认镜像标签、配置注入和健康检查。

## 端口

| 服务 | 本地地址或端口 | 说明 |
| --- | --- | --- |
| big-market HTTP | `127.0.0.1:8092` | `dev` 和本地 compose；compose 另提供 `8098` 到同一容器端口 |
| MySQL | `3306` 或环境 compose 的 `13306` | 取决于所用 compose 文件 |
| Redis | 容器 `6379`，本地映射通常为 `16379` | 不要对公网开放 |
| Nacos | `8848`、客户端端口 `9848` | Dubbo 注册中心 |
| RocketMQ | NameServer `9876`，Broker `10909`/`10911` | 具体暴露端口取决于 compose |
| Elasticsearch | `9200`，传输端口 `9300` | CQRS/查询链路按需使用 |
| XXL-JOB executor | `9999` | 启用调度器时使用 |
| ZooKeeper | `2181` | 默认关闭，仅 DCC 需要时启用 |

`application-prod.yml` 和旧的 `docs/dev-ops/docker-compose-app.yml` 仍使用 `8091`，与当前开发和本地 compose 的 `8092` 不一致。生产部署必须先选定端口并同步 Spring 配置、容器映射、网关和前端地址。

## 联调与冒烟

使用已经存在且处于有效期的活动 ID；仓库示例活动日期可能已经过期。先装配活动和策略，再调用抽奖：

```bash
BASE=http://127.0.0.1:8092
curl -G "$BASE/api/v1/raffle/activity/query_stage_activity_id" \
  --data-urlencode 'channel=front' --data-urlencode 'source=web'

curl "$BASE/api/v1/raffle/activity/armory?activityId=100301"

curl -X POST "$BASE/api/v1/raffle/activity/draw" \
  -H 'Content-Type: application/json' \
  -d '{"userId":"smoke-user","activityId":100301}'
```

同一抽奖请求需要重试幂等时，在完成 `idempotency-request-id.sql` 后携带稳定的 `requestId`。带用户 Token 的接口使用 `Authorization` 请求头；Token 必须由同一 `APP_JWT_SECRET` 签发并且对应 Redis 会话。与 `chatgpt-data` 联调时，两边还要共享 Redis 和 JWT 配置；本地 overlay 的网关地址、聊天服务端口和实际运行方式需要逐项核对。

Canal 链路不是本地 compose 的默认组成部分。需要订单查询时，先初始化 Elasticsearch 索引，再按 `docs/dev-ops/canal-local/README.md` 的 `prepare.sh`、`up.sh`、`verify.sh` 顺序启动并验证。

## 生产安全

- 使用密钥管理系统注入 JWT、数据库、Redis、Nacos、RocketMQ、第三方 API 和支付凭证；上线前轮换仓库及历史中出现过的凭证。
- 将 MySQL、Redis、Nacos、Elasticsearch、RocketMQ、Canal 和管理端口限制在内网，启用认证、TLS、网络 ACL 和最小权限数据库账号。
- 将 `app.config.cross-origin` 从开发用的宽松值收紧为明确的前端来源；不要直接暴露 DCC、ERP、XXL-JOB、Nacos、Kibana、phpMyAdmin 或 Redis 管理端。
- 增量 SQL 逐条执行并留存迁移记录，先备份再执行；不要在生产运行 ERP fixture、修复脚本或带 `DROP TABLE` 的基线 SQL。
- 为 RocketMQ、Redis、MySQL、Elasticsearch、JVM、抽奖失败和库存告警配置监控、日志脱敏、备份恢复演练和回滚方案。

## 当前部署阻塞项

1. `application-prod.yml` 目前只有端口、线程池和日志配置，数据库、Redis、Nacos、RocketMQ 等生产连接并未完整外置；不能直接视为可上线生产配置。
2. 开发 YAML、旧 compose 和脚本存在静态凭证/端点及 `8091`、`8092` 端口差异。公开部署前必须完成密钥审计、轮换和端口统一。
3. 本地 compose 依赖外部 `local-mysql`、忽略的 `mysql.env`、同级仓库和聊天 JAR；缺少这些文件时不能按该 compose 独立启动。
