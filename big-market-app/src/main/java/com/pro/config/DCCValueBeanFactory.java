package com.pro.config;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import com.pro.types.annotations.DCCValue;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 上有 @Configuration 注解，导致 Spring 启动时无条件扫描并实例化它，
 * 强制要求通过构造器注入 CuratorFramework bean。
 * 但 CuratorFramework Bean 在
 * ZooKeeperClientConfig中被 @ConditionalOnProperty(enable=true) 保护着，
 * 当 ZooKeeper 未启用时根本不会创建，于是就报错
 *
 * 【修复方案】：去掉
 * DCCValueBeanFactory上的 @Configuration 注解。这样DCCValueBeanFactory
 *  不再被 Spring 自动扫描，完全由
 * ZooKeeperClientConfig里条件受控的 @Bean 方法来管理：
 */
@Slf4j
//@Configuration
public class DCCValueBeanFactory implements BeanPostProcessor {
    private static final String BASE_CONFIG_PATH = "/big-market-dcc";
    private static final String BASE_CONFIG_PATH_CONFIG = BASE_CONFIG_PATH + "/config";

//    private final CuratorFramework client;
    private CuratorFramework client;

    private final Map<String, Object> dccObjGroup = new HashMap<>();
    // ❌ 之前：Spring 用构造器注入，强制找 CuratorFramework bean
    public DCCValueBeanFactory(CuratorFramework client) {
        if (null == client) {
            // 记录一条日志，告诉自己/运维这里没有注入 client，以防日后排查问题
            log.info("CuratorFramework client 未注入，将以无 client 模式运行。");
            return;
        }
        this.client = client;
    }

    @PostConstruct
    public void init() throws Exception {
        // 核心重点：既然 client 是可选的，init 里面第一件事就是判空！
        if (this.client == null) {
            log.warn("由于 client 为空，跳过 Zookeeper 相关的初始化逻辑...");
            // 直接返回，或者执行一些不需要 client 的降级初始化逻辑
            return;
        }
        log.info("DCC 正在连接 Zookeeper 81.68.91.52...");
        // 设定 3 秒超时，防止无限死等
        if (!client.blockUntilConnected(3, TimeUnit.SECONDS)) {
            log.warn("DCC 警告：连接 Zookeeper 超时，跳过初始化，系统将在后台持续重试。");
            return; // ⚠️ 超时就直接返回，避免后续 forPath 操作抛 ConnectionLossException
        }
        // 1.检查并且创建基础节点
        if(null == client.checkExists().forPath(BASE_CONFIG_PATH_CONFIG)){
            client.create().creatingParentsIfNeeded().forPath(BASE_CONFIG_PATH_CONFIG);
            log.info("DCC 节点监听 base node {} not absent create new done!", BASE_CONFIG_PATH_CONFIG);
        }
        // 2.创建缓存监听器，监听 /big-market-dcc/config 下的所有节点
        CuratorCache curatorCache = CuratorCache.build(client, BASE_CONFIG_PATH_CONFIG);
        curatorCache.start();
        // 3.添加监听器
        curatorCache.listenable().addListener((type, oldData, data) ->{
            switch (type){
                case NODE_CHANGED:
                    String dccValuePath = data.getPath();
                    Object objBean = dccObjGroup.get(dccValuePath);
                    if(objBean == null){
                        return;
                    }
                    try {
                        // 1. getDeclaredField 方法用于获取指定类中声明的所有字段，包括私有字段、受保护字段和公共字段。
                        // 2. getField 方法用于获取指定类中的公共字段，即只能获取到公共访问修饰符（public）的字段。
                        Field field = objBean.getClass().getDeclaredField(dccValuePath.substring(dccValuePath.lastIndexOf("/") + 1));
                        field.setAccessible(true);
                        field.set(objBean, new String(data.getData()));
                        field.setAccessible(false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    break;
                default:
                    break;
            }
        });
    }

    @Nullable
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (client == null) return bean;
        // 原来：获取bean中的所有字段
        // Class<?> beanClass = bean.getClass();
        // 现在：注意，增加 AOP 代理后，获得类的方式要通过 AopProxyUtils.getTargetClass(bean);
        // 不能直接 bean.class 因为代理后类的结构发生变化，这样不能获得到自己的自定义注解了。
        Class<?> targetBeanClass = bean.getClass();
        Object targetBeanObject = bean;
        if (AopUtils.isAopProxy(bean)) {
            targetBeanClass = AopUtils.getTargetClass(bean);
            targetBeanObject = AopProxyUtils.getSingletonTarget(bean);
        }
        Field[] fields = targetBeanClass.getDeclaredFields();
        for(Field field : fields){
            if(!field.isAnnotationPresent(DCCValue.class)){
                continue;
            }
            DCCValue dccValue = field.getAnnotation(DCCValue.class);
            String value = dccValue.value();
            if (StringUtils.isBlank(value)) {
                throw new RuntimeException(field.getName() +
                        " @DCCValue is not config value config case 「isSwitch/isSwitch:1」");
            }

            String[] splits = value.split(":");
            String key = splits[0];
            String defaultValue = splits.length == 2 ? splits[1] : null;

            try{
                // 判断当前节点是否存在，否则创建zookeeper节点
                String keyPath = BASE_CONFIG_PATH_CONFIG.concat("/").concat(key);
                if(null == client.checkExists().forPath(keyPath)){
                    client.create().creatingParentsIfNeeded().forPath(keyPath);
                    if (StringUtils.isNotBlank(defaultValue)) {
                        field.setAccessible(true);
                        field.set(targetBeanObject, defaultValue);
                        field.setAccessible(false);
                    }
                    log.info("DCC 节点监听 创建节点 {}", keyPath);
                }else{ // 节点存在，直接读取zk里面的值
                    String configValue = new String(client.getData().forPath(keyPath));
                    if (StringUtils.isNotBlank(configValue)) {
                        field.setAccessible(true);
                        field.set(targetBeanObject, configValue);
                        field.setAccessible(false);
                        log.info("DCC 节点监听 设置配置 {} {} {}", keyPath, field.getName(), configValue);
                    }
                }
                // 记录到缓存
                dccObjGroup.put(BASE_CONFIG_PATH_CONFIG.concat("/").concat(key), bean);
            } catch (Exception e) {
                // ZK 连接不稳定时，优雅降级：使用默认值，不抛异常炸掉 Spring 容器
                log.warn("DCC 连接 ZooKeeper 失败，字段 [{}] 将使用默认值 [{}]，原因：{}", field.getName(), defaultValue, e.getMessage());
                try {
                    if (StringUtils.isNotBlank(defaultValue)) {
                        field.setAccessible(true);
                        field.set(targetBeanObject, defaultValue);
                        field.setAccessible(false);
                    }
                } catch (Exception ex) {
                    log.error("DCC 设置默认值也失败了，字段 [{}]", field.getName(), ex);
                }
            }
        }

        return bean;
    }
}
