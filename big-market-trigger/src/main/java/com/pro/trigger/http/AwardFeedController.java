package com.pro.trigger.http;

import com.pro.adapter.repository.IAwardFeedRepository;
import com.pro.domain.auth.service.IAuthService;
import com.pro.model.valobj.AwardFeedRecordVO;
import com.pro.trigger.api.IAwardFeedService;
import com.pro.trigger.api.dto.AwardFeedItemDTO;
import com.pro.trigger.api.dto.AwardFeedResponseDTO;
import com.pro.trigger.api.response.Response;
import com.pro.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
@DubboService(version = "1.0")
public class AwardFeedController implements IAwardFeedService {
    @Resource private IAuthService authService;
    @Resource private IAwardFeedRepository awardFeedRepository;

    @Override
    @PostMapping("query_award_feed")
    public Response<AwardFeedResponseDTO> queryAwardFeed(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) Long activityId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        try {
            if (StringUtils.isBlank(token) || !authService.checkToken(token)) {
                return Response.<AwardFeedResponseDTO>builder().code(ResponseCode.Login.TOKEN_ERROR.getCode())
                        .info(ResponseCode.Login.TOKEN_ERROR.getInfo()).build();
            }
            if (activityId == null || activityId <= 0 || pageNo == null || pageNo < 1 || pageNo > 100
                    || pageSize == null || pageSize < 1 || pageSize > 10) {
                return Response.<AwardFeedResponseDTO>builder().code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info(ResponseCode.ILLEGAL_PARAMETER.getInfo()).build();
            }
            List<AwardFeedItemDTO> all = awardFeedRepository.queryRecentAwards(activityId).stream()
                    .limit(100).map(this::toItem).collect(Collectors.toList());
            int from = Math.min((pageNo - 1) * pageSize, all.size());
            int to = Math.min(from + pageSize, all.size());
            AwardFeedResponseDTO result = new AwardFeedResponseDTO(all.subList(from, to),
                    all.subList(0, Math.min(10, all.size())), all.size(), pageNo, pageSize, 100);
            return Response.<AwardFeedResponseDTO>builder().code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo()).data(result).build();
        } catch (Exception e) {
            log.error("查询中奖动态失败 activityId:{}", activityId, e);
            return Response.<AwardFeedResponseDTO>builder().code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo()).build();
        }
    }

    private AwardFeedItemDTO toItem(AwardFeedRecordVO record) {
        String recordId = DigestUtils.sha256Hex(record.getUserId() + "\0" + record.getOrderId());
        String date = record.getAwardTime().toInstant().atZone(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new AwardFeedItemDTO(recordId, maskName(record.getUserId()), record.getAwardId(), record.getAwardTitle(), date);
    }

    private String maskName(String name) {
        if (StringUtils.isBlank(name)) return "***";
        int[] points = name.codePoints().toArray();
        if (points.length <= 2) return "***";
        String prefix = new String(points, 0, 1);
        return prefix + "***" + (points.length > 4 ? new String(points, points.length - 2, 2) : "");
    }
}
