package com.pro.test.domain.credit;

import com.pro.domain.credit.event.CreditAdjustSuccessMessageEvent;
import com.pro.domain.credit.repository.ICreditRepository;
import com.pro.domain.credit.service.CreditAdjustService;
import com.pro.domain.credit.model.entity.TradeEntity;
import com.pro.domain.credit.model.valobj.TradeNameVO;
import com.pro.domain.credit.model.valobj.TradeTypeVO;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class CreditAdjustServiceValidationTest {

    private CreditAdjustService service;

    @Before
    public void setUp() {
        service = new CreditAdjustService();
        ReflectionTestUtils.setField(service, "creditRepository", mock(ICreditRepository.class));
        ReflectionTestUtils.setField(service, "creditAdjustSuccessMessageEvent",
                mock(CreditAdjustSuccessMessageEvent.class));
    }

    @Test
    public void rejectsMissingBusinessIdentityBeforeBuildingAnOrder() {
        assertIllegal(null);
        assertIllegal(TradeEntity.builder()
                .userId("user")
                .tradeName(TradeNameVO.REBATE)
                .tradeType(TradeTypeVO.FORWARD)
                .amount(new BigDecimal("1"))
                .build());
        assertIllegal(TradeEntity.builder()
                .userId("user")
                .tradeName(TradeNameVO.REBATE)
                .tradeType(TradeTypeVO.FORWARD)
                .amount(new BigDecimal("1.001"))
                .outBusinessNo("business")
                .build());
    }

    @Test
    public void rejectsBusinessKeysThatWouldBeTruncatedByTheLedgerSchema() {
        assertIllegal(TradeEntity.builder()
                .userId("user")
                .tradeName(TradeNameVO.REBATE)
                .tradeType(TradeTypeVO.FORWARD)
                .amount(new BigDecimal("1"))
                .outBusinessNo(repeat('x', 65))
                .build());
    }

    private void assertIllegal(TradeEntity trade) {
        try {
            service.createOrder(trade);
            fail("Expected invalid credit trade to be rejected");
        } catch (AppException exception) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), exception.getCode());
        }
    }

    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
