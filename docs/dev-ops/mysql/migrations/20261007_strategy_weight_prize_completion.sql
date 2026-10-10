-- Follow-up to strategy_weight_intervals: preserve higher prizes at every tier.
-- 0..19: all prizes; 20..99: all balance prizes; >=100: all balance prizes except 0.10.
UPDATE big_market.strategy_rule
SET rule_value='0:101,1110,1111,1112,1113,1114,1115,1116 20:1110,1111,1112,1113,1114,1115,1116 100:1111,1112,1113,1114,1115,1116',
 rule_desc='积分区间：0至不足20全奖池；20至不足100仅去掉积分奖；100及以上再去掉0.10元；保留全部高额奖及解锁检查',
 update_time=NOW()
WHERE strategy_id=10008 AND rule_model='rule_weight';
-- Evict strategy_rule_weight_key_10008, re-armory 10008 before accepting draws.
-- Preserve all live inventory keys.
