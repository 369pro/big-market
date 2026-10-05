# RocketMQ 本地部署与切换

项目使用 RocketMQ 5.3.2 的 Java 8 兼容 remoting 客户端。发奖、返利、积分调整、活动 SKU 归零、奖品库存回写各使用独立 Topic 和消费组。

## 启动

从仓库根目录执行，仅启动消息队列：

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml up -d rocketmq-namesrv rocketmq-broker
docker compose -f docs/dev-ops/docker-compose-environment.yml ps
```

等待 Broker healthy 后初始化 Topic（可重复执行）：

```bash
docker compose -f docs/dev-ops/docker-compose-environment.yml exec rocketmq-broker sh /etc/rocketmq/init-topics.sh
```

应用默认连接 `127.0.0.1:9876`，可以通过 `ROCKETMQ_NAMESRV_ADDR` 覆盖。NameServer 仅负责路由发现；客户端还必须能访问 Broker 广播的地址和 10911、10909 端口。

宿主机开发时 `ROCKETMQ_BROKER_IP` 默认是 `127.0.0.1`。远程部署应设置为客户端可访问的服务器 IP；应用与 Broker 均在同一 Docker 网络时可设置为 `rocketmq-broker`，并让应用使用 `ROCKETMQ_NAMESRV_ADDR=rocketmq-namesrv:9876`。分开的 Compose 项目必须先接入同一个网络。私有及云服务器 Compose 文件使用相同配置结构，镜像可按环境镜像仓库调整。

本地示例是单 Broker、同步刷盘，数据保存在 `docs/dev-ops/rocketmq/store/`。生产环境还需部署复制/高可用、访问控制及监控；本地示例不提供这些保证。

## 消费行为

- 所有生产者同步检查 `SEND_OK`。异常会传给原有数据库 task 或 Redis outbox 补偿逻辑。
- 四类普通业务消息保持原有业务处理，成功返回 `CONSUME_SUCCESS`，异常返回 `RECONSUME_LATER`。
- 奖品库存采用应用聚合器：`rocketmq.stock.batch-size=500`、`rocketmq.stock.flush-interval-ms=1000`，先满足的条件触发。参数位于 `application.yml`。
- 同一奖品取最小剩余库存，并用 `LEAST` 幂等同步。回调等待数据库更新完成，不会在消息只进入内存时就确认。
- 首次创建消费组从最早保留消息开始消费。消费组名必须跨重启稳定，不要随意变更。
- 监控积压、消费失败及 `%DLQ%<consumerGroup>`；死信需要排查后重新投递。业务消费者原有的主动丢弃规则保持不变。
- 活动 SKU 的普通扣减仍使用 Redis 延迟队列与定时聚合；其归零通知已切换到 RocketMQ。

## 切换存量系统

1. 暂停产生新业务事件，等待旧消息系统积压及数据库 task 完成；旧系统消息不会自动出现在新 Topic 中。
2. 部署 RocketMQ、初始化五个 Topic，核对网络地址。
3. 停止旧版本应用，再启动迁移后的应用，避免同一业务同时由两套消费者处理。
4. Redis 库存 outbox 的事件格式未改变，保留未发送记录，由新生产者补发。
5. 验证发奖、返利、积分调整和库存回写后恢复入口，并核对库存与任务状态。

仅完成代码切换，不会自动迁移旧消息系统中尚未消费的消息。

## 验证

无需启动业务服务即可运行聚合与消息确认单元测试：

```bash
mvn -Pdev test -DskipTests=false -Dtest=StockMessageBatcherTest,RocketMqMigrationTest -DfailIfNoTests=false
```

真实 Broker 测试需在专用测试集群预建 `big_market_migration_test` Topic。它会注入一次写入失败并等待 Broker 重投；不连接 MySQL、Redis 等业务服务：

```bash
ROCKETMQ_TEST_NAMESRV=127.0.0.1:9876 mvn -Pdev test -DskipTests=false -Dtest=RocketMqBrokerTest -DfailIfNoTests=false
```

未设置 `ROCKETMQ_TEST_NAMESRV` 时，该测试自动跳过。使用 Java 8 或项目兼容的 JDK 构建；本次在 JDK 17 上验证。
