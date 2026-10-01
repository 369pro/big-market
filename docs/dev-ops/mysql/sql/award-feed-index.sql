-- 中奖展示查询索引（一次性迁移，不修改中奖数据）。
-- 已有相同索引时跳过对应语句；非默认分片数量的环境请覆盖实际全部分片。
-- 查询条件 activity_id，排序 award_time DESC, order_id DESC, user_id DESC。
ALTER TABLE big_market_01.user_award_record_000
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_01.user_award_record_001
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_01.user_award_record_002
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_01.user_award_record_003
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_02.user_award_record_000
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_02.user_award_record_001
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_02.user_award_record_002
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
ALTER TABLE big_market_02.user_award_record_003
    ADD INDEX idx_activity_award_feed (activity_id, award_time, order_id, user_id);
