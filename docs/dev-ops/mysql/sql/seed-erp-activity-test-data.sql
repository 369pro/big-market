-- Local ERP activity-stage test fixtures.
--
-- This script only writes the default configuration database. It creates four
-- activities, four independent strategy copies of 10008, one private SKU and
-- count configuration per activity, and one inactive stage row for 100501.
-- It does not create users, orders, awards, grants, or any c01/s01 rows.
--
-- The source strategy, award rows, and SKU/count rows are read as a snapshot.
-- Existing target rows are never updated or deleted. A complete rerun is a
-- no-op; a partially present target set fails closed so it cannot silently
-- mix with unrelated test data.

SET NAMES utf8mb4;
USE `big_market`;

DELIMITER $$

DROP PROCEDURE IF EXISTS `seed_erp_activity_test_data`$$

CREATE PROCEDURE `seed_erp_activity_test_data`()
BEGIN
    DECLARE source_strategy_rows INT DEFAULT 0;
    DECLARE source_strategy_award_rows INT DEFAULT 0;
    DECLARE source_strategy_award_ids INT DEFAULT 0;
    DECLARE target_activity_rows INT DEFAULT 0;
    DECLARE target_strategy_rows INT DEFAULT 0;
    DECLARE target_strategy_award_rows INT DEFAULT 0;
    DECLARE target_count_rows INT DEFAULT 0;
    DECLARE target_sku_rows INT DEFAULT 0;
    DECLARE target_stage_rows INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- Fail closed if the source migration has not been prepared or has drifted.
    SELECT COUNT(*)
      INTO source_strategy_rows
      FROM `strategy`
     WHERE `strategy_id` = 10008;

    SELECT COUNT(*)
      INTO source_strategy_award_rows
      FROM `strategy_award`
     WHERE `strategy_id` = 10008;

    SELECT COUNT(DISTINCT `award_id`)
      INTO source_strategy_award_ids
      FROM `strategy_award`
     WHERE `strategy_id` = 10008
       AND `award_id` IN (101, 1110, 1111, 1112, 1113, 1114, 1115, 1116);

    IF source_strategy_rows <> 1
       OR source_strategy_award_rows <> 8
       OR source_strategy_award_ids <> 8 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Source strategy 10008 is missing or changed';
    END IF;

    -- Never merge a partial hand-created fixture with this seed.
    SELECT COUNT(*)
      INTO target_activity_rows
      FROM `raffle_activity`
     WHERE `activity_id` IN (100501, 100502, 100503, 100504);

    SELECT COUNT(*)
      INTO target_strategy_rows
      FROM `strategy`
     WHERE `strategy_id` IN (100081, 100082, 100083, 100084);

    SELECT COUNT(*)
      INTO target_strategy_award_rows
      FROM `strategy_award`
     WHERE `strategy_id` IN (100081, 100082, 100083, 100084);

    SELECT COUNT(*)
      INTO target_count_rows
      FROM `raffle_activity_count`
     WHERE `activity_count_id` IN (22301, 22302, 22303, 22304);

    SELECT COUNT(*)
      INTO target_sku_rows
      FROM `raffle_activity_sku`
     WHERE `sku` IN (9911, 9912, 9913, 9914);

    SELECT COUNT(*)
      INTO target_stage_rows
      FROM `raffle_activity_stage`
     WHERE `channel` = 'test'
       AND `source` = 'erp_demo'
       AND `activity_id` = 100501;

    IF (target_activity_rows NOT IN (0, 4))
       OR (target_strategy_rows NOT IN (0, 4))
       OR (target_strategy_award_rows NOT IN (0, 32))
       OR (target_count_rows NOT IN (0, 4))
       OR (target_sku_rows NOT IN (0, 4))
       OR (target_stage_rows NOT IN (0, 1)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Partial ERP activity test fixture exists; refusing to merge';
    END IF;

    -- Copy the strategy definition four times. strategy_id is the business key;
    -- omitting id/create_time/update_time keeps this safe for reruns.
    INSERT INTO `strategy` (`strategy_id`, `strategy_desc`, `rule_models`)
    SELECT 100081, '测试活动100501：复制策略10008', `rule_models`
      FROM `strategy` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1 FROM `strategy` t WHERE t.`strategy_id` = 100081
       );

    INSERT INTO `strategy` (`strategy_id`, `strategy_desc`, `rule_models`)
    SELECT 100082, '测试活动100502：复制策略10008', `rule_models`
      FROM `strategy` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1 FROM `strategy` t WHERE t.`strategy_id` = 100082
       );

    INSERT INTO `strategy` (`strategy_id`, `strategy_desc`, `rule_models`)
    SELECT 100083, '测试活动100503：复制策略10008', `rule_models`
      FROM `strategy` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1 FROM `strategy` t WHERE t.`strategy_id` = 100083
       );

    INSERT INTO `strategy` (`strategy_id`, `strategy_desc`, `rule_models`)
    SELECT 100084, '测试活动100504：复制策略10008', `rule_models`
      FROM `strategy` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1 FROM `strategy` t WHERE t.`strategy_id` = 100084
       );

    -- Each strategy owns its strategy_award inventory. Immutable award
    -- definitions in award (101 and 1110-1116) are intentionally shared.
    INSERT INTO `strategy_award`
        (`strategy_id`, `award_id`, `award_title`, `award_subtitle`,
         `award_count`, `award_count_surplus`, `award_rate`, `rule_models`, `sort`)
    SELECT 100081, s.`award_id`, s.`award_title`, s.`award_subtitle`,
           s.`award_count`, s.`award_count_surplus`, s.`award_rate`,
           s.`rule_models`, s.`sort`
      FROM `strategy_award` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1
             FROM `strategy_award` t
            WHERE t.`strategy_id` = 100081
              AND t.`award_id` = s.`award_id`
       );

    INSERT INTO `strategy_award`
        (`strategy_id`, `award_id`, `award_title`, `award_subtitle`,
         `award_count`, `award_count_surplus`, `award_rate`, `rule_models`, `sort`)
    SELECT 100082, s.`award_id`, s.`award_title`, s.`award_subtitle`,
           s.`award_count`, s.`award_count_surplus`, s.`award_rate`,
           s.`rule_models`, s.`sort`
      FROM `strategy_award` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1
             FROM `strategy_award` t
            WHERE t.`strategy_id` = 100082
              AND t.`award_id` = s.`award_id`
       );

    INSERT INTO `strategy_award`
        (`strategy_id`, `award_id`, `award_title`, `award_subtitle`,
         `award_count`, `award_count_surplus`, `award_rate`, `rule_models`, `sort`)
    SELECT 100083, s.`award_id`, s.`award_title`, s.`award_subtitle`,
           s.`award_count`, s.`award_count_surplus`, s.`award_rate`,
           s.`rule_models`, s.`sort`
      FROM `strategy_award` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1
             FROM `strategy_award` t
            WHERE t.`strategy_id` = 100083
              AND t.`award_id` = s.`award_id`
       );

    INSERT INTO `strategy_award`
        (`strategy_id`, `award_id`, `award_title`, `award_subtitle`,
         `award_count`, `award_count_surplus`, `award_rate`, `rule_models`, `sort`)
    SELECT 100084, s.`award_id`, s.`award_title`, s.`award_subtitle`,
           s.`award_count`, s.`award_count_surplus`, s.`award_rate`,
           s.`rule_models`, s.`sort`
      FROM `strategy_award` s
     WHERE s.`strategy_id` = 10008
       AND NOT EXISTS (
           SELECT 1
             FROM `strategy_award` t
            WHERE t.`strategy_id` = 100084
              AND t.`award_id` = s.`award_id`
       );

    -- Clone the existing participation-count definitions into private IDs.
    INSERT INTO `raffle_activity_count`
        (`activity_count_id`, `total_count`, `day_count`, `month_count`)
    SELECT 22301, c.`total_count`, c.`day_count`, c.`month_count`
      FROM `raffle_activity_count` c
     WHERE c.`activity_count_id` = 22201
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_count` t
            WHERE t.`activity_count_id` = 22301
       );

    INSERT INTO `raffle_activity_count`
        (`activity_count_id`, `total_count`, `day_count`, `month_count`)
    SELECT 22302, c.`total_count`, c.`day_count`, c.`month_count`
      FROM `raffle_activity_count` c
     WHERE c.`activity_count_id` = 22202
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_count` t
            WHERE t.`activity_count_id` = 22302
       );

    INSERT INTO `raffle_activity_count`
        (`activity_count_id`, `total_count`, `day_count`, `month_count`)
    SELECT 22303, c.`total_count`, c.`day_count`, c.`month_count`
      FROM `raffle_activity_count` c
     WHERE c.`activity_count_id` = 22203
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_count` t
            WHERE t.`activity_count_id` = 22303
       );

    INSERT INTO `raffle_activity_count`
        (`activity_count_id`, `total_count`, `day_count`, `month_count`)
    SELECT 22304, c.`total_count`, c.`day_count`, c.`month_count`
      FROM `raffle_activity_count` c
     WHERE c.`activity_count_id` = 22201
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_count` t
            WHERE t.`activity_count_id` = 22304
       );

    -- Clone each SKU with its own SKU/count IDs and the source inventory
    -- snapshot. stock_count_surplus remains mutable after an ERP test.
    INSERT INTO `raffle_activity_sku`
        (`sku`, `activity_id`, `activity_count_id`, `stock_count`,
         `stock_count_surplus`, `product_amount`)
    SELECT 9911, 100501, 22301, s.`stock_count`, s.`stock_count_surplus`, s.`product_amount`
      FROM `raffle_activity_sku` s
     WHERE s.`sku` = 9901
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_sku` t WHERE t.`sku` = 9911
       );

    INSERT INTO `raffle_activity_sku`
        (`sku`, `activity_id`, `activity_count_id`, `stock_count`,
         `stock_count_surplus`, `product_amount`)
    SELECT 9912, 100502, 22302, s.`stock_count`, s.`stock_count_surplus`, s.`product_amount`
      FROM `raffle_activity_sku` s
     WHERE s.`sku` = 9902
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_sku` t WHERE t.`sku` = 9912
       );

    INSERT INTO `raffle_activity_sku`
        (`sku`, `activity_id`, `activity_count_id`, `stock_count`,
         `stock_count_surplus`, `product_amount`)
    SELECT 9913, 100503, 22303, s.`stock_count`, s.`stock_count_surplus`, s.`product_amount`
      FROM `raffle_activity_sku` s
     WHERE s.`sku` = 9903
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_sku` t WHERE t.`sku` = 9913
       );

    INSERT INTO `raffle_activity_sku`
        (`sku`, `activity_id`, `activity_count_id`, `stock_count`,
         `stock_count_surplus`, `product_amount`)
    SELECT 9914, 100504, 22304, s.`stock_count`, s.`stock_count_surplus`, s.`product_amount`
      FROM `raffle_activity_sku` s
     WHERE s.`sku` = 9901
       AND NOT EXISTS (
           SELECT 1 FROM `raffle_activity_sku` t WHERE t.`sku` = 9914
       );

    -- Activity 100501 is valid and open, but its stage row is deliberately
    -- inactive (create) so ERP up/down actions can be exercised.
    INSERT INTO `raffle_activity`
        (`activity_id`, `activity_name`, `activity_desc`, `begin_date_time`,
         `end_date_time`, `strategy_id`, `state`)
    SELECT 100501, '测试活动-已上架待激活', 'ERP 上架/下架往返测试：有效开放活动',
           '2026-10-01 00:00:00', '2026-12-31 23:59:59', 100081, 'open'
      FROM DUAL
     WHERE NOT EXISTS (
           SELECT 1 FROM `raffle_activity` WHERE `activity_id` = 100501
       );

    INSERT INTO `raffle_activity`
        (`activity_id`, `activity_name`, `activity_desc`, `begin_date_time`,
         `end_date_time`, `strategy_id`, `state`)
    SELECT 100502, '测试活动-有效未上架', 'ERP 列表测试：有效开放但从未上架',
           '2026-10-01 00:00:00', '2026-12-31 23:59:59', 100082, 'open'
      FROM DUAL
     WHERE NOT EXISTS (
           SELECT 1 FROM `raffle_activity` WHERE `activity_id` = 100502
       );

    INSERT INTO `raffle_activity`
        (`activity_id`, `activity_name`, `activity_desc`, `begin_date_time`,
         `end_date_time`, `strategy_id`, `state`)
    SELECT 100503, '测试活动-未来开放', 'ERP 列表测试：活动时间尚未开始',
           '2026-11-01 00:00:00', '2027-01-31 23:59:59', 100083, 'open'
      FROM DUAL
     WHERE NOT EXISTS (
           SELECT 1 FROM `raffle_activity` WHERE `activity_id` = 100503
       );

    INSERT INTO `raffle_activity`
        (`activity_id`, `activity_name`, `activity_desc`, `begin_date_time`,
         `end_date_time`, `strategy_id`, `state`)
    SELECT 100504, '测试活动-已结束关闭', 'ERP 列表测试：活动已结束且已关闭',
           '2026-08-01 00:00:00', '2026-09-30 23:59:59', 100084, 'close'
      FROM DUAL
     WHERE NOT EXISTS (
           SELECT 1 FROM `raffle_activity` WHERE `activity_id` = 100504
       );

    INSERT INTO `raffle_activity_stage`
        (`channel`, `source`, `activity_id`, `state`, `create_time`, `update_time`)
    SELECT 'test', 'erp_demo', 100501, 'create', NOW(), NOW()
      FROM DUAL
     WHERE NOT EXISTS (
           SELECT 1
             FROM `raffle_activity_stage`
            WHERE `channel` = 'test'
              AND `source` = 'erp_demo'
              AND `activity_id` = 100501
       );

    -- Structural verification. Mutable stock and stage state are intentionally
    -- not compared so a rerun cannot reset a fixture after it has been tested.
    SELECT COUNT(*) INTO target_activity_rows
      FROM `raffle_activity`
     WHERE `activity_id` IN (100501, 100502, 100503, 100504);
    SELECT COUNT(*) INTO target_strategy_rows
      FROM `strategy`
     WHERE `strategy_id` IN (100081, 100082, 100083, 100084);
    SELECT COUNT(*) INTO target_strategy_award_rows
      FROM `strategy_award`
     WHERE `strategy_id` IN (100081, 100082, 100083, 100084);
    SELECT COUNT(*) INTO target_count_rows
      FROM `raffle_activity_count`
     WHERE `activity_count_id` IN (22301, 22302, 22303, 22304);
    SELECT COUNT(*) INTO target_sku_rows
      FROM `raffle_activity_sku`
     WHERE `sku` IN (9911, 9912, 9913, 9914);
    SELECT COUNT(*) INTO target_stage_rows
      FROM `raffle_activity_stage`
     WHERE `channel` = 'test'
       AND `source` = 'erp_demo'
       AND `activity_id` = 100501;

    IF target_activity_rows <> 4
       OR target_strategy_rows <> 4
       OR target_strategy_award_rows <> 32
       OR target_count_rows <> 4
       OR target_sku_rows <> 4
       OR target_stage_rows <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity`
             WHERE `activity_id` = 100501 AND `strategy_id` = 100081) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity`
             WHERE `activity_id` = 100502 AND `strategy_id` = 100082) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity`
             WHERE `activity_id` = 100503 AND `strategy_id` = 100083) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity`
             WHERE `activity_id` = 100504 AND `strategy_id` = 100084) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity_sku`
             WHERE `sku` = 9911 AND `activity_id` = 100501 AND `activity_count_id` = 22301) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity_sku`
             WHERE `sku` = 9912 AND `activity_id` = 100502 AND `activity_count_id` = 22302) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity_sku`
             WHERE `sku` = 9913 AND `activity_id` = 100503 AND `activity_count_id` = 22303) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity_sku`
             WHERE `sku` = 9914 AND `activity_id` = 100504 AND `activity_count_id` = 22304) <> 1
       OR (SELECT COUNT(*) FROM `raffle_activity_stage`
             WHERE `channel` = 'test' AND `source` = 'erp_demo' AND `activity_id` = 100501) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ERP activity test fixture verification failed';
    END IF;

    COMMIT;
END$$

CALL `seed_erp_activity_test_data`()$$
DROP PROCEDURE IF EXISTS `seed_erp_activity_test_data`$$

DELIMITER ;

-- Reviewable result after the transaction; no user-scoped rows are touched.
SELECT `activity_id`, `activity_name`, `begin_date_time`, `end_date_time`,
       `strategy_id`, `state`
  FROM `raffle_activity`
 WHERE `activity_id` IN (100501, 100502, 100503, 100504)
 ORDER BY `activity_id`;

SELECT `id`, `channel`, `source`, `activity_id`, `state`
  FROM `raffle_activity_stage`
 WHERE `channel` = 'test'
   AND `source` = 'erp_demo'
 ORDER BY `id`;

SELECT `sku`, `activity_id`, `activity_count_id`, `stock_count`,
       `stock_count_surplus`, `product_amount`
  FROM `raffle_activity_sku`
 WHERE `sku` IN (9911, 9912, 9913, 9914)
 ORDER BY `sku`;
