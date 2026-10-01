-- Apply once before enabling requestId-aware draw requests.
-- request_id is nullable so existing raffle orders keep working. The
-- composite key is per user because all user raffle tables are sharded.

USE `big_market_01`;

ALTER TABLE `user_raffle_order_000`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
ALTER TABLE `user_raffle_order_001`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
ALTER TABLE `user_raffle_order_002`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
ALTER TABLE `user_raffle_order_003`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);

USE `big_market_02`;

ALTER TABLE `user_raffle_order_000`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
ALTER TABLE `user_raffle_order_001`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
ALTER TABLE `user_raffle_order_002`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
ALTER TABLE `user_raffle_order_003`
    ADD COLUMN `request_id` varchar(64) DEFAULT NULL COMMENT '用户请求幂等ID（SHA-256）' AFTER `order_id`,
    ADD UNIQUE KEY `uq_user_request_id` (`user_id`, `request_id`);
