package com.pro.domain.strategy.service.armory.algorithm.impl;


import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description o(1) 时间复杂度算法，将抽奖概率分配到 Redis Map 结构中，1个概率对应的奖品就是一个Key，抽奖时直接通过 get 获取。
 * @create 2024-08-24 10:31
 */
@Slf4j
@Component("o1Algorithm")
public class O1Algorithm extends AbstractAlgorithm {
    /**
     * 计算公式；
     * 1. 找到范围内最小的概率值，比如 0.1、0.02、0.003，需要找到的值是 0.003
     * 2. 基于1找到的最小值，0.003 就可以计算出百分比、千分比的整数值。这里就是1000
     * 3. 那么「概率 * 1000」分别占比100个、20个、3个，总计是123个
     * 4. 后续的抽奖就用123作为随机数的范围值，生成的值100个都是0.1概率的奖品、20个是概率0.02的奖品、最后是3个是0.003的奖品。
     */
    /**
     * 对应之前的 void assembleLotteryStrategy(String key, List<StrategyAwardEntity> strategyAwardEntities)
     * 最开始设计的O(1)查找表抽奖
     */
    @Override
    public void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities, BigDecimal rateRange) {
        log.info("抽奖算法 O(1) 装配 key:{}", key);
        // 1. 生成策略奖品概率查找表「这里指需要在list集合中，存放上对应的奖品占位即可，占位越多等于概率越高」
        List<Integer> strategyAwardSearchRateTables = new ArrayList<>(rateRange.intValue());
        for (StrategyAwardEntity strategyAward : strategyAwardEntities) {
            Integer awardId = strategyAward.getAwardId();
            BigDecimal awardRate = strategyAward.getAwardRate();
            // 计算出每个概率值需要存放到查找表的数量，循环填充
//            for (int i = 0; i < rateRange.multiply(awardRate).setScale(0, RoundingMode.CEILING).intValue(); i++) {
//                strategyAwardSearchRateTables.add(awardId);
//            }
            strategyAwardSearchRateTables.addAll(Collections.nCopies(
                    rateRange.multiply(awardRate).setScale(0, RoundingMode.CEILING).intValue(), awardId.intValue()));
        }

        // 2. 对存储的奖品进行乱序操作
        Collections.shuffle(strategyAwardSearchRateTables);

        // 3. 生成出Map集合，key值，对应的就是后续的概率值。通过概率来获得对应的奖品ID
        Map<Integer, Integer> shuffleStrategyAwardSearchRateTable = new LinkedHashMap<>();
        for (int i = 0; i < strategyAwardSearchRateTables.size(); i++) {
            // <i, awardId(打乱后的)>
            shuffleStrategyAwardSearchRateTable.put(i, strategyAwardSearchRateTables.get(i));
        }

        // 4. 存放到 Redis
        repository.storeStrategyAwardSearchRateTable(key, shuffleStrategyAwardSearchRateTable.size(), shuffleStrategyAwardSearchRateTable);

    }

    // 等价于之前的 Integer getRandomAwardId(Long strategyId)
    @Override
    public Integer dispatchAlgorithm(String key) {
        log.info("抽奖算法 O(1) 抽奖计算 key:{}", key);
        // 分布式部署下，不一定为当前应用做的策略装配。也就是值不一定会保存到本应用，而是分布式应用，所以需要从 Redis 中获取。
        int rateRange = repository.getRateRange(key);
        // 通过生成的随机值，获取概率值奖品查找表的结果
        return repository.getStrategyAwardAssemble(key, secureRandom.nextInt(rateRange));
    }

}
