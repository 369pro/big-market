package com.pro.infrastructure.elasticsearch;

import com.pro.model.valobj.ESUserRaffleOrderPageVO;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;

public interface IElasticSearchUserRaffleOrderDao {

    ESUserRaffleOrderPageVO queryUserRaffleOrderPage(ESUserRaffleOrderQueryVO query);

}
