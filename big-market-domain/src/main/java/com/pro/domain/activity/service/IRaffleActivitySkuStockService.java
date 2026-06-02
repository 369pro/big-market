package com.pro.domain.activity.service;


import com.pro.domain.activity.model.valobj.ActivitySkuStockKeyVO;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动sku库存处理接口
 * @create 2024-03-30 09:55
 */
public interface IRaffleActivitySkuStockService {

    /**
     * 获取活动sku库存消耗队列
     *
     * @return 奖品库存Key信息
     * @throws InterruptedException 异常
     */
    ActivitySkuStockKeyVO takeQueueValue(Long sku) throws InterruptedException;

    /**
     * 排空队列中该SKU的所有消息，返回聚合后的总扣减数量
     */
    int takeQueueValues(Long sku);

    /**
     * 清空队列
     */
    void clearQueueValue(Long sku);

    /**
     * 延迟队列 + 任务趋势更新活动sku库存
     *
     * @param sku 活动商品
     */
    void updateActivitySkuStock(Long sku);

    /**
     * 批量更新活动sku库存（聚合扣减）
     *
     * @param sku   活动商品
     * @param count 扣减数量
     */
    void updateActivitySkuStock(Long sku, int count);

    /**
     * 缓存库存以消耗完毕，清空数据库库存
     *
     * @param sku 活动商品
     */
    void clearActivitySkuStock(Long sku);

    List<Long> querySkuList();
}
