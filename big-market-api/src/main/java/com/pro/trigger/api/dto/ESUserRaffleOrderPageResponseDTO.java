package com.pro.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * ERP 用户抽奖订单分页结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ESUserRaffleOrderPageResponseDTO implements Serializable {

    private List<ESUserRaffleOrderResponseDTO> list;
    private long total;
    private int pageNo;
    private int pageSize;
}
