package com.pro.infrastructure.adapter.repository;

import static com.pro.types.enums.ResponseCode.UN_ASSEMBLED_STRATEGY_ARMORY;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.model.entity.StrategyEntity;
import com.pro.domain.strategy.model.entity.StrategyRuleEntity;
import com.pro.domain.strategy.model.valobj.RuleLimitTypeVO;
import com.pro.domain.strategy.model.valobj.RuleLogicCheckTypeVO;
import com.pro.domain.strategy.model.valobj.RuleTreeNodeLineVO;
import com.pro.domain.strategy.model.valobj.RuleTreeNodeVO;
import com.pro.domain.strategy.model.valobj.RuleTreeVO;
import com.pro.domain.strategy.model.valobj.RuleWeightVO;
import com.pro.domain.strategy.model.valobj.StrategyAwardRuleModelVO;
import com.pro.domain.strategy.model.valobj.StrategyAwardStockDeductEvent;
import com.pro.domain.strategy.model.valobj.StrategyAwardStockKeyVO;
import com.pro.domain.strategy.repository.IStrategyRepository;
import com.pro.domain.strategy.service.rule.chain.factory.DefaultChainFactory;
import com.pro.infrastructure.dao.IRaffleActivityAccountDao;
import com.pro.infrastructure.dao.IRaffleActivityAccountDayDao;
import com.pro.infrastructure.dao.IRaffleActivityDao;
import com.pro.infrastructure.dao.IRuleTreeDao;
import com.pro.infrastructure.dao.IRuleTreeNodeDao;
import com.pro.infrastructure.dao.IRuleTreeNodeLineDao;
import com.pro.infrastructure.dao.IStrategyAwardDao;
import com.pro.infrastructure.dao.IStrategyDao;
import com.pro.infrastructure.dao.IStrategyRuleDao;
import com.pro.infrastructure.dao.po.RaffleActivityAccount;
import com.pro.infrastructure.dao.po.RaffleActivityAccountDay;
import com.pro.infrastructure.dao.po.RuleTree;
import com.pro.infrastructure.dao.po.RuleTreeNode;
import com.pro.infrastructure.dao.po.RuleTreeNodeLine;
import com.pro.infrastructure.dao.po.Strategy;
import com.pro.infrastructure.dao.po.StrategyAward;
import com.pro.infrastructure.dao.po.StrategyAwardStockInbox;
import com.pro.infrastructure.dao.po.StrategyRule;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.infrastructure.event.StrategyAwardStockMessagePublisher;
import com.pro.types.common.Constants;
import com.pro.types.exception.AppException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 策略服务仓储实现
 * @create 2023-12-23 10:33
 */
@Slf4j
@Repository
public class StrategyRepository implements IStrategyRepository {

    @Resource
    private IStrategyDao strategyDao;
    @Resource
    private IStrategyRuleDao strategyRuleDao;

    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Resource
    private IRaffleActivityAccountDao raffleActivityAccountDao;
    @Resource
    private IRuleTreeDao ruleTreeDao;
    @Resource
    private IRuleTreeNodeDao ruleTreeNodeDao;
    @Resource
    private IRuleTreeNodeLineDao ruleTreeNodeLineDao;
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IRaffleActivityAccountDayDao raffleActivityAccountDayDao;

    @Resource
    private IRedisService redisService;
    @Resource
    private StrategyAwardStockMessagePublisher stockMessagePublisher;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private IDBRouterStrategy dbRouter;

