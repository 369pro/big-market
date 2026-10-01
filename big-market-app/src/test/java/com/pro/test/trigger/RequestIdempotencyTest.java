package com.pro.test.trigger;

import com.pro.trigger.idempotency.RequestIdempotency;
import com.pro.types.exception.AppException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class RequestIdempotencyTest {

    @Test
    public void businessKeyIsStableAndNamespaced() {
        String first = RequestIdempotency.businessKey("user-a", "credit_exchange", "retry-1");

        assertEquals(first, RequestIdempotency.businessKey("user-a", "credit_exchange", " retry-1 "));
        assertEquals(64, first.length());
        assertNotEquals(first, RequestIdempotency.businessKey("user-b", "credit_exchange", "retry-1"));
        assertNotEquals(first, RequestIdempotency.businessKey("user-a", "draw", "retry-1"));
        assertNull(RequestIdempotency.businessKey("user-a", "credit_exchange", "  "));
    }

    @Test
    public void rejectsUnboundedRequestId() {
        try {
            RequestIdempotency.businessKey("user-a", "draw", "x".repeat(RequestIdempotency.MAX_REQUEST_ID_LENGTH + 1));
            fail("expected illegal requestId");
        } catch (AppException expected) {
            assertEquals("0002", expected.getCode());
        }
    }
}
