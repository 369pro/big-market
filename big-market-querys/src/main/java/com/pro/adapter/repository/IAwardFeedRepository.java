package com.pro.adapter.repository;

import com.pro.model.valobj.AwardFeedRecordVO;
import java.util.List;

public interface IAwardFeedRepository {
    /** Latest 100 confirmed wins for one activity, newest first. */
    List<AwardFeedRecordVO> queryRecentAwards(Long activityId);
}
