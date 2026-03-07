package com.pro.trigger.api;



import com.pro.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.pro.trigger.api.response.Response;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ERP 运营接口
 * @create 2024-09-21 12:26
 */
public interface IErpOperateService {

    Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder();

}
