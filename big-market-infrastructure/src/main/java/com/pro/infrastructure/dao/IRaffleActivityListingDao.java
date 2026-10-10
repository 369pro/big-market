package com.pro.infrastructure.dao;


import com.pro.infrastructure.dao.po.RaffleActivityListing;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动展台 - 上架活动 DAO
 * @create 2024-10-26 17:23
 */
@Mapper
public interface IRaffleActivityListingDao {

    void insert(RaffleActivityListing raffleActivityListing);

    Integer updateStageActivity2ActiveById(Long id);

    Integer updateStageActivityState(RaffleActivityListing raffleActivityListing);

    RaffleActivityListing queryStageActivityById(Long id);

    RaffleActivityListing queryStageActivityByKey(RaffleActivityListing raffleActivityListing);

    List<RaffleActivityListing> queryStageActiveBySC(RaffleActivityListing raffleActivityListing);

    List<RaffleActivityListing> queryStageActiveBySCForConflict(RaffleActivityListing raffleActivityListing);

    Long queryStageActivity2ActiveById(Long id);

    List<RaffleActivityListing> queryStageActivityList();

}
