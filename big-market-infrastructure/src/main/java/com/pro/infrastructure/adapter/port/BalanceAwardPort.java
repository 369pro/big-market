package com.pro.infrastructure.adapter.port;

import com.pro.domain.award.adapter.port.IBalanceAwardPort;
import com.pro.infrastructure.gateway.IOpenAIAccountService;
import com.pro.infrastructure.gateway.dto.AccountBalanceResponseDTO;
import com.pro.infrastructure.gateway.dto.AdjustBalanceRequestDTO;
import com.pro.infrastructure.gateway.response.Response;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BalanceAwardPort implements IBalanceAwardPort {
    private final IOpenAIAccountService service;
    private final String appId;
    private final String appToken;

    public BalanceAwardPort(IOpenAIAccountService service,
                            @Value("${gateway.config.big-market-appId}") String appId,
                            @Value("${gateway.config.big-market-appToken}") String appToken) {
        this.service = service;
        this.appId = appId;
        this.appToken = appToken;
    }

    @Override
    public void creditBalance(String userId, String requestId, BigDecimal amount) throws Exception {
        Response<AccountBalanceResponseDTO> response = service.adjustBalance(AdjustBalanceRequestDTO.builder()
                .appId(appId).appToken(appToken).openid(userId).requestId(requestId).increaseAmount(amount).build())
                .execute().body();
        if (response == null || !"0000".equals(response.getCode())) {
            throw new AppException(ResponseCode.GATEWAY_ERROR.getCode(), ResponseCode.GATEWAY_ERROR.getInfo());
        }
    }
}
