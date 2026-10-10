package com.pro.trigger.api;



import com.pro.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderPageResponseDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderQueryRequestDTO;
import com.pro.trigger.api.dto.RaffleActivityDetailResponseDTO;
import com.pro.trigger.api.dto.RaffleActivityListingResponseDTO;
import com.pro.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.pro.trigger.api.dto.UpdateStageActivityStateRequestDTO;
import com.pro.trigger.api.response.Response;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ERP 运营接口
 * @create 2024-09-21 12:26
 */
public interface IErpOperateService {

    Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder();

    Response<ESUserRaffleOrderPageResponseDTO> queryUserRaffleOrderPage(ESUserRaffleOrderQueryRequestDTO requestDTO);

    /**
     * 上架活动，上架后驱动装配
     *
     * @param requestDTO 上架流水ID
     */
    Response<Boolean> updateStageActivity2Active(UpdateStageActivity2ActiveRequestDTO requestDTO);

    /**
     * Create or update an activity stage for an exact channel/source pair.
     * The accepted states are {@code active} and {@code inactive}.
     */
    Response<Boolean> updateStageActivityState(UpdateStageActivityStateRequestDTO requestDTO);

    Response<List<RaffleActivityListingResponseDTO>> queryRaffleActivityListingList();

    /**
     * Query one activity and its current strategy prize/SKU configuration.
     * This endpoint is read-only and does not assemble or publish activity state.
     */
    Response<RaffleActivityDetailResponseDTO> queryActivityDetail(Long activityId);

}
