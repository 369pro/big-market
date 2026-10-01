package com.pro.test.award;

import com.pro.domain.award.adapter.port.IAwardPort;
import com.pro.domain.award.model.aggregate.GiveOutPrizesAggregate;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.model.entity.UserCreditAwardEntity;
import com.pro.domain.award.model.valobj.AwardStateVO;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.domain.award.service.distribute.impl.OpenAIAccountAdjustQuotaAward;
import com.pro.infrastructure.adapter.repository.AwardRepository;
import com.pro.infrastructure.dao.IUserAwardRecordDao;
import com.pro.infrastructure.dao.IUserCreditAccountDao;
import com.pro.infrastructure.dao.po.UserAwardRecord;
import com.pro.infrastructure.dao.po.UserCreditAccount;
import com.pro.infrastructure.redis.IRedisService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;

import java.math.BigDecimal;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AwardIdempotencyTest {

    private AwardRepository repository;
    private IUserAwardRecordDao userAwardRecordDao;
    private IUserCreditAccountDao userCreditAccountDao;
    private TransactionTemplate transactionTemplate;
    private TransactionStatus transactionStatus;

    @Before
    public void setUp() {
        repository = new AwardRepository();
        userAwardRecordDao = mock(IUserAwardRecordDao.class);
        userCreditAccountDao = mock(IUserCreditAccountDao.class);
        transactionTemplate = mock(TransactionTemplate.class);
        transactionStatus = mock(TransactionStatus.class);

        ReflectionTestUtils.setField(repository, "userAwardRecordDao", userAwardRecordDao);
        ReflectionTestUtils.setField(repository, "userCreditAccountDao", userCreditAccountDao);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(repository, "dbRouter", mock(IDBRouterStrategy.class));

        IRedisService redisService = mock(IRedisService.class);
        RLock lock = mock(RLock.class);
        when(redisService.getLock(anyString())).thenReturn(lock);
        when(lock.isLocked()).thenReturn(false);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        ReflectionTestUtils.setField(repository, "redisService", redisService);

        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        }).when(transactionTemplate).execute(any(TransactionCallback.class));
    }

    @Test
    public void duplicateCreditDeliveryDoesNotTouchAccount() {
        when(userAwardRecordDao.updateAwardRecordCompletedState(any())).thenReturn(0);
        when(userAwardRecordDao.queryByUserIdAndOrderId(any())).thenReturn(completedAwardRecord());

        repository.saveGiveOutPrizesAggregate(creditAggregate());

        verify(transactionStatus).setRollbackOnly();
        verifyNoInteractions(userCreditAccountDao);
    }

    @Test
    public void guardedAccountUpdateFailureRollsBackAwardClaim() {
        when(userAwardRecordDao.updateAwardRecordCompletedState(any())).thenReturn(1);
        when(userCreditAccountDao.queryUserCreditAccount(any())).thenReturn(new UserCreditAccount());
        when(userCreditAccountDao.updateAddAmount(any())).thenReturn(0);

        try {
            repository.saveGiveOutPrizesAggregate(creditAggregate());
            fail("Expected the guarded account update failure to propagate");
        } catch (RuntimeException expected) {
            // The transaction must not acknowledge a closed or insufficient account.
        }

        verify(transactionStatus).setRollbackOnly();
        verify(userCreditAccountDao).updateAddAmount(any());
    }

    @Test
    public void unexpectedAccountInsertDuplicatePropagatesForRetry() {
        when(userAwardRecordDao.updateAwardRecordCompletedState(any())).thenReturn(1);
        doThrow(new DuplicateKeyException("account race"))
                .when(userCreditAccountDao).insert(any());

        try {
            repository.saveGiveOutPrizesAggregate(creditAggregate());
            fail("Expected the account insert race to propagate");
        } catch (DuplicateKeyException expected) {
            // A rolled-back account race must remain retryable by the MQ consumer.
        }

        verify(transactionStatus).setRollbackOnly();
    }

    @Test
    public void missingAwardRecordIsNotAcknowledgedAsDuplicate() {
        when(userAwardRecordDao.updateAwardRecordCompletedState(any())).thenReturn(0);
        when(userAwardRecordDao.queryByUserIdAndOrderId(any())).thenReturn(null);

        try {
            repository.saveGiveOutPrizesAggregate(creditAggregate());
            fail("Expected a missing award record to propagate");
        } catch (IllegalStateException expected) {
            // CAS zero is only idempotent after a persisted completed matching award is verified.
        }

        verify(transactionStatus).setRollbackOnly();
        verifyNoInteractions(userCreditAccountDao);
    }

    @Test
    public void repeatedRemoteCompletionVerifiesPersistedState() {
        when(userAwardRecordDao.queryByUserIdAndOrderId(any())).thenReturn(completedAwardRecord());
        org.junit.Assert.assertFalse(repository.updateAwardRecordCompletedState("user-1", "award-order-1"));
    }

    @Test(expected = IllegalStateException.class)
    public void missingRemoteCompletionRecordRequiresRetry() {
        repository.updateAwardRecordCompletedState("user-1", "award-order-1");
    }

    @Test
    public void remoteAwardUsesOrderIdAndCompletesLocalRecordAfterSuccess() throws Exception {
        IAwardPort port = mock(IAwardPort.class);
        IAwardRepository awardRepository = mock(IAwardRepository.class);
        OpenAIAccountAdjustQuotaAward award = new OpenAIAccountAdjustQuotaAward();
        ReflectionTestUtils.setField(award, "port", port);
        ReflectionTestUtils.setField(award, "repository", awardRepository);
        when(awardRepository.queryAwardConfig(110)).thenReturn("3");

        DistributeAwardEntity entity = DistributeAwardEntity.builder()
                .userId("user-1")
                .orderId("award-order-1")
                .awardId(110)
                .build();

        award.giveOutPrizes(entity);

        InOrder order = org.mockito.Mockito.inOrder(port, awardRepository);
        order.verify(port).adjustAmount("user-1", "award-order-1", 3);
        order.verify(awardRepository).updateAwardRecordCompletedState("user-1", "award-order-1");
    }

    @Test
    public void remoteFailureDoesNotCompleteLocalRecord() throws Exception {
        IAwardPort port = mock(IAwardPort.class);
        IAwardRepository awardRepository = mock(IAwardRepository.class);
        OpenAIAccountAdjustQuotaAward award = new OpenAIAccountAdjustQuotaAward();
        ReflectionTestUtils.setField(award, "port", port);
        ReflectionTestUtils.setField(award, "repository", awardRepository);
        when(awardRepository.queryAwardConfig(110)).thenReturn("3");
        doThrow(new IllegalStateException("gateway unavailable"))
                .when(port).adjustAmount("user-1", "award-order-1", 3);

        try {
            award.giveOutPrizes(DistributeAwardEntity.builder()
                    .userId("user-1")
                    .orderId("award-order-1")
                    .awardId(110)
                    .build());
            fail("Expected the gateway failure to propagate");
        } catch (IllegalStateException expected) {
            // MQ must retry while the local award remains unfinished.
        }

        verify(awardRepository, never()).updateAwardRecordCompletedState(anyString(), anyString());
    }

    private GiveOutPrizesAggregate creditAggregate() {
        UserAwardRecordEntity awardRecord = UserAwardRecordEntity.builder()
                .userId("user-1")
                .orderId("award-order-1")
                .awardId(101)
                .awardState(AwardStateVO.complete)
                .build();
        UserCreditAwardEntity creditAward = UserCreditAwardEntity.builder()
                .userId("user-1")
                .creditAmount(new BigDecimal("5"))
                .build();
        return GiveOutPrizesAggregate.builder()
                .userId("user-1")
                .userAwardRecordEntity(awardRecord)
                .userCreditAwardEntity(creditAward)
                .build();
    }

    private UserAwardRecord completedAwardRecord() {
        UserAwardRecord record = new UserAwardRecord();
        record.setUserId("user-1");
        record.setOrderId("award-order-1");
        record.setAwardId(101);
        record.setAwardState("completed");
        return record;
    }
}