    @Override
    public List<StrategyAwardEntity> queryStrategyAwardList(Long strategyId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId;
        List<StrategyAwardEntity> strategyAwardEntities = redisService.getValue(cacheKey);
        if (null != strategyAwardEntities && !strategyAwardEntities.isEmpty()) return strategyAwardEntities;
        // 从库中获取数据
        List<StrategyAward> strategyAwards = strategyAwardDao.queryStrategyAwardListByStrategyId(strategyId);
        strategyAwardEntities = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            StrategyAwardEntity strategyAwardEntity = StrategyAwardEntity.builder()
                    .strategyId(strategyAward.getStrategyId())
                    .awardId(strategyAward.getAwardId())
                    .awardTitle(strategyAward.getAwardTitle())
                    .awardSubtitle(strategyAward.getAwardSubtitle())
                    .awardCount(strategyAward.getAwardCount())
                    .awardCountSurplus(strategyAward.getAwardCountSurplus())
                    .awardRate(strategyAward.getAwardRate())
                    .sort(strategyAward.getSort())
                    .ruleModels(strategyAward.getRuleModels())
                    .build();
            strategyAwardEntities.add(strategyAwardEntity);
        }
        redisService.setValue(cacheKey, strategyAwardEntities);
        return strategyAwardEntities;
    }
    // key: strategyId
     @Override
     public <K, V> void storeStrategyAwardSearchRateTable(String key, Integer rateRange, Map<K, V> strategyAwardSearchRateTable) {
         // 1. 存储抽奖策略范围值，如10000，用于生成1000以内的随机数
         redisService.setValue(Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key, rateRange);
//        // 2. 存储概率查找表
//        Map<Integer, Integer> cacheRateTable = redisService.getMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key);
//        cacheRateTable.putAll(strategyAwardSearchRateTable);
         String rateTableKey = Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + key;
        if(redisService.isExists(rateTableKey)){
            redisService.remove(rateTableKey);
        }
        RMap<K, V> cacheRateTable = redisService.getMap(rateTableKey);
        cacheRateTable.putAll(strategyAwardSearchRateTable);
    }

    @Override
    public <K, V> Map<K, V> getMap(String key) {
        return redisService.getMap(key);
    }

    @Override
    public StrategyEntity queryStrategyEntityByStrategyId(Long strategyId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_KEY + strategyId;
        StrategyEntity strategyEntity = redisService.getValue(cacheKey);
        if (null != strategyEntity) return strategyEntity;
        Strategy strategy = strategyDao.queryStrategyByStrategyId(strategyId);
        strategyEntity = StrategyEntity.builder()
                .strategyId(strategy.getStrategyId())
                .strategyDesc(strategy.getStrategyDesc())
                .ruleModels(strategy.getRuleModels())
                .build();
        redisService.setValue(cacheKey, strategyEntity);
        return strategyEntity;
    }

    @Override
    public StrategyRuleEntity queryStrategyRule(Long strategyId, String ruleModel) {
        StrategyRule strategyRuleReq = new StrategyRule();
        strategyRuleReq.setStrategyId(strategyId);
        strategyRuleReq.setRuleModel(ruleModel);
        StrategyRule strategyRuleRes = strategyRuleDao.queryStrategyRule(strategyRuleReq);
        return StrategyRuleEntity.builder()
                .strategyId(strategyRuleRes.getStrategyId())
                .awardId(strategyRuleRes.getAwardId())
                .ruleType(strategyRuleRes.getRuleType())
                .ruleModel(strategyRuleRes.getRuleModel())
                .ruleValue(strategyRuleRes.getRuleValue())
                .ruleDesc(strategyRuleRes.getRuleDesc())
                .build();
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, String ruleModel) {
        return queryStrategyRuleValue(strategyId, null, ruleModel);
    }

    @Override
    public String queryStrategyRuleValue(Long strategyId, Integer awardId, String ruleModel) {
        StrategyRule strategyRule = new StrategyRule();
        strategyRule.setStrategyId(strategyId);
        strategyRule.setAwardId(awardId);
        strategyRule.setRuleModel(ruleModel);
        return strategyRuleDao.queryStrategyRuleValue(strategyRule);
    }

    @Override
    public StrategyAwardRuleModelVO queryStrategyAwardRuleModelVO(Long strategyId, Integer awardId) {
        StrategyAward strategyAward = new StrategyAward();
        strategyAward.setStrategyId(strategyId);
        strategyAward.setAwardId(awardId);
        String ruleModels = strategyAwardDao.queryStrategyAwardRuleModels(strategyAward);
        if(ruleModels == null) return null;
        return StrategyAwardRuleModelVO.builder().ruleModels(ruleModels).build();
    }

    @Override
    public Integer getStrategyAwardAssemble(String strategyId, Integer rateKey) {
        return redisService.getFromMap(Constants.RedisKey.STRATEGY_RATE_TABLE_KEY + strategyId, rateKey);
    }

    @Override
    public int getRateRange(Long strategyId) {
        return getRateRange(String.valueOf(strategyId));
    }

    @Override
    public int getRateRange(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_RATE_RANGE_KEY + key;
        if (!redisService.isExists(cacheKey)) {
            throw new AppException(UN_ASSEMBLED_STRATEGY_ARMORY.getCode(), cacheKey + Constants.COLON + UN_ASSEMBLED_STRATEGY_ARMORY.getInfo());
        }
        return redisService.getValue(cacheKey);
    }

    @Override
    public RuleTreeVO queryRuleTreeVOByTreeId(String treeId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.RULE_TREE_VO_KEY + treeId;
        RuleTreeVO ruleTreeVOCache = redisService.getValue(cacheKey);
        if (null != ruleTreeVOCache) return ruleTreeVOCache;

        // 从数据库获取
        RuleTree ruleTree = ruleTreeDao.queryRuleTreeByTreeId(treeId);
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleTreeNodeListByTreeId(treeId);
        List<RuleTreeNodeLine> ruleTreeNodeLines = ruleTreeNodeLineDao.queryRuleTreeNodeLineListByTreeId(treeId);

        // 1. tree node line 转换Map结构
        Map<String, List<RuleTreeNodeLineVO>> ruleTreeNodeLineMap = new HashMap<>();
        for (RuleTreeNodeLine ruleTreeNodeLine : ruleTreeNodeLines) {
            RuleTreeNodeLineVO ruleTreeNodeLineVO = RuleTreeNodeLineVO.builder()
                    .treeId(ruleTreeNodeLine.getTreeId())
                    .ruleNodeFrom(ruleTreeNodeLine.getRuleNodeFrom())
                    .ruleNodeTo(ruleTreeNodeLine.getRuleNodeTo())
                    .ruleLimitType(RuleLimitTypeVO.valueOf(ruleTreeNodeLine.getRuleLimitType()))
                    .ruleLimitValue(RuleLogicCheckTypeVO.valueOf(ruleTreeNodeLine.getRuleLimitValue()))
                    .build();

            ruleTreeNodeLineMap
                    .computeIfAbsent(ruleTreeNodeLine.getRuleNodeFrom(), k -> new ArrayList<>())
                    .add(ruleTreeNodeLineVO);
        }

        // 2. tree node 转换为Map结构
        Map<String, RuleTreeNodeVO> treeNodeMap = new HashMap<>();
        for (RuleTreeNode ruleTreeNode : ruleTreeNodes) {
            RuleTreeNodeVO ruleTreeNodeVO = RuleTreeNodeVO.builder()
                    .treeId(ruleTreeNode.getTreeId())
                    .ruleKey(ruleTreeNode.getRuleKey())
                    .ruleDesc(ruleTreeNode.getRuleDesc())
                    .ruleValue(ruleTreeNode.getRuleValue())
                    .treeNodeLineVOList(ruleTreeNodeLineMap.get(ruleTreeNode.getRuleKey()))
                    .build();
            treeNodeMap.put(ruleTreeNode.getRuleKey(), ruleTreeNodeVO);
        }

        // 3. 构建 Rule Tree
        RuleTreeVO ruleTreeVODB = RuleTreeVO.builder()
                .treeId(ruleTree.getTreeId())
                .treeName(ruleTree.getTreeName())
                .treeDesc(ruleTree.getTreeDesc())
                .treeRootRuleNode(ruleTree.getTreeRootRuleKey())
                .treeNodeMap(treeNodeMap)
                .build();

        redisService.setValue(cacheKey, ruleTreeVODB);
        return ruleTreeVODB;
    }

    @Override
    public void cacheStrategyAwardCount(String cacheKey, Integer awardCount) {
        redisService.setAtomicLongIfAbsent(cacheKey, awardCount == null ? 0 : awardCount);
    }

    @Override
    public Boolean subtractionAwardStock(String cacheKey){
        return subtractionAwardStock(cacheKey, null);
    }
    /**
     * 扣减库存（Lua 原子脚本 + Redis outbox + RocketMQ）
     * <p>相比旧实现的改进：
     * <ul>
     *   <li>decr + setNx 合并为一次原子执行，消除并发窗口</li>
     *   <li>setNx 失败时自动 INCR 回滚 decr，库存不丢失</li>
     *   <li>写入唯一 outbox 事件，MQ 失败时由定时任务补发</li>
     * </ul>
     *
     * @param cacheKey    缓存Key (格式: strategy_award_count_key_{sid}_{aid})
     * @param endDateTime 活动结束时间
     */
    @Override
    public Boolean subtractionAwardStock(String cacheKey, Date endDateTime) {
        // 1. 计算锁过期时间
        long expireMs = 0;
        if (null != endDateTime) {
            expireMs = endDateTime.getTime() - System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        }
        // 2. 从 cacheKey 提取事件前缀: "strategy_award_count_key_100001_101" → "100001:101"
        String suffix = cacheKey.substring(Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY.length());
        String strategyAwardKey = suffix.replace(Constants.UNDERLINE, Constants.COLON);
        String eventPrefix = strategyAwardKey + Constants.COLON + UUID.randomUUID().toString();
        // 3. 执行 Lua 脚本 (decr + setNx + 回滚 + 写 outbox 全部原子)
        List<Long> result = redisService.execStockDeduction(
                cacheKey,
                Constants.RedisKey.STRATEGY_AWARD_MQ_OUTBOX_SET,
                expireMs,
                eventPrefix,
                cacheKey);
        int status = result.get(0).intValue();
        if (status != 1) {
            log.warn("策略奖品库存扣减失败 status={} cacheKey={}", status, cacheKey);
        }
        if (status != 1) return false;

        // 4. 尝试实时投递；失败不影响已成功的抽奖，outbox 会由定时任务补发。
        String outboxMember = eventPrefix + Constants.COLON + result.get(1);
        try {
            stockMessagePublisher.publish(outboxMember);
        } catch (Exception e) {
            log.error("奖品库存扣减MQ发送失败，等待outbox补偿 member={}", outboxMember, e);
        }
        return true;
    }

    @Override
    public void awardStockConsumeSendQueue(StrategyAwardStockKeyVO strategyAwardStockKeyVO) {
        // 按 per-award key 写入队列，与消费者 takeQueueValue(sid, aid) 对齐
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY
                + Constants.UNDERLINE + strategyAwardStockKeyVO.getStrategyId()
                + Constants.UNDERLINE + strategyAwardStockKeyVO.getAwardId();
        RBlockingQueue<StrategyAwardStockKeyVO> blockingQueue = redisService.getBlockingQueue(cacheKey);
        RDelayedQueue<StrategyAwardStockKeyVO> delayedQueue = redisService.getDelayedQueue(blockingQueue);
        delayedQueue.offer(strategyAwardStockKeyVO, 3, TimeUnit.SECONDS);
    }

    @Override
    public StrategyAwardStockKeyVO takeQueueValue() throws InterruptedException {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    @Override
    public StrategyAwardStockKeyVO takeQueueValue(Long strategyId, Integer awardId) throws InterruptedException{
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY + Constants.UNDERLINE + strategyId + Constants.UNDERLINE + awardId;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        return destinationQueue.poll();
    }

    @Override
    public int takeQueueValueCount(Long strategyId, Integer awardId) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_QUERY_KEY + Constants.UNDERLINE + strategyId + Constants.UNDERLINE + awardId;
        RBlockingQueue<StrategyAwardStockKeyVO> destinationQueue = redisService.getBlockingQueue(cacheKey);
        List<StrategyAwardStockKeyVO> list = new ArrayList<>();
        destinationQueue.drainTo(list);
        return list.size();
    }

    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId) {
        StrategyAward strategyAward = new StrategyAward();
        strategyAward.setStrategyId(strategyId);
        strategyAward.setAwardId(awardId);
        strategyAwardDao.updateStrategyAwardStock(strategyAward);
    }

    @Override
    public void updateStrategyAwardStock(Long strategyId, Integer awardId, int count) {
        strategyAwardDao.updateStrategyAwardStockBatch(strategyId, awardId, count);
    }

    @Override
    public void syncStrategyAwardStock(Long strategyId, Integer awardId, int surplus) {
        strategyAwardDao.syncStrategyAwardStock(strategyId, awardId, surplus);
    }

    @Override
    public void syncStrategyAwardStock(List<StrategyAwardStockDeductEvent> events) {
        if (events == null || events.isEmpty()) return;

        try {
            // Strategy configuration and its inbox live in the unsharded default database.
            dbRouter.setDBKey(0);
            dbRouter.setTBKey(0);
            transactionTemplate.execute(status -> {
                Map<String, StrategyAwardStockDeductEvent> newEvents = new LinkedHashMap<>();
                for (StrategyAwardStockDeductEvent event : events) {
                    validateStockEvent(event);
                    int inserted;
                    try {
                        inserted = strategyAwardDao.insertStrategyAwardStockInbox(
                                event.getEventId(), event.getStrategyId(), event.getAwardId(), event.getSurplus());
                    } catch (DuplicateKeyException duplicate) {
                        StrategyAwardStockInbox existing =
                                strategyAwardDao.queryStrategyAwardStockInbox(event.getEventId());
                        if (existing == null || !sameStockEvent(event, existing)) {
                            status.setRollbackOnly();
                            throw new IllegalArgumentException("库存事件 ID 载荷冲突: " + event.getEventId(), duplicate);
                        }
                        continue;
                    }
                    if (inserted == 1) {
                        newEvents.putIfAbsent(event.getEventId(), event);
                    } else {
                        status.setRollbackOnly();
                        throw new IllegalStateException("策略库存 inbox 写入结果异常: " + event.getEventId());
                    }
                }

                Map<String, StrategyAwardStockDeductEvent> lowestSnapshotByAward = new HashMap<>();
                for (StrategyAwardStockDeductEvent event : newEvents.values()) {
                    String awardKey = event.getStrategyId() + Constants.COLON + event.getAwardId();
                    lowestSnapshotByAward.merge(awardKey, event,
                            (left, right) -> left.getSurplus() <= right.getSurplus() ? left : right);
                }

                for (StrategyAwardStockDeductEvent event : lowestSnapshotByAward.values()) {
                    int updated = strategyAwardDao.syncStrategyAwardStock(
                            event.getStrategyId(), event.getAwardId(), event.getSurplus());
                    if (updated == 0) {
                        StrategyAward lookup = new StrategyAward();
                        lookup.setStrategyId(event.getStrategyId());
                        lookup.setAwardId(event.getAwardId());
                        if (strategyAwardDao.queryStrategyAward(lookup) == null) {
                            throw new IllegalStateException("策略奖品不存在，库存事件无法回写: "
                                    + event.getStrategyId() + Constants.COLON + event.getAwardId());
                        }
                    }
                }
                return null;
            });
        } finally {
            dbRouter.clear();
        }
    }

    private void validateStockEvent(StrategyAwardStockDeductEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()
                || event.getEventId().length() > 128
                || event.getStrategyId() == null || event.getAwardId() == null
                || event.getSurplus() == null || event.getSurplus() < 0) {
            throw new IllegalArgumentException("非法策略奖品库存回写事件: " + event);
        }
    }

    private boolean sameStockEvent(StrategyAwardStockDeductEvent event, StrategyAwardStockInbox existing) {
        return event.getStrategyId().equals(existing.getStrategyId())
                && event.getAwardId().equals(existing.getAwardId())
                && event.getSurplus().equals(existing.getSurplus());
    }

    @Override
    public StrategyAwardEntity queryStrategyAwardEntity(Long strategyId, Integer awardId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_KEY + strategyId + Constants.UNDERLINE + awardId;
        StrategyAwardEntity strategyAwardEntity = redisService.getValue(cacheKey);
        if (null != strategyAwardEntity) return strategyAwardEntity;
        // 查询数据
        StrategyAward strategyAwardReq = new StrategyAward();
        strategyAwardReq.setStrategyId(strategyId);
        strategyAwardReq.setAwardId(awardId);
        StrategyAward strategyAwardRes = strategyAwardDao.queryStrategyAward(strategyAwardReq);
        // 转换数据
        strategyAwardEntity = StrategyAwardEntity.builder()
                .strategyId(strategyAwardRes.getStrategyId())
                .awardId(strategyAwardRes.getAwardId())
                .awardTitle(strategyAwardRes.getAwardTitle())
                .awardSubtitle(strategyAwardRes.getAwardSubtitle())
                .awardCount(strategyAwardRes.getAwardCount())
                .awardCountSurplus(strategyAwardRes.getAwardCountSurplus())
                .awardRate(strategyAwardRes.getAwardRate())
                .sort(strategyAwardRes.getSort())
                .build();
        // 缓存结果
        redisService.setValue(cacheKey, strategyAwardEntity);
        // 返回数据
        return strategyAwardEntity;
    }
    @Override
    public Long queryStrategyIdByActivityId(Long activityId) {
        return raffleActivityDao.queryStrategyIdByActivityId(activityId);
    }

    @Override
    public Integer queryTodayUserRaffleCount(String userId, Long strategyId) {
        // 活动ID
        Long activityId = raffleActivityDao.queryActivityIdByStrategyId(strategyId);
        // 封装参数
        RaffleActivityAccountDay raffleActivityAccountDayReq = new RaffleActivityAccountDay();
        raffleActivityAccountDayReq.setUserId(userId);
        raffleActivityAccountDayReq.setActivityId(activityId);
        raffleActivityAccountDayReq.setDay(raffleActivityAccountDayReq.currentDay());
        RaffleActivityAccountDay raffleActivityAccountDay = raffleActivityAccountDayDao.queryActivityAccountDayByUserId(raffleActivityAccountDayReq);
        if (null == raffleActivityAccountDay) return 0;
        // 总次数 - 剩余的，等于今日参与的
        return raffleActivityAccountDay.getDayCount() - raffleActivityAccountDay.getDayCountSurplus();
    }

    @Override
    public Map<String, Integer> queryAwardRuleLockCount(String[] treeIds) {
        if(null == treeIds || treeIds.length == 0){
            return new HashMap<>();
        }
        List<RuleTreeNode> ruleTreeNodes = ruleTreeNodeDao.queryRuleLocks(treeIds);
        Map<String, Integer> resultMap = new HashMap<>();
        for(RuleTreeNode ruleTreeNode : ruleTreeNodes){
            String treeId = ruleTreeNode.getTreeId();
            Integer ruleValue = Integer.valueOf(ruleTreeNode.getRuleValue());
            resultMap.put(treeId, ruleValue);
        }
        return resultMap;
    }

    @Override
    public Integer queryActivityAccountTotalUseCount(String userId, Long strategyId) {
        Long activityId = raffleActivityDao.queryActivityIdByStrategyId(strategyId);
        RaffleActivityAccount raffleActivityAccount = raffleActivityAccountDao.queryActivityAccountByUserId(RaffleActivityAccount.builder()
                .userId(userId)
                .activityId(activityId)
                .build());
        // 返回计算使用量
        return raffleActivityAccount.getTotalCount() - raffleActivityAccount.getTotalCountSurplus();
    }

    /**
     * // todo 重点调试(特别是对象转化)
     * @param strategyId
     * @return
     */
    @Override
    public List<RuleWeightVO> queryAwardRuleWeight(Long strategyId) {
        // 优先从缓存获取
        String cacheKey = Constants.RedisKey.STRATEGY_RULE_WEIGHT_KEY + strategyId;
        List<RuleWeightVO> ruleWeightVOS = redisService.getValue(cacheKey);
        if (null != ruleWeightVOS) return ruleWeightVOS;

        ruleWeightVOS = new ArrayList<>();
        // 1. 查询权重规则配置
        StrategyRule strategyRuleReq = new StrategyRule();
        strategyRuleReq.setStrategyId(strategyId);
        strategyRuleReq.setRuleModel(DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode());
        String ruleValue = strategyRuleDao.queryStrategyRuleValue(strategyRuleReq);
        // 2. 借助实体对象转换规则
        StrategyRuleEntity strategyRuleEntity = new StrategyRuleEntity();
        strategyRuleEntity.setRuleModel(DefaultChainFactory.LogicModel.RULE_WEIGHT.getCode());
        strategyRuleEntity.setRuleValue(ruleValue);
        Map<String, List<Integer>> ruleWeightValues = strategyRuleEntity.getRuleWeightValues();
        // 3. 遍历规则组装奖品配置
        Set<String> ruleWeightKeys = ruleWeightValues.keySet();
        for (String ruleWeightKey : ruleWeightKeys) {
            List<Integer> awardIds = ruleWeightValues.get(ruleWeightKey);
            List<RuleWeightVO.Award> awardList = new ArrayList<>();
            // 也可以修改为一次从数据库查询
            for (Integer awardId : awardIds) {
                StrategyAward strategyAwardReq = new StrategyAward();
                strategyAwardReq.setStrategyId(strategyId);
                strategyAwardReq.setAwardId(awardId);
                StrategyAward strategyAward = strategyAwardDao.queryStrategyAward(strategyAwardReq);
                awardList.add(RuleWeightVO.Award.builder()
                        .awardId(strategyAward.getAwardId())
                        .awardTitle(strategyAward.getAwardTitle())
                        .build());
            }

            ruleWeightVOS.add(RuleWeightVO.builder()
                    .ruleValue(ruleValue)
                    .weight(Integer.valueOf(ruleWeightKey.split(Constants.COLON)[0]))
                    .awardIds(awardIds)
                    .awardList(awardList)
                    .build());
        }

        // 设置缓存 - 实际场景中，这类数据，可以在活动下架的时候统一清空缓存。
        redisService.setValue(cacheKey, ruleWeightVOS);

        return ruleWeightVOS;
    }

    @Override
    public List<StrategyAwardStockKeyVO> queryOpenActivityStrategyAwardList() {
        List<StrategyAward> strategyAwards = strategyAwardDao.queryOpenActivityStrategyAwardList();
        if (null == strategyAwards || strategyAwards.isEmpty()) return null;

        List<StrategyAwardStockKeyVO> strategyAwardStockKeyVOS = new ArrayList<>();
        for (StrategyAward strategyAward: strategyAwards){
            StrategyAwardStockKeyVO strategyAwardStockKeyVO = StrategyAwardStockKeyVO.builder()
                    .strategyId(strategyAward.getStrategyId())
                    .awardId(strategyAward.getAwardId())
                    .build();
            strategyAwardStockKeyVOS.add(strategyAwardStockKeyVO);
        }

        return strategyAwardStockKeyVOS;
    }

    @Override
    public void cacheStrategyArmoryAlgorithm(String key, String beanName) {
        String cacheKey = Constants.RedisKey.STRATEGY_ARMORY_ALGORITHM_KEY + key;
        redisService.setValue(cacheKey, beanName);
    }
    // return beanName 好装配算法
    @Override
    public String queryStrategyArmoryAlgorithmFromCache(String key) {
        String cacheKey = Constants.RedisKey.STRATEGY_ARMORY_ALGORITHM_KEY + key;
        if (!redisService.isExists(cacheKey)) return null;
        return redisService.getValue(cacheKey);
    }
}
