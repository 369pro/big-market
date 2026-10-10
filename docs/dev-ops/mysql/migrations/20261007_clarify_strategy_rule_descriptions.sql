-- Correct descriptions only; retain existing strategy IDs, prize IDs and rule values.
UPDATE big_market.strategy_rule SET rule_desc =
 '旧策略：可用积分整数值等于60/200/1000时匹配对应奖池；不扣积分，非大于等于门槛',update_time=NOW()
WHERE strategy_id=100001 AND rule_model='rule_weight';
UPDATE big_market.strategy_rule SET rule_desc =
 '测试活动策略：可用积分整数值等于10/70/1000时匹配对应奖池；不扣积分，非大于等于门槛',update_time=NOW()
WHERE strategy_id=100006 AND rule_model='rule_weight';
UPDATE big_market.strategy_rule SET rule_desc =
 '演示黑名单：冒号前为兜底奖品ID，后为用户ID名单；命中直接返回101积分奖',update_time=NOW()
WHERE strategy_id IN (100001,100006) AND rule_model='rule_blacklist';
