package com.pro.domain.award.adapter.port;

import java.math.BigDecimal;

public interface IBalanceAwardPort {
    void creditBalance(String userId, String requestId, BigDecimal amount) throws Exception;
}
