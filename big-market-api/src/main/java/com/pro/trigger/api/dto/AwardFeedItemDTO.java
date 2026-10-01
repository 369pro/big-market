package com.pro.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.io.Serializable;

@Data
@AllArgsConstructor
public class AwardFeedItemDTO implements Serializable {
    private String recordId;
    private String winnerName;
    private Integer awardId;
    private String awardTitle;
    private String awardTime;
}
