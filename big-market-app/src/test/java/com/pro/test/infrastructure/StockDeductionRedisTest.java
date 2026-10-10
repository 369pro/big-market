package com.pro.test.infrastructure;

import com.pro.infrastructure.redis.RedissonService;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.redisson.Redisson;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Isolated Redis checks. Supply stock.redis.address to opt in. The password
 * comes from stock.redis.password or STOCK_REDIS_PASSWORD.
 */
public class StockDeductionRedisTest {

    private static RedissonClient redis;
    private String prefix;
    private String stockKey;
    private String outboxKey;
    private String lockKeyPrefix;

    @BeforeClass
    public static void connectToIsolatedRedis() {
        String address = System.getProperty("stock.redis.address");
        if (address == null || address.trim().isEmpty()) {
            Assume.assumeTrue("set -Dstock.redis.address to run the isolated Redis checks", false);
            return;
        }
        String password = System.getProperty("stock.redis.password");
        if (password == null || password.trim().isEmpty()) {
            password = System.getenv("STOCK_REDIS_PASSWORD");
        }
        Config config = new Config();
        org.redisson.config.SingleServerConfig server = config.useSingleServer()
                .setAddress(address)
                .setConnectTimeout(1000)
                .setTimeout(1000)
                .setRetryAttempts(0);
        if (password != null && !password.trim().isEmpty()) {
            server.setPassword(password);
        }
        try {
            redis = Redisson.create(config);
            redis.getBucket("codex:test:stock-deduction:probe:" + UUID.randomUUID()).isExists();
        } catch (Exception e) {
            if (redis != null) {
                redis.shutdown();
            }
            redis = null;
            throw new IllegalStateException("isolated Redis is unavailable: " + address, e);
        }
    }

    @AfterClass
    public static void closeRedis() {
        if (redis != null) {
            redis.shutdown();
        }
    }

    @Before
    public void setUpKeys() {
        prefix = "codex:test:stock-deduction:" + UUID.randomUUID();
        stockKey = prefix + ":stock";
        outboxKey = prefix + ":outbox";
        lockKeyPrefix = prefix + ":lock";
    }

    @After
    public void cleanUpKeys() {
        redis.getKeys().delete(stockKey, outboxKey,
                lockKeyPrefix + "_0", lockKeyPrefix + "_1", lockKeyPrefix + "_2");
    }

    @Test
    public void validDeductionWritesOneStockUnitAndOneOutboxMember() {
        RedissonService service = serviceWithLoadedScript();
        bucket(stockKey).set("2");

        assertEquals(Arrays.asList(1L, 1L), service.execStockDeduction(
                stockKey, outboxKey, 1000, "100001:101:event", lockKeyPrefix));
        assertEquals("1", bucket(stockKey).get());
        assertEquals(Collections.singleton("100001:101:event:1"), outbox().readAll());
        assertTrue(redis.getBucket(lockKeyPrefix + "_1").isExists());
    }

    @Test
    public void missingStockIsInsufficientWithoutCreatingTheKey() {
        RedissonService service = serviceWithLoadedScript();

        assertEquals(Arrays.asList(-1L, 0L), service.execStockDeduction(
                stockKey, outboxKey, 1000, "missing:event", lockKeyPrefix));
        assertFalse(redis.getBucket(stockKey).isExists());
        assertFalse(redis.getBucket(outboxKey).isExists());
    }

    @Test
    public void invalidTtlDoesNotConsumeStockOrCreateLock() {
        RedissonService service = serviceWithLoadedScript();
        bucket(stockKey).set("2");

        try {
            service.execStockDeduction(stockKey, outboxKey, -1,
                    "invalid:ttl", lockKeyPrefix);
            fail("negative TTL must be rejected before DECR");
        } catch (RedisException expected) {
            assertTrue(expected.getMessage().contains("expireMs"));
        }

        assertEquals("2", bucket(stockKey).get());
        assertFalse(redis.getBucket(lockKeyPrefix + "_1").isExists());
        assertFalse(redis.getBucket(outboxKey).isExists());
    }

    @Test
    public void ttlThatWouldOverflowRedisExpiryDoesNotConsumeStock() {
        RedissonService service = serviceWithLoadedScript();
        bucket(stockKey).set("2");

        try {
            service.execStockDeduction(stockKey, outboxKey, Long.MAX_VALUE,
                    "overflow:ttl", lockKeyPrefix);
            fail("an expiry that overflows Redis time must be rejected before DECR");
        } catch (RedisException expected) {
            assertTrue(expected.getMessage().contains("out of range"));
        }

        assertEquals("2", bucket(stockKey).get());
        assertFalse(redis.getBucket(lockKeyPrefix + "_1").isExists());
        assertFalse(redis.getBucket(outboxKey).isExists());
    }

    @Test
    public void wrongTypeOutboxDoesNotConsumeStockOrCreateLock() {
        RedissonService service = serviceWithLoadedScript();
        bucket(stockKey).set("2");
        bucket(outboxKey).set("wrong type");

        try {
            service.execStockDeduction(stockKey, outboxKey, 1000,
                    "wrong:outbox", lockKeyPrefix);
            fail("wrong type outbox must be rejected before DECR");
        } catch (RedisException expected) {
            assertTrue(expected.getMessage().contains("outbox"));
        }

        assertEquals("2", bucket(stockKey).get());
        assertEquals("wrong type", bucket(outboxKey).get());
        assertFalse(redis.getBucket(lockKeyPrefix + "_1").isExists());
    }

    @Test
    public void evalShaMissFallsBackToTheSameClasspathScript() {
        RedissonService service = serviceWithLoadedScript();
        ReflectionTestUtils.setField(service, "stockDeductionSha", "missing-sha");
        bucket(stockKey).set("1");

        assertEquals(Arrays.asList(1L, 0L), service.execStockDeduction(
                stockKey, outboxKey, 1000, "fallback:event", lockKeyPrefix));
        assertEquals("0", bucket(stockKey).get());
        assertEquals(Collections.singleton("fallback:event:0"), outbox().readAll());
    }

    private RedissonService serviceWithLoadedScript() {
        RedissonService service = new RedissonService();
        ReflectionTestUtils.setField(service, "redissonClient", redis);
        service.loadStockDeductionScript();
        return service;
    }

    private org.redisson.api.RBucket<String> bucket(String key) {
        return redis.getBucket(key, StringCodec.INSTANCE);
    }

    private RSet<String> outbox() {
        return redis.getSet(outboxKey, StringCodec.INSTANCE);
    }
}
