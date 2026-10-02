package com.pro.infrastructure.gateway.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccountBalanceResponseDTO {
    private String currency;
    private BigDecimal availableAmount;
    private BigDecimal totalAmount;
}
