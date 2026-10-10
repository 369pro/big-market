-- Apply AFTER deploying the shared explicit-interval parser.
-- Left endpoint inclusive, right endpoint exclusive; +∞ means no upper bound.
-- Same award sets as the previous prize-completion migration; no odds change.
UPDATE big_market.strategy_rule
SET rule_value='[0,20):101,1110,1111,1112,1113,1114,1115,1116 [20,100):1110,1111,1112,1113,1114,1115,1116 [100,+∞):1111,1112,1113,1114,1115,1116',
    rule_desc='显式积分区间，左闭右开：[0,20)全奖池；[20,100)全部余额奖；[100,+∞)排除0.10元；保留解锁与库存检查',
    update_time=NOW()
WHERE strategy_id=10008 AND rule_model='rule_weight';
-- Refresh strategy_rule_weight_key_10008 and re-armory strategy 10008.
-- Do not delete inventory. Older strategies retain compatible threshold syntax.
