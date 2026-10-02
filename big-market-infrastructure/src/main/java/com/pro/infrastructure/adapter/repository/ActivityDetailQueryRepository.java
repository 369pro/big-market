package com.pro.infrastructure.adapter.repository;

import cn.bugstack.middleware.db.router.DBContextHolder;
import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.pro.adapter.repository.IActivityDetailQueryRepository;
import com.pro.infrastructure.dao.IAwardDao;
import com.pro.infrastructure.dao.IRaffleActivityCountDao;
import com.pro.infrastructure.dao.IRaffleActivityDao;
import com.pro.infrastructure.dao.IRaffleActivitySkuDao;
import com.pro.infrastructure.dao.IRuleTreeNodeDao;
import com.pro.infrastructure.dao.IStrategyAwardDao;
import com.pro.infrastructure.dao.po.Award;
import com.pro.infrastructure.dao.po.RaffleActivity;
import com.pro.infrastructure.dao.po.RaffleActivityCount;
import com.pro.infrastructure.dao.po.RaffleActivitySku;
import com.pro.infrastructure.dao.po.RuleTreeNode;
import com.pro.infrastructure.dao.po.StrategyAward;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.model.valobj.RaffleActivityDetailQueryVO;
import com.pro.types.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default-database read adapter for the ERP activity detail page.
 *
 * <p>This adapter only calls SELECTs and reads the already assembled Redis
 * counters. It never initializes, refreshes, decrements, or otherwise changes
 * an activity or its stock.</p>
 */
@Slf4j
@Repository
public class ActivityDetailQueryRepository implements IActivityDetailQueryRepository {

    private static final String REDIS_SOURCE = "redis";
    private static final String DATABASE_SOURCE = "database";

    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private IRaffleActivityDao raffleActivityDao;
    @Resource
    private IStrategyAwardDao strategyAwardDao;
    @Resource
    private IAwardDao awardDao;
    @Resource
    private IRuleTreeNodeDao ruleTreeNodeDao;
    @Resource
    private IRaffleActivitySkuDao raffleActivitySkuDao;
    @Resource
    private IRaffleActivityCountDao raffleActivityCountDao;
    @Resource
    private IRedisService redisService;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public RaffleActivityDetailQueryVO queryActivityDetail(Long activityId) {
        if (activityId == null || activityId <= 0) {
            throw new IllegalArgumentException("activityId must be greater than 0");
        }

        String previousDb = DBContextHolder.getDBKey();
        String previousTable = DBContextHolder.getTBKey();
        try {
            // Activity, strategy, award and SKU configuration are stored in db00.
            dbRouter.setDBKey(0);
            dbRouter.setTBKey(0);

            RaffleActivity activity = raffleActivityDao.queryRaffleActivityByActivityId(activityId);
            if (activity == null) {
                return null;
            }

            List<StrategyAward> strategyAwards = strategyAwardDao
                    .queryStrategyAwardListByStrategyId(activity.getStrategyId());
            if (strategyAwards == null) {
                strategyAwards = Collections.emptyList();
            }
            Map<String, Integer> unlockCountByTree = queryUnlockCounts(strategyAwards);
            List<RaffleActivityDetailQueryVO.Prize> prizes = toPrizes(
                    activity.getStrategyId(), strategyAwards, unlockCountByTree);
            List<RaffleActivityDetailQueryVO.Sku> skus = toSkus(
                    raffleActivitySkuDao.queryActivitySkuListByActivityId(activityId));

            return RaffleActivityDetailQueryVO.builder()
                    .activityId(activity.getActivityId())
                    .activityName(activity.getActivityName())
                    .activityDesc(activity.getActivityDesc())
                    .strategyId(activity.getStrategyId())
                    .activityState(activity.getState())
                    .beginDateTime(activity.getBeginDateTime())
                    .endDateTime(activity.getEndDateTime())
                    .prizes(prizes)
                    .skus(skus)
                    .build();
        } finally {
            dbRouter.clear();
            if (previousDb != null) {
                DBContextHolder.setDBKey(previousDb);
            }
            if (previousTable != null) {
                DBContextHolder.setTBKey(previousTable);
            }
        }
    }

    private List<RaffleActivityDetailQueryVO.Prize> toPrizes(Long strategyId,
                                                               List<StrategyAward> strategyAwards,
                                                               Map<String, Integer> unlockCountByTree) {
        if (strategyAwards.isEmpty()) {
            return Collections.emptyList();
        }
        List<RaffleActivityDetailQueryVO.Prize> prizes = new ArrayList<>(strategyAwards.size());
        for (StrategyAward strategyAward : strategyAwards) {
            Award award = awardDao.queryAwardByAwardId(strategyAward.getAwardId());
            Integer dbSurplus = strategyAward.getAwardCountSurplus();
            StockSnapshot stock = readAwardStock(strategyId, strategyAward.getAwardId(), dbSurplus);
            Integer unlockCount = unlockCountByTree.get(strategyAward.getRuleModels());

            prizes.add(RaffleActivityDetailQueryVO.Prize.builder()
                    .awardId(strategyAward.getAwardId())
                    .awardTitle(strategyAward.getAwardTitle())
                    .awardSubtitle(strategyAward.getAwardSubtitle())
                    .awardKey(award == null ? null : award.getAwardKey())
                    .awardConfig(award == null ? null : award.getAwardConfig())
                    .awardRate(strategyAward.getAwardRate())
                    .awardCount(strategyAward.getAwardCount())
                    .awardCountSurplus(stock.value)
                    .awardCountSurplusSource(stock.source)
                    .awardCountSurplusFallbackDb(dbSurplus)
                    .sort(strategyAward.getSort())
                    .ruleModels(strategyAward.getRuleModels())
                    .ruleUnlockCount(unlockCount)
                    .build());
        }
        return prizes;
    }

