# 日历签到返利流程分析

## 一、涉及哪些订单（按层级展开）

签到返利一条请求最终会写到 **5 张表**，分两个 MQ 分支：

---

### 第一层：同步写入（BehaviorRebateService.createOrder 内）

#### 1. user_behavior_rebate_order（行为返利流水）
PO: `UserBehaviorRebateOrder.java`

| 字段 | 写什么 | 说明 |
|---|---|---|
| `order_id` | 12 位随机数字 | 主键 |
| `user_id` | 签到用户 ID |
| `behavior_type` | `"sign"` |
| `rebate_type` | `"sku"` 或 `"integral"` | 取决于 `daily_behavior_rebate` 配置 |
| `rebate_config` | sku → SKU_ID；integral → 积分数量 |
| `rebate_desc` | 配置里的描述 |
| `out_business_no` | `yyyyMMdd` 日期 | 签到日期 |
| `biz_id` | `userId_rebateType_outBusinessNo` | **唯一索引**，幂等去重 |

- **无状态**：纯流水记录，写入后不再变化
- **幂等**：靠 `biz_id` 唯一索引，同一天同用户同返利类型只生成一条

#### 2. task（MQ 消息投递任务）
PO: `Task.java`

| 字段 | 写什么 | 说明 |
|---|---|---|
| `user_id` | 签到用户 ID |
| `topic` | `send_rebate` |
| `message_id` | 11 位随机数字 |
| `message` | `SendRebateMessageEvent.RebateMessage` 的 JSON |
| `state` | **`create`** | 初始状态 |

- 事务内 INSERT，状态 `create`
- 事务外同步发 MQ → **成功**：UPDATE `state = 'complete'`；**失败**：UPDATE `state = 'fail'`
- 补偿 Job 每 5 秒扫 `fail` 和超时 `create` 重试

---

### 第二层（MQ 消费）：SKU 分支

MQ Listener: `RebateMessageCustomer`，收到消息后根据 `rebateType` 分发。

#### rebateType = "sku" 写入的表：

#### 3. raffle_activity_order（活动订单）
PO: `RaffleActivityOrder.java`

| 字段 | 写什么 |
|---|---|
| `order_id` | 12 位随机数字 |
| `user_id` | 签到用户 ID |
| `sku` | 商品 SKU ID |
| `activity_id` / `activity_name` / `strategy_id` | 从 SKU 关联的活动信息查出来 |
| `total_count` / `day_count` / `month_count` | 该 SKU 对应的抽奖次数 |
| `pay_amount` | **0**（返利无需支付） |
| `state` | **`completed`**（直接完成，不走 wait_pay） |
| `out_business_no` | `rebateMessage.bizId` |
| `order_trade_type` | `rebate_no_pay_trade` |

- 走 `RebateNoPayTradePolicy` → 直接设 `state = completed`，无需支付环节

#### 4. raffle_activity_account（用户活动账户，总次数）
PO: `RaffleActivityAccount.java`

初次签到 INSERT，之后签到 **UPDATE 累加**：

```
total_count        += N
total_count_surplus += N
day_count          += N
day_count_surplus  += N
month_count        += N
month_count_surplus += N
```

#### 5. raffle_activity_account_month（月度次数）
PO: `RaffleActivityAccountMonth.java`

UPDATE 累加当月次数。

#### 6. raffle_activity_account_day（日度次数）
PO: `RaffleActivityAccountDay.java`

UPDATE 累加当日次数。

**幂等**：`raffle_activity_order` 有唯一约束，重复 MQ 消费时抛 `DuplicateKeyException`，消费者直接 ack 丢弃。

---

### 第二层（MQ 消费）：Integral 分支

#### rebateType = "integral" 写入的表：

#### 7. user_credit_account（用户积分账户）
PO: `UserCreditAccount.java`

| 字段 | 写什么 |
|---|---|
| `user_id` | 签到用户 ID |
| `total_amount` | 累加配置的积分数量 |
| `available_amount` | 累加配置的积分数量 |
| `account_status` | `"open"` |

初次 INSERT，后面 UPDATE 累加。

