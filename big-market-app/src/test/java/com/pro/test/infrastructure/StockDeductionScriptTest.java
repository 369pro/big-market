package com.pro.test.infrastructure;

import com.pro.infrastructure.redis.RedissonService;
import org.junit.Test;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit checks for the single classpath source used by EVALSHA and EVAL. */
public class StockDeductionScriptTest {

    @Test
    @SuppressWarnings("unchecked")
    public void noscriptFallbackEvaluatesTheClasspathSource() throws Exception {
        RedissonClient redis = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redis.getScript(StringCodec.INSTANCE)).thenReturn(script);
        doThrow(new RedisException("NOSCRIPT No matching script")).when(script).evalSha(
                eq(RScript.Mode.READ_WRITE), eq("sha"), eq(RScript.ReturnType.MULTI),
                anyList(), any());
        doReturn(Arrays.asList(1L, 7L)).when(script).eval(
                eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.MULTI),
                anyList(), any());

        RedissonService service = new RedissonService();
        ReflectionTestUtils.setField(service, "redissonClient", redis);
        ReflectionTestUtils.setField(service, "stockDeductionSha", "sha");

        List<Long> result = service.execStockDeduction(
                "codex:unit:stock", "codex:unit:outbox", 1000,
                "event", "codex:unit:lock");

        assertEquals(Arrays.asList(1L, 7L), result);
        verify(script).eval(eq(RScript.Mode.READ_WRITE),
                eq(classpathScript()), eq(RScript.ReturnType.MULTI), anyList(), any());
    }

    private static String classpathScript() throws Exception {
        ClassPathResource resource = new ClassPathResource("lua/stock_deduction.lua");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
