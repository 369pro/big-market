package com.pro.test.trigger;

import com.alibaba.fastjson.JSON;
import com.pro.adapter.repository.IAwardFeedRepository;
import com.pro.domain.auth.service.IAuthService;
import com.pro.model.valobj.AwardFeedRecordVO;
import com.pro.trigger.api.dto.AwardFeedItemDTO;
import com.pro.trigger.api.dto.AwardFeedResponseDTO;
import com.pro.trigger.api.response.Response;
import com.pro.trigger.http.AwardFeedController;
import com.pro.types.enums.ResponseCode;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AwardFeedControllerTest {

    private AwardFeedController controller;
    private IAuthService authService;
    private IAwardFeedRepository awardFeedRepository;

    @Before
    public void setUp() {
        controller = new AwardFeedController();
        authService = mock(IAuthService.class);
        awardFeedRepository = mock(IAwardFeedRepository.class);
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "awardFeedRepository", awardFeedRepository);
    }

    @Test
    public void invalidTokenDoesNotQueryTheFeed() {
        when(authService.checkToken("expired-token")).thenReturn(false);

        Response<AwardFeedResponseDTO> response = controller.queryAwardFeed(
                "expired-token", 100401L, 1, 5);

        assertEquals(ResponseCode.Login.TOKEN_ERROR.getCode(), response.getCode());
        verify(awardFeedRepository, never()).queryRecentAwards(anyLong());
    }

    @Test
    public void missingTokenIsRejectedBeforeAuthenticationLookup() {
        Response<AwardFeedResponseDTO> response = controller.queryAwardFeed(
                null, 100401L, 1, 5);

        assertEquals(ResponseCode.Login.TOKEN_ERROR.getCode(), response.getCode());
        verify(authService, never()).checkToken(org.mockito.ArgumentMatchers.any());
        verify(awardFeedRepository, never()).queryRecentAwards(anyLong());
    }

    @Test
    public void invalidActivityAndPageParametersAreRejected() {
        when(authService.checkToken("token")).thenReturn(true);

        Response<AwardFeedResponseDTO> nullActivity = controller.queryAwardFeed(
                "token", null, 1, 5);
        Response<AwardFeedResponseDTO> pageTooLarge = controller.queryAwardFeed(
                "token", 100401L, 101, 5);
        Response<AwardFeedResponseDTO> sizeTooLarge = controller.queryAwardFeed(
                "token", 100401L, 1, 11);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), nullActivity.getCode());
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), pageTooLarge.getCode());
        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), sizeTooLarge.getCode());
        verify(awardFeedRepository, never()).queryRecentAwards(anyLong());
    }

    @Test
    public void pageAndBroadcastsAreBuiltFromMaskedRecords() {
        when(authService.checkToken("token")).thenReturn(true);
        List<AwardFeedRecordVO> records = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            records.add(new AwardFeedRecordVO("david01", "order-" + i, 100 + i,
                    "award-" + i, new Date(1_700_000_000_000L - i * 1_000L)));
        }
        when(awardFeedRepository.queryRecentAwards(100401L)).thenReturn(records);

        Response<AwardFeedResponseDTO> response = controller.queryAwardFeed(
                "token", 100401L, 2, 5);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        AwardFeedResponseDTO data = response.getData();
        assertNotNull(data);
        assertEquals(12, data.getTotal());
        assertEquals(2, data.getPageNo());
        assertEquals(5, data.getPageSize());
        assertEquals(100, data.getRecordLimit());
        assertEquals(5, data.getRecords().size());
        assertEquals(Integer.valueOf(105), data.getRecords().get(0).getAwardId());
        assertEquals(10, data.getBroadcasts().size());
        assertEquals("d***01", data.getRecords().get(0).getWinnerName());

        String wireBody = JSON.toJSONString(response);
        assertFalse(wireBody.contains("david01"));
        assertFalse(wireBody.contains("order-6"));
        assertEquals(64, data.getRecords().get(0).getRecordId().length());
        assertTrue(data.getRecords().get(0).getAwardTime().contains("+08:00"));
    }

    @Test
    public void emptyActivityReturnsSuccessfulEmptyLists() {
        when(authService.checkToken("token")).thenReturn(true);
        when(awardFeedRepository.queryRecentAwards(100401L)).thenReturn(java.util.Collections.emptyList());
        Response<AwardFeedResponseDTO> response = controller.queryAwardFeed("token", 100401L, 1, 5);
        assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
        assertEquals(0, response.getData().getTotal());
        assertTrue(response.getData().getRecords().isEmpty());
        assertTrue(response.getData().getBroadcasts().isEmpty());
    }
}