#### 8. user_credit_order（积分流水）
PO: `UserCreditOrder.java`

| 字段 | 写什么 |
|---|---|
| `order_id` | 12 位随机数字 |
| `user_id` | 签到用户 ID |
| `trade_name` | `"行为返利"` (`TradeNameVO.REBATE`) |
| `trade_type` | `"forward"` (`TradeTypeVO.FORWARD`) |
| `trade_amount` | 积分数值 |
| `out_business_no` | 日期 `yyyyMMdd` |

- **无状态**：纯流水记录

#### 9. task（积分侧 MQ 投递任务）
同上表 `task`，另写一条：
- `topic` = `credit_adjust_success`
- `state` = `create` → MQ 发送成功 `complete` / 失败 `fail`

#### 额外：CreditAdjustSuccessCustomer 消费

积分账户写完后会再发一条 MQ (`credit_adjust_success`)，由 `CreditAdjustSuccessCustomer` 消费，作用是：**在积分兑换 SKU 场景**（`credit_pay_trade`）下，查 `raffle_activity_order` 把 `wait_pay` → `completed`。

对于签到返利 integral 路径，因为 `raffle_activity_order` 里没有对应的 `wait_pay` 记录，这个 Consumer 实际是 **空跑**，不做任何更新。

---

## 二、状态流转

### task 表的状态机（两条分支共用模式）

```
                    ┌───────────┐
                    │  create   │  ← 事务内初始 INSERT
                    └─────┬─────┘
                          │
             ┌────────────┼────────────┐
             │                         │
        MQ 发送成功               MQ 发送失败
             │                         │
             ▼                         ▼
       ┌──────────┐             ┌──────────┐
       │ complete │             │   fail   │
       └──────────┘             └─────┬────┘
                                      │
                         SendMessageTaskJob（每 5 秒）
                         扫：state = 'fail' OR
                             (state = 'create' AND now() - update_time > 6s)
                                      │
                             ┌────────┼────────┐
                             │                 │
                         发送成功            发送失败
                             │                 │
                             ▼                 ▼
                        ┌────────┐       ┌──────────┐
                        │complete│       │  fail    │（下一轮继续）
                        └────────┘       └──────────┘
```

### raffle_activity_order 的状态机

sku 分支（`rebate_no_pay_trade`）：

```
        ┌───────────┐
        │ completed │  ← 直接完成，不走支付流程
        └───────────┘
```

对比：积分兑换 SKU 的 `credit_pay_trade` 路径：

```
   ┌──────────┐      积分支付成功      ┌───────────┐
   │ wait_pay │ ─────────────────────→ │ completed │
   └──────────┘         MQ 回调        └───────────┘
```

签到返利的 sku 分支是 `rebate_no_pay_trade`，不涉及支付，所以直接 `completed`。

---

## 三、完整时序图

```
POST /calendar_sign_rebate?userId=xxx
  │
  ▼
Controller
  │ behaviorType = SIGN, outBusinessNo = "20260528"
  ▼
BehaviorRebateService.createOrder()
  │
  ├─① 查 daily_behavior_rebate WHERE behavior_type='sign' AND state='open'
  │    → 通常返回 2 条: rebateType=sku, rebateType=integral
  │
  ├─② 遍历配置，构建 BehaviorRebateAggregate:
  │   ├─ BehaviorRebateOrderEntity (orderId = 12位随机数)
  │   └─ TaskEntity (state = create)
  │
  ├─③ 事务写入
  │   INSERT user_behavior_rebate_order (biz_id 唯一索引)
  │   INSERT task (state = 'create')
  │
  ├─④ 同步发 MQ → topic: send_rebate
  │   成功: UPDATE task SET state = 'complete'
  │   失败: UPDATE task SET state = 'fail' → SendMessageTaskJob 补偿
  │
  ▼
返回 List<orderIds>
```

