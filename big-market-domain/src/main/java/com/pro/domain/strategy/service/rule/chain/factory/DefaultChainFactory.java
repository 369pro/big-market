package com.pro.domain.strategy.service.rule.chain.factory;

import com.pro.domain.strategy.model.entity.StrategyEntity;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.rule.ILogicFilter;
import com.pro.domain.strategy.service.rule.chain.ILogicChain;
import com.pro.domain.strategy.service.rule.chain.impl.DefaultLogicChain;
import org.springframework.stereotype.Service;

import java.util.Map;
@Service
public class DefaultChainFactory {
    /**
     * 构造器注入：Spring 看到构造函数参数 Map<String, ILogicChain> 和 IStrategyRepository，会分别注入符合类型的 bean。
     * 对于 Map<String, ILogicChain>，Spring 会把上下文中所有实现 ILogicChain 的 bean 收集到这个 Map，键是 bean 名称，值是对应实例。
     * bean 名称来源：默认是类名首字母小写，或通过 @Component("name") / @Service("name") / @Bean("name") 显式指定
     */
    protected IStrategyRepository repository;
    private Map<String, ILogicChain> logicChainGroup;

    public DefaultChainFactory(Map<String, ILogicChain> logicChainGroup, IStrategyRepository repository){
        this.repository = repository;
        this.logicChainGroup = logicChainGroup;
    }

    /**
     * 通过策略ID，构建责任链
     *
     * @param strategyId 策略ID
     * @return LogicChain
     */
    public ILogicChain openLogicChain(Long strategyId){
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        String[] ruleModels = strategyEntity.ruleModels();
        if(ruleModels == null || ruleModels.length == 0){
//            return new DefaultLogicChain();
            return logicChainGroup.get("default");
        }
        // 按照配置顺序装填用户配置的责任链；rule_blacklist、rule_weight
        // 「注意此数据从Redis缓存中获取，如果更新库表，记得在测试阶段手动处理缓存」
        ILogicChain firstNode = logicChainGroup.get(ruleModels[0]);
        ILogicChain curNode = firstNode;
        for (int i = 1; i < ruleModels.length; i++) {
            curNode = curNode.appendNext(logicChainGroup.get(ruleModels[i]));
        }
        // 【责任链的最后装填默认责任链】
        curNode.appendNext(logicChainGroup.get("default"));
        return firstNode;
    }
}
