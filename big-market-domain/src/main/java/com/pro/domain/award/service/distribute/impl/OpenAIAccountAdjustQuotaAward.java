package com.pro.domain.award.service.distribute.impl;


import com.pro.domain.award.adapter.port.IAwardPort;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.domain.award.service.distribute.IDistributeAward;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description OpenAI 账户调额奖品
 * @create 2024-10-06 11:18
 */
@Component("openai_use_count")
public class OpenAIAccountAdjustQuotaAward implements IDistributeAward {

    @Resource
    private IAwardPort port;
    @Resource
    private IAwardRepository repository;

    @Override
    public void giveOutPrizes(DistributeAwardEntity distributeAwardEntity) throws Exception {
        if (StringUtils.isBlank(distributeAwardEntity.getOrderId())) {
            throw new IllegalArgumentException("award orderId is required for idempotent quota grant");
        }

        // 奖品ID
        Integer awardId = distributeAwardEntity.getAwardId();
        // 查询奖品ID 「优先走透传的随机积分奖品配置」
        String awardConfig = distributeAwardEntity.getAwardConfig();
        if (StringUtils.isBlank(awardConfig)) {
            awardConfig = repository.queryAwardConfig(awardId);
        }
        final int increaseQuota;
        try {
            increaseQuota = Integer.parseInt(awardConfig);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("award quota configuration is invalid", e);
        }
        if (increaseQuota <= 0) {
            throw new IllegalArgumentException("award quota must be positive");
        }
        // 发奖接口；订单号作为远端调额接口的幂等键。
        port.adjustAmount(distributeAwardEntity.getUserId(), distributeAwardEntity.getOrderId(), increaseQuota);

        // 远端成功后再完成本地中奖记录。重复消息会被远端唯一键拦截，且本地状态更新也是条件更新。
        // 已完成的重复消息会被条件更新拦截，不抛异常，避免触发无意义的 MQ 重试。
        repository.updateAwardRecordCompletedState(distributeAwardEntity.getUserId(),
                distributeAwardEntity.getOrderId(), distributeAwardEntity.getAwardId());
    }

}
