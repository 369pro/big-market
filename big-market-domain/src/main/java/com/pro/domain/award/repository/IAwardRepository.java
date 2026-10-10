package com.pro.domain.award.repository;


import com.pro.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.pro.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品仓储服务
 * @create 2024-04-06 09:02
 */
public interface IAwardRepository {

    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);

    String queryAwardConfig(Integer awardId);

    UserAwardRecordEntity queryAwardRecord(String userId, String orderId);

    void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate);

    /**
     * Mark the exact persisted award record as completed after an external grant succeeds.
     * The award id is part of the compare-and-set key so a stale or malformed message for
     * the same order cannot complete a different prize.
     */
    boolean updateAwardRecordCompletedState(String userId, String orderId, Integer awardId);

    String queryAwardKey(Integer awardId);
}
