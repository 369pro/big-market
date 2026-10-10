package com.pro.test.domain.activity;

import com.pro.domain.activity.model.aggregate.CreatePartakeOrderAggregate;
import com.pro.domain.activity.model.entity.ActivityEntity;
import com.pro.domain.activity.model.entity.PartakeRaffleActivityEntity;
import com.pro.domain.activity.model.entity.UserRaffleOrderEntity;
import com.pro.domain.activity.model.valobj.ActivityStateVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.partake.AbstractRaffleActivityPartake;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RaffleActivityPartakeIdempotencyTest {

    @Test
    public void requestKeyDuplicateInsertReturnsCommittedWinner() {
        IActivityRepository repository = mock(IActivityRepository.class);
        TestPartakeService service = new TestPartakeService(repository);
        UserRaffleOrderEntity winner = UserRaffleOrderEntity.builder()
                .userId("user-1")
                .activityId(1001L)
                .orderId("winner")
                .requestId("request-key")
                .build();

        when(repository.queryRaffleOrderByRequestId(any(PartakeRaffleActivityEntity.class)))
                .thenReturn(null, winner);
        when(repository.queryRaffleActivityByActivityId(1001L)).thenReturn(ActivityEntity.builder()
                .activityId(1001L)
                .state(ActivityStateVO.open)
                .beginDateTime(new Date(System.currentTimeMillis() - 1_000L))
                .endDateTime(new Date(System.currentTimeMillis() + 60_000L))
                .build());
        doThrow(new AppException(ResponseCode.INDEX_DUP.getCode()))
                .when(repository).saveCreatePartakeOrderAggregate(any(CreatePartakeOrderAggregate.class));

        UserRaffleOrderEntity result = service.createOrder("user-1", 1001L, "request-key");

        assertSame(winner, result);
        verify(repository).saveCreatePartakeOrderAggregate(any(CreatePartakeOrderAggregate.class));
    }

    private static final class TestPartakeService extends AbstractRaffleActivityPartake {

        private TestPartakeService(IActivityRepository activityRepository) {
            super(activityRepository);
        }

        @Override
        protected CreatePartakeOrderAggregate doFilterAccount(String userId, Long activityId, Date currentDate) {
            return CreatePartakeOrderAggregate.builder()
                    .userId(userId)
                    .activityId(activityId)
                    .build();
        }

        @Override
        protected UserRaffleOrderEntity buildUserRaffleOrder(String userId, Long activityId, Date currentDate) {
            return UserRaffleOrderEntity.builder()
                    .userId(userId)
                    .activityId(activityId)
                    .orderId("loser")
                    .build();
        }
    }
}
