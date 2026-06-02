package com.pro.domain.strategy.service.rule.chain.factory;

import com.pro.domain.strategy.model.entity.StrategyEntity;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.rule.chain.ILogicChain;
import lombok.*;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DefaultChainFactory {
    private final ApplicationContext applicationContext;
    /**
     * 构造器注入：Spring 看到构造函数参数 Map<String, ILogicChain> 和 IStrategyRepository，会分别注入符合类型的 bean。
     * 对于 Map<String, ILogicChain>，Spring 会把上下文中所有实现 ILogicChain 的 bean 收集到这个 Map，键是 bean 名称，值是对应实例。
     * bean 名称来源：默认是类名首字母小写，或通过 @Component("name") / @Service("name") / @Bean("name") 显式指定
     */
    protected IStrategyRepository repository;
    // 存放策略链，策略ID -> 责任链
    private final Map<Long, ILogicChain> strategyChainGroup;
    public DefaultChainFactory(ApplicationContext applicationContext, IStrategyRepository repository){
        this.applicationContext = applicationContext;
        this.repository = repository;
        this.strategyChainGroup = new ConcurrentHashMap<>();
    }
    public ILogicChain openLogicChain(Long strategyId){
        ILogicChain cacheLogicChain = strategyChainGroup.get(strategyId);
        if(null != cacheLogicChain){
            return cacheLogicChain;
        }
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        String[] ruleModels = strategyEntity.ruleModels();
        ILogicChain ruleDefaultLogicChain = applicationContext.getBean(LogicModel.RULE_DEFAULT.getCode(), ILogicChain.class);
        if(ruleModels == null || ruleModels.length == 0){
//            return new DefaultLogicChain();
            strategyChainGroup.put(strategyId, ruleDefaultLogicChain);
            return ruleDefaultLogicChain;
        }
        // 按照配置顺序装填用户配置的责任链；rule_blacklist、rule_weight
        // 「注意此数据从Redis缓存中获取，如果更新库表，记得在测试阶段手动处理缓存」
        ILogicChain firstNode = applicationContext.getBean(ruleModels[0], ILogicChain.class);
        ILogicChain curNode = firstNode;
        for (int i = 1; i < ruleModels.length; i++) {
            curNode = curNode.appendNext(applicationContext.getBean(ruleModels[i], ILogicChain.class));
        }
        // 【责任链的最后装填默认责任链】
        curNode.appendNext(ruleDefaultLogicChain);
        strategyChainGroup.put(strategyId, firstNode);
        return firstNode;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StrategyAwardVO {
        /** 抽奖奖品ID - 内部流转使用 */
        private Integer awardId;
        /**  */
        private String logicModel;

        /**
         * 抽奖奖品规则
         */
        private String awardRuleValue;
    }

    @Getter
    @AllArgsConstructor
    public enum LogicModel {

        RULE_DEFAULT("rule_default", "默认抽奖"),
        RULE_BLACKLIST("rule_blacklist", "黑名单抽奖"),
        RULE_WEIGHT("rule_weight", "权重规则"),
        ;

        private final String code;
        private final String info;

    }
}
