package com.pro.domain.strategy.service;


import com.pro.domain.strategy.model.valobj.StrategyAwardStockDeductEvent;
import com.pro.domain.strategy.model.valobj.StrategyAwardStockKeyVO;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖库存相关服务，获取库存消耗队列
 * @create 2024-02-09 12:17
 */
public interface IRaffleStock {

    /**
     * 获取奖品库存消耗队列
     *
     * @return 奖品库存Key信息
     * @throws InterruptedException 异常
     */
    StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException;

    /**
     * 获取奖品库存消耗队列
     *
     * @return 奖品库存Key信息
     * @throws InterruptedException 异常
     */
    StrategyAwardStockKeyVO takeQueueValue(Long strategyId, Integer awardId) throws InterruptedException;

    /**
     * 获取奖品库存消耗队列（聚合排空）
     *
     * @return 聚合后的扣减数量
     */
    int takeQueueValueCount(Long strategyId, Integer awardId);

    /**
     * 更新奖品库存消耗记录
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId);

    /**
     * 批量更新奖品库存消耗（聚合扣减）
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @param count      扣减数量
     */
    void updateStrategyAwardStock(Long strategyId, Integer awardId, int count);

    /** 将数据库库存幂等同步到 Redis 已扣减后的库存快照。 */
    void syncStrategyAwardStock(Long strategyId, Integer awardId, int surplus);

    /** 批量幂等同步具有稳定事件 ID 的库存快照。 */
    void syncStrategyAwardStock(List<StrategyAwardStockDeductEvent> events);

}
