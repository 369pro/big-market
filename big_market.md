# 大市场系统 - 抽奖系统文档

## 1. 概述

本系统是一个基于DDD（领域驱动设计）架构的大型抽奖系统，支持高并发抽奖场景。系统采用分库分表设计，支持活动管理、策略抽奖、奖品发放、积分返利等核心功能。

## 2. 核心 ER 图

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         基础配置库 (big_market)                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│   award         │       │   strategy      │       │raffle_activity  │
│   奖品表        │───────│   策略表        │───────│   活动表        │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│ PK award_id     │       │ PK strategy_id  │       │ PK activity_id  │
│ award_key       │       │ strategy_desc   │       │ activity_name   │
│ award_config    │       │ rule_models     │       │ begin_datetime  │
│ award_desc      │       └────────┬────────┘       │ end_datetime    │
└─────────────────┘                │                │ strategy_id     │
                                   │                │ activity_count_id
                                   ▼                └────────┬────────┘
                            ┌─────────────────┐              │
                            │strategy_award   │              │
                            │策略奖品表        │◄─────────────┘
                            ├─────────────────┤
                            │PK strategy_id    │
                            │PK award_id       │
                            │award_rate        │
                            │award_count       │
                            │rule_models       │
                            └────────┬────────┘
                                   │
                                   ▼
                            ┌─────────────────┐
                            │strategy_rule    │
                            │策略规则表        │
                            ├─────────────────┤
                            │PK strategy_id    │
                            │award_id          │
                            │rule_type         │
                            │rule_model        │
                            │rule_value        │
                            └─────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                         分库分表 (big_market_01/02/03/04)                   │
└─────────────────────────────────────────────────────────────────────────────┘

                              user_id (分片键)
                                  │
        ┌─────────────┬───────────┼───────────┬─────────────┐
        ▼             ▼           ▼           ▼             ▼
┌──────────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐ ┌─────────────┐
│raffle_activity│ │user_award│ │user_    │ │user_credit│ │user_behavior│
│    _account    │ │ _record  │ │raffle   │ │  _account │ │_rebate_order│
│   活动账户     │ │ 中奖记录 │ │ _order  │ │  积分账户  │ │  返利订单   │
├──────────────┤ ├─────────┤ ├─────────┤ ├──────────┤ ├─────────────┤
│PK:userId     │ │PK:orderId│ │PK:orderId│ │PK:userId │ │PK:orderId   │
│PK:activityId │ │userId    │ │userId   │ │total_amount│ │userId      │
│ total_count   │ │activity_id│ │activity_id│ │available│ │behavior_type│
│ day_count     │ │strategy_id│ │order_state│ │          │ │rebate_type  │
│ month_count   │ │order_id  │ │          │ │          │ │biz_id       │
└──────────────┘ └─────────┘ └─────────┘ └──────────┘ └─────────────┘
        │                      │                  │
        ▼                      ▼                  ▼
