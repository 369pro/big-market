package com.pro.domain.rebate.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.apache.commons.codec.digest.DigestUtils;

import javax.annotation.Resource;

import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import com.pro.domain.award.model.valobj.TaskStateVO;
import com.pro.domain.rebate.event.SendRebateMessageEvent;
import com.pro.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.pro.domain.rebate.model.entity.BehaviorEntity;
import com.pro.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.pro.domain.rebate.model.entity.TaskEntity;
import com.pro.domain.rebate.model.valobj.DailyBehaviorRebateVO;
import com.pro.domain.rebate.repository.IBehaviorRebateRepository;
import com.pro.types.common.Constants;
import com.pro.types.event.BaseEvent;
@Service
public class BehaviorRebateService implements IBehaviorRebateService{
    @Resource
    private IBehaviorRebateRepository behaviorRebateRepository;
    @Resource
    private SendRebateMessageEvent sendRebateMessageEvent;

    @Override
    public List<String> createOrder(BehaviorEntity behaviorEntity) {
        if (behaviorEntity == null || behaviorEntity.getBehaviorTypeVO() == null
                || org.apache.commons.lang3.StringUtils.isBlank(behaviorEntity.getUserId())
                || "null".equals(behaviorEntity.getUserId())
                || org.apache.commons.lang3.StringUtils.isBlank(behaviorEntity.getOutBusinessNo())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Invalid rebate business key");
        }
        List<String> previous = previousOrders(behaviorEntity);
        if (!previous.isEmpty()) return previous;
        // 1. 查询返利配置
        List<DailyBehaviorRebateVO> dailyBehaviorRebates = behaviorRebateRepository.
                queryDailyBehaviorRebateByBehaviorType(behaviorEntity.getBehaviorTypeVO().getCode().toLowerCase());
        if(null == dailyBehaviorRebates || dailyBehaviorRebates.isEmpty()){
            return new ArrayList<>();
        }
        // 2. 构建聚合对象
        List<String> orderIds = new ArrayList<>();
        List<BehaviorRebateAggregate> behaviorRebateAggregates = new ArrayList<>();
        // 3. 存储聚合对象数据
        for(DailyBehaviorRebateVO dailyBehaviorRebateVO : dailyBehaviorRebates){
            SendRebateMessageEvent.RebateMessage sendRebateMessage = getRebateMessage(behaviorEntity, dailyBehaviorRebateVO);
            BaseEvent.EventMessage<SendRebateMessageEvent.RebateMessage>
                    rebateMessageEventMessage = sendRebateMessageEvent.buildEventMessage(sendRebateMessage);

            // 构建task对象
            TaskEntity taskEntity = new TaskEntity();
            taskEntity.setUserId(behaviorEntity.getUserId());
            taskEntity.setTopic(sendRebateMessageEvent.topic());
            taskEntity.setMessageId(rebateMessageEventMessage.getId());
            taskEntity.setMessage(rebateMessageEventMessage);
            taskEntity.setState(TaskStateVO.create);

            // 构建 behaviorRebateOrderEntity 对象
            // 拼装业务ID；用户ID_返利类型_外部透彻业务ID
            String bizId = sendRebateMessage.getBizId();
            BehaviorRebateOrderEntity behaviorRebateOrderEntity = BehaviorRebateOrderEntity.builder()
                    .userId(behaviorEntity.getUserId())
                    .orderId(RandomStringUtils.randomNumeric(12))
                    .behaviorType(dailyBehaviorRebateVO.getBehaviorType())
                    .rebateDesc(dailyBehaviorRebateVO.getRebateDesc())
                    .rebateType(dailyBehaviorRebateVO.getRebateType())
                    .rebateConfig(dailyBehaviorRebateVO.getRebateConfig())
                    .outBusinessNo(behaviorEntity.getOutBusinessNo())
                    .bizId(bizId)
                    .build();
            orderIds.add(behaviorRebateOrderEntity.getOrderId());

            // 构建聚合对象
            BehaviorRebateAggregate behaviorRebateAggregate = BehaviorRebateAggregate.builder()
                    .userId(behaviorEntity.getUserId())
                    .behaviorRebateOrderEntity(behaviorRebateOrderEntity)
                    .taskEntity(taskEntity).build();

            behaviorRebateAggregates.add(behaviorRebateAggregate);
        }

        try {
            behaviorRebateRepository.saveUserRebateRecord(behaviorEntity.getUserId(), behaviorRebateAggregates);
        } catch (AppException e) {
            if (!ResponseCode.INDEX_DUP.getCode().equals(e.getCode())) throw e;
            previous = previousOrders(behaviorEntity);
            if (previous.isEmpty()) throw e;
            return previous;
        }
        // 4. 返回订单ID集合
        return orderIds;
    }

    private static SendRebateMessageEvent.@NonNull RebateMessage getRebateMessage(BehaviorEntity behaviorEntity, DailyBehaviorRebateVO dailyBehaviorRebateVO) {
        // 防御：userId 为 null 或字符串 "null" 时，拒绝构建消息，避免脏数据流入 MQ
        if (behaviorEntity.getUserId() == null || "null".equals(behaviorEntity.getUserId())) {
            throw new IllegalArgumentException("BehaviorRebateService getRebateMessage userId 非法: " + behaviorEntity.getUserId());
        }
        SendRebateMessageEvent.RebateMessage sendRebateMessage =
                new SendRebateMessageEvent.RebateMessage();
        sendRebateMessage.setUserId(behaviorEntity.getUserId());
        sendRebateMessage.setRebateDesc(dailyBehaviorRebateVO.getRebateDesc());
        sendRebateMessage.setRebateType(dailyBehaviorRebateVO.getRebateType());
        sendRebateMessage.setRebateConfig(dailyBehaviorRebateVO.getRebateConfig());
        // Bound the downstream varchar(64) key and namespace different business operations.
        sendRebateMessage.setBizId(DigestUtils.sha256Hex(behaviorEntity.getUserId() + "\0"
                + behaviorEntity.getBehaviorTypeVO().getCode() + "\0"
                + dailyBehaviorRebateVO.getRebateType() + "\0" + behaviorEntity.getOutBusinessNo()));
        return sendRebateMessage;
    }

    @Override
    public List<BehaviorRebateOrderEntity> queryOrderByOutBusinessNo(String userId, String outBusinessNo) {
        return behaviorRebateRepository.queryOrderByOutBusinessNo(userId, outBusinessNo);
    }

    @Override
    public List<String> querySignOutBusinessNoByMonth(String userId, String monthPrefix) {
        return behaviorRebateRepository.querySignOutBusinessNoByMonth(userId, monthPrefix);
    }

    private List<String> previousOrders(BehaviorEntity behavior) {
        return behaviorRebateRepository.queryOrderByOutBusinessNo(behavior.getUserId(), behavior.getOutBusinessNo())
                .stream().filter(order -> behavior.getBehaviorTypeVO().getCode().equalsIgnoreCase(order.getBehaviorType()))
                .map(BehaviorRebateOrderEntity::getOrderId).collect(Collectors.toList());
    }
}
