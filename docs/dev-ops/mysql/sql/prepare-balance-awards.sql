-- Local activity 100401: prepare a NEW strategy, preserving historical count prizes.
-- Set @cny_per_legacy_count in this same connection BEFORE sourcing this file.
-- Example proposal (requires a business decision): SET @cny_per_legacy_count = 0.01;
-- This script deliberately does not switch the live activity.
-- Deploy openai_balance and the account wallet migration before activation.
USE big_market;

DELIMITER $$
DROP PROCEDURE IF EXISTS prepare_balance_awards_100401$$
CREATE PROCEDURE prepare_balance_awards_100401()
BEGIN
    DECLARE source_count INT;
    DECLARE rate DECIMAL(20,8);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF @cny_per_legacy_count IS NULL OR @cny_per_legacy_count <= 0
       OR @cny_per_legacy_count > 20000
       OR ROUND(@cny_per_legacy_count, 8) <> @cny_per_legacy_count THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Set an approved positive CNY conversion rate (up to 8 decimals)';
    END IF;
    SET rate = @cny_per_legacy_count;
    START TRANSACTION;
    SELECT COUNT(*) INTO source_count FROM strategy_award WHERE strategy_id = 10007;
    IF source_count <> 8 OR
       (SELECT COUNT(*) FROM strategy_award WHERE strategy_id = 10007 AND award_id IN (101,110,111,112,113,114,115,116)) <> 8 OR
       (SELECT COUNT(*) FROM strategy WHERE strategy_id = 10007 AND (rule_models IS NULL OR rule_models = '')) <> 1 OR
       (SELECT COUNT(*) FROM strategy_rule WHERE strategy_id = 10007) <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Source strategy changed; review the migration first';
    END IF;
    IF EXISTS (SELECT 1 FROM strategy WHERE strategy_id = 10008) OR
       EXISTS (SELECT 1 FROM strategy_award WHERE strategy_id = 10008) OR
       EXISTS (SELECT 1 FROM award WHERE award_id BETWEEN 1110 AND 1116) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Target strategy or awards already exist; do not overwrite';
    END IF;

    -- The old 113 / 116 titles and grant configurations disagree. New tiers follow
    -- the visible prize tiers: rare locked slot = 50, ordinary slot = 15.
    INSERT INTO award (award_id, award_key, award_config, award_desc)
    SELECT old_id + 1000, 'openai_balance', CAST(tier * rate AS CHAR), 'CNY 对话余额奖励'
    FROM (
        SELECT 110 old_id, 1 tier UNION ALL SELECT 111, 5 UNION ALL SELECT 112, 10
        UNION ALL SELECT 113, 50 UNION ALL SELECT 114, 20 UNION ALL SELECT 115, 25
        UNION ALL SELECT 116, 15
    ) tiers;

    INSERT INTO strategy (strategy_id, strategy_desc, rule_models)
    VALUES (10008, '活动100401：对话余额奖励', NULL);

    INSERT INTO strategy_award
        (strategy_id, award_id, award_title, award_subtitle, award_count, award_count_surplus, award_rate, rule_models, sort)
    SELECT 10008, IF(s.award_id = 101, 101, s.award_id + 1000),
        IF(s.award_id = 101, s.award_title,
            CONCAT('¥', TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM a.award_config)), ' 对话余额')),
        s.award_subtitle, s.award_count, s.award_count_surplus, s.award_rate, s.rule_models, s.sort
    FROM strategy_award s
    LEFT JOIN award a ON a.award_id = s.award_id + 1000
    WHERE s.strategy_id = 10007;
    -- Existing rule trees all fall back to unchanged random credit award 101.
    COMMIT;
END$$
CALL prepare_balance_awards_100401()$$
DROP PROCEDURE prepare_balance_awards_100401$$
DELIMITER ;

SELECT s.strategy_id, s.award_id, s.award_title, a.award_key, a.award_config, s.award_rate
FROM strategy_award s JOIN award a USING (award_id)
WHERE s.strategy_id = 10008 ORDER BY s.sort;