┌──────────────┐       ┌──────────────┐   ┌──────────────┐
│raffle_activity│       │user_raffle_order│ │user_credit   │
│   _order     │       │  用户抽奖订单  │   │   _order     │
│   活动订单    │       └──────────────┘   │   积分订单    │
├──────────────┤                            ├──────────────┤
│PK:orderId    │                            │PK:orderId    │
│ userId       │                            │ userId       │
│ activityId   │                            │ trade_amount │
│ sku          │                            │ trade_type   │
│ pay_amount   │                            │              │
└──────────────┘                            └──────────────┘
```

### 2.2 分库分表策略

| 表名 | 分片键 | 分片库数量 | 每库分表数 | 总表数 |
|------|--------|-----------|-----------|--------|
| raffle_activity_account | user_id | 4 | 4 | 16 |
| raffle_activity_order | user_id | 4 | 4 | 16 |
| user_award_record | user_id | 4 | 4 | 16 |
| user_raffle_order | user_id | 4 | 4 | 16 |
| user_credit_order | user_id | 4 | 4 | 16 |
| user_behavior_rebate_order | user_id | 4 | 4 | 16 |

## 3. 数据库表详细说明

### 3.1 基础配置表

#### 3.1.1奖品表 (award)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| award_id | int | 奖品ID |
| award_key | varchar | 奖品标识 |
| award_config | varchar | 奖品配置（JSON格式） |
| award_desc | varchar | 奖品描述 |

#### 3.1.2策略表 (strategy)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| strategy_id | bigint | 策略ID |
| strategy_desc | varchar | 策略描述 |
| rule_models | varchar | 规则模型（rule_weight,rule_blacklist） |

#### 3.1.3活动表 (raffle_activity)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| activity_id | bigint | 活动ID |
| activity_name | varchar | 活动名称 |
| activity_desc | varchar | 活动描述 |
| begin_date_time | datetime | 开始时间 |
| end_date_time | datetime | 结束时间 |
| strategy_id | bigint | 策略ID |
| activity_count_id | bigint | 活动次数配置ID |
| state | varchar | 活动状态 |

#### 3.1.4活动次数配置表 (raffle_activity_count)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| activity_count_id | bigint | 活动次数配置ID |
| total_count | int | 总次数 |
| day_count | int | 日次数 |
| month_count | int | 月次数 |

#### 3.1.5活动SKU表 (raffle_activity_sku)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| sku | bigint | 商品SKU |
| activity_id | bigint | 活动ID |
| activity_count_id | bigint | 活动次数配置ID |
| stock_count | int | 库存总量 |
| stock_count_surplus | int | 剩余库存 |
| product_amount | decimal | 商品金额 |

#### 3.1.6策略奖品表 (strategy_award)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| strategy_id | bigint | 策略ID |
| award_id | int | 奖品ID |
| award_title | varchar | 奖品标题 |
| award_subtitle | varchar | 奖品副标题 |
| award_count | int | 奖品库存总量 |
| award_count_surplus | int | 奖品库存剩余 |
| award_rate | decimal | 奖品中奖概率 |
| sort | int | 排序 |
| rule_models | varchar | 规则模型 |

#### 3.1.7策略规则表 (strategy_rule)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | bigint | 主键 |
| strategy_id | bigint | 策略ID |
| award_id | int | 奖品ID |
| rule_type | int | 规则类型（1-策略规则，2-奖品规则） |
| rule_model | varchar | 规则模型 |
| rule_value | varchar | 规则值 |
| rule_desc | varchar | 规则描述 |

### 3.2 分库分表

#### 3.2.1活动账户表 (raffle_activity_account_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| activity_id | bigint | 活动ID |
| total_count | int | 总次数 |
| total_count_surplus | int | 总次数剩余 |
| day_count | int | 日次数 |
| day_count_surplus | int | 日次数剩余 |
| month_count | int | 月次数 |
| month_count_surplus | int | 月次数剩余 |

#### 3.2.2日账户表 (raffle_activity_account_day_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| activity_id | bigint | 活动ID |
| day | varchar | 日期（yyyy-MM-dd） |
| day_count | int | 日次数 |
| day_count_surplus | int | 日次数剩余 |

#### 3.2.3月账户表 (raffle_activity_account_month_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| activity_id | bigint | 活动ID |
| month | varchar | 月份（yyyy-MM） |
| month_count | int | 月次数 |
| month_count_surplus | int | 月次数剩余 |

#### 3.2.4活动订单表 (raffle_activity_order_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| order_id | varchar | 订单ID |
| user_id | varchar | 用户ID |
| sku | bigint | SKU |
| activity_id | bigint | 活动ID |
| activity_name | varchar | 活动名称 |
| strategy_id | bigint | 策略ID |
| order_time | datetime | 下单时间 |
| total_count | int | 总次数 |
| day_count | int | 日次数 |
| month_count | int | 月次数 |
| pay_amount | decimal | 支付金额 |
| state | varchar | 订单状态 |
| out_business_no | varchar | 业务仿重ID |

#### 3.2.5用户抽奖订单表 (user_raffle_order_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| activity_id | bigint | 活动ID |
| activity_name | varchar | 活动名称 |
| strategy_id | bigint | 策略ID |
| order_id | varchar | 订单ID |
| order_time | datetime | 下单时间 |
| order_state | varchar | 订单状态（create, used, cancel） |
| end_datetime | datetime | 结束时间 |

#### 3.2.6用户中奖记录表 (user_award_record_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| activity_id | bigint | 活动ID |
| strategy_id | bigint | 策略ID |
| order_id | varchar | 订单ID |
| award_id | int | 奖品ID |
| award_title | varchar | 奖品标题 |
| award_time | datetime | 中奖时间 |
| award_config | varchar | 奖品配置 |
| award_state | varchar | 奖品状态 |

#### 3.2.7用户积分账户表 (user_credit_account)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| adjust_amount | decimal | 可用积分 |

#### 3.2.8用户积分订单表 (user_credit_order_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| order_id | varchar | 订单ID |
| user_id | varchar | 用户ID |
| trade_name | varchar | 交易名称 |
| trade_type | varchar | 交易类型 |
| trade_amount | decimal | 交易金额 |
| out_business_no | varchar | 业务仿重ID |

#### 3.2.9用户行为返利订单表 (user_behavior_rebate_order_)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| user_id | varchar | 用户ID |
| order_id | varchar | 订单ID |
| behavior_type | varchar | 行为类型 |
| rebate_desc | varchar | 返利描述 |
| rebate_type | varchar | 返利类型 |
| rebate_config | varchar | 返利配置 |
| out_business_no | varchar | 业务仿重ID |
| biz_id | varchar | 业务ID |

## 4. 抽奖流程数据流转

### 4.1 抽奖主流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            抽奖主流程                                        │
└─────────────────────────────────────────────────────────────────────────────┘

  用户请求
      │
      ▼
  ┌─────────────────┐
  │ 1. Token校验    │
  │   (auth_service)│
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │ 2. 降级检查     │
  │   (degradeSwitch)│
  └────────┬────────┘
           │
           ▼
  ┌────────────────────────────┐
  │ 3. 参与活动 - 创建订单     │
  │   raffleActivityPartakeService│
  │   ┌──────────────────────┐ │
  │   │ActivityAccount检查   │ │
  │   │ActivityAccountMonth  │ │
  │   │ActivityAccountDay    │ │
  │   └──────────────────────┘ │
  │      │                      │
  │      ▼                      │
  │   创建UserRaffleOrder      │
  └────────┬───────────────────┘
           │
           ▼
  ┌────────────────────────────┐
  │ 4. 抽奖策略 - 执行抽奖     │
  │   raffleStrategy           │
  │   ┌──────────────────────┐ │
  │   │责任链抽奖计算         │ │
  │   │  - rule_blacklist    │ │
  │   │  - rule_weight       │ │
  │   └──────────────────────┘ │
  │      │                      │
  │      ▼                      │
  │   ┌──────────────────────┐ │
  │   │规则树抽奖过滤         │ │
  │   │  - rule_lock (次数锁) │ │
  │   │  - rule_stock (库存) │ │
  │   │  - rule_luck_award   │ │
  │   └───────┬──────────────┘ │
  │           │                │
  │           ▼                │
  │   返回RaffleAwardEntity   │
  └────────┬───────────────────┘
           │
           ▼
  ┌────────────────────────────┐
  │ 5. 保存中奖记录            │
  │   awardService             │
  │   ┌──────────────────────┐ │
  │   │创建UserAwardRecord   │ │
  │   │保存到user_award_record│ │
  │   └──────────────────────┘ │
  └────────┬───────────────────┘
           │
           ▼
  ┌────────────────────────────┐
  │ 6. 发奖（异步MQ）          │
  │   发送MQ消息               │
  │   更新Task表               │
  └────────────────────────────┘
```

