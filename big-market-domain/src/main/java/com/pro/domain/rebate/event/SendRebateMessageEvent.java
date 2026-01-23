package com.pro.domain.rebate.event;

import com.pro.types.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class SendRebateMessageEvent extends BaseEvent<SendRebateMessageEvent.RebateMessage> {
    @Value("${spring.rabbitmq.topic.send_rebate}")
    private String topic;


    @Override
    public EventMessage<RebateMessage> buildEventMessage(RebateMessage data) {
        return EventMessage.<RebateMessage>builder()
                .id(RandomStringUtils.randomNumeric(11))
                .timestamp(new Date())
                .data(data)
                .build();
    }

    @Override
    public String topic() {
        return topic;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RebateMessage {
        /** 用户ID */
        private String userId;
        // 这是用户返利，不是用户行为
//        /** 行为类型（sign 签到、openai_pay 支付） */
//        private String behaviorType;
        /** 返利类型（sku 活动库存充值商品、integral 用户活动积分） */
        private String rebateType;
        /** 返利配置【sku值，积分值】 */
        private String rebateConfig;
        /** 返利描述 */
        private String rebateDesc;
        /** 业务ID - 唯一ID，确保幂等 */
        private String bizId;
    }

}
