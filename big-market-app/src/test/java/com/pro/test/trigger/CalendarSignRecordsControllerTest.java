package com.pro.test.trigger;

import com.pro.domain.auth.service.IAuthService;
import com.pro.domain.rebate.service.IBehaviorRebateService;
import com.pro.trigger.api.response.Response;
import com.pro.trigger.http.RaffleActivityController;
import com.pro.types.enums.ResponseCode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CalendarSignRecordsControllerTest {

    private RaffleActivityController controller;
    private IAuthService authService;
    private IBehaviorRebateService behaviorRebateService;

    @Before
    public void setUp() {
        controller = new RaffleActivityController();
        authService = mock(IAuthService.class);
        behaviorRebateService = mock(IBehaviorRebateService.class);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "behaviorRebateService", behaviorRebateService);
    }

    @Test
    public void invalidMonthReturnsIllegalParameterWithoutQueryingRecords() {
        when(authService.checkToken("token")).thenReturn(true);
        when(authService.openid("token")).thenReturn("user-1");

        Response<List<String>> response = controller.queryCalendarSignRecordsByToken("token", "2024-13");

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        verify(behaviorRebateService, never()).querySignOutBusinessNoByMonth("user-1", "202413");
    }

    @Test
    public void invalidDaySuffixIsRejectedAsAnInvalidMonth() {
        when(authService.checkToken("token")).thenReturn(true);
        when(authService.openid("token")).thenReturn("user-1");

        Response<List<String>> response = controller.queryCalendarSignRecordsByToken("token", "2024-02-30");

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
        verify(behaviorRebateService, never()).querySignOutBusinessNoByMonth(anyString(), anyString());
    }

    @Test
    public void invalidTokenReturnsTokenErrorWithoutQueryingRecords() {
        when(authService.checkToken("expired-token")).thenReturn(false);

        Response<List<String>> response = controller.queryCalendarSignRecordsByToken("expired-token", "2024-10");

        assertEquals(ResponseCode.Login.TOKEN_ERROR.getCode(), response.getCode());
        verify(behaviorRebateService, never()).querySignOutBusinessNoByMonth("user-1", "202410");
    }

    @Test
    public void missingIdentityReturnsTokenErrorWithoutQueryingRecords() {
        when(authService.checkToken("token")).thenReturn(true);
        when(authService.openid("token")).thenReturn(" ");

        Response<List<String>> response = controller.queryCalendarSignRecordsByToken("token", "2024-02");

        assertEquals(ResponseCode.Login.TOKEN_ERROR.getCode(), response.getCode());
        verify(behaviorRebateService, never()).querySignOutBusinessNoByMonth(anyString(), anyString());
    }

    @Test
    public void validLeapMonthWithNoRecordsReturnsEmptyList() {
        when(authService.checkToken("token")).thenReturn(true);
        when(authService.openid("token")).thenReturn("user-1");
        when(behaviorRebateService.querySignOutBusinessNoByMonth("user-1", "202402"))
                .thenReturn(java.util.Collections.emptyList());

        Response<List<String>> response = controller.queryCalendarSignRecordsByToken("token", "2024-02");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(java.util.Collections.emptyList(), response.getData());
    }

    @Test
    public void recordsAreFilteredConvertedDeduplicatedAndSorted() {
        when(authService.checkToken("token")).thenReturn(true);
        when(authService.openid("token")).thenReturn("user-1");
        when(behaviorRebateService.querySignOutBusinessNoByMonth("user-1", "202410"))
                .thenReturn(Arrays.asList("20241010", "20241005", "20241010", "20240930", "20241099"));

        Response<List<String>> response = controller.queryCalendarSignRecordsByToken("token", "2024-10");

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(Arrays.asList("2024-10-05", "2024-10-10"), response.getData());
    }
}