### 4.2 责任链抽奖流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          责任链抽奖流程                                      │
└─────────────────────────────────────────────────────────────────────────────┘

                ┌──────────────────┐
                │  ILogicChain     │
                │  启动点          │
                └────────┬─────────┘
                         │
                         ▼
              ┌────────────────────┐
              │ rule_blacklist     │◄─── 黑名单责任链
              │ 检查用户是否在黑名单 │
              └────────┬───────────┘
                       │
           ┌───────────┴───────────┐
           │ 规则命中,接管抽奖     │
           │ 返回指定奖品ID        │
           └───────────┬───────────┐
                       ▼
              ┌────────────────────┐
              │ rule_weight        │◄─── 权重责任链
              │ 根据用户积分权重   │
              │ 匹配奖品范围       │
              └────────┬───────────┘
                       │
           ┌───────────┴───────────┐
           │ 规则命中,接管抽奖     │
           │ 返回权重对应奖品ID    │
           └───────────┬───────────┐
                       ▼
              ┌────────────────────┐
              │ rule_default       │◄─── 默认规则
              │ 进入规则树抽奖     │
              └────────────────────┘
```

### 4.3 规则树抽奖流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         规则树抽奖流程                                       │
└─────────────────────────────────────────────────────────────────────────────┘

  规则树节点执行流程
      │
      ▼
  ┌─────────────────┐
  │ rule_lock       │◄──── 次数锁节点
  │ 检查用户当天抽奖 │
  │ 次数是否达到上限 │
  └────────┬────────┘
           │
    ┌──────┴──────┐
    │ 达到上限     │
    │ 返回锁定     │
    └──────┬──────┘
           ▼
    ┌─────────────────┐
    │ rule_stock      │◄──── 库存扣减节点
    │ 扣减奖品库存    │
    └────────┬────────┘
             │
      ┌──────┴──────┐
      │ 库存不足    │
      │ 返回放行    │
      └──────┬──────┘
             ▼
    ┌─────────────────┐
    │ rule_luck_award │◄──── 兜底奖品节点
    │ 配置兜底奖品    │
    └─────────┬───────┘
              │
              ▼
      ┌─────────────────┐
      │ 返回最终奖品ID  │
      └─────────────────┘
```

### 4.4 订单创建数据流转

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         订单创建数据流转                                     │
└─────────────────────────────────────────────────────────────────────────────┘

   创建订单请求
        │
        ▼
  ┌──────────────────┐
  │ 1. 检查总账户    │
  │ raffle_activity_account│
  │ 查询 total_count │
  │ 查询 total_count_surplus│
  └────────┬─────────┘
           │
      ┌────┴────┐
      │ 有额度  │
      └────┬────┘
           ▼
  ┌──────────────────┐
  │ 2. 检查月账户    │
  │ raffle_activity_account_month│
  │ 查询月剩余次数   │
  └────────┬─────────┘
           │
      ┌────┴────┐
      │ 有额度  │
      └────┬────┘
           ▼
  ┌──────────────────┐
  │ 3. 检查日账户    │
  │ raffle_activity_account_day│
  │ 查询日剩余次数   │
  └────────┬─────────┘
           │
      ┌────┴────┐
      │ 有额度  │
      └────┬────┘
           ▼
  ┌──────────────────┐
  │ 4. 扣减次数      │
  │ - total_count_surplus-1│
  │ - month_count_surplus-1│
  │ - day_count_surplus-1  │
  └────────┬─────────┘
           ▼
  ┌──────────────────┐
  │ 5. 创建订单      │
  │ UserRaffleOrder  │
  └──────────────────┘
```

### 4.5 发奖异步流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           发奖异步流程                                       │
└─────────────────────────────────────────────────────────────────────────────┘

   保存中奖记录
        │
        ▼
  ┌──────────────────┐
  │ 构建MQ消息       │
  │ SendAwardMessage │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ 创建Task任务     │
  │ state=create     │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ 发送MQ消息       │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ MQ消费端         │
  │ 消费消息         │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ 根据award_key    │
  │ 选择 Distributor │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ 执行发奖         │
  │ - 积分调整       │
  │ - SKU配送       │
  │ - 其他奖品类型   │
  └──────────────────┘
```

### 4.6 积分兑换流程（完整版）

#### 4.6.1 流程概述

用户使用积分兑换抽奖次数的完整流程，分为三个核心阶段：

1. **阶段一：创建待支付活动订单** - 在活动订单表创建记录，状态为待支付
2. **阶段二：积分账户交易** - 扣减用户积分，发送MQ消息
3. **阶段三：消费MQ充值额度** - 异步消费消息，真正充值活动账户额度

**关键设计要点**：
- 用户真正拿到可用抽奖次数是在**阶段三**，而不是创建订单时
- 采用最终一致性，通过MQ解耦积分扣减和活动充值
- 使用 `out_business_no` 实现幂等性，防止重复充值

