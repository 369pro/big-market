package com.pro.domain.award.repository;


import com.pro.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.pro.domain.award.model.aggregate.UserAwardRecordAggregate;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品仓储服务
 * @create 2024-04-06 09:02
 */
public interface IAwardRepository {

    void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate);

    String queryAwardConfig(Integer awardId);

    void saveGiveOutPrizesAggregate(GiveOutPrizesAggregate giveOutPrizesAggregate);

    boolean updateAwardRecordCompletedState(String userId, String orderId);

    String queryAwardKey(Integer awardId);
}
