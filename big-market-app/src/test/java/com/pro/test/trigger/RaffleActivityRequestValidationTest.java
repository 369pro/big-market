package com.pro.test.trigger;

import com.pro.trigger.api.dto.ActivityDrawRequestDTO;
import com.pro.trigger.api.dto.SkuProductShopCartRequestDTO;
import com.pro.trigger.http.RaffleActivityController;
import com.pro.trigger.api.response.Response;
import com.pro.domain.auth.service.IAuthService;
import com.pro.types.enums.ResponseCode;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RaffleActivityRequestValidationTest {

    @Test
    public void drawRejectsNullRequest() {
        RaffleActivityController controller = new RaffleActivityController();

        Response<?> response = controller.draw((ActivityDrawRequestDTO) null);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
    }

    @Test
    public void exchangeRejectsNullRequest() {
        RaffleActivityController controller = new RaffleActivityController();

        Response<?> response = controller.creditPayExchangeSku((SkuProductShopCartRequestDTO) null);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
    }

    @Test
    public void tokenDrawRejectsNullRequestAfterAuthentication() {
        RaffleActivityController controller = authenticatedController();

        Response<?> response = controller.draw("token", (ActivityDrawRequestDTO) null);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
    }

    @Test
    public void tokenExchangeRejectsNullRequestAfterAuthentication() {
        RaffleActivityController controller = authenticatedController();

        Response<?> response = controller.creditPayExchangeSku("token", (SkuProductShopCartRequestDTO) null);

        assertEquals(ResponseCode.ILLEGAL_PARAMETER.getCode(), response.getCode());
    }

    private RaffleActivityController authenticatedController() {
        RaffleActivityController controller = new RaffleActivityController();
        IAuthService authService = mock(IAuthService.class);
        when(authService.checkToken("token")).thenReturn(true);
        ReflectionTestUtils.setField(controller, "authService", authService);
        return controller;
    }
}