#### 4.6.2 完整流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       积分兑换完整流程                                       │
└─────────────────────────────────────────────────────────────────────────────┘

  用户请求 (userId, sku)
      │
      ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ 阶段一：创建待支付活动订单                                   │
  │ 入口：RaffleActivityController.creditPayExchangeSku()       │
  │                                                              │
  │ 1. 查询未支付订单 (raffle_activity_order_)                   │
  │    - 条件：userId + outBusinessNo                           │
  │    - 避免重复下单                                           │
  │    - 如果存在未支付订单，直接返回                            │
  │                                                              │
  │ 2. 查询SKU信息 (raffle_activity_sku)                        │
  │    - 获取：activityId, activityCountId, productAmount       │
  │                                                              │
  │ 3. 查询活动信息 (raffle_activity)                           │
  │    - 获取：activityName, strategyId                         │
  │                                                              │
  │ 4. 查询次数配置 (raffle_activity_count)                     │
  │    - 获取：totalCount, dayCount, monthCount                 │
  │                                                              │
  │ 5. 活动规则校验 (责任链)                                    │
  │    - 检查活动状态、时间、库存等                              │
  │                                                              │
  │ 6. 创建活动订单 (raffle_activity_order_)                    │
  │    插入记录：                                                │
  │    - order_id (12位随机数)                                  │
  │    - user_id, sku, activity_id                             │
  │    - total_count, day_count, month_count (应充值次数)       │
  │    - pay_amount (积分金额)                                 │
  │    - state = 'wait_pay' (待支付)                           │
  │    - out_business_no (业务单号，用于幂等)                   │
  │                                                              │
  │ ⚠️ 重要：此阶段不更新活动账户额度！                          │
  └────────────────────────────┬────────────────────────────────┘
                               │
                               ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ 阶段二：积分账户交易（扣减积分）                             │
  │ 入口：CreditAdjustService.createOrder()                     │
  │                                                              │
  │ 1. 校验积分余额                                             │
  │    - 查询：user_credit_account                              │
  │    - 判断：available_amount >= pay_amount                   │
  │    - 余额不足则抛异常                                       │
  │                                                              │
  │ 2. 事务内操作：                                             │
  │    a) 更新/创建积分账户 (user_credit_account)               │
  │       - 不存在则：insert (adjustAmount = -pay_amount)       │
  │       - 已存在则：updateAddAmount (-pay_amount)             │
  │                                                              │
  │    b) 写入积分订单 (user_credit_order_)                     │
  │       - order_id (12位随机数)                              │
  │       - trade_name = '兑换抽奖'                            │
  │       - trade_type = 'reverse' (逆向，扣减)                │
  │       - trade_amount = -pay_amount                         │
  │       - out_business_no (与活动订单相同)                    │
  │                                                              │
  │    c) 写入任务表 (task)                                    │
  │       - topic = 'credit_adjust_success'                     │
  │       - message = JSON格式MQ消息                            │
  │       - state = 'create'                                    │
  │                                                              │
  │ 3. 发送MQ消息（事务外）                                     │
  │    - 发送成功：更新 task.state = 'completed'                │
  │    - 发送失败：更新 task.state = 'fail' (补偿重试)          │
  │                                                              │
  │ 消息内容：                                                   │
  │ {                                                           │
  │   "userId": "xxx",                                          │
  │   "orderId": "积分订单ID",                                   │
  │   "amount": -100,                                           │
  │   "outBusinessNo": "业务单号"                               │
  │ }                                                           │
  └────────────────────────────┬────────────────────────────────┘
                               │
                               ▼
                          ┌─────────┐
                          │ MQ 队列 │
                          │ RabbitMQ│
                          └────┬────┘
                               │
                               ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ 阶段三：消费MQ消息，充值活动账户额度                         │
  │ 入口：CreditAdjustSuccessCustomer.listener()                │
  │                                                              │
  │ MQ 消费端监听：credit_adjust_success                         │
  │    ↓                                                        │
  │ raffleActivityAccountQuotaService.updateOrder()             │
  │                                                              │
  │ 事务内操作：                                                 │
  │                                                              │
  │ 1. 查询活动订单 (raffle_activity_order_)                     │
  │    - 条件：userId + out_business_no                         │
  │    - 验证订单状态是否为 wait_pay                            │
  │                                                              │
  │ 2. 更新活动订单状态                                         │
  │    - 更新：state = 'completed'                              │
  │    - 如果订单已完成或不存在，则回滚（幂等）                 │
  │                                                              │
  │ 3. 充值总账户 (raffle_activity_account_)                    │
  │    - 不存在则：insert                                       │
  │      total_count = N, total_count_surplus = N              │
  │    - 已存在则：updateAccountQuota                           │
  │      total_count_surplus += N                               │
  │    (N = 订单中的 total_count)                               │
  │                                                              │
  │ 4. 充值月账户 (raffle_activity_account_month_)               │
  │    - addAccountQuota: month_count_surplus += M              │
  │    (M = 订单中的 month_count)                               │
  │                                                              │
  │ 5. 充值日账户 (raffle_activity_account_day_)                 │
  │    - addAccountQuota: day_count_surplus += D                │
  │    (D = 订单中的 day_count)                                 │
  │                                                              │
  │ ✅ 完成：用户现在拥有可用抽奖次数！                          │
  └─────────────────────────────────────────────────────────────┘
```

#### 4.6.3 库表变更明细

| 阶段 | 表名 | 操作 | 字段变化 |
|------|------|------|----------|
| **阶段一** | raffle_activity_order_ | INSERT | order_id, user_id, sku, activity_id, total_count, day_count, month_count, pay_amount, **state='wait_pay'**, out_business_no |
| **阶段二** | user_credit_account | INSERT/UPDATE | adjustAmount -= pay_amount |
| | user_credit_order_ | INSERT | order_id, user_id, trade_name='兑换抽奖', trade_type='reverse', trade_amount=-pay_amount, out_business_no |
| | task | INSERT | topic='credit_adjust_success', state='create', message=MQ消息内容 |
| | task | UPDATE | state='completed' (MQ发送成功后) |
| **阶段三** | raffle_activity_order_ | UPDATE | **state='completed'** (userId + out_business_no) |
| | raffle_activity_account_ | INSERT/UPDATE | total_count_surplus += total_count |
| | raffle_activity_account_month_ | UPDATE | month_count_surplus += month_count |
| | raffle_activity_account_day_ | UPDATE | day_count_surplus += day_count |

#### 4.6.4 关键代码映射

| 功能 | 代码位置 | 核心方法 |
|------|----------|----------|
| 创建待支付订单 | ActivityRepository.doSaveCreditPayOrder() | 插入 raffle_activity_order_, state=wait_pay |
| 扣减积分 | CreditRepository.saveUserCreditTradeOrder() | 更新 user_credit_account, 插入 user_credit_order_ |
| 发送MQ消息 | CreditRepository (事务外) | eventPublisher.publish(credit_adjust_success) |
| 消费MQ消息 | CreditAdjustSuccessCustomer.listener() | 监听 credit_adjust_success 主题 |
| 充值活动额度 | ActivityRepository.updateOrder() | 更新订单状态，充值三个账户表 |

#### 4.6.5 幂等性保证

使用 `out_business_no`（业务单号）实现幂等性：

1. **阶段一**：查询是否存在相同 userId + outBusinessNo 的未支付订单
2. **阶段二**：积分订单和任务表使用 outBusinessNo 关联
3. **阶段三**：查询订单时使用 outBusinessNo，如果订单已完成则直接返回

```sql
-- 防重复下单
SELECT * FROM raffle_activity_order_ 
WHERE user_id = ? AND out_business_no = ? AND state = 'wait_pay'