    private List<RaffleActivityDetailQueryVO.Sku> toSkus(List<RaffleActivitySku> activitySkus) {
        if (activitySkus == null || activitySkus.isEmpty()) {
            return Collections.emptyList();
        }
        List<RaffleActivityDetailQueryVO.Sku> skus = new ArrayList<>(activitySkus.size());
        for (RaffleActivitySku activitySku : activitySkus) {
            RaffleActivityCount activityCount = raffleActivityCountDao
                    .queryRaffleActivityCountByActivityCountId(activitySku.getActivityCountId());
            Integer dbSurplus = activitySku.getStockCountSurplus();
            StockSnapshot stock = readSkuStock(activitySku.getSku(), dbSurplus);

            skus.add(RaffleActivityDetailQueryVO.Sku.builder()
                    .sku(activitySku.getSku())
                    .activityId(activitySku.getActivityId())
                    .activityCountId(activitySku.getActivityCountId())
                    .countQuota(activityCount == null ? null : activityCount.getTotalCount())
                    .dayCount(activityCount == null ? null : activityCount.getDayCount())
                    .monthCount(activityCount == null ? null : activityCount.getMonthCount())
                    .stockCount(activitySku.getStockCount())
                    .stockCountSurplus(stock.value)
                    .stockCountSurplusSource(stock.source)
                    .stockCountSurplusFallbackDb(dbSurplus)
                    .productAmount(activitySku.getProductAmount())
                    .build());
        }
        return skus;
    }

    private Map<String, Integer> queryUnlockCounts(List<StrategyAward> strategyAwards) {
        Set<String> treeIds = new LinkedHashSet<>();
        for (StrategyAward strategyAward : strategyAwards) {
            if (strategyAward != null && StringUtils.hasText(strategyAward.getRuleModels())) {
                treeIds.add(strategyAward.getRuleModels());
            }
        }
        if (treeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RuleTreeNode> lockNodes = ruleTreeNodeDao.queryRuleLocks(treeIds.toArray(new String[0]));
        if (lockNodes == null || lockNodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> result = new HashMap<>();
        for (RuleTreeNode node : lockNodes) {
            try {
                result.put(node.getTreeId(), Integer.valueOf(node.getRuleValue()));
            } catch (RuntimeException e) {
                log.warn("忽略无法解析的规则树解锁次数 treeId={} ruleValue={}",
                        node.getTreeId(), node.getRuleValue());
            }
        }
        return result;
    }

    private StockSnapshot readAwardStock(Long strategyId, Integer awardId, Integer dbSurplus) {
        String cacheKey = Constants.RedisKey.STRATEGY_AWARD_COUNT_KEY
                + strategyId + Constants.UNDERLINE + awardId;
        try {
            // getAtomicLong returns zero for a missing key; exists distinguishes
            // a real sold-out counter from an unassembled cache.
            if (redisService.isExists(cacheKey)) {
                Long cached = redisService.getAtomicLong(cacheKey);
                if (cached != null && cached >= 0 && cached <= Integer.MAX_VALUE) {
                    return new StockSnapshot(cached.intValue(), REDIS_SOURCE);
                }
                log.warn("奖品库存 Redis 值非法，使用数据库快照 strategyId={} awardId={} value={}",
                        strategyId, awardId, cached);
            }
        } catch (Exception e) {
            log.warn("读取奖品库存 Redis 失败，使用数据库快照 strategyId={} awardId={}",
                    strategyId, awardId, e);
        }
        return new StockSnapshot(dbSurplus, DATABASE_SOURCE);
    }

    private StockSnapshot readSkuStock(Long sku, Integer dbSurplus) {
        String cacheKey = Constants.RedisKey.ACTIVITY_SKU_STOCK_COUNT_KEY + sku;
        try {
            String cached = redissonClient.<String>getBucket(cacheKey, StringCodec.INSTANCE).get();
            if (StringUtils.hasText(cached)) {
                long value = Long.parseLong(cached);
                if (value >= 0 && value <= Integer.MAX_VALUE) {
                    return new StockSnapshot((int) value, REDIS_SOURCE);
                }
                log.warn("SKU库存 Redis 值非法，使用数据库快照 sku={} value={}", sku, cached);
            }
        } catch (Exception e) {
            log.warn("读取 SKU 库存 Redis 失败，使用数据库快照 sku={}", sku, e);
        }
        return new StockSnapshot(dbSurplus, DATABASE_SOURCE);
    }

    private static final class StockSnapshot {
        private final Integer value;
        private final String source;

        private StockSnapshot(Integer value, String source) {
            this.value = value;
            this.source = source;
        }
    }
}
