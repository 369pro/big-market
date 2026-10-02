package com.pro.infrastructure.gateway.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class AdjustBalanceRequestDTO {
    private String appId;
    private String appToken;
    private String openid;
    private String requestId;
    private BigDecimal increaseAmount;
}