-- 更新订单（幂等）
UPDATE raffle_activity_order_ 
SET state = 'completed' 
WHERE user_id = ? AND out_business_no = ? AND state = 'wait_pay'
```

#### 4.6.6 流程对照表

| 方法 | 功能 | 是否充值活动额度 | 库表操作 |
|------|------|-----------------|----------|
| doSaveCreditPayOrder() | 创建待支付订单 | ❌ | INSERT raffle_activity_order_ (wait_pay) |
| saveUserCreditTradeOrder() | 扣减积分+发MQ | ❌ | UPDATE/INSERT user_credit_account, INSERT user_credit_order_, INSERT task |
| updateOrder() | 充值活动额度 | ✅ | UPDATE raffle_activity_order_ (completed), INSERT/UPDATE raffle_activity_account_, UPDATE raffle_activity_account_month_, UPDATE raffle_activity_account_day_ |

#### 4.6.7 关键实体类

| 实体类 | 说明 | 关键字段 |
|--------|------|----------|
| SkuRechargeEntity | SKU充值请求 | userId, sku, outBusinessNo, orderTradeType=credit_pay_trade |
| UnpaidActivityOrderEntity | 未支付订单响应 | userId, orderId, outBusinessNo, payAmount |
| TradeEntity | 积分交易请求 | userId, tradeName=CONVERT_SKU, tradeType=REVERSE, amount=-payAmount, outBusinessNo |
| TradeAggregate | 积分交易聚合 | creditAccountEntity, creditOrderEntity, taskEntity |
| DeliveryOrderEntity | 发货单（MQ消费） | userId, outBusinessNo |

#### 4.6.8 交易类型说明

| 交易类型 | 代码 | 说明 |
|----------|------|------|
| OrderTradeTypeVO | credit_pay_trade | 积分兑换，需要支付类交易 |
| OrderTradeTypeVO | rebate_no_pay_trade | 返利奖品，不需要支付类交易 |
| TradeTypeVO | REVERSE | 逆向交易，扣减积分 |
| TradeTypeVO | FORWARD | 正向交易，增加积分 |
| TradeNameVO | CONVERT_SKU | 兑换抽奖 |
| TradeNameVO | REBATE | 行为返利 |

#### 4.6.9 订单状态流转

```
wait_pay (待支付) 
    ↓
[阶段二：扣减积分成功，发送MQ]
    ↓
[阶段三：MQ消费，充值额度]
    ↓
