package com.pro.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AwardFeedRecordVO {
    private String userId;
    private String orderId;
    private Integer awardId;
    private String awardTitle;
    private Date awardTime;
}
