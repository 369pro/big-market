package com.pro.aop;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.pro.types.annotations.DCCValue;
import com.pro.types.annotations.RateLimiterAccessInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class RateLimiterAOP {
    @DCCValue("rateLimiterSwitch:close")
    private String rateLimiterSwitch;

    // 缓存1：个人限频记录1分钟
    // <userid,RateLimiter>
    private final Cache<String, RateLimiter> loginRecord = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();

    // 缓存2：个人限频黑名单24h - 分布式业务场景，可以记录到 Redis 中
    // <userid, 拦截次数>
    private final Cache<String, Long> blacklist = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    @Pointcut("@annotation(com.pro.types.annotations.RateLimiterAccessInterceptor)")
    public void aopPoint(){}

    // jp: 获取目标参数，返回值等信息
    // rateLimiterAccessInterceptor: 注解对象，可以获取注解中的配置参数
    @Around("aopPoint() && @annotation(rateLimiterAccessInterceptor)")
    public Object doRouter(ProceedingJoinPoint jp, RateLimiterAccessInterceptor rateLimiterAccessInterceptor) throws Throwable {
        // 0. 限流开关【open 开启、close 关闭】关闭后，不会走限流策略
        if (StringUtils.isBlank(rateLimiterSwitch) || "close".equals(rateLimiterSwitch)) {
            return jp.proceed();
        }
        // 1. 获取限流key注意这里 key = "userId"
        String key = rateLimiterAccessInterceptor.key();
        if (StringUtils.isBlank(key)) {
            throw new RuntimeException("annotation RateLimiter uId is null！");
        }
        // 获取拦截字段的值即userId=xiaoming获取 xiaoming
        String keyValue = getAttrValue(key, jp.getArgs());
        log.info("aop attr value{}", keyValue);
        // 2. 黑名单拦截
        if (!"all".equals(key) && rateLimiterAccessInterceptor.blacklistCount() != 0 && null != blacklist.getIfPresent(keyValue) && blacklist.getIfPresent(keyValue) > rateLimiterAccessInterceptor.blacklistCount()) {
            log.info("限流-黑名单拦截(24h)：{}", keyValue);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }

        // 3. 获取/创建该用户的限流器
        RateLimiter ratelimiter = loginRecord.getIfPresent(keyValue);
        // 创建限流器
        if (null == ratelimiter) {
            ratelimiter = RateLimiter.create(rateLimiterAccessInterceptor.permitsPerSecond());
            loginRecord.put(keyValue, ratelimiter);
        }

        // 4. 限流拦截，没有令牌桶了
        if(!ratelimiter.tryAcquire()){
            // blacklistCount：超过多少次都被限制了，还访问的，扔到黑名单里24小时
            if(rateLimiterAccessInterceptor.blacklistCount() != 0){
                if(null == blacklist.getIfPresent(keyValue)){
                    // 第一次被限流，记录次数
                    blacklist.put(keyValue, 1L);
                }else{
                    blacklist.put(keyValue, blacklist.getIfPresent(keyValue) + 1);
                }
            }
            log.info("限流-超频次拦截：{}", keyValue);
            return fallbackMethodResult(jp, rateLimiterAccessInterceptor.fallbackMethod());
        }
        // 返回结果
        return jp.proceed();
    }


    Object fallbackMethodResult(JoinPoint jp, String fallbackMethod) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Signature sig = jp.getSignature();
        MethodSignature methodSignature = (MethodSignature) sig;
        // 重点：在 AOP 中，jp.getTarget() 拿到的是原始类，而 jp.getThis() 拿到的是 Spring 的代理对象。
        Method method = jp.getTarget().getClass().getMethod(fallbackMethod, methodSignature.getParameterTypes());
        return method.invoke(jp.getThis(), jp.getArgs());
    }

    /**
     * todo 没看懂这里是什么情况，调试一下啊
     * 实际根据自身业务调整，主要是为了获取通过某个值做拦截
     */
    public String getAttrValue(String attr, Object[] args) {
        if (args[0] instanceof String) {
            return args[0].toString();
        }
        String filedValue = null;
        for (Object arg : args) {
            try {
                if (StringUtils.isNotBlank(filedValue)) {
                    break;
                }
                // filedValue = BeanUtils.getProperty(arg, attr);
                // fix: 使用lombok时，uId这种字段的get方法与idea生成的get方法不同，会导致获取不到属性值，改成反射获取解决
                filedValue = String.valueOf(this.getValueByName(arg, attr));
            } catch (Exception e) {
                log.error("获取路由属性值失败 attr：{}", attr, e);
            }
        }
        return filedValue;
    }

    /**
     * 获取对象的特定属性值
     *
     * @param item 对象
     * @param name 属性名
     * @return 属性值
     * @author tang
     */
    private Object getValueByName(Object item, String name) {
        try {
            Field field = getFieldByName(item, name);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object o = field.get(item);
            field.setAccessible(false);
            return o;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 根据名称获取方法，该方法同时兼顾继承类获取父类的属性
     *
     * @param item 对象
     * @param name 属性名
     * @return 该属性对应方法
     * @author tang
     */
    private Field getFieldByName(Object item, String name) {
        try {
            Field field;
            try {
                field = item.getClass().getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                field = item.getClass().getSuperclass().getDeclaredField(name);
            }
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