completed (完成)
```

**注意**：订单从 wait_pay 到 completed 的状态转换，必须在阶段三的事务中完成，确保积分扣减和活动充值的一致性。

#### 4.6.10 异常处理

| 异常场景 | 处理方式 |
|----------|----------|
| 重复下单 | 查询未支付订单，如果存在则直接返回 |
| 积分余额不足 | 阶段二校验，抛出异常，不创建积分订单 |
| MQ发送失败 | 更新task状态为fail，后续补偿机制重试 |
| 订单不存在 | 阶段三查询订单不存在时，记录日志并返回（幂等） |
| 订单已完成 | 阶段三更新订单时影响行数为0，事务回滚（幂等） |

### 4.7 签到领取积分流程（完整版）

#### 4.7.1 流程概述

用户签到后，系统根据返利配置发放奖励（可能是积分或抽奖次数）。

**与积分兑换的区别**：
- **积分兑换**：扣减积分 → 增加抽奖次数（积分换次数）
- **签到领取积分**：签到动作 → 增加积分（白送积分）

**核心流程**：
1. 用户签到，创建返利订单
2. 发送MQ消息（send_rebate）
3. 消费MQ消息，根据返利类型发放奖励
   - integral：增加积分
   - sku：增加抽奖次数（直接充值，不需要扣积分）

#### 4.7.2 完整流程图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       签到领取积分完整流程                                    │
└─────────────────────────────────────────────────────────────────────────────┘

  用户签到请求 (userId)
      │
      ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ 阶段一：创建签到返利订单                                     │
  │ 入口：RaffleActivityController.calendarSignRebate()         │
  │                                                              │
  │ 1. 构建行为实体                                               │
  │    BehaviorEntity:                                            │
  │    - userId = "用户ID"                                       │
  │    - behaviorTypeVO = SIGN (签到)                           │
  │    - outBusinessNo = "2024-12-19" (当前日期)                 │
  │                                                              │
  │ 2. 查询返利配置 (daily_behavior_rebate)                     │
  │    SELECT * FROM daily_behavior_rebate                       │
  │    WHERE behavior_type = 'sign'                             │
  │                                                              │
  │    返回配置项（可以配置多个返利）：                            │
  │    ┌────────┬──────────┬──────────────┬──────────────────┐   │
  │    │rebate_type│rebate_config│rebate_desc│说明              │   │
  │    ├────────┼──────────┼──────────────┼──────────────────┤   │
  │    │integral │ 10       │ 签到送10积分 │ 增加用户积分     │   │
  │    │sku      │ 901001   │ 送抽奖次数   │ 增加抽奖次数     │   │
  │    └────────┴──────────┴──────────────┴──────────────────┘   │
  │                                                              │
  │ 3. 为每个配置创建返利订单（支持一个签到送多种奖励）          │
  │                                                              │
  │    a) 创建 BehaviorRebateOrderEntity                         │
  │       - orderId (12位随机数)                                │
  │       - behaviorType = 'sign'                               │
  │       - rebateType = 'integral' 或 'sku'                    │
  │       - rebateConfig = '10' 或 '901001'                     │
  │       - outBusinessNo = "2024-12-19"                        │
  │       - bizId = "user_integral_2024-12-19" (幂等唯一ID)     │
  │                                                              │
  │    b) 创建 TaskEntity (MQ消息任务)                           │
  │       - topic = 'send_rebate'                               │
  │       - message = {                                          │
  │           userId, rebateType, rebateConfig, bizId           │
  │         }                                                    │
  │       - state = 'create'                                    │
  │                                                              │
  │ 4. 保存聚合对象                                              │
  │    INSERT user_behavior_rebate_order_ (返利订单)            │
  │    INSERT task (MQ任务)                                     │
  │                                                              │
  │ 5. 发送MQ消息                                                │
  │    发送到 send_rebate 主题                                   │
  └────────────────────────────┬────────────────────────────────┘
                               │
                               ▼
                          ┌─────────┐
                          │ MQ 队列 │
                          │ send_rebate
                          └────┬────┘
                               │
                               ▼
  ┌─────────────────────────────────────────────────────────────┐
  │ 阶段二：消费MQ消息，发放返利                                   │
  │ 入口：RebateMessageCustomer.listener()                      │
  │                                                              │
  │ 监听主题：send_rebate                                        │
  │                                                              │
  │ 根据 rebateType 分发处理：                                   │
  │                                                              │
  │ ┌──────────────────────────────────────────────────────────┐│
  │ │ case "integral" (发放积分) ← 签到送积分                   ││
  │ │                                                           ││
  │ │ 1. 构建积分交易实体                                       ││
  │ │    TradeEntity:                                           ││
  │ │    - userId = "用户ID"                                    ││
  │ │    - tradeName = REBATE (行为返利)                       ││
  │ │    - tradeType = FORWARD (正向交易，增加积分)            ││
  │ │    - amount = +10 (增加10积分) ✅ 关键：正向交易          ││
  │ │    - outBusinessNo = bizId (幂等)                        ││
  │ │                                                           ││
  │ │ 2. 调用 creditAdjustService.createOrder()                ││
  │ │    ⬇️ 这里是【增加积分】的核心逻辑！                        ││
  │ │                                                           ││
  │ │ 3. 事务内操作：                                           ││
  │ │    a) 更新/创建 user_credit_account                        ││
  │ │       不存在则：insert                                    ││
  │ │         adjustAmount = +10                               ││
  │ │       已存在则：updateAddAmount                           ││
  │ │         adjustAmount += 10 ✅ 增加积分                    ││
  │ │                                                           ││
  │ │    b) 插入 user_credit_order_                             ││
  │ │       - trade_name = '行为返利'                          ││
  │ │       - trade_type = 'forward' ✅ 正向交易               ││
  │ │       - trade_amount = +10                               ││
  │ │                                                           ││
  │ │    c) 插入 task                                           ││
  │ │       - topic = 'credit_adjust_success'                   ││
  │ │       - message = MQ消息内容                              ││
  │ │       - state = 'create'                                 ││
  │ │                                                           ││
  │ │ 4. 发送MQ消息（事务外）                                   ││
  │ │    发送到 credit_adjust_success 主题                      ││
  │ │    ⚠️ 注意：这个消息在"签到送积分"场景下没有实际作用       ││
  │ │    因为 CreditAdjustSuccessConsumer 是处理"积分兑换"的    ││
  │ │                                                           ││
  │ │ ✅ 完成：用户积分增加10分！                                ││
  │ └──────────────────────────────────────────────────────────┘│
  │                                                              │
  │ ┌──────────────────────────────────────────────────────────┐│
  │ │ case "sku" (发放抽奖次数) ← 签到送抽奖次数                ││
  │ │                                                           ││
  │ │ 1. 构建SKU充值实体                                        ││
  │ │    SkuRechargeEntity:                                     ││
  │ │    - userId = "用户ID"                                    ││
  │ │    - sku = 901001                                        ││
  │ │    - outBusinessNo = bizId                               ││
  │ │    - orderTradeType = rebate_no_pay_trade ✅ 返利不支付  ││
  │ │                                                           ││
  │ │ 2. 调用 raffleActivityAccountQuotaService.createOrder()  ││
  │ │    ⬇️ 这里是【直接充值】，不需要扣积分！                    ││
  │ │                                                           ││
  │ │ 3. 查询SKU信息、活动信息、次数配置                         ││
  │ │                                                           ││
  │ │ 4. 活动规则校验（责任链）                                  ││
  │ │                                                           ││
  │ │ 5. 直接充值活动账户（与"返利不支付"策略对应）            ││
  │ │    插入 raffle_activity_order_ (state=completed) ✅ 直接完成││
  │ │    充值 raffle_activity_account_                          ││
  │ │      total_count_surplus += 3                            ││
  │ │    充值 raffle_activity_account_month_                    ││
  │ │      month_count_surplus += 1                            ││
  │ │    充值 raffle_activity_account_day_                      ││
  │ │      day_count_surplus += 1                              ││
  │ │                                                           ││
  │ │ ✅ 完成：用户获得3次抽奖机会！                              ││
  │ └──────────────────────────────────────────────────────────┘│
  └─────────────────────────────────────────────────────────────┘
```

#### 4.7.3 库表变更明细

| 阶段 | 表名 | 操作 | 字段变化 |
|------|------|------|----------|
| **阶段一** | user_behavior_rebate_order_ | INSERT | orderId, userId, behaviorType='sign', rebateType='integral', rebateConfig='10', outBusinessNo, bizId |
| | task | INSERT | topic='send_rebate', state='create', message=MQ消息内容 |
| **阶段二（integral）** | user_credit_account | INSERT/UPDATE | **adjustAmount += 10** ✅ 增加积分 |
| | user_credit_order_ | INSERT | order_id, userId, trade_name='行为返利', trade_type='forward', **trade_amount=+10**, out_businessNo |
| | task | INSERT | topic='credit_adjust_success', state='create' |
| **阶段二（sku）** | raffle_activity_order_ | INSERT | order_id, userId, sku, activity_id, total_count=3, day_count=1, month_count=1, **state='completed'** |
| | raffle_activity_account_ | INSERT/UPDATE | **total_count_surplus += 3** |
| | raffle_activity_account_month_ | UPDATE | **month_count_surplus += 1** |
| | raffle_activity_account_day_ | UPDATE | **day_count_surplus += 1** |

