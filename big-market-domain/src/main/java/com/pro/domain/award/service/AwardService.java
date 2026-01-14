package com.pro.domain.award.service;

import com.pro.domain.award.event.SendAwardMessageEvent;
import com.pro.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.pro.domain.award.model.entity.TaskEntity;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.model.valobj.TaskStateVO;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.types.event.BaseEvent;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class AwardService implements IAwardService{
    @Resource
    private IAwardRepository awardRepository;
    @Resource
    private SendAwardMessageEvent sendAwardMessageEvent;
    /**
     * 根据用户的中奖记录要做两件事
     * 1. 保存用户的中奖流水
     * 2. 保存task记录
     * @param userAwardRecordEntity
     */
    @Override
    public void saveUserAwardRecord(UserAwardRecordEntity userAwardRecordEntity) {
        // 1. 构建 SendAwardMessage 消息
        SendAwardMessageEvent.SendAwardMessage sendAwardMessage =
                new SendAwardMessageEvent.SendAwardMessage();
        sendAwardMessage.setUserId(userAwardRecordEntity.getUserId());
        sendAwardMessage.setAwardId(userAwardRecordEntity.getAwardId());
        sendAwardMessage.setAwardTitle(userAwardRecordEntity.getAwardTitle());
        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> sendAwardMessageEventMessage =
                sendAwardMessageEvent.buildEventMessage(sendAwardMessage);

        // 构建task对象
        TaskEntity taskEntity = TaskEntity.builder()
                .userId(userAwardRecordEntity.getUserId())
                .topic(sendAwardMessageEvent.topic())
                .messageId(sendAwardMessageEventMessage.getId())
                .message(sendAwardMessageEventMessage)
                .state(TaskStateVO.create)
                .build();
        // 构建聚合对象
        UserAwardRecordAggregate userAwardRecordAggregate = UserAwardRecordAggregate.builder()
                .taskEntity(taskEntity)
                .userAwardRecordEntity(userAwardRecordEntity)
                .build();

        // 存储聚合对象 - 一个事务下，用户的中奖记录
        awardRepository.saveUserAwardRecord(userAwardRecordAggregate);
    }
}