package com.pro.domain.strategy.service.armory;

import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.model.entity.StrategyEntity;
import com.pro.domain.strategy.model.entity.StrategyRuleEntity;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;
@Slf4j
public abstract class AbstractStrategyAlgorithm implements IStrategyArmory, IStrategyDispatch {
    @Resource
    protected IStrategyRepository repository;

    protected final SecureRandom secureRandom = new SecureRandom();

    @Override
    public boolean assembleLotteryStrategyByActivityId(Long activityId) {
        Long strategyId = repository.queryStrategyIdByActivityId(activityId);
        return assembleLotteryStrategy(strategyId);
    }

    /**
     * 4000:102,103,104,105 5000:102,103,104,105,106,107 6000:102,103,104,105,106,107,108,109
     * @param strategyId 策略ID
     * @return
     */
    @Override
    public boolean assembleLotteryStrategy(Long strategyId) {
        // 1. 查询策略配置
        // 没查到就存到RDS里面 <strategyId,StrategyAwardEntityList>
        List<StrategyAwardEntity> strategyAwardEntities = repository.queryStrategyAwardList(strategyId);
        if(ObjectUtils.isEmpty(strategyAwardEntities)){
            return false;
        }
        // 2 缓存每种奖品库存数量【用于decr扣减库存使用】
        // strategy_award_count_key_100001_101:8000
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            // Repeated armory calls must preserve the live Redis counter.  The
            // repository value is the remaining stock, not the original total.
            Integer awardCountSurplus = strategyAward.getAwardCountSurplus();
            cacheStrategyAwardCount(strategyId, awardId,
                    awardCountSurplus == null ? 0 : awardCountSurplus);
        }

        // 3.1 默认装配配置【全量抽奖概率】 装配最开始的抽奖table
        // eg: O(1): <big_market_strategy_rate_table_key_100001,{表格编号,awardId...是哈希结构}>
        armoryAlgorithm(String.valueOf(strategyId), strategyAwardEntities);
        // 3.2 查询权重配置--适用于 rule_weight 权重规则配置
        StrategyEntity strategyEntity = repository.queryStrategyEntityByStrategyId(strategyId);
        String ruleWeight = strategyEntity.getRuleWeight();
        // 没有权重配置
        if(ruleWeight == null){
            return true;
        }
        // 权重装配
        // <big_market_strategy_rate_table_key_100001_200:106,107,{}...哈希结构>
        StrategyRuleEntity strategyRuleEntity = repository.queryStrategyRule(strategyId, ruleWeight);
        Map<String, List<Integer>> ruleWeightValueMap = strategyRuleEntity.getRuleWeightValues();
        Set<String> keys = ruleWeightValueMap.keySet();
        for(String key : keys){
            List<Integer> ruleWeightValueList = ruleWeightValueMap.get(key);
            ArrayList<StrategyAwardEntity> strategyAwardEntitiesClone = new ArrayList<>(strategyAwardEntities);
            // 删除规则权重外的entity，剩下的（表里面配置的）重新装配并且放入redis
            strategyAwardEntitiesClone.removeIf(entity -> !ruleWeightValueList.contains(entity.getAwardId()));
            // note 按照不同的key切分，分开装配
            armoryAlgorithm(String.valueOf(strategyId).concat("_").concat(key), strategyAwardEntitiesClone);
        }
        return true;
    }

    private void cacheStrategyAwardCount(Long strategyId, Integer awardId, Integer awardCount) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        repository.cacheStrategyAwardCount(cacheKey, awardCount);
    }

    @Override
    public Integer getRandomAwardId(Long strategyId) {
        return dispatchAlgorithm(String.valueOf(strategyId));
    }

    @Override
    public Integer getRandomAwardId(String key) {
        return dispatchAlgorithm(key);
    }

    @Override
    public Integer getRandomAwardId(Long strategyId, String ruleWeightValue) {
        String key = String.valueOf(strategyId).concat("_").concat(ruleWeightValue);
        // 100001_4000:102,103,104,105
        log.info("key = {}", key);
        return dispatchAlgorithm(key);
    }

    /**
     * note 此处问题转换计算，只根据小数位来计算。如【0.01返回100】、【0.009返回1000】、【0.0018返回10000】
     */
    protected double convert(double min){
        double rangeRes = 1.0;
        while(min < 1.0){
            min *= 10;
            rangeRes *= 10;
        }
        return rangeRes;
    }

    // 概率最小值
    protected BigDecimal minAwardRate(List<StrategyAwardEntity> strategyAwardEntities){
        return strategyAwardEntities.stream()
                .map(StrategyAwardEntity::getAwardRate)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public Boolean subtractionAwardStock(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        return repository.subtractionAwardStock(cacheKey);
    }

    @Override
    public Boolean subtractionAwardStock(Long strategyId, Integer awardId, Date endDateTime) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY + strategyId + Constants.UNDERLINE + awardId;
        return repository.subtractionAwardStock(cacheKey, endDateTime);
    }

    /**
     * 装配算法
     * @param key 为策略ID、权重ID
     * @param strategyAwardEntities 对应的奖品概率
     */
    protected abstract void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities);

    protected abstract Integer dispatchAlgorithm(String key);
}