#### 4.7.4 关键代码映射

| 功能 | 代码位置 | 核心方法 |
|------|----------|----------|
| 创建签到返利订单 | BehaviorRebateService.createOrder() | 查询返利配置，创建 BehaviorRebateOrderEntity，发送MQ |
| 消费返利消息 | RebateMessageCustomer.listener() | 监听 send_rebate 主题 |
| 发放积分 | RebateMessageCustomer → CreditAdjustService | creditAdjustService.createOrder(tradeType=FORWARD) |
| 发放抽奖次数 | RebateMessageCustomer → RaffleActivityAccountQuotaService | createOrder(orderTradeType=rebate_no_pay_trade) |

#### 4.7.5 交易类型对比

| 场景 | TradeTypeVO | OrderTradeTypeVO | 说明 |
|------|-------------|------------------|------|
| **签到送积分** | FORWARD (正向) | - | 增加积分 |
| **签到送抽奖次数** | - | rebate_no_pay_trade | 直接充值，不扣积分 |
| **积分兑换抽奖次数** | REVERSE (逆向) | credit_pay_trade | 扣减积分，兑换抽奖次数 |

#### 4.7.6 与积分兑换的关键区别

| 对比项 | 签到领取积分 | 积分兑换抽奖次数 |
|--------|-------------|-----------------|
| **用户动作** | 签到 | 用积分兑换 |
| **积分变化** | **+10 (增加)** ✅ | -10 (扣减) |
| **抽奖次数** | - | +3次 |
| **TradeTypeVO** | **FORWARD** (正向) | REVERSE (逆向) |
| **OrderTradeTypeVO** | rebate_no_pay_trade | credit_pay_trade |
| **订单状态** | - | wait_pay → completed |
| **MQ消息** | send_rebate → (积分增加) | credit_adjust_success → (充值活动账户) |
| **目的** | 奖励用户，增加积分 | 消耗积分，获得抽奖次数 |

#### 4.7.7 返利配置表

**daily_behavior_rebate** 表结构：

| 字段名 | 说明 | 示例 |
|--------|------|------|
| behavior_type | 行为类型 | sign (签到)、openai_pay (支付) |
| rebate_type | 返利类型 | integral (积分)、sku (抽奖次数) |
| rebate_config | 返利配置 | 10 (积分值)、901001 (SKU) |
| rebate_desc | 返利描述 | 签到送10积分、签到送抽奖次数 |
| state | 状态 | open (开启)、close (关闭) |

**配置示例**：

```sql
-- 签到送10积分
INSERT INTO daily_behavior_rebate (behavior_type, rebate_type, rebate_config, rebate_desc, state)
VALUES ('sign', 'integral', '10', '签到送10积分', 'open');

-- 签到送抽奖次数（SKU 901001）
INSERT INTO daily_behavior_rebate (behavior_type, rebate_type, rebate_config, rebate_desc, state)
VALUES ('sign', 'sku', '901001', '签到送抽奖次数', 'open');
```

#### 4.7.8 幂等性保证

使用 `bizId` (业务唯一ID) 实现幂等性：

```
bizId格式：{userId}_{rebateType}_{outBusinessNo}
示例：user001_integral_2024-12-19
```

1. **阶段一**：创建返利订单时使用 bizId
2. **阶段二**：消费MQ消息时通过 bizId 判断是否已处理
3. **数据库唯一索引**：user_behavior_rebate_order_ 表的 bizId 字段有唯一索引

#### 4.7.9 核心实体类

| 实体类 | 说明 | 关键字段 |
|--------|------|----------|
| BehaviorEntity | 行为实体 | userId, behaviorTypeVO, outBusinessNo |
| BehaviorRebateOrderEntity | 返利订单实体 | orderId, behaviorType, rebateType, rebateConfig, outBusinessNo, bizId |
| BehaviorRebateAggregate | 返利聚合对象 | behaviorRebateOrderEntity, taskEntity |
| SendRebateMessageEvent.RebateMessage | 返利消息 | userId, rebateType, rebateConfig, bizId |

#### 4.7.10 异常处理

| 异常场景 | 处理方式 |
|----------|----------|
| 重复签到 | bizId 唯一索引冲突，返回 INDEX_DUP 错误码 |
| 返利配置不存在 | 返回空订单列表，不抛异常 |
| SKU库存不足 | 记录警告日志，丢弃消息，避免MQ无限重试 |
| userId为null | 防御性检查，直接丢弃消息 |

#### 4.7.11 业务语义总结

**签到领取积分的业务本质**：

1. **用户行为**：签到（每天一次）
2. **系统奖励**：
   - integral 类型：增加积分（白送积分）
   - sku 类型：增加抽奖次数（白送次数）
3. **数据流转**：
   - 签到 → 创建返利订单 → 发送MQ → 消费MQ → **增加积分**

**与积分兑换的对比**：

```
签到领取积分：
  用户签到 → 系统奖励 → 增加积分 (✅ 用户获得积分)

积分兑换抽奖次数：
  用户支付积分 → 系统充值 → 增加抽奖次数 (✅ 用户获得抽奖次数，消耗积分)
```

所以签到领取积分就是一个**奖励行为**，用户什么都不用付出（除了签到动作），就能获得积分！

## 5. 核心实体类对应关系

### 5.1 活动领域实体

| 实体类 | 对应表 | 说明 |
|--------|--------|------|
| ActivityEntity | raffle_activity | 活动主表 |
| ActivitySkuEntity | raffle_activity_sku | 活动SKU表 |
| ActivityAccountEntity | raffle_activity_account | 活动账户表 |
| ActivityAccountDayEntity | raffle_activity_account_day | 日账户表 |
| ActivityAccountMonthEntity | raffle_activity_account_month | 月账户表 |
| ActivityOrderEntity | raffle_activity_order | 活动订单表 |
| UserRaffleOrderEntity | user_raffle_order | 用户抽奖订单表 |
| ActivityCountEntity | raffle_activity_count | 活动次数配置表 |

