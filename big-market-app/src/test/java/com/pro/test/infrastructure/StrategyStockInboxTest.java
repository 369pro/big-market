package com.pro.test.infrastructure;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.pro.domain.strategy.event.StrategyAwardStockDeductMessageEvent;
import com.pro.domain.strategy.model.valobj.StrategyAwardStockDeductEvent;
import com.pro.infrastructure.adapter.repository.StrategyRepository;
import com.pro.infrastructure.dao.IStrategyAwardDao;
import com.pro.infrastructure.dao.po.StrategyAwardStockInbox;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StrategyStockInboxTest {

    private StrategyRepository repository;
    private IStrategyAwardDao strategyAwardDao;
    private TransactionTemplate transactionTemplate;
    private IDBRouterStrategy dbRouter;

    @Before
    public void setUp() {
        repository = new StrategyRepository();
        strategyAwardDao = mock(IStrategyAwardDao.class);
        transactionTemplate = mock(TransactionTemplate.class);
        dbRouter = mock(IDBRouterStrategy.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);

        ReflectionTestUtils.setField(repository, "strategyAwardDao", strategyAwardDao);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(repository, "dbRouter", dbRouter);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        }).when(transactionTemplate).execute(any(TransactionCallback.class));
    }

    @Test
    public void firstEventInsertsInboxAndUpdatesOneLowestSnapshot() {
        when(strategyAwardDao.insertStrategyAwardStockInbox(any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(1);
        when(strategyAwardDao.syncStrategyAwardStock(anyLong(), anyInt(), anyInt()))
                .thenReturn(1);

        repository.syncStrategyAwardStock(Arrays.asList(
                event("event-9", 100001L, 101, 9),
                event("event-8", 100001L, 101, 8)));

        verify(transactionTemplate).execute(any(TransactionCallback.class));
        verify(strategyAwardDao).insertStrategyAwardStockInbox("event-9", 100001L, 101, 9);
        verify(strategyAwardDao).insertStrategyAwardStockInbox("event-8", 100001L, 101, 8);
        verify(strategyAwardDao).syncStrategyAwardStock(100001L, 101, 8);
    }

    @Test
    public void duplicateEventDoesNotWriteStockAgain() {
        doThrow(new DuplicateKeyException("duplicate event"))
                .when(strategyAwardDao)
                .insertStrategyAwardStockInbox("event-1", 100001L, 101, 8);
        when(strategyAwardDao.queryStrategyAwardStockInbox("event-1"))
                .thenReturn(inbox("event-1", 100001L, 101, 8));

        repository.syncStrategyAwardStock(Collections.singletonList(
                event("event-1", 100001L, 101, 8)));

        verify(strategyAwardDao, never()).syncStrategyAwardStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    public void duplicateEventIdWithDifferentPayloadIsRejected() {
        doThrow(new DuplicateKeyException("duplicate event"))
                .when(strategyAwardDao)
                .insertStrategyAwardStockInbox("event-1", 100001L, 102, 8);
        when(strategyAwardDao.queryStrategyAwardStockInbox("event-1"))
                .thenReturn(inbox("event-1", 100001L, 101, 8));

        try {
            repository.syncStrategyAwardStock(Collections.singletonList(
                    event("event-1", 100001L, 102, 8)));
            fail("Expected conflicting event payload to be rejected");
        } catch (IllegalArgumentException expected) {
            verify(strategyAwardDao, never()).syncStrategyAwardStock(anyLong(), anyInt(), anyInt());
        }
    }

    @Test
    public void missingAwardRollsBackInsteadOfCommittingInboxOnly() {
        when(strategyAwardDao.insertStrategyAwardStockInbox(any(), anyLong(), anyInt(), anyInt()))
                .thenReturn(1);
        when(strategyAwardDao.syncStrategyAwardStock(anyLong(), anyInt(), anyInt()))
                .thenReturn(0);
        when(strategyAwardDao.queryStrategyAward(any())).thenReturn(null);

        try {
            repository.syncStrategyAwardStock(Collections.singletonList(
                    event("event-missing", 100001L, 101, 8)));
            fail("Expected missing strategy award to fail the transaction");
        } catch (IllegalStateException expected) {
            verify(strategyAwardDao).insertStrategyAwardStockInbox("event-missing", 100001L, 101, 8);
        }
    }

    @Test
    public void eventIdRemainsStableAcrossEnvelopeRebuilds() {
        StrategyAwardStockDeductMessageEvent event = new StrategyAwardStockDeductMessageEvent();
        StrategyAwardStockDeductMessageEvent.StockDeductMessage message =
                StrategyAwardStockDeductMessageEvent.StockDeductMessage.builder()
                        .strategyId(100001L)
                        .awardId(101)
                        .surplus(8)
                        .build();

        String member = message.outboxMember();

        assertEquals(member, message.outboxMember());
        assertEquals(member, event.buildEventMessage(message).getId());
        assertEquals(member,
                StrategyAwardStockDeductMessageEvent.StockDeductMessage
                        .fromOutboxMember(member).outboxMember());
    }

    private static StrategyAwardStockDeductEvent event(
            String eventId, Long strategyId, Integer awardId, Integer surplus) {
        return StrategyAwardStockDeductEvent.builder()
                .eventId(eventId)
                .strategyId(strategyId)
                .awardId(awardId)
                .surplus(surplus)
                .build();
    }

    private static StrategyAwardStockInbox inbox(
            String eventId, Long strategyId, Integer awardId, Integer surplus) {
        StrategyAwardStockInbox inbox = new StrategyAwardStockInbox();
        inbox.setEventId(eventId);
        inbox.setStrategyId(strategyId);
        inbox.setAwardId(awardId);
        inbox.setSurplus(surplus);
        return inbox;
    }
}
