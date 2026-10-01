package com.pro.test.infrastructure;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.pro.domain.award.model.valobj.TaskStateVO;
import com.pro.domain.credit.model.aggregate.TradeAggregate;
import com.pro.domain.credit.model.entity.CreditAccountEntity;
import com.pro.domain.credit.model.entity.CreditOrderEntity;
import com.pro.domain.credit.model.entity.TaskEntity;
import com.pro.domain.credit.model.valobj.TradeNameVO;
import com.pro.domain.credit.model.valobj.TradeTypeVO;
import com.pro.infrastructure.adapter.repository.CreditRepository;
import com.pro.infrastructure.dao.ITaskDao;
import com.pro.infrastructure.dao.IUserCreditAccountDao;
import com.pro.infrastructure.dao.IUserCreditOrderDao;
import com.pro.infrastructure.dao.po.UserCreditAccount;
import com.pro.infrastructure.dao.po.UserCreditOrder;
import com.pro.infrastructure.event.EventPublisher;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreditTransactionFailureTest {

    private CreditRepository repository;
    private IUserCreditAccountDao userCreditAccountDao;
    private IUserCreditOrderDao userCreditOrderDao;
    private IRedisService redisService;
    private IDBRouterStrategy dbRouter;
    private TransactionTemplate transactionTemplate;
    private TransactionStatus transactionStatus;
    private EventPublisher eventPublisher;

    @Before
    public void setUp() {
        repository = new CreditRepository();
        userCreditAccountDao = mock(IUserCreditAccountDao.class);
        userCreditOrderDao = mock(IUserCreditOrderDao.class);
        redisService = mock(IRedisService.class);
        dbRouter = mock(IDBRouterStrategy.class);
        transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);
        eventPublisher = mock(EventPublisher.class);

        ReflectionTestUtils.setField(repository, "userCreditAccountDao", userCreditAccountDao);
        ReflectionTestUtils.setField(repository, "userCreditOrderDao", userCreditOrderDao);
        ReflectionTestUtils.setField(repository, "taskDao", mock(ITaskDao.class));
        ReflectionTestUtils.setField(repository, "redisService", redisService);
        ReflectionTestUtils.setField(repository, "dbRouter", dbRouter);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(repository, "eventPublisher", eventPublisher);

        RLock lock = mock(RLock.class);
        when(redisService.getLock(anyString())).thenReturn(lock);
        when(lock.isLocked()).thenReturn(false);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        when(userCreditAccountDao.queryUserCreditAccount(any(UserCreditAccount.class)))
                .thenReturn(new UserCreditAccount());

        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        }).when(transactionTemplate).execute(any(TransactionCallback.class));
    }

    @Test
    public void unexpectedUniqueCollisionMustNotBeAcknowledgedAsSuccess() {
        doThrow(new DuplicateKeyException("duplicate out business number"))
                .when(userCreditOrderDao).insert(any());

        try {
            repository.saveUserCreditTradeOrder(tradeAggregate());
            fail("Expected an unverified duplicate to fail for retry");
        } catch (DuplicateKeyException expected) {
            // No existing matching business ledger was found.
        }
        verify(transactionStatus).setRollbackOnly();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    public void exactDuplicateReturnsOriginalOrderWithoutChangingBalance() {
        TradeAggregate trade = tradeAggregate();
        UserCreditOrder existing = new UserCreditOrder();
        existing.setOrderId("original-order");
        existing.setTradeName(trade.getCreditOrderEntity().getTradeName().getName());
        existing.setTradeType("forward");
        existing.setTradeAmount(new BigDecimal("5.00"));
        when(userCreditOrderDao.queryByBusinessNo(any())).thenReturn(existing);
        repository.saveUserCreditTradeOrder(trade);
        assertEquals("original-order", trade.getCreditOrderEntity().getOrderId());
        verifyNoInteractions(transactionTemplate, eventPublisher);
    }

    @Test
    public void failedGuardedBalanceUpdateRollsBackAndDoesNotPublish() {
        when(userCreditAccountDao.updateAddAmount(any())).thenReturn(0);
        AppException exception = expectAppException();
        assertEquals(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode(), exception.getCode());
        verify(transactionStatus).setRollbackOnly();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    public void reusedKeyWithDifferentAmountIsRejected() {
        UserCreditOrder existing = new UserCreditOrder();
        existing.setTradeName(TradeNameVO.REBATE.getName());
        existing.setTradeType("forward");
        existing.setTradeAmount(new BigDecimal("6"));
        when(userCreditOrderDao.queryByBusinessNo(any())).thenReturn(existing);
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), expectAppException().getCode());
        verifyNoInteractions(transactionTemplate, eventPublisher);
    }

    @Test
    public void shouldRethrowTransactionFailureAndSkipPublish() {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(userCreditOrderDao).insert(any());

        try {
            repository.saveUserCreditTradeOrder(tradeAggregate());
            fail("Expected the transaction failure to be rethrown");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }

        verify(transactionStatus).setRollbackOnly();
        verifyNoInteractions(eventPublisher);
    }

    private AppException expectAppException() {
        try {
            repository.saveUserCreditTradeOrder(tradeAggregate());
            fail("Expected duplicate key to be translated");
            return null;
        } catch (AppException exception) {
            return exception;
        }
    }

    private TradeAggregate tradeAggregate() {
        String userId = "user-a";
        return TradeAggregate.builder()
                .userId(userId)
                .creditAccountEntity(CreditAccountEntity.builder()
                        .userId(userId)
                        .adjustAmount(new BigDecimal("5"))
                        .build())
                .creditOrderEntity(CreditOrderEntity.builder()
                        .userId(userId)
                        .orderId("credit-order-1")
                        .tradeName(TradeNameVO.REBATE)
                        .tradeType(TradeTypeVO.FORWARD)
                        .tradeAmount(new BigDecimal("5"))
                        .outBusinessNo("user-a_integral_2026-01-24")
                        .build())
                .taskEntity(TaskEntity.builder()
                        .userId(userId)
                        .topic("credit_adjust_success")
                        .messageId("message-1")
                        .state(TaskStateVO.create)
                        .build())
                .build();
    }
}
