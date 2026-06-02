package com.pro.infrastructure.dao;

import com.pro.infrastructure.dao.po.RaffleActivitySku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 商品sku dao
 * @create 2024-03-16 11:04
 */
@Mapper
public interface IRaffleActivitySkuDao {

    RaffleActivitySku queryActivitySku(Long sku);

    void updateActivitySkuStock(Long sku);

    void clearActivitySkuStock(Long sku);

    /**
     * 批量更新活动sku库存（聚合扣减）
     * @param sku   活动商品
     * @param count 扣减数量
     */
    void updateActivitySkuStockBatch(@Param("sku") Long sku, @Param("count") int count);

    List<RaffleActivitySku> queryActivitySkuListByActivityId(Long activityId);

    List<Long> listSku();

    List<Long> querySkuList();
}
