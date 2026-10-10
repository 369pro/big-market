-- Back up the table and stop big-market before running. Preserve HTTP routes.
SET NAMES utf8mb4;
DELIMITER $$
CREATE PROCEDURE migrate_20261007_activity_listing()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='raffle_activity_stage') THEN
        RENAME TABLE raffle_activity_stage TO raffle_activity_listing;
    END IF;
    ALTER TABLE raffle_activity_listing
        COMMENT='活动上架配置：指定渠道入口关联的活动及上下架状态',
        MODIFY id int unsigned NOT NULL AUTO_INCREMENT COMMENT '上架配置记录主键；不是活动业务编号',
        MODIFY channel varchar(8) NOT NULL COMMENT '渠道编码；与source共同定位活动入口，例如c01或test',
        MODIFY source varchar(8) NOT NULL COMMENT '渠道内来源或入口编码，例如s01或erp_demo',
        MODIFY activity_id bigint NOT NULL COMMENT '上架的活动业务编号；关联raffle_activity.activity_id',
        MODIFY state varchar(8) NOT NULL DEFAULT 'create' COMMENT '入口上架状态：create待上架、active已上架、inactive已下架；expire为历史状态',
        MODIFY create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上架配置创建时间',
        MODIFY update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '上架配置最后更新时间';
END$$
DELIMITER ;
CALL migrate_20261007_activity_listing();
DROP PROCEDURE migrate_20261007_activity_listing;
