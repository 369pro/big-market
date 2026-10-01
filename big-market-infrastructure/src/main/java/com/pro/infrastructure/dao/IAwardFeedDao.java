package com.pro.infrastructure.dao;

import cn.bugstack.middleware.db.router.annotation.DBRouterStrategy;
import com.pro.infrastructure.dao.po.UserAwardRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
@DBRouterStrategy(splitTable = true)
public interface IAwardFeedDao {
    List<UserAwardRecord> queryRecentAwards(@Param("activityId") Long activityId, @Param("limit") int limit);
}
