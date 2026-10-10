package com.pro.domain.award.service;

import com.pro.domain.award.adapter.event.SendAwardMessageEvent;
import com.pro.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.model.entity.TaskEntity;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.model.valobj.AwardStateVO;
import com.pro.domain.award.model.valobj.TaskStateVO;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.domain.award.service.distribute.IDistributeAward;
import com.pro.types.event.BaseEvent;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 奖品服务
 * @create 2024-04-06 09:39
 */
@Slf4j
@Service
public class AwardService implements IAwardService {

    private final IAwardRepository awardRepository;
    private final SendAwardMessageEvent sendAwardMessageEvent;
    private final Map<String, IDistributeAward> distributeAwardMap;

    public AwardService(IAwardRepository awardRepository, SendAwardMessageEvent sendAwardMessageEvent, Map<String, IDistributeAward> distributeAwardMap) {
        this.awardRepository = awardRepository;
        this.sendAwardMessageEvent = sendAwardMessageEvent;
        this.distributeAwardMap = distributeAwardMap;
    }

    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 构建消息对象
        SendAwardMessageEvent.SendAwardMessage sendAwardMessage = new SendAwardMessageEvent.SendAwardMessage();
        sendAwardMessage.setUserId(userAwardRecordEntity.getUserId());
        sendAwardMessage.setOrderId(userAwardRecordEntity.getOrderId());
        sendAwardMessage.setAwardId(userAwardRecordEntity.getAwardId());
        sendAwardMessage.setAwardTitle(userAwardRecordEntity.getAwardTitle());
        sendAwardMessage.setAwardConfig(userAwardRecordEntity.getAwardConfig());

        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> sendAwardMessageEventMessage = sendAwardMessageEvent.buildEventMessage(sendAwardMessage);

        // 构建任务对象
        TaskEntity taskEntity = new TaskEntity();
        taskEntity.setUserId(userAwardRecordEntity.getUserId());
        taskEntity.setTopic(sendAwardMessageEvent.topic());
        taskEntity.setMessageId(sendAwardMessageEventMessage.getId());
        taskEntity.setMessage(sendAwardMessageEventMessage);
        taskEntity.setState(TaskStateVO.create);

        // 构建聚合对象
        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate.builder()
                .taskEntity(taskEntity)
                .userAwardRecordEntity(userAwardRecordEntity)
                .build();

        // 存储聚合对象 - 一个事务下，用户的中奖记录
        awardRepository.saveUserAwardRecord(userAwardRecordAggregate);

        log.info("中奖记录保存完成 userId:{} orderId:{}", userAwardRecordEntity.getUserId(), userAwardRecordEntity.getOrderId());
    }

    @Override
    public void distributeAward(DistributeAwardEntity distributeAwardEntity) throws Exception {
        if (distributeAwardEntity == null
                || StringUtils.isBlank(distributeAwardEntity.getUserId())
                || StringUtils.isBlank(distributeAwardEntity.getOrderId())
                || distributeAwardEntity.getAwardId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Invalid award delivery identity");
        }
        UserAwardRecordEntity persistedAward = awardRepository.queryAwardRecord(
                distributeAwardEntity.getUserId(), distributeAwardEntity.getOrderId());
        if (persistedAward == null
                || !Objects.equals(persistedAward.getAwardId(), distributeAwardEntity.getAwardId())) {
            throw new IllegalStateException("Award record does not match the delivery message");
        }
        if (AwardStateVO.complete.equals(persistedAward.getAwardState())) {
            // A completed record for the same prize is a verified duplicate.  Returning
            // before any port call also avoids relying on remote idempotency for retries.
            return;
        }
        if (!AwardStateVO.create.equals(persistedAward.getAwardState())) {
            throw new IllegalStateException("Award record is not pending delivery");
        }
        // 奖品Key
        String awardKey = awardRepository.queryAwardKey(distributeAwardEntity.getAwardId());
        if (StringUtils.isBlank(awardKey)) {
            log.error("分发奖品，奖品ID不存在。awardKey:{}", awardKey);
            throw new IllegalStateException("Award definition does not exist: " + distributeAwardEntity.getAwardId());
        }

        // 奖品服务
        IDistributeAward distributeAward = distributeAwardMap.get(awardKey);

        if (null == distributeAward) {
            log.error("分发奖品，对应的服务不存在。awardKey:{}", awardKey);
            throw new RuntimeException("分发奖品，奖品" + awardKey + "对应的服务不存在");
        }

        // 发放奖品
        distributeAward.giveOutPrizes(distributeAwardEntity);
    }

}
