# ERP 活动测试数据

执行 [seed-erp-activity-test-data.sql](seed-erp-activity-test-data.sql) 可在本地 `big_market` 配置库创建四个相互独立的活动测试夹具。脚本读取当前策略 `10008`、奖品库存和 SKU 库存快照；奖品定义表 `award` 中的不可变定义复用现有记录，四个 `strategy_award` 集合和四个 SKU 都有自己的业务 ID。

| 活动 | 场景 | 策略 | SKU | 次数配置 | 活动状态 | 展台状态 |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `100501` 测试活动-上下架演示 | 有效开放，供 ERP 上下架往返 | `100081` | `9911` | `22301` | `open` | `create` |
| `100502` 测试活动-有效未上架 | 有效开放，从未上架 | `100082` | `9912` | `22302` | `open` | 无展台记录 |
| `100503` 测试活动-未来开放 | 开始时间为 2026-11-01 | `100083` | `9913` | `22303` | `open` | 无展台记录 |
| `100504` 测试活动-已结束关闭 | 结束时间为 2026-09-30，已关闭 | `100084` | `9914` | `22304` | `close` | 无展台记录 |

唯一的初始展台记录是 `channel=test`、`source=erp_demo`、活动 `100501`、状态 `create`。它不会占用线上 `c01/s01` 展台。有效活动使用 2026-10-01 至 2026-12-31 的时间窗口；当前本地维护日期为 2026-10-02。

脚本使用事务并且不执行更新或删除。完整重复执行是空操作；部分目标记录会触发回滚，避免与手工数据拼接。已经经过 ERP 测试的展台状态和库存剩余量不会在重复执行时被重置。活动测试仅写默认配置库，不写用户账户、抽奖订单、中奖记录或发奖记录。

执行后可以用以下查询复核：

```sql
SELECT activity_id, activity_name, begin_date_time, end_date_time,
       strategy_id, state
FROM big_market.raffle_activity
WHERE activity_id BETWEEN 100501 AND 100504
ORDER BY activity_id;

SELECT id, channel, source, activity_id, state
FROM big_market.raffle_activity_stage
WHERE channel = 'test' AND source = 'erp_demo'
ORDER BY id;

SELECT strategy_id, COUNT(*) AS award_rows,
       MIN(award_count_surplus) AS min_surplus,
       MAX(award_count_surplus) AS max_surplus
FROM big_market.strategy_award
WHERE strategy_id IN (100081, 100082, 100083, 100084)
GROUP BY strategy_id
ORDER BY strategy_id;
```
