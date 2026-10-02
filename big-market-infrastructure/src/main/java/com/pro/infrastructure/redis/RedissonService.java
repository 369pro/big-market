package com.pro.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis 服务 - Redisson
 *
 * @author Fuzhengwei bugstack.cn @小傅哥
 */
@Slf4j
@Service("redissonService")
public class RedissonService implements IRedisService {

    @Resource
    private RedissonClient redissonClient;

    /** Lua 脚本 SHA（启动时预加载） */
    private String stockDeductionSha;

    public <T> void setValue(String key, T value) {
        redissonClient.<T>getBucket(key).set(value);
    }

    @Override
    public <T> void setValue(String key, T value, long expired) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, Duration.ofMillis(expired));
    }

    public <T> T getValue(String key) {
        return redissonClient.<T>getBucket(key).get();
    }

    @Override
    public <T> RQueue<T> getQueue(String key) {
        return redissonClient.getQueue(key);
    }

    @Override
    public <T> RBlockingQueue<T> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }

    @Override
    public <T> RDelayedQueue<T> getDelayedQueue(RBlockingQueue<T> rBlockingQueue) {
        return redissonClient.getDelayedQueue(rBlockingQueue);
    }

    @Override
    public long incr(String key) {
        return redissonClient.getAtomicLong(key).incrementAndGet();
    }

    @Override
    public long incrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    @Override
    public long decr(String key) {
         return redissonClient.getAtomicLong(key).decrementAndGet();
//        return redissonClient.getAtomicLong(key).getAndDecrement();
    }

    @Override
    public long decrBy(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(-delta);
    }

    @Override
    public void remove(String key) {
        redissonClient.getBucket(key).delete();
    }

    @Override
    public boolean isExists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    public void addToSet(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        set.add(value);
    }

    public boolean isSetMember(String key, String value) {
        RSet<String> set = redissonClient.getSet(key);
        return set.contains(value);
    }

    public void addToList(String key, String value) {
        RList<String> list = redissonClient.getList(key);
        list.add(value);
    }

    public String getFromList(String key, int index) {
        RList<String> list = redissonClient.getList(key);
        return list.get(index);
    }

    @Override
    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }

    public void addToMap(String key, String field, String value) {
        RMap<String, String> map = redissonClient.getMap(key);
        map.put(field, value);
    }

    public String getFromMap(String key, String field) {
        RMap<String, String> map = redissonClient.getMap(key);
        return map.get(field);
    }

    @Override
    public <K, V> V getFromMap(String key, K field) {
        return redissonClient.<K, V>getMap(key).get(field);
    }

    public void addToSortedSet(String key, String value) {
        RSortedSet<String> sortedSet = redissonClient.getSortedSet(key);
        sortedSet.add(value);
    }

    @Override
    public RLock getLock(String key) {
        return redissonClient.getLock(key);
    }

    @Override
    public RLock getFairLock(String key) {
        return redissonClient.getFairLock(key);
    }

    @Override
    public RReadWriteLock getReadWriteLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }

    @Override
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(String key) {
        return redissonClient.getPermitExpirableSemaphore(key);
    }

    @Override
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    @Override
    public <T> RBloomFilter<T> getBloomFilter(String key) {
        return redissonClient.getBloomFilter(key);
    }

    @Override
    public void setAtomicLong(String key, long value) {
        redissonClient.getAtomicLong(key).set(value);
    }

    @Override
    public Boolean setAtomicLongIfAbsent(String key, long value) {
        // RAtomicLong and StringCodec both store a Redis integer string.  NX
        // makes initialization safe when multiple armory requests race.
        return redissonClient.getBucket(key, StringCodec.INSTANCE)
                .trySet(String.valueOf(value));
    }

    @Override
    public Long getAtomicLong(String key) {
        return redissonClient.getAtomicLong(key).get();
    }

    @Override
    public Boolean setNx(String key) {
        return redissonClient.getBucket(key).trySet("lock");
    }

    @Override
    public Boolean setNx(String key, long expired, TimeUnit timeUnit) {
        return redissonClient.getBucket(key).trySet("lock", expired, timeUnit);
    }

    // ==================== Lua 脚本 + MQ outbox ====================

    @PostConstruct
    public void loadStockDeductionScript() {
        try {
            ClassPathResource resource = new ClassPathResource("lua/stock_deduction.lua");
            String script;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                script = reader.lines().collect(Collectors.joining("\n"));
            }
            stockDeductionSha = redissonClient.getScript().scriptLoad(script);
            log.info("库存扣减 Lua 脚本加载成功 sha={}", stockDeductionSha);
        } catch (Exception e) {
            log.error("库存扣减 Lua 脚本加载失败，将回退到 eval 模式", e);
        }
    }

    @Override
    public List<Long> execStockDeduction(String stockKey, String trackSetKey,
                                          long expireMs, String eventPrefix, String lockKeyPrefix) {
        RScript script = redissonClient.getScript(org.redisson.client.codec.StringCodec.INSTANCE);
        List<Object> args = Arrays.asList(expireMs, eventPrefix, lockKeyPrefix);
        List<Object> keys = Arrays.asList(stockKey, trackSetKey);
        try {
            // 优先使用 EVALSHA（预加载过）
            if (stockDeductionSha != null) {
                return toLongList(script.evalSha(RScript.Mode.READ_WRITE,
                        stockDeductionSha, RScript.ReturnType.MULTI, keys, args.toArray()));
            }
        } catch (Exception e) {
            if (!(e instanceof org.redisson.client.RedisException)
                    || e.getMessage() == null || !e.getMessage().startsWith("NOSCRIPT")) throw e;
            log.warn("EVALSHA 执行失败，回退到 EVAL stockKey={}", stockKey, e);
        }
        // 回退到 eval 模式（脚本未预加载或 SHA 过期）
        String rawScript = "-- fallback: loaded from classpath\n" +
                "local surplus = redis.call('DECR', KEYS[1])\n" +
                "if surplus < 0 then redis.call('SET', KEYS[1], 0); return {-1, 0}; end\n" +
                "local lockKey = ARGV[3] .. '_' .. surplus\n" +
                "local lockOk; if ARGV[1] == '0' then lockOk = redis.call('SET', lockKey, '1', 'NX') " +
                "else lockOk = redis.call('SET', lockKey, '1', 'PX', ARGV[1], 'NX') end\n" +
                "if not lockOk then redis.call('INCR', KEYS[1]); return {0, surplus}; end\n" +
                "redis.call('SADD', KEYS[2], ARGV[2] .. ':' .. surplus); return {1, surplus}";
        return toLongList(script.eval(RScript.Mode.READ_WRITE,
                rawScript, RScript.ReturnType.MULTI, keys, args.toArray()));
    }

    @Override
    public Set<String> randomMembersFromSet(String key, int count) {
        RSet<String> set = redissonClient.getSet(key, org.redisson.client.codec.StringCodec.INSTANCE);
        Set<String> members = set.random(count);
        return members == null ? Collections.emptySet() : members;
    }

    @Override
    public boolean removeFromSet(String key, String member) {
        return redissonClient.<String>getSet(key, org.redisson.client.codec.StringCodec.INSTANCE).remove(member);
    }

    /** Redisson Lua 返回的 List<Object> 转 List<Long> */
    private List<Long> toLongList(List<Object> raw) {
        if (raw == null) return Collections.emptyList();
        return raw.stream()
                .map(o -> ((Number) o).longValue())
                .collect(Collectors.toList());
    }
}
