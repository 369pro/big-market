package com.pro.domain.credit.service;

import com.pro.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.pro.domain.credit.model.aggregate.TradeAggregate;
import com.pro.domain.credit.model.entity.CreditAccountEntity;
import com.pro.domain.credit.model.entity.CreditOrderEntity;
import com.pro.domain.credit.model.entity.TaskEntity;
import com.pro.domain.credit.model.entity.TradeEntity;
import com.pro.domain.credit.model.valobj.TradeTypeVO;
import com.pro.domain.credit.repository.ICreditRepository;
import com.pro.types.enums.ResponseCode;
import com.pro.types.event.BaseEvent;
import com.pro.types.exception.AppException;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 积分调额服务【正逆向，增减积分】
 * @create 2024-06-01 09:36
 */
@Slf4j
@Service
public class CreditAdjustService implements ICreditAdjustService{
    @Resource
    private ICreditRepository creditRepository;
    @Resource
    private CreditAdjustSuccessMessageEvent creditAdjustSuccessMessageEvent;

    @Override
    public String createOrder(TradeEntity tradeEntity) {
        validateTrade(tradeEntity);
//        CreditAccountEntity creditAccountEntity = CreditAccountEntity.builder()
//                .userId(tradeEntity.getUserId())
//                .adjustAmount(tradeEntity.getAmount()).build();
        log.info("增加账户积分额度开始 userId:{} tradeName:{} amount:{}", tradeEntity.getUserId(), tradeEntity.getTradeName(), tradeEntity.getAmount());
        // Balance sufficiency is checked atomically in the database, after duplicate detection.
        // 1. 创建账户积分实体
        CreditAccountEntity creditAccountEntity = TradeAggregate.createCreditAccountEntity(
                tradeEntity.getUserId(),
                tradeEntity.getAmount());

        // 2. 创建积分订单实体 note 与积分兑换活动中【创建活动订单】区分
        CreditOrderEntity creditOrderEntity = TradeAggregate.createCreditOrderEntity(
                tradeEntity.getUserId(),
                tradeEntity.getTradeName(),
                tradeEntity.getTradeType(),
                tradeEntity.getAmount(),
                tradeEntity.getOutBusinessNo());

        // 3. 构建消息任务对象
        CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage creditAdjustSuccessMessage = new CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage();
        creditAdjustSuccessMessage.setUserId(tradeEntity.getUserId());
        creditAdjustSuccessMessage.setOrderId(creditOrderEntity.getOrderId());
        creditAdjustSuccessMessage.setAmount(tradeEntity.getAmount());
        creditAdjustSuccessMessage.setOutBusinessNo(tradeEntity.getOutBusinessNo());
        BaseEvent.EventMessage<CreditAdjustSuccessMessageEvent.CreditAdjustSuccessMessage> creditAdjustSuccessMessageEventMessage = creditAdjustSuccessMessageEvent.buildEventMessage(creditAdjustSuccessMessage);

        TaskEntity taskEntity = TradeAggregate.createTaskEntity(tradeEntity.getUserId(), creditAdjustSuccessMessageEvent.topic(), creditAdjustSuccessMessageEventMessage.getId(), creditAdjustSuccessMessageEventMessage);

        TradeAggregate tradeAggregate = TradeAggregate.builder()
                .userId(tradeEntity.getUserId())
                .creditAccountEntity(creditAccountEntity)
                .creditOrderEntity(creditOrderEntity)
                .taskEntity(taskEntity)
                .build();
        // 3. 保存积分交易订单
        creditRepository.saveUserCreditTradeOrder(tradeAggregate);
        log.info("创建账户积分额度订单完成 userId:{} orderId:{}", tradeEntity.getUserId(), creditOrderEntity.getOrderId());
        return creditOrderEntity.getOrderId();
    }

    @Override
    public CreditAccountEntity queryUserCreditAccount(String userId) {
        return creditRepository.queryUserCreditAccount(userId);
    }

    private void validateTrade(TradeEntity tradeEntity) {
        if (tradeEntity == null
                || StringUtils.isBlank(tradeEntity.getUserId())
                || "null".equalsIgnoreCase(tradeEntity.getUserId())
                || tradeEntity.getUserId().length() > 128
                || tradeEntity.getTradeName() == null
                || tradeEntity.getTradeType() == null
                || StringUtils.isBlank(tradeEntity.getOutBusinessNo())
                || "null".equalsIgnoreCase(tradeEntity.getOutBusinessNo())
                || tradeEntity.getOutBusinessNo().length() > 64
                || tradeEntity.getAmount() == null
                || tradeEntity.getAmount().stripTrailingZeros().scale() > 2
                || (TradeTypeVO.REVERSE.equals(tradeEntity.getTradeType()) && tradeEntity.getAmount().signum() >= 0)
                || (TradeTypeVO.FORWARD.equals(tradeEntity.getTradeType()) && tradeEntity.getAmount().signum() <= 0)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Invalid credit trade");
        }
    }
}
