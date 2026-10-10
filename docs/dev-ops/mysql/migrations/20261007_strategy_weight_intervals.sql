-- Supersedes exact-score demo configuration. Apply with floor-threshold code.
-- N:awardIds means lower bound N (inclusive), upper bound = next N (exclusive).
-- Last tier has no upper bound. Config order is irrelevant.
START TRANSACTION;
UPDATE big_market.strategy_rule
SET rule_value='0:101,1110,1111,1112,1113,1114,1115,1116 20:1110,1111,1112,1116 100:1111,1112,1116',
    rule_desc='可用积分区间：0≤积分<20完整奖池；20≤积分<100四种余额奖；积分≥100三种余额奖；不扣积分',
    update_time=NOW()
WHERE strategy_id=10008 AND rule_model='rule_weight';
UPDATE big_market.strategy SET rule_models='rule_weight',update_time=NOW() WHERE strategy_id=10008;
UPDATE big_market.strategy_rule
SET rule_desc='旧策略：积分<60默认池；[60,200)、[200,1000)、[1000,+∞)对应指定奖池；不扣积分',update_time=NOW()
WHERE strategy_id=100001 AND rule_model='rule_weight';
UPDATE big_market.strategy_rule
SET rule_desc='测试策略：积分<10默认池；[10,70)、[70,1000)、[1000,+∞)对应指定奖池；不扣积分',update_time=NOW()
WHERE strategy_id=100006 AND rule_model='rule_weight';
COMMIT;
-- Evict strategy_rule_weight_key_{10008,100001,100006} and big_market_strategy_key_10008.
-- Restart marketing service to clear in-process chains, then re-armory strategy 10008.
-- Do NOT delete live stock keys.
