package com.pro.test.domain.rebate;

import com.pro.domain.rebate.event.SendRebateMessageEvent;
import com.pro.domain.rebate.model.aggregate.BehaviorRebateAggregate;
import com.pro.domain.rebate.model.entity.BehaviorEntity;
import com.pro.domain.rebate.model.entity.BehaviorRebateOrderEntity;
import com.pro.domain.rebate.model.valobj.BehaviorTypeVO;
import com.pro.domain.rebate.model.valobj.DailyBehaviorRebateVO;
import com.pro.domain.rebate.repository.IBehaviorRebateRepository;
import com.pro.domain.rebate.service.BehaviorRebateService;
import com.pro.types.event.BaseEvent;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BehaviorRebateMessageTest {

    private IBehaviorRebateRepository repository;
    private SendRebateMessageEvent sendRebateMessageEvent;
    private BehaviorRebateService service;

    @Before
    public void setUp() {
        repository = mock(IBehaviorRebateRepository.class);
        sendRebateMessageEvent = mock(SendRebateMessageEvent.class);
        service = new BehaviorRebateService();

        ReflectionTestUtils.setField(service, "behaviorRebateRepository", repository);
        ReflectionTestUtils.setField(service, "sendRebateMessageEvent", sendRebateMessageEvent);

        when(sendRebateMessageEvent.topic()).thenReturn("send_rebate");
        when(sendRebateMessageEvent.buildEventMessage(any(SendRebateMessageEvent.RebateMessage.class)))
                .thenAnswer(invocation -> {
                    SendRebateMessageEvent.RebateMessage message = invocation.getArgument(0);
                    return BaseEvent.EventMessage.<SendRebateMessageEvent.RebateMessage>builder()
                            .id("event-" + message.getBizId())
                            .data(message)
                            .build();
                });
    }

    @Test
    public void shouldScopeBizIdByUserWhenUsersShareDate() {
        DailyBehaviorRebateVO rebate = rebate("integral");
        when(repository.queryDailyBehaviorRebateByBehaviorType("sign"))
                .thenReturn(Arrays.asList(rebate));

        service.createOrder(behavior("user-a", "2026-01-24"));
        service.createOrder(behavior("user-b", "2026-01-24"));

        List<List<BehaviorRebateAggregate>> savedAggregates = captureSavedAggregates(2);
        BehaviorRebateAggregate first = savedAggregates.get(0).get(0);
        BehaviorRebateAggregate second = savedAggregates.get(1).get(0);

        assertNotEquals(first.getBehaviorRebateOrderEntity().getBizId(),
                second.getBehaviorRebateOrderEntity().getBizId());
        assertEquals(64, first.getBehaviorRebateOrderEntity().getBizId().length());
        assertEquals(64, second.getBehaviorRebateOrderEntity().getBizId().length());
        assertEventBizIdMatchesOrder(first);
        assertEventBizIdMatchesOrder(second);
    }

    @Test
    public void shouldKeepTypeSpecificBizIdsStableAcrossRepeatedCalls() {
        when(repository.queryDailyBehaviorRebateByBehaviorType("sign"))
                .thenReturn(Arrays.asList(rebate("integral"), rebate("sku")));

        BehaviorEntity behavior = behavior("user-a", "2026-01-24");
        service.createOrder(behavior);
        service.createOrder(behavior);

        List<List<BehaviorRebateAggregate>> savedAggregates = captureSavedAggregates(2);
        List<BehaviorRebateAggregate> firstCall = savedAggregates.get(0);
        List<BehaviorRebateAggregate> secondCall = savedAggregates.get(1);

        assertNotEquals(firstCall.get(0).getBehaviorRebateOrderEntity().getBizId(),
                firstCall.get(1).getBehaviorRebateOrderEntity().getBizId());
        assertEquals(firstCall.get(0).getBehaviorRebateOrderEntity().getBizId(),
                secondCall.get(0).getBehaviorRebateOrderEntity().getBizId());
        assertEquals(firstCall.get(1).getBehaviorRebateOrderEntity().getBizId(),
                secondCall.get(1).getBehaviorRebateOrderEntity().getBizId());
        assertEventBizIdMatchesOrder(firstCall.get(0));
        assertEventBizIdMatchesOrder(firstCall.get(1));
    }

    @Test
    public void retryReturnsLegacyOrderWithoutCreatingAnotherReward() {
        when(repository.queryOrderByOutBusinessNo("user-a", "20261001"))
                .thenReturn(Arrays.asList(BehaviorRebateOrderEntity.builder().behaviorType("sign")
                        .orderId("original-order").bizId("user-a_integral_20261001").build()));
        assertEquals(Arrays.asList("original-order"), service.createOrder(behavior("user-a", "20261001")));
        verify(repository, never()).saveUserRebateRecord(anyString(), any());
    }

    @Test
    public void differentBehaviorsAndLongExternalIdsHaveDistinctBoundedKeys() {
        when(repository.queryDailyBehaviorRebateByBehaviorType(anyString()))
                .thenReturn(Arrays.asList(rebate("integral")));
        BehaviorEntity sign = behavior("user-a", "external-payment-reference-" + new String(new char[80]).replace('\0', 'x'));
        service.createOrder(sign);
        sign.setBehaviorTypeVO(BehaviorTypeVO.OPENAI_PAY);
        service.createOrder(sign);
        List<List<BehaviorRebateAggregate>> orders = captureSavedAggregates(2);
        String first = orders.get(0).get(0).getBehaviorRebateOrderEntity().getBizId();
        String second = orders.get(1).get(0).getBehaviorRebateOrderEntity().getBizId();
        assertEquals(64, first.length());
        assertEquals(64, second.length());
        assertNotEquals(first, second);
    }

    @Test
    public void rejectsBusinessKeysThatWouldBeTruncatedByTheRebateSchema() {
        try {
            service.createOrder(behavior(new String(new char[129]).replace('\0', 'u'), "2026-01-24"));
            org.junit.Assert.fail("Expected an oversized user id to be rejected");
        } catch (AppException exception) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), exception.getCode());
        }
    }

    private BehaviorEntity behavior(String userId, String outBusinessNo) {
        return BehaviorEntity.builder()
                .userId(userId)
                .behaviorTypeVO(BehaviorTypeVO.SIGN)
                .outBusinessNo(outBusinessNo)
                .build();
    }

    private DailyBehaviorRebateVO rebate(String rebateType) {
        return DailyBehaviorRebateVO.builder()
                .behaviorType("sign")
                .rebateType(rebateType)
                .rebateConfig("1")
                .rebateDesc(rebateType + " rebate")
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<List<BehaviorRebateAggregate>> captureSavedAggregates(int invocationCount) {
        ArgumentCaptor<List<BehaviorRebateAggregate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(invocationCount)).saveUserRebateRecord(anyString(), captor.capture());
        return captor.getAllValues();
    }

    private void assertEventBizIdMatchesOrder(BehaviorRebateAggregate aggregate) {
        assertEquals(aggregate.getBehaviorRebateOrderEntity().getBizId(),
                aggregate.getTaskEntity().getMessage().getData().getBizId());
    }
}
