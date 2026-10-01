package com.pro.trigger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class AwardFeedResponseDTO implements Serializable {
    private List<AwardFeedItemDTO> records;
    private List<AwardFeedItemDTO> broadcasts;
    private int total;
    private int pageNo;
    private int pageSize;
    private int recordLimit;
}
