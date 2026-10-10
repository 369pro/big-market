package com.pro.test.award;

import com.pro.domain.award.adapter.event.SendAwardMessageEvent;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.model.valobj.AwardStateVO;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.domain.award.service.AwardService;
import com.pro.domain.award.service.distribute.IDistributeAward;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AwardServiceValidationTest {

    @Test
    public void missingAwardDefinitionRemainsRetryable() throws Exception {
        IAwardRepository repository = mock(IAwardRepository.class);
        when(repository.queryAwardRecord("user", "order")).thenReturn(pendingAward(101));
        when(repository.queryAwardKey(101)).thenReturn(null);
        AwardService service = new AwardService(repository, mock(SendAwardMessageEvent.class), Collections.emptyMap());

        try {
            service.distributeAward(DistributeAwardEntity.builder()
                    .userId("user")
                    .orderId("order")
                    .awardId(101)
                    .build());
            fail("A missing award definition must not acknowledge the message");
        } catch (IllegalStateException expected) {
            // The MQ consumer must retry or route this configuration error for inspection.
        }
    }

    @Test
    public void mismatchedAwardCannotReachDistributor() throws Exception {
        IAwardRepository repository = mock(IAwardRepository.class);
        IDistributeAward distributor = mock(IDistributeAward.class);
        when(repository.queryAwardRecord("user", "order")).thenReturn(pendingAward(101));
        AwardService service = new AwardService(repository, mock(SendAwardMessageEvent.class),
                Collections.singletonMap("openai_balance", distributor));

        try {
            service.distributeAward(DistributeAwardEntity.builder()
                    .userId("user")
                    .orderId("order")
                    .awardId(102)
                    .build());
            fail("A message for another award must be rejected before external delivery");
        } catch (IllegalStateException expected) {
            // The persisted order identity is authoritative for the delivery.
        }

        verifyNoInteractions(distributor);
        org.mockito.Mockito.verify(repository, never()).queryAwardKey(102);
    }

    @Test
    public void completedAwardRetryDoesNotCallDistributorAgain() throws Exception {
        IAwardRepository repository = mock(IAwardRepository.class);
        IDistributeAward distributor = mock(IDistributeAward.class);
        when(repository.queryAwardRecord("user", "order")).thenReturn(UserAwardRecordEntity.builder()
                .userId("user")
                .orderId("order")
                .awardId(101)
                .awardState(AwardStateVO.complete)
                .build());
        AwardService service = new AwardService(repository, mock(SendAwardMessageEvent.class),
                Collections.singletonMap("openai_balance", distributor));

        service.distributeAward(DistributeAwardEntity.builder()
                .userId("user")
                .orderId("order")
                .awardId(101)
                .build());

        verifyNoInteractions(distributor);
        org.mockito.Mockito.verify(repository, never()).queryAwardKey(101);
    }

    private UserAwardRecordEntity pendingAward(int awardId) {
        return UserAwardRecordEntity.builder()
                .userId("user")
                .orderId("order")
                .awardId(awardId)
                .awardState(AwardStateVO.create)
                .build();
    }
}
