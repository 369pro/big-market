# Canal 同步 Elasticsearch 面试总结

## 1. 项目当前方案

当前项目没有在 Canal 和 Elasticsearch 之间引入 MQ，实际链路为：

```text
MySQL 业务写入
    -> MySQL binlog
    -> Canal Server 解析 binlog
    -> Canal Adapter 通过 TCP 拉取变更
    -> Elasticsearch Adapter 批量写入 ES
```

对应配置位于 `docs/dev-ops/canal-adapter/application.yml`：

```yaml
canal.conf:
  mode: tcp
  syncBatchSize: 1000
  retries: -1
  consumerProperties:
    canal.tcp.batch.size: 500
```

- `mode: tcp`：Canal Adapter 直接连接 Canal Server，不经过消息队列。
- `canal.tcp.batch.size: 500`：每次从 Canal 获取一批 binlog 事件。
- `syncBatchSize: 1000`：Adapter 按批次向下游同步，降低网络和 ES 写入开销。
- `retries: -1`：同步失败后持续重试，不能把失败批次直接跳过。

这里的批量同步是吞吐优化，不是延迟队列。

## 2. 常规方案是否必须增加 MQ

不必须。是否增加 MQ 取决于业务规模、下游数量、可用性要求和运维能力。

### 2.1 Canal Adapter 直接写 ES

```text
MySQL -> Canal Server -> Canal Adapter -> Elasticsearch
```

适用于：

- 数据量和同步压力可控。
- 只有 Elasticsearch 等少量下游。
- 希望减少组件数量和运维复杂度。
- 可以接受 ES 故障时 Canal 链路暂停并重试。

优点是链路短、延迟低、部署简单；缺点是 Canal 与 ES 耦合更紧，ES 长时间故障时会产生同步积压。

### 2.2 Canal 通过 MQ 解耦

```text
MySQL -> Canal Server -> Kafka/RocketMQ -> 同步消费者 -> Elasticsearch
```

也可以让 Canal Adapter 以 MQ 模式消费消息后写入 ES，或者开发独立消费者完成业务转换和写入。

适用于：

- binlog 事件量较大，需要削峰缓冲。
- ES 可能短暂不可用，但不希望阻塞 Canal 解析链路。
- 同一份变更数据需要供 ES、数仓、缓存、风控等多个下游消费。
- 需要保留消息并按消费位点进行重放。
- 需要独立扩容 ES 写入消费者。

MQ 在这里是缓冲和解耦组件，不是为了实现延迟消息。

## 3. MQ 消费者如何同步 ES

消费者收到 Canal 产生的行变更事件后，处理流程如下：

1. 根据数据库名、表名过滤目标分库分表。
2. 根据事件类型区分 `INSERT`、`UPDATE` 和 `DELETE`。
3. 将各分表字段转换为统一的订单宽表 Document。
4. 使用 `order_id` 作为 Elasticsearch 文档 `_id`。
5. `INSERT` 和 `UPDATE` 使用 `index` 或 `upsert` 覆盖文档。
6. `DELETE` 根据相同 `_id` 删除文档，或者按业务要求标记为删除状态。
7. ES 写入成功后再提交 MQ 消费位点。
8. 写入失败时重试；超过阈值后写入死信队列或补偿表。

示意逻辑：

```text
收到 Canal 消息
    -> 校验库表和字段
    -> 转换订单 Document
    -> 以 order_id 为 _id 写 ES
       -> 成功：提交消费位点
       -> 失败：重试
          -> 多次失败：进入死信/补偿任务
```

## 4. 数据一致性设计

这条链路采用最终一致性，MySQL 是事实数据源，ES 是面向 B 端查询的读模型。

### 4.1 幂等写入

MQ 和 Canal 都可能产生重复投递，因此不能每消费一次就创建一个新文档。固定使用 `order_id` 作为 `_id`，重复消费时覆盖同一文档，避免产生重复订单。

### 4.2 失败重试和死信补偿

如果 ES 写入失败，不能直接提交消费位点并跳过数据。处理方式为：

- 短暂异常进行有限次数重试和退避。
- 多次失败后记录数据库、表名、主键、binlog 位点和失败原因。
- 异常消息进入死信队列或补偿表。
- 补偿任务根据订单主键查询 MySQL 最新数据，再完整覆盖 ES 文档。

补偿时重新查询 MySQL，而不是机械重放旧消息，可以避免旧事件覆盖新状态。

### 4.3 顺序和旧消息覆盖

同一订单的消息应尽量路由到同一分区，保证局部有序。消费者还可以携带 `update_time`、业务版本号或 binlog 位点，在写入前判断消息版本，拒绝旧事件覆盖新文档。

### 4.4 定时对账

定时按照更新时间范围或主键区间比对 MySQL 与 ES：

- MySQL 有、ES 没有：补建文档。
- 两边都有但核心字段不同：以 MySQL 为准覆盖 ES。
- ES 存在脏数据：根据业务规则删除或修正。

## 5. 如何选择

| 场景 | 推荐方案 |
| --- | --- |
| 数据量较小、下游单一、追求简单 | Canal Adapter 直接写 ES |
| 高吞吐、多个下游、需要削峰和重放 | Canal -> MQ -> 消费者 -> ES |
| 写入需要复杂业务转换 | 自定义 MQ 消费者写 ES |
| 只做简单表到索引映射 | Canal ES Adapter |

增加 MQ 会提升缓冲、重放和扩展能力，但也会引入 Topic、分区、消费位点、重复消费、消息积压、死信和监控等额外复杂度。因此不能为了架构看起来复杂而增加 MQ。

## 6. 面试回答

> Canal 同步 Elasticsearch 不一定必须经过 MQ。我们项目采用 Canal Adapter 的 TCP 模式，Adapter 直接订阅 Canal Server，并将分库分表订单变更批量写入 Elasticsearch。这种方案链路短、延迟低，能够满足当前的数据规模。
>
> 如果订单量进一步增大，或者同一份 binlog 数据需要被数仓、风控等多个下游消费，我会在 Canal 与 ES 之间引入 Kafka 或 RocketMQ。消费者解析 Canal 消息，把分表数据转换为统一的订单文档，以 `order_id` 作为 ES `_id` 幂等写入，成功后再提交消费位点。写入失败先重试，多次失败后进入死信队列，并由补偿任务查询 MySQL 最新记录覆盖 ES。MQ 在这里主要用于削峰、解耦和消息重放，不是用于延迟消费。

## 7. 容易说错的地方

- 当前项目没有使用 RocketMQ，不要把 Canal 配置注释中的 `rocketMQ` 说成实际配置。
- 当前 `mode` 是 `tcp`，所以 Canal 到 ES 没有经过 RocketMQ。
- `syncBatchSize` 表示批量同步，不表示延迟队列。
- 项目中的 RocketMQ 和 Redisson 延迟队列用于库存等业务链路，与 Canal 同步 ES 是不同链路。
- ES 是查询读模型，不能替代 MySQL 成为订单事实数据源。
