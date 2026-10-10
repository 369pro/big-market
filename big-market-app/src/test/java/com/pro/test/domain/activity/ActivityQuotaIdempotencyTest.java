package com.pro.test.domain.activity;

import com.pro.domain.activity.model.entity.*;
import com.pro.domain.activity.model.valobj.OrderTradeTypeVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.quota.RaffleActivityAccountQuotaService;
import com.pro.domain.activity.service.quota.policy.ITradePolicy;
import com.pro.domain.activity.service.quota.rule.IActionChain;
import com.pro.domain.activity.service.quota.rule.factory.DefaultActivityChainFactory;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Test;
import java.math.BigDecimal;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ActivityQuotaIdempotencyTest {
    private final IActivityRepository repository = mock(IActivityRepository.class);
    private final DefaultActivityChainFactory factory = mock(DefaultActivityChainFactory.class);
    private final ITradePolicy policy = mock(ITradePolicy.class);
    private final RaffleActivityAccountQuotaService service = new RaffleActivityAccountQuotaService(repository,
            factory, Collections.singletonMap(OrderTradeTypeVO.rebate_no_pay_trade.getCode(), policy));

    @Test
    public void completedOrderRetryDoesNotRunStockChain() {
        SkuRechargeEntity request = request();
        UnpaidActivityOrderEntity original = UnpaidActivityOrderEntity.builder().orderId("original").build();
        when(repository.queryActivityOrder(request)).thenReturn(original);
        assertSame(original, service.createOrder(request));
        verifyNoInteractions(factory, policy);
    }

    @Test
    public void concurrentDuplicateReturnsWinnerAndUsesBusinessReservation() {
        SkuRechargeEntity request = request();
        UnpaidActivityOrderEntity winner = UnpaidActivityOrderEntity.builder().orderId("winner").build();
        when(repository.queryActivityOrder(request)).thenReturn(null, winner);
        when(repository.queryActivitySku(9901L)).thenReturn(ActivitySkuEntity.builder()
                .sku(9901L).activityId(1L).quotaPlanId(2L).productAmount(BigDecimal.ONE).build());
        when(repository.queryRaffleActivityByActivityId(1L)).thenReturn(ActivityEntity.builder().activityId(1L).build());
        when(repository.queryRaffleQuotaPlanByQuotaPlanId(2L)).thenReturn(QuotaPlanEntity.builder()
                .totalCount(1).dayCount(1).monthCount(1).build());
        IActionChain chain = mock(IActionChain.class);
        when(factory.openActionChain()).thenReturn(chain);
        doThrow(new AppException(ResponseCode.INDEX_DUP.getCode())).when(policy).trade(any());
        assertSame(winner, service.createOrder(request));
        verify(chain).action(any(), any(), any(), eq(org.apache.commons.codec.digest.DigestUtils.sha256Hex("u\0business")));
    }

    @Test
    public void missingTradeTypeIsRejectedBeforeStockReservation() {
        SkuRechargeEntity request = request();
        request.setOrderTradeType(null);

        try {
            service.createOrder(request);
            fail("expected illegal trade type");
        } catch (AppException expected) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), expected.getCode());
        }

        verifyNoInteractions(repository, factory, policy);
    }

    private SkuRechargeEntity request() {
        return SkuRechargeEntity.builder().userId("u").sku(9901L).outBusinessNo("business")
                .orderTradeType(OrderTradeTypeVO.rebate_no_pay_trade).build();
    }
}
