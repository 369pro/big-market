package com.pro.infrastructure.dao;

import com.pro.infrastructure.dao.po.StrategyAward;
import com.pro.infrastructure.dao.po.StrategyAwardStockInbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖策略奖品明细配置 - 概率、规则 DAO
 * @create 2023-12-16 13:24
 */
@Mapper
public interface IStrategyAwardDao {

    List<StrategyAward> queryStrategyAwardList();

    List<StrategyAward> queryStrategyAwardListByStrategyId(Long strategyId);

    String queryStrategyAwardRuleModels(StrategyAward strategyAward);

    void updateStrategyAwardStock(StrategyAward strategyAward);

    /**
     * 批量更新奖品库存（聚合扣减）
     */
    void updateStrategyAwardStockBatch(@Param("strategyId") Long strategyId, @Param("awardId") Integer awardId, @Param("count") int count);

    /** 写入库存回写 inbox；返回 1 表示首次处理，0 表示重复事件。 */
    int insertStrategyAwardStockInbox(@Param("eventId") String eventId,
                                      @Param("strategyId") Long strategyId,
                                      @Param("awardId") Integer awardId,
                                      @Param("surplus") int surplus);

    StrategyAwardStockInbox queryStrategyAwardStockInbox(@Param("eventId") String eventId);

    /** 幂等同步 Redis 扣减后的库存快照，只允许库存向下变化。 */
    int syncStrategyAwardStock(@Param("strategyId") Long strategyId,
                               @Param("awardId") Integer awardId,
                               @Param("surplus") int surplus);

    StrategyAward queryStrategyAward(StrategyAward strategyAwardReq);

    List<StrategyAward> queryOpenActivityStrategyAwardList();
}
