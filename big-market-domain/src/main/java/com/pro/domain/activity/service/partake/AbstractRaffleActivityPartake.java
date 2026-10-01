package com.pro.domain.activity.service.partake;

import com.alibaba.fastjson.JSON;
import com.pro.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.pro.domain.activity.model.entity.ActivityEntity;
import com.pro.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.pro.domain.activity.model.entity.UserRaffleOrderEntity;
import com.pro.domain.activity.model.valobj.ActivityStateVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.IRaffleActivityPartakeService;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动参与抽奖类
 * @create 2024-04-05 07:53
 */
@Slf4j
public abstract class AbstractRaffleActivityPartake implements IRaffleActivityPartakeService {

    protected final IActivityRepository activityRepository;

    public AbstractRaffleActivityPartake(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public UserRaffleOrderEntity createOrder(String userId, Long activityId) {
        return createOrder(PartakeRaffleActivityEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .build());
    }

    @Override
    public UserRaffleOrderEntity createOrder(String userId, Long activityId, String requestId) {
        return createOrder(PartakeRaffleActivityEntity.builder()
                .userId(userId)
                .activityId(activityId)
                .requestId(requestId)
                .build());
    }

    @Override
    public boolean markOrderProcessing(UserRaffleOrderEntity userRaffleOrderEntity) {
        return activityRepository.markRaffleOrderProcessing(userRaffleOrderEntity);
    }

    /**
     * @param partakeRaffleActivityEntity 参与抽奖活动实体对象
     * @return 抽奖订单 实体
     */
    @Override
    public UserRaffleOrderEntity createOrder(PartakeRaffleActivityEntity partakeRaffleActivityEntity) {
        // 0. 基础信息
        String userId = partakeRaffleActivityEntity.getUserId();
        Long activityId = partakeRaffleActivityEntity.getActivityId();
        Date currentDate = new Date();
        log.info("创建活动抽奖单开始 userId:{} activityId:{}", userId, activityId);

        // A request-keyed order must be recovered before activity status/date
        // checks; a delayed network retry must still return its original order.
        if (StringUtils.isNotBlank(partakeRaffleActivityEntity.getRequestId())) {
            UserRaffleOrderEntity requestOrder = activityRepository
                    .queryRaffleOrderByRequestId(partakeRaffleActivityEntity);
            if (requestOrder != null) {
                if (!activityId.equals(requestOrder.getActivityId())) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                            ResponseCode.ILLEGAL_PARAMETER.getInfo());
                }
                return requestOrder;
            }
        }

        // 1. 活动查询
        ActivityEntity activityEntity = activityRepository.queryRaffleActivityByActivityId(activityId);

        // 校验；活动状态
        if (!ActivityStateVO.open.equals(activityEntity.getState())) {
            log.error("创建活动抽奖单失败，活动状态未开启 activityId:{} state:{}", activityId, activityEntity.getState());
            throw new AppException(ResponseCode.ACTIVITY_STATE_ERROR.getCode(), ResponseCode.ACTIVITY_STATE_ERROR.getInfo());
        }
        // 校验；活动日期「开始时间 <- 当前时间 -> 结束时间」
        if (activityEntity.getBeginDateTime().after(currentDate) || activityEntity.getEndDateTime().before(currentDate)) {
            log.error("创建活动抽奖单失败，活动时间未开始 activityId:{} state:{}", activityId, activityEntity.getState());
            throw new AppException(ResponseCode.ACTIVITY_DATE_ERROR.getCode(), ResponseCode.ACTIVITY_DATE_ERROR.getInfo());
        }

        // Legacy callers retain the old unused-order reuse behavior. A
        // request-keyed call must create its own durable mapping instead of
        // accidentally inheriting an unrelated legacy order.
        UserRaffleOrderEntity userRaffleOrderEntity = null;
        if (StringUtils.isBlank(partakeRaffleActivityEntity.getRequestId())) {
            userRaffleOrderEntity = activityRepository.queryNoUsedRaffleOrder(partakeRaffleActivityEntity);
            if (null != userRaffleOrderEntity) {
                log.info("创建参与活动订单存在 userId:{} activityId:{} userRaffleOrderEntity:{}", userId, activityId, JSON.toJSONString(userRaffleOrderEntity));
                return userRaffleOrderEntity;
            }
        }

        // 3. 额度账户过滤&返回账户构建对象【创建日月账户额度】
        CreatePartakeOrderAggregate createPartakeOrderAggregate = this.doFilterAccount(userId, activityId, currentDate);

        // 4. 构建订单
        UserRaffleOrderEntity userRaffleOrder = this.buildUserRaffleOrder(userId, activityId, currentDate);
        userRaffleOrder.setRequestId(partakeRaffleActivityEntity.getRequestId());

        // 5. 填充抽奖单实体对象
        createPartakeOrderAggregate.setUserRaffleOrderEntity(userRaffleOrder);

        // 6. 保存聚合对象 - 一个领域内的一个聚合是一个事务操作【更新日月账户额度】
        activityRepository.saveCreatePartakeOrderAggregate(createPartakeOrderAggregate);
        log.info("创建活动抽奖单完成 userId:{} activityId:{} orderId:{}", userId, activityId, userRaffleOrder.getOrderId());
        // 7. 返回订单信息
        return userRaffleOrder;
    }

    protected abstract CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId, Date currentDate);

    protected abstract UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId, Date currentDate);

}
