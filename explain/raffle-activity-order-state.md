# raffle_activity_order 状态流转

## 状态枚举

`OrderStateVO` 只有两个值：

| 状态码 | 含义 |
|---|---|
| `completed` | 完成 |
| `wait_pay` | 待支付 |

## 两条创建路径

```
                    raffle_activity_order
                          │
          ┌───────────────┴───────────────┐
          │                               │
   rebate_no_pay_trade              credit_pay_trade
   (返利 / 签到送次数)              (积分兑换 SKU)
          │                               │
          ▼                               ▼
   直接 INSERT                      INSERT
   state = 'completed'             state = 'wait_pay'
   (出生即终态)                         │
                                       │ 扣积分成功
                                       │ MQ 回调
                                       ▼
                                  UPDATE
                                  state = 'completed'
```

### 路径一：rebate_no_pay_trade — 直接完成

**入口**：签到返利 sku 分支、或其他免支付场景

**Policy**：`RebateNoPayTradePolicy.java:29`
```java
createQuotaOrderAggregate.setOrderState(OrderStateVO.completed);
createQuotaOrderAggregate.getActivityOrderEntity().setPayAmount(BigDecimal.ZERO);
activityRepository.doSaveNoPayOrder(createQuotaOrderAggregate);
```

INSERT 即 `completed`，不经过 `wait_pay`。同时 `pay_amount = 0`，账户额度同步累加，整个流程在一个事务内完成。

### 路径二：credit_pay_trade — 先占位后确认

**入口**：用户用积分兑换抽奖次数

1. 先创建 `wait_pay` 订单（占位，不更新账户额度）
2. 扣积分 → 发 MQ `credit_adjust_success`
3. `CreditAdjustSuccessCustomer` 消费 MQ → 调 `updateOrder()`
4. 查 `raffle_activity_order` WHERE `state = 'wait_pay'`
5. UPDATE `state = 'completed'` + 累加账户额度

**`updateOrder()` 实现**：`ActivityRepository.java:282-360`
- 事务 + Redis 锁
- 只更新匹配 `state = 'wait_pay'` 的订单
- 同步累加 `raffle_activity_account` / `_month` / `_day`

## 所以

- **签到返利 sku**：INSERT 即 `completed`，无流转
- **积分兑换**：`wait_pay` → `completed`，两步流转
- 没有 `create`、`processing`、`fail` 等其他中间状态
