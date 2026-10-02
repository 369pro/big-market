package com.pro.adapter.repository;

import com.pro.model.valobj.RaffleActivityDetailQueryVO;

/**
 * Read-only ERP activity detail query port.
 */
public interface IActivityDetailQueryRepository {

    /**
     * Loads the activity, its current strategy prizes and purchasable SKUs.
     *
     * @param activityId activity identifier
     * @return detail, or {@code null} when the activity does not exist
     */
    RaffleActivityDetailQueryVO queryActivityDetail(Long activityId);
}
