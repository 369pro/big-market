package com.pro.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户抽奖订单分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ESUserRaffleOrderPageVO {

    private List<ESUserRaffleOrderVO> list;
    private long total;
    private int pageNo;
    private int pageSize;
}
