package com.pro.domain.award.service;


import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品服务接口
 * @create 2024-04-06 09:03
 */
public interface IAwardService {

    void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity);

    void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception;
}
