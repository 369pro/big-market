package com.pro.domain.award.service.distribute.impl;

import com.pro.domain.award.adapter.port.IBalanceAwardPort;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.domain.award.service.distribute.IDistributeAward;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** CNY balance awards have their own IDs; historical count awards retain their original meaning. */
@Component("openai_balance")
public class OpenAIBalanceAward implements IDistributeAward {
    private final IBalanceAwardPort port;
    private final IAwardRepository repository;

    public OpenAIBalanceAward(IBalanceAwardPort port, IAwardRepository repository) {
        this.port = port;
        this.repository = repository;
    }

    @Override
    public void giveOutPrizes(DistributeAwardEntity award) throws Exception {
        if (StringUtils.isBlank(award.getUserId()) || StringUtils.isBlank(award.getOrderId())) {
            throw new IllegalArgumentException("userId and orderId are required for a balance award");
        }
        String config = award.getAwardConfig();
        if (StringUtils.isBlank(config)) config = repository.queryAwardConfig(award.getAwardId());
        BigDecimal amount = new BigDecimal(config);
        if (amount.signum() <= 0 || amount.scale() > 8 || amount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("balance award must be positive CNY with at most 8 decimal places");
        }
        port.creditBalance(award.getUserId(), "raffle:" + award.getUserId() + ":" + award.getOrderId(), amount);
        repository.updateAwardRecordCompletedState(award.getUserId(), award.getOrderId(), award.getAwardId());
    }
}
