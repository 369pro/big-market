package com.pro.domain.activity.service.stage;

import com.pro.domain.activity.model.entity.RaffleActivityListingEntity;
import com.pro.domain.activity.model.entity.ActivityEntity;
import com.pro.domain.activity.model.valobj.ActivityStateVO;
import com.pro.domain.activity.repository.IActivityRepository;
import com.pro.domain.activity.service.IRaffleActivityListingService;
import com.pro.domain.activity.service.armory.IActivityArmory;
import com.pro.domain.strategy.service.armory.IStrategyArmory;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动上架服务
 * @create 2024-10-26 17:52
 */
@Service
public class RaffleActivityListingService implements IRaffleActivityListingService {

    @Resource
    private IActivityRepository repository;
    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;

    @Override
    public void appendStageActivity(String channel, String source, Long activityId) {
        repository.appendStageActivity(channel, source, activityId);
    }

    @Override
    public void updateStageActivity2Active(Long id) {
        RaffleActivityListingEntity stage = queryStageActivityById(id);
        if (stage == null || stage.getActivityId() == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "上架记录不存在: " + id);
        }
        updateStageActivityState(stage.getActivityId(), stage.getChannel(), stage.getSource(), "active");
    }

    @Override
    public void updateStageActivityState(Long activityId, String channel, String source, String state) {
        String normalizedChannel = normalizeRequired(channel, "channel");
        String normalizedSource = normalizeRequired(source, "source");
        String normalizedState = normalizeState(state);
        if (activityId == null || activityId <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "activityId must be greater than 0");
        }

        if ("active".equals(normalizedState)) {
            ActivityEntity activity = repository.queryRaffleActivityByActivityId(activityId);
            validateActivityForActivation(activity, activityId);

            boolean activityAssembled = activityArmory.assembleActivitySkuByActivityId(activityId);
            if (!activityAssembled) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "活动库存预热失败: " + activityId);
            }
            boolean strategyAssembled = strategyArmory.assembleLotteryStrategy(activity.getStrategyId());
            if (!strategyAssembled) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                        "抽奖策略预热失败: " + activity.getStrategyId());
            }
        }

        repository.updateStageActivityState(activityId, normalizedChannel, normalizedSource, normalizedState);
    }

    @Override
    public Long queryStageActivityId(String channel, String source) {
        return repository.queryStageActiveBySC(channel, source);
    }

    @Override
    public List<RaffleActivityListingEntity> queryStageActivityList() {
        return repository.queryStageActivityList();
    }

    @Override
    public Long queryStageActivity2ActiveById(Long id) {
        return repository.queryStageActivity2ActiveById(id);
    }

    @Override
    public RaffleActivityListingEntity queryStageActivityById(Long id) {
        return repository.queryStageActivityById(id);
    }

    private void validateActivityForActivation(ActivityEntity activity, Long activityId) {
        if (activity == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "活动不存在: " + activityId);
        }
        if (!ActivityStateVO.open.equals(activity.getState())) {
            throw new AppException(ResponseCode.ACTIVITY_STATE_ERROR.getCode(),
                    "活动未开启: " + activityId);
        }
        Date now = new Date();
        if (activity.getBeginDateTime() == null || activity.getEndDateTime() == null
                || now.before(activity.getBeginDateTime()) || now.after(activity.getEndDateTime())) {
            String message = activity.getBeginDateTime() != null && now.before(activity.getBeginDateTime())
                    ? "活动尚未开始: " + activityId : "活动已结束: " + activityId;
            throw new AppException(ResponseCode.ACTIVITY_DATE_ERROR.getCode(),
                    message);
        }
        if (activity.getStrategyId() == null || activity.getStrategyId() <= 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    "活动未配置抽奖策略: " + activityId);
        }
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), field + " must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,8}")) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(),
                    field + " must contain only lowercase letters, digits, _ or - and be at most 8 characters");
        }
        return normalized;
    }

    private String normalizeState(String state) {
        if (state == null) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "state must be active or inactive");
        }
        String normalized = state.trim().toLowerCase(Locale.ROOT);
        if (!"active".equals(normalized) && !"inactive".equals(normalized)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "state must be active or inactive");
        }
        return normalized;
    }

}
