package com.pro.infrastructure.adapter.repository;

import com.pro.adapter.repository.IESUserRaffleOrderRepository;
import com.pro.infrastructure.elasticsearch.IElasticSearchUserRaffleOrderDao;
import com.pro.model.valobj.ESUserRaffleOrderPageVO;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;
import com.pro.model.valobj.ESUserRaffleOrderVO;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户抽奖订单查询仓储。
 */
@Repository
public class ESUserRaffleOrderRepository implements IESUserRaffleOrderRepository {

    @Resource
    private IElasticSearchUserRaffleOrderDao elasticSearchUserRaffleOrderDao;

    /**
     * 兼容旧的无参查询接口，返回默认第一页的十条数据。
     */
    @Override
    public List<ESUserRaffleOrderVO> queryESUserRaffleOrderVOList() {
        ESUserRaffleOrderPageVO page = queryESUserRaffleOrderPage(ESUserRaffleOrderQueryVO.builder()
                .pageNo(1)
                .pageSize(10)
                .build());
        if (page == null || page.getList() == null) {
            throw new IllegalStateException("订单查询未返回分页结果");
        }
        return page.getList();
    }

    @Override
    public ESUserRaffleOrderPageVO queryESUserRaffleOrderPage(ESUserRaffleOrderQueryVO query) {
        return elasticSearchUserRaffleOrderDao.queryUserRaffleOrderPage(query);
    }

}
