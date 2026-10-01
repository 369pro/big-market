-- Strategy-award RocketMQ consumer inbox.
-- Run this additive migration once against the default `big_market` database
-- (db00); strategy_award is not stored in the user-sharded databases.

USE `big_market`;

CREATE TABLE IF NOT EXISTS `strategy_award_stock_inbox` (
  `event_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RocketMQ event envelope ID',
  `strategy_id` bigint(8) NOT NULL COMMENT '抽奖策略ID',
  `award_id` int(8) NOT NULL COMMENT '抽奖奖品ID',
  `surplus` int(8) NOT NULL COMMENT 'Redis 扣减后的库存快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次接收时间',
  PRIMARY KEY (`event_id`),
  KEY `idx_strategy_award` (`strategy_id`,`award_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='策略奖品库存消息消费幂等 inbox';
