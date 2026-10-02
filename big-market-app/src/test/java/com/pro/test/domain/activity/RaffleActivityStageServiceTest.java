package com.pro.test.domain.activity;

import com.pro.domain.activity.model.entity.ActivityEntity;
import com.pro.domain.activity.model.valobj.ActivityStateVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.armory.IActivityArmory;
import com.pro.domain.activity.service.stage.RaffleActivityStageService;
import com.pro.domain.strategy.service.armory.IStrategyArmory;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

public class RaffleActivityStageServiceTest {

    private IActivityRepository repository;
    private IActivityArmory activityArmory;
    private IStrategyArmory strategyArmory;
    private RaffleActivityStageService service;

    @Before
    public void setUp() {
        repository = mock(IActivityRepository.class);
        activityArmory = mock(IActivityArmory.class);
        strategyArmory = mock(IStrategyArmory.class);
        service = new RaffleActivityStageService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "activityArmory", activityArmory);
        ReflectionTestUtils.setField(service, "strategyArmory", strategyArmory);
    }

    @Test
    public void activationPreparesBothCachesAndPublishesExactChannel() {
        ActivityEntity activity = openActivity();
        when(repository.queryRaffleActivityByActivityId(1001L)).thenReturn(activity);
        when(activityArmory.assembleActivitySkuByActivityId(1001L)).thenReturn(true);
        when(strategyArmory.assembleLotteryStrategy(2001L)).thenReturn(true);

        service.updateStageActivityState(1001L, " C01 ", "S01", "active");

        verify(activityArmory).assembleActivitySkuByActivityId(1001L);
        verify(strategyArmory).assembleLotteryStrategy(2001L);
        verify(repository).updateStageActivityState(1001L, "c01", "s01", "active");
    }

    @Test
    public void inactiveCreatesOrUpdatesWithoutPreparingCaches() {
        service.updateStageActivityState(1001L, "c01", "s01", "inactive");

        verify(repository).updateStageActivityState(1001L, "c01", "s01", "inactive");
        verifyNoInteractions(activityArmory, strategyArmory);
    }

    @Test
    public void activationRejectsActivityOutsideDateRange() {
        ActivityEntity activity = openActivity();
        activity.setEndDateTime(new Date(System.currentTimeMillis() - 1));
        when(repository.queryRaffleActivityByActivityId(1001L)).thenReturn(activity);

        try {
            service.updateStageActivityState(1001L, "c01", "s01", "active");
            fail("expected date validation failure");
        } catch (AppException e) {
            assertEquals(ResponseCode.ACTIVITY_DATE_ERROR.getCode(), e.getCode());
        }

        verifyNoInteractions(activityArmory, strategyArmory);
        verify(repository, never()).updateStageActivityState(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    public void activationPropagatesExistingChannelConflict() {
        ActivityEntity activity = openActivity();
        when(repository.queryRaffleActivityByActivityId(1001L)).thenReturn(activity);
        when(activityArmory.assembleActivitySkuByActivityId(1001L)).thenReturn(true);
        when(strategyArmory.assembleLotteryStrategy(2001L)).thenReturn(true);
        doThrow(new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "该渠道已有上架活动，请先下架"))
                .when(repository).updateStageActivityState(1001L, "c01", "s01", "active");

        try {
            service.updateStageActivityState(1001L, "c01", "s01", "active");
            fail("expected channel conflict");
        } catch (AppException e) {
            assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), e.getCode());
            assertEquals("该渠道已有上架活动，请先下架", e.getInfo());
        }
    }

    private ActivityEntity openActivity() {
        return ActivityEntity.builder()
                .activityId(1001L)
                .strategyId(2001L)
                .state(ActivityStateVO.open)
                .beginDateTime(new Date(System.currentTimeMillis() - 60_000))
                .endDateTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
    }
}
