package com.pro.trigger.api;

import com.pro.trigger.api.dto.AwardFeedResponseDTO;
import com.pro.trigger.api.response.Response;

public interface IAwardFeedService {
    Response<AwardFeedResponseDTO> queryAwardFeed(String token, Long activityId, Integer pageNo, Integer pageSize);
}
