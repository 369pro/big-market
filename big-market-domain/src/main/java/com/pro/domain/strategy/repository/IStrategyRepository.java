package com.pro.domain.strategy.repository;


import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.model.entity.StrategyEntity;
import com.pro.domain.strategy.model.entity.StrategyRuleEntity;
import com.pro.domain.strategy.model.valobj.RuleTreeVO;
import com.pro.domain.strategy.model.valobj.RuleWeightVO;
import com.pro.domain.strategy.model.valobj.StrategyAwardRuleModelVO;
import com.pro.domain.strategy.model.valobj.StrategyAwardStockKeyVO;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 策略服务仓储接口
 * @create 2023-12-23 09:33
 */
public interface IStrategyRepository {

    List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId);

    <K, V> void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<K, V> strategyAwardSearchRateTable);

    <K, V> Map<K, V> getMap(String key);

    Integer getStrategyAwardAssemble(String key, Integer rateKey);

    int getRateRange(String key);

    int getRateRange(Long strategyId);

    StrategyEntity queryStrategyEntityByStrategyId(Long strategyId);

    StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel);

    String queryStrategyRuleValue(Long strategyId, String ruleModel);
    String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel);

    StrategyAwardRuleModelVO queryStrategyAwardRuleModelVO(Long strategyId, Integer awardId);

    RuleTreeVO queryRuleTreeVOByTreeId(String treeId);

    void cacheStrategyAwardCount(String cacheKey, Integer awardCount);

    /**
     * 缓存key，decr 方式扣减库存
     *
     * @param cacheKey 缓存Key
     * @return 扣减结果
     */
    Boolean subtractionAwardStock(String cacheKey);

    Boolean subtractionAwardStock(String cacheKey, Date endDateTime);
    /**
     * 写入奖品库存消费队列
     *
     * @param strategyAwardStockKeyVO 对象值对象
     */
    void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO);

    /**
     * 获取奖品库存消费队列
     */
    StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException;

    StrategyAwardStockKeyVO takeQueueValue(Long strategyId, Integer awardId) throws InterruptedException;

    /**
     * 排空队列中该奖品的所有消息，返回聚合后的总扣减数量
     */
    int takeQueueValueCount(Long strategyId, Integer awardId);

    /**
     * 更新奖品库存消耗
     *
     * @param strategyId 策略ID
     * @param awardId 奖品ID
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

    /**
     * 根据策略ID+奖品ID的唯一值组合，查询奖品信息
     *
     * @param strategyId 策略ID
     * @param awardId    奖品ID
     * @return 奖品信息
     */
    StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId);

    Long queryStrategyIdByActivityId(Long activityId);

    /**
     * 查询用户抽奖次数 - 当天的；策略ID:活动ID 1:1 的配置，可以直接用 strategyId 查询。
     *
     * @param userId     用户ID
     * @param strategyId 策略ID
     * @return 用户今日参与次数
     */
    Integer queryTodayUserRaffleCount(String userId, Long strategyId);

    Map<String, Integer> queryAwardRuleLockCount(String[] treeIds);

    List<RuleWeightVO> queryAwardRuleWeight(Long strategyId);

    Integer queryActivityAccountTotalUseCount(String userId, Long strategyId);

    /**
     * 查询有效活动的奖品配置
     *
     * @return 奖品配置列表
     */
    List<StrategyAwardStockKeyVO> queryOpenActivityStrategyAwardList();

    /**
     * 存储抽奖策略对应的Bean算法
     *
     * @param key      策略ID
     * @param beanName 策略对象名称
     */
    void cacheStrategyArmoryAlgorithm(String key, String beanName);

    /**
     * 获取存储抽奖策略对应的Bean算法
     *
     * @param key 策略ID
     * @return 策略对象名称
     */
    String queryStrategyArmoryAlgorithmFromCache(String key);

}
