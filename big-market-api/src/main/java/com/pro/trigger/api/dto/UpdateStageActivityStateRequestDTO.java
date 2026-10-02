package com.pro.trigger.api.dto;

import lombok.Data;

/**
 * Update the channel/source stage state for an activity.
 */
@Data
public class UpdateStageActivityStateRequestDTO {

    private Long activityId;
    private String channel;
    private String source;
    private String state;
}