### 5.2 策略领域实体

| 实体类 | 对应表 | 说明 |
|--------|--------|------|
| StrategyEntity | strategy | 策略表 |
| StrategyAwardEntity | strategy_award | 策略奖品表 |
| StrategyRuleEntity | strategy_rule | 策略规则表 |
| RaffleAwardEntity | - | 抽奖结果聚合实体（非独立表） |
| RaffleFactorEntity | - | 抽奖因子DTO（非独立表） |
| AwardEntity | award | 奖品表 |

### 5.3 奖品领域实体

| 实体类 | 对应表 | 说明 |
|--------|--------|------|
| UserAwardRecordEntity | user_award_record | 用户中奖记录表 |
| DistributeAwardEntity | - | 发奖聚合实体（非独立表） |
| UserCreditAwardEntity | - | 积分奖品实体（非独立表） |
| TaskEntity | task | 任务表 |

### 5.4 积分领域实体

| 实体类 | 对应表 | 说明 |
|--------|--------|------|
| CreditAccountEntity | user_credit_account | 用户积分账户表 |
| CreditOrderEntity | user_credit_order | 积分订单表 |
| TradeEntity | - | 交易聚合实体（非独立表） |

### 5.5 返利领域实体

| 实体类 | 对应表 | 说明 |
|--------|--------|------|
| BehaviorEntity | - | 行为聚合实体（非独立表） |
| BehaviorRebateOrderEntity | user_behavior_rebate_order | 行为返利订单表 |

## 6. 关键业务关系说明

### 6.1 活动与策略关系
- **raffle_activity** 与 **strategy** 是多对一关系
- 一个策略可以被多个活动使用
- 一个活动只使用一个策略

### 6.2 策略与奖品关系
- **strategy** 与 **strategy_award** 是一对多关系
- 一个策略包含多个奖品配置
- 每个奖品有独立的中奖概率和库存

### 6.3 用户参与活动关系
```
用户 → raffle_activity_order (购买SKU) → raffle_activity_account (账户额度)
                                             ↓
                                        user_raffle_order (抽奖订单)
                                             ↓
                                        user_award_record (中奖记录)
```

### 6.4 抽奖次数消耗
- 抽奖时从 **raffle_activity_account** 扣减总次数
- 同时从 **raffle_activity_account_day** 扣减日次数
- 同时从 **raffle_activity_account_month** 扣减月次数
- 任一额度不足都会阻止抽奖

### 6.5 库存扣减机制
- 库存扣减通过延迟队列实现
- 抽奖成功后发送消息到队列
- Job消费队列更新库存
- 避免库存超扣

## 7. 分库分表说明

### 7.1 分库策略
- 按 user_id 取模分 4 个库
- 库命名：big_market_01, big_market_02, big_market_03, big_market_04

### 7.2 分表策略
- 每个库再按 user_id 取模分 4 张表
- 表命名：_000, _001, _002, _003

### 7.3 分库分表表清单
- raffle_activity_account (_000~_003)
- raffle_activity_order (_000~_003)
- user_raffle_order (_000~_003)
- user_award_record (_000~_003)
- user_credit_order (_000~_003)
- user_behavior_rebate_order (_000~_003)

## 8. 数据流转时序图

### 8.1 抽奖完整时序

```
┌────────────┐  ┌─────────────────┐  ┌──────────────────┐  ┌────────────┐
│ Controller │  │ PartakeService  │  │  StrategyService │  │ AwardService│
└──────┬─────┘  └────────┬────────┘  └──────────┬───────┘  └──────┬─────┘
       │                 │                      │                 │
       │ 1. createOrder  │                      │                 │
       │────────────────>│                      │                 │
       │                 │                      │                 │
       │                 │ 2. check quota       │                 │
       │                 │─────────────────────>│                 │
       │                 │                      │                 │
       │                 │ 3. query account     │                 │
       │                 │<─────────────────────│                 │
       │                 │                      │                 │
       │                 │ 4. build order       │                 │
       │                 │<─────────────────────│                 │
       │                 │                      │                 │
       │ 5. return order │                      │                 │
       │<────────────────│                      │                 │
       │                 │                      │                 │
       │ 6. performRaffle│                      │                 │
       │────────────────>│                      │                 │
       │                 │                      │                 │
       │                 │ 7. logic chain       │                 │
       │                 │─────────────────────>│                 │
       │                 │                      │                 │
       │                 │ 8. logic tree        │                 │
       │                 │<─────────────────────│                 │
       │                 │                      │                 │
       │ 9. return award │                      │                 │
       │<────────────────│                      │                 │
       │                 │                      │                 │
       │ 10. save record │                      │                 │
       │────────────────>│                      │                 │
       │                 │                      │                 │
       │                 │ 11. save to db       │                 │
       │                 │─────────────────────>│                 │
       │                 │                      │                 │
       │                 │ 12. send MQ message  │                 │
       │                 │────────────────────────────────────────>│
       │                 │                      │                 │
```

## 9. 关键配置说明

### 9.1 规则模型类型
- `rule_weight`: 权重抽奖
- `rule_blacklist`: 黑名单指定
- `rule_lock`: 次数锁
- `rule_stock`: 库存扣减
- `rule_luck_award`: 兜底奖品

### 9.2 抽奖规则示例
```
rule_weight: 4000:102,103,104,105 5000:102,103,104,105,106,107
rule_blacklist: 101:user_001,user_002
rule_lock: 5 (抽奖5次后解锁)
```

## 10. 附录

### 10.1 表前缀说明
- `raffle_activity_`: 活动相关表
- `user_`: 用户相关表
- `strategy_`: 策略相关表
- `credit_`: 积分相关表
- `behavior_`: 行为返利相关表

### 10.2 状态枚举
- 订单状态：create(创建), used(已使用), cancel(已作废)
- 奖品状态：create(创建), completed(发奖完成)
- 活动状态：close(关闭), open(开启), running(进行中)
