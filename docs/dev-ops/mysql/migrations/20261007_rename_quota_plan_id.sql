-- Run after 20261007_rename_raffle_quota_tables.sql, with application stopped.
-- Back up both tables first. IDs and quota values are preserved.
SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE migrate_20261007_quota_plan_id()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='raffle_quota_plan' AND column_name='activity_count_id') THEN
        ALTER TABLE raffle_quota_plan RENAME COLUMN activity_count_id TO quota_plan_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='raffle_quota_product' AND column_name='activity_count_id') THEN
        ALTER TABLE raffle_quota_product RENAME COLUMN activity_count_id TO quota_plan_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='raffle_quota_plan' AND index_name='uq_activity_count_id') THEN
        ALTER TABLE raffle_quota_plan RENAME INDEX uq_activity_count_id TO uq_quota_plan_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='raffle_quota_product' AND index_name='idx_activity_id_activity_count_id') THEN
        ALTER TABLE raffle_quota_product RENAME INDEX idx_activity_id_activity_count_id TO idx_activity_id_quota_plan_id;
    END IF;
    ALTER TABLE raffle_quota_plan MODIFY COLUMN quota_plan_id bigint NOT NULL COMMENT '抽奖次数套餐业务编号；次数商品关联此编号';
    ALTER TABLE raffle_quota_product MODIFY COLUMN quota_plan_id bigint NOT NULL COMMENT '关联raffle_quota_plan.quota_plan_id；决定每份商品发放的次数额度';
END$$
DELIMITER ;
CALL migrate_20261007_quota_plan_id();
DROP PROCEDURE migrate_20261007_quota_plan_id;
