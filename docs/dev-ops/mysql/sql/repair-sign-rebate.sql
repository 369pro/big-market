-- Local recovery for one verified affected user on shard big_market_02 / table 002.
-- Set @repair_user and @repair_day (YYYYMMDD) before sourcing this file.
-- Existing legacy or corrected reward orders prevent a second grant.
-- Requeue the original outbox messages; never update account balances directly.
START TRANSACTION;

UPDATE big_market_02.task t
JOIN big_market_02.user_behavior_rebate_order_002 r
  ON r.user_id = t.user_id
 AND r.rebate_type = JSON_UNQUOTE(JSON_EXTRACT(t.message, '$.data.rebateType'))
 AND r.rebate_config = JSON_UNQUOTE(JSON_EXTRACT(t.message, '$.data.rebateConfig'))
LEFT JOIN big_market_02.user_credit_order_002 c
  ON c.user_id = r.user_id AND c.out_business_no IN (r.out_business_no, r.biz_id)
LEFT JOIN big_market_02.raffle_activity_order_002 a
  ON a.user_id = r.user_id AND a.out_business_no IN (r.out_business_no, r.biz_id)
SET t.message = JSON_SET(t.message, '$.data.bizId', r.biz_id),
    t.state = 'fail', t.update_time = NOW()
WHERE t.user_id = @repair_user
  AND t.topic = 'send_rebate'
  AND r.behavior_type = 'sign'
  AND r.out_business_no = @repair_day
  AND JSON_UNQUOTE(JSON_EXTRACT(t.message, '$.data.bizId')) = @repair_day
  AND ((r.rebate_type = 'integral' AND c.id IS NULL)
    OR (r.rebate_type = 'sku' AND a.id IS NULL));

SELECT ROW_COUNT() AS requeued_rewards;
COMMIT;