```
══════════════════════ MQ 异步消费 ══════════════════════

RebateMessageCustomer 收到消息
  │
  ├── rebateType = "sku"
  │   │
  │   ▼
  │   RaffleActivityAccountQuotaService.createOrder()
  │   │  查活动/SKU/次数配置
  │   │  构建 ActivityOrderEntity (orderId=12位)
  │   │  RebateNoPayTradePolicy: state = completed, amount = 0
  │   │
  │   ▼
  │   ActivityRepository.doSaveNoPayOrder() (事务 + Redis 锁)
  │   │  INSERT raffle_activity_order   (state = 'completed')
  │   │  INSERT/UPDATE raffle_activity_account (累加次数)
  │   │  UPDATE raffle_activity_account_month  (累加月次数)
  │   │  UPDATE raffle_activity_account_day    (累加日次数)
  │   │
  │   ▼
  │   重复消费: DuplicateKeyException → ack 丢弃
  │
  └── rebateType = "integral"
      │
      ▼
      CreditAdjustService.createOrder()
      │  TradeAggregate: + CreditOrderEntity + CreditAccountEntity + TaskEntity
      │
      ▼
      CreditRepository.saveUserCreditTradeOrder() (事务 + Redis 锁)
      │  INSERT/UPDATE user_credit_account (累加积分)
      │  INSERT user_credit_order (流水)
      │  INSERT task (state = 'create')
      │
      ├─ 同步发 MQ → topic: credit_adjust_success
      │  成功: UPDATE task SET state = 'complete'
      │  失败: UPDATE task SET state = 'fail'
      │
      │  ┌─────────────────────────────────────────┐
      │  │ CreditAdjustSuccessCustomer 消费         │
      │  │ 查 raffle_activity_order 的 wait_pay 记录 │
      │  │ (签到返利场景不存在，空跑)                │
      │  └─────────────────────────────────────────┘
      │
      ▼
      重复消费: DuplicateKeyException → ack 丢弃
```

---

## 四、总结：一次签到 = 几张订单

| 表 | 数量 | 状态 |
|---|---|---|
| `user_behavior_rebate_order` | 2 条 (sku + integral) | 无状态 |
| `task` (第一层 MQ) | 2 条 (sku + integral) | create → complete/fail |
| `raffle_activity_order` | 1 条 | completed |
| `raffle_activity_account` | 1 条 (INSERT/UPDATE) | 无状态，累加计数器 |
| `raffle_activity_account_month` | 1 条 (UPDATE) | 无状态 |
| `raffle_activity_account_day` | 1 条 (UPDATE) | 无状态 |
| `user_credit_account` | 1 条 (INSERT/UPDATE) | account_status = open |
| `user_credit_order` | 1 条 | 无状态 |
| `task` (第二层 MQ, integral) | 1 条 | create → complete/fail |

---

## 五、关键代码索引

| 层次 | 文件 |
|---|---|
| Controller | `big-market-trigger/.../http/RaffleActivityController.java:354` |
| Rebate Service | `big-market-domain/.../rebate/service/BehaviorRebateService.java:29` |
| Rebate Repository | `big-market-infrastructure/.../adapter/repository/BehaviorRebateRepository.java:64` |
| Rebate MQ Consumer | `big-market-trigger/.../listener/RebateMessageCustomer.java:44` |
| 补偿 Job | `big-market-trigger/.../job/SendMessageTaskJob.java` |
| SKU: Quota Service | `big-market-domain/.../activity/service/quota/RaffleActivityAccountQuotaService.java` |
| SKU: Trade Policy | `big-market-domain/.../quota/policy/impl/RebateNoPayTradePolicy.java` |
| SKU: Activity Repository | `big-market-infrastructure/.../adapter/repository/ActivityRepository.java:134` |
| Integral: Credit Service | `big-market-domain/.../credit/service/CreditAdjustService.java` |
| Integral: Credit Repository | `big-market-infrastructure/.../adapter/repository/CreditRepository.java:60` |
| Integral: CreditAdjustSuccessCustomer | `big-market-trigger/.../listener/CreditAdjustSuccessCustomer.java` |
| OrderStateVO | `big-market-domain/.../activity/model/valobj/OrderStateVO.java` |
| TaskStateVO | `big-market-domain/.../award/model/valobj/TaskStateVO.java` |
