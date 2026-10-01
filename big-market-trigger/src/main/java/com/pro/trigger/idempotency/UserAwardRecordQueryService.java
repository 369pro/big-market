package com.pro.trigger.idempotency;

import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.model.valobj.AwardStateVO;
import com.pro.infrastructure.dao.IUserAwardRecordDao;
import com.pro.infrastructure.dao.po.UserAwardRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Read-only award lookup used to complete a draw retry after the award
 * transaction has already marked the raffle order as used.
 */
@Service
public class UserAwardRecordQueryService {

    @Resource
    private IUserAwardRecordDao userAwardRecordDao;

    public UserAwardRecordEntity query(String userId, String orderId) {
        UserAwardRecord record = new UserAwardRecord();
        record.setUserId(userId);
        record.setOrderId(orderId);
        UserAwardRecord result = userAwardRecordDao.queryByUserIdAndOrderId(record);
        if (result == null) return null;
        return UserAwardRecordEntity.builder()
                .userId(result.getUserId())
                .activityId(result.getActivityId())
                .strategyId(result.getStrategyId())
                .orderId(result.getOrderId())
                .awardId(result.getAwardId())
                .awardTitle(result.getAwardTitle())
                .awardTime(result.getAwardTime())
                .awardState(AwardStateVO.valueOf(result.getAwardState()))
                .build();
    }
}
