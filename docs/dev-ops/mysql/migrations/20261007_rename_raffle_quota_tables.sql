-- Run against the target marketing configuration database after backing it up.
-- Stop application writers first. This migration preserves all columns and rows.
SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE migrate_20261007_raffle_quota_names()
BEGIN
    DECLARE old_tables INT DEFAULT 0;
    DECLARE new_tables INT DEFAULT 0;
    SELECT COUNT(*) INTO old_tables FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
        AND table_name IN ('raffle_activity_count', 'raffle_activity_sku');
    SELECT COUNT(*) INTO new_tables FROM information_schema.tables
      WHERE table_schema = DATABASE()
        AND table_name IN ('raffle_quota_plan', 'raffle_quota_product');
    IF old_tables = 2 AND new_tables = 0 THEN
        RENAME TABLE raffle_activity_count TO raffle_quota_plan,
                     raffle_activity_sku TO raffle_quota_product;
    ELSEIF NOT (old_tables = 0 AND new_tables = 2) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected old/new quota tables; inspect schema before migration';
    END IF;
    ALTER TABLE raffle_activity COMMENT = '抽奖活动：名称、有效期、开放状态和抽奖策略';
    ALTER TABLE raffle_quota_plan COMMENT = '抽奖次数套餐：每份发放的总、日、月参与额度；不是AI对话额度';
    ALTER TABLE raffle_quota_product COMMENT = '抽奖次数商品：关联活动和次数套餐，配置积分价格及商品库存';
    ALTER TABLE raffle_activity MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键；不是活动业务编号';
    ALTER TABLE raffle_activity MODIFY COLUMN `activity_id` bigint NOT NULL COMMENT '活动业务编号；商品、上架配置和参与账户关联此编号';
    ALTER TABLE raffle_activity MODIFY COLUMN `activity_name` varchar(64) NOT NULL COMMENT '抽奖活动名称；用于用户端和运营端展示';
    ALTER TABLE raffle_activity MODIFY COLUMN `activity_desc` varchar(128) NOT NULL COMMENT '抽奖活动说明';
    ALTER TABLE raffle_activity MODIFY COLUMN `begin_date_time` datetime NOT NULL COMMENT '活动有效期开始时间；参与资格校验使用';
    ALTER TABLE raffle_activity MODIFY COLUMN `end_date_time` datetime NOT NULL COMMENT '活动有效期结束时间；不等于渠道下架时间';
    ALTER TABLE raffle_activity MODIFY COLUMN `strategy_id` bigint NOT NULL COMMENT '抽奖策略业务编号；关联奖池、概率和抽奖规则';
    ALTER TABLE raffle_activity MODIFY COLUMN `state` varchar(8) NOT NULL DEFAULT 'create' COMMENT '活动开放状态：create创建、open开启、close关闭；与渠道上下架独立';
    ALTER TABLE raffle_activity MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '配置记录创建时间';
    ALTER TABLE raffle_activity MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '配置记录最后更新时间';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键；不是套餐业务编号';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `activity_count_id` bigint NOT NULL COMMENT '抽奖次数套餐业务编号；次数商品关联此编号，保留历史字段名';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `total_count` int NOT NULL COMMENT '每份套餐增加的总抽奖次数额度；不是已抽次数或AI对话额度';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `day_count` int NOT NULL COMMENT '每份套餐增加的每日抽奖次数额度；与总、月额度共同限制参与';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `month_count` int NOT NULL COMMENT '每份套餐增加的每月抽奖次数额度；与总、日额度共同限制参与';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '套餐配置创建时间';
    ALTER TABLE raffle_quota_plan MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '套餐配置最后更新时间';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键；不是商品业务编号';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `sku` bigint NOT NULL COMMENT '抽奖次数商品业务编号；兑换、返利发放及库存处理使用';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `activity_id` bigint NOT NULL COMMENT '所属抽奖活动业务编号；关联raffle_activity.activity_id';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `activity_count_id` bigint NOT NULL COMMENT '关联raffle_quota_plan.activity_count_id；决定每份商品发放的次数额度';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `stock_count` int NOT NULL COMMENT '次数商品配置总库存；单位为份，不是奖品库存或抽奖次数';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `stock_count_surplus` int NOT NULL COMMENT '次数商品数据库剩余库存；Redis扣减后异步回写，可能有延迟';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `product_amount` decimal(10,2) NOT NULL COMMENT '每份次数商品的积分价格；不是人民币或AI对话余额';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '次数商品配置创建时间';
    ALTER TABLE raffle_quota_product MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '次数商品配置最后更新时间';
END$$
DELIMITER ;
CALL migrate_20261007_raffle_quota_names();
DROP PROCEDURE migrate_20261007_raffle_quota_names;
