package com.pro.domain.activity.service;


import com.pro.domain.activity.model.entity.RaffleActivityListingEntity;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动上架服务接口
 * @create 2024-10-26 17:37
 */
public interface IRaffleActivityListingService {

    /**
     * 添加上架活动
     *
     * @param channel    渠道
     * @param source     来源
     * @param activityId 活动ID
     */
    void appendStageActivity(String channel, String source, Long activityId);

    /**
     * 上架活动 - 将上架状态变更为有效
     *
     * @param id 上架流水ID
     * @return 活动ID
     */
    void updateStageActivity2Active(Long id);

    /**
     * Create or update a channel/source stage state. Activation validates the
     * activity and prepares its caches before publication.
     */
    void updateStageActivityState(Long activityId, String channel, String source, String state);

    /**
     * 查询展台活动ID - 一个渠道&来源 sc 值下，只能有一个在线的活动。
     *
     * @param channel 渠道
     * @param source  来源
     * @return 活动ID
     */
    Long queryStageActivityId(String channel, String source);

    List<RaffleActivityListingEntity> queryStageActivityList();

    Long queryStageActivity2ActiveById(Long id);

    RaffleActivityListingEntity queryStageActivityById(Long id);

}
