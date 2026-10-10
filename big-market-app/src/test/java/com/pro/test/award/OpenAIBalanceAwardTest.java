package com.pro.test.award;

import com.pro.domain.award.adapter.port.IBalanceAwardPort;
import com.pro.domain.award.model.entity.DistributeAwardEntity;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.domain.award.service.distribute.impl.OpenAIBalanceAward;
import org.junit.Test;
import java.math.BigDecimal;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class OpenAIBalanceAwardTest {
    @Test
    public void creditsExactAmountBeforeCompletingRecord() throws Exception {
        IBalanceAwardPort port = mock(IBalanceAwardPort.class);
        IAwardRepository repository = mock(IAwardRepository.class);
        when(repository.queryAwardConfig(1110)).thenReturn("0.01");
        new OpenAIBalanceAward(port, repository).giveOutPrizes(award(null));
        org.mockito.InOrder order = inOrder(port, repository);
        order.verify(port).creditBalance("user", "raffle:user:order", new BigDecimal("0.01"));
        order.verify(repository).updateAwardRecordCompletedState("user", "order", 1110);
    }

    @Test
    public void remoteFailureKeepsRecordRetryable() throws Exception {
        IBalanceAwardPort port = mock(IBalanceAwardPort.class);
        IAwardRepository repository = mock(IAwardRepository.class);
        doThrow(new RuntimeException("unavailable")).when(port).creditBalance(anyString(), anyString(), any());
        try {
            new OpenAIBalanceAward(port, repository).giveOutPrizes(award("0.05"));
            fail("remote failure must propagate");
        } catch (RuntimeException expected) {
            verify(repository, never()).updateAwardRecordCompletedState(anyString(), anyString(), anyInt());
        }
    }

    @Test
    public void rejectsInvalidAmountsBeforeAnyCredit() throws Exception {
        IBalanceAwardPort port = mock(IBalanceAwardPort.class);
        IAwardRepository repository = mock(IAwardRepository.class);
        for (String amount : new String[]{"0", "-1", "0.000000001", "1000001", "invalid"}) {
            try {
                new OpenAIBalanceAward(port, repository).giveOutPrizes(award(amount));
                fail("invalid amount accepted: " + amount);
            } catch (IllegalArgumentException expected) { /* invalid monetary grant */ }
        }
        verifyNoInteractions(port, repository);
    }

    private DistributeAwardEntity award(String config) {
        return DistributeAwardEntity.builder().userId("user").orderId("order").awardId(1110).awardConfig(config).build();
    }
}
