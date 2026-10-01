package com.pro.test.mq;

import com.pro.domain.strategy.model.valobj.StrategyAwardStockDeductEvent;
import com.pro.domain.strategy.service.IRaffleStock;
import com.pro.trigger.listener.StrategyAwardStockDeductCustomer;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class StrategyAwardStockDeductCustomerTest {

    @Test
    public void forwardsStableEnvelopeIdsAndDeduplicatesWithinBatch() {
        IRaffleStock stock = mock(IRaffleStock.class);
        StrategyAwardStockDeductCustomer customer = new StrategyAwardStockDeductCustomer();
        ReflectionTestUtils.setField(customer, "raffleStock", stock);

        customer.listener(Arrays.asList(
                message("event-1", 101, 9),
                message("event-1", 101, 9),
                message("event-2", 101, 8)));

        ArgumentCaptor<List<StrategyAwardStockDeductEvent>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(stock).syncStrategyAwardStock(captor.capture());
        List<StrategyAwardStockDeductEvent> events = captor.getValue();
        assertEquals(2, events.size());
        assertEquals("event-1", events.get(0).getEventId());
        assertEquals("event-2", events.get(1).getEventId());
        assertEquals(Integer.valueOf(8), events.get(1).getSurplus());
    }

    @Test
    public void rejectsMessageWithoutStableEventIdBeforeWriting() {
        IRaffleStock stock = mock(IRaffleStock.class);
        StrategyAwardStockDeductCustomer customer = new StrategyAwardStockDeductCustomer();
        ReflectionTestUtils.setField(customer, "raffleStock", stock);

        try {
            customer.listener(Arrays.asList(message(null, 101, 8)));
            fail("Expected missing event ID to be rejected");
        } catch (IllegalArgumentException expected) {
            verifyNoInteractions(stock);
        }
    }

    private static MessageExt message(String eventId, int awardId, int surplus) {
        String id = eventId == null ? "" : eventId;
        String body = "{\"id\":\"" + id + "\",\"data\":{"
                + "\"strategyId\":100001,\"awardId\":" + awardId
                + ",\"surplus\":" + surplus + "}}";
        MessageExt message = new MessageExt();
        message.setBody(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
