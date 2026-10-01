package com.pro.test.mq;

import com.pro.domain.activity.service.IRaffleActivityAccountQuotaService;
import com.pro.trigger.listener.CreditAdjustSuccessCustomer;
import com.pro.trigger.listener.RebateMessageCustomer;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

public class RewardRetryTest {
    @Test
    public void positiveCreditDoesNotAttemptActivityDelivery() {
        CreditAdjustSuccessCustomer consumer = new CreditAdjustSuccessCustomer();
        IRaffleActivityAccountQuotaService quota = mock(IRaffleActivityAccountQuotaService.class);
        ReflectionTestUtils.setField(consumer, "raffleActivityAccountQuotaService", quota);
        consumer.listener("{\"id\":\"1\",\"data\":{\"userId\":\"u\",\"amount\":10,\"outBusinessNo\":\"rebate\"}}");
        verifyNoInteractions(quota);
    }

    @Test(expected = AppException.class)
    public void unverifiedDeliveryConflictRemainsRetryable() {
        CreditAdjustSuccessCustomer consumer = new CreditAdjustSuccessCustomer();
        IRaffleActivityAccountQuotaService quota = mock(IRaffleActivityAccountQuotaService.class);
        ReflectionTestUtils.setField(consumer, "raffleActivityAccountQuotaService", quota);
        doThrow(new AppException(ResponseCode.INDEX_DUP.getCode())).when(quota).updateOrder(any());
        consumer.listener("{\"id\":\"1\",\"data\":{\"userId\":\"u\",\"amount\":-5,\"outBusinessNo\":\"pay\"}}");
    }

    @Test(expected = AppException.class)
    public void failedRebateIsNotAcknowledgedAsDelivered() {
        RebateMessageCustomer consumer = new RebateMessageCustomer();
        IRaffleActivityAccountQuotaService quota = mock(IRaffleActivityAccountQuotaService.class);
        ReflectionTestUtils.setField(consumer, "raffleActivityAccountQuotaService", quota);
        when(quota.createOrder(any())).thenThrow(new AppException(ResponseCode.ACTIVITY_SKU_STOCK_ERROR.getCode()));
        consumer.listener("{\"id\":\"1\",\"data\":{\"userId\":\"u\",\"bizId\":\"sign\",\"rebateType\":\"sku\",\"rebateConfig\":\"9901\"}}");
    }
}
