-- Local demo: activity 100401 is the only activity using strategy 10008.
-- Deploy the weight->rule-tree safety fix BEFORE enabling this rule.
-- Current code matches exact integer available credit, NOT >= thresholds.
-- Snapshot before first application: strategy.rule_models=NULL; no rule_weight row.
-- Afterwards evict only the strategy config caches, restart to clear in-process
-- responsibility-chain cache, then call strategy_armory?strategyId=10008.
START TRANSACTION;
INSERT INTO big_market.strategy_rule
(strategy_id,award_id,rule_type,rule_model,rule_value,rule_desc)
VALUES (10008,NULL,1,'rule_weight',
 '20:1110,1111,1112,1116 100:1111,1112,1116',
 '本地演示：当前可用积分整数值恰好20或100时进入指定余额奖池；不扣积分，非门槛匹配')
ON DUPLICATE KEY UPDATE rule_value=VALUES(rule_value),rule_desc=VALUES(rule_desc),update_time=NOW();
UPDATE big_market.strategy SET rule_models='rule_weight',update_time=NOW()
WHERE strategy_id=10008;
COMMIT;

-- Explicit rollback for this demo (execute separately, not with the above):
-- START TRANSACTION;
-- UPDATE big_market.strategy SET rule_models=NULL,update_time=NOW()
-- WHERE strategy_id=10008 AND rule_models='rule_weight';
-- DELETE FROM big_market.strategy_rule WHERE strategy_id=10008
-- AND rule_model='rule_weight'
-- AND rule_value='20:1110,1111,1112,1116 100:1111,1112,1116';
-- COMMIT;
-- Repeat cache eviction/restart/armory after rollback.
