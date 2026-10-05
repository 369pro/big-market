package com.pro.test.auth;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.pro.infrastructure.adapter.repository.CreditRepository;
import com.pro.infrastructure.dao.IUserCreditAccountDao;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class NewAccountCreditTest {
    @Test
    public void missingCreditAccountHasZeroBalanceAndClearsRouting() {
        CreditRepository repository = new CreditRepository();
        IDBRouterStrategy router = mock(IDBRouterStrategy.class);
        ReflectionTestUtils.setField(repository, "dbRouter", router);
        ReflectionTestUtils.setField(repository, "userCreditAccountDao", mock(IUserCreditAccountDao.class));
        assertEquals(BigDecimal.ZERO, repository.queryUserCreditAccount("new-user").getAdjustAmount());
        verify(router).doRouter("new-user");
        verify(router).clear();
    }
}
