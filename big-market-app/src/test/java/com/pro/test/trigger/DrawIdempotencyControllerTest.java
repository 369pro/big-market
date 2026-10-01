package com.pro.test.trigger;

import com.pro.domain.activity.model.entity.UserRaffleOrderEntity;
import com.pro.domain.activity.model.valobj.UserRaffleOrderStateVO;
import com.pro.domain.activity.service.IRaffleActivityPartakeService;
import com.pro.domain.strategy.service.IRaffleStrategy;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.trigger.api.dto.ActivityDrawRequestDTO;
import com.pro.trigger.api.response.Response;
import com.pro.trigger.http.RaffleActivityController;
import com.pro.trigger.idempotency.RequestIdempotency;
import com.pro.types.enums.ResponseCode;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RLock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DrawIdempotencyControllerTest {

    private RaffleActivityController controller;
    private IRaffleActivityPartakeService partakeService;
    private IRaffleStrategy raffleStrategy;

    @Before
    public void setUp() {
        controller = new RaffleActivityController();
        partakeService = mock(IRaffleActivityPartakeService.class);
        raffleStrategy = mock(IRaffleStrategy.class);
        IRedisService redisService = mock(IRedisService.class);
        RLock lock = mock(RLock.class);

        when(redisService.getLock(anyString())).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        ReflectionTestUtils.setField(controller, "raffleActivityPartakeService", partakeService);
        ReflectionTestUtils.setField(controller, "raffleStrategy", raffleStrategy);
        ReflectionTestUtils.setField(controller, "redisService", redisService);
    }

    @Test
    public void processingRetryReturnsReconcileWithoutRerolling() {
        String requestId = "draw-retry-1";
        when(partakeService.createOrder(eq("user-1"), eq(100001L),
                eq(RequestIdempotency.businessKey("user-1", "draw", requestId))))
                .thenReturn(UserRaffleOrderEntity.builder()
                        .userId("user-1")
                        .activityId(100001L)
                        .orderId("order-1")
                        .orderState(UserRaffleOrderStateVO.processing)
                        .build());

        ActivityDrawRequestDTO request = new ActivityDrawRequestDTO();
        request.setUserId("user-1");
        request.setActivityId(100001L);
        request.setRequestId(requestId);

        Response<?> response = controller.draw(request);

        assertEquals(ResponseCode.ACTIVITY_ORDER_PROCESSING.getCode(), response.getCode());
        verify(partakeService, never()).markOrderProcessing(any(UserRaffleOrderEntity.class));
        verify(raffleStrategy, never()).performRaffle(any());
    }
}
