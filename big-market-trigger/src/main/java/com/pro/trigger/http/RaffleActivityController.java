package com.pro.trigger.http;


import com.pro.domain.activity.model.entity.UserRaffleOrderEntity;
import com.pro.domain.activity.service.IRaffleActivityPartakeService;
import com.pro.domain.activity.service.armory.IActivityArmory;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.model.valobj.AwardStateVO;
import com.pro.domain.award.service.IAwardService;
import com.pro.domain.strategy.model.entity.RaffleAwardEntity;
import com.pro.domain.strategy.model.entity.RaffleFactorEntity;
import com.pro.domain.strategy.service.IRaffleStrategy;
import com.pro.domain.strategy.service.armory.IStrategyArmory;
import com.pro.trigger.api.IRaffleActivityService;
import com.pro.trigger.api.dto.ActivityDrawRequestDTO;
import com.pro.trigger.api.dto.ActivityDrawResponseDTO;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import com.pro.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 抽奖活动服务 注意；在不引用 application/case 层的时候，就需要让接口实现层来做领域的串联。一些较大规模的系统，需要加入 case 层。
 * @create 2024-04-13 09:42
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/activity/")
public class RaffleActivityController implements IRaffleActivityService {

    @Resource
    private IRaffleActivityPartakeService raffleActivityPartakeService;
    @Resource
    private IRaffleStrategy raffleStrategy;
    @Resource
    private IAwardService awardService;
    @Resource
    private IActivityArmory activityArmory;
    @Resource
    private IStrategyArmory strategyArmory;

    /**
     * 活动装配 - 数据预热 | 把活动配置的对应的 sku 一起装配
     *
     * @param activityId 活动ID
     * @return 装配结果
     * <p>
     * 接口：<a href="http://localhost:8091/api/v1/raffle/activity/armory">/api/v1/raffle/activity/armory</a>
     * 入参：{"activityId":100001,"userId":"xiaofuge"}
     *
     * curl --request GET \
     *   --url 'http://localhost:8091/api/v1/raffle/activity/armory?activityId=100301'
     */
    @RequestMapping(value = "armory", method = RequestMethod.GET)
    @Override
    public Response<Boolean> armory(@RequestParam Long activityId) {
        try {
            log.info("活动装配，数据预热，开始 activityId:{}", activityId);
            // 1. 活动装配
            activityArmory.assembleActivitySkuByActivityId(activityId);
            // 2. 策略装配
            strategyArmory.assembleLotteryStrategyByActivityId(activityId);
            Response<Boolean> response = Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
            log.info("活动装配，数据预热，完成 activityId:{}", activityId);
            return response;
        } catch (Exception e) {
            log.error("活动装配，数据预热，失败 activityId:{}", activityId, e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 抽奖接口
     *
     * @param request 请求对象
     * @return 抽奖结果
     * <p>
     * 接口：<a href="http://localhost:8091/api/v1/raffle/activity/draw">/api/v1/raffle/activity/draw</a>
     * 入参：{"activityId":100001,"userId":"xiaofuge"}
     *
     * curl --request POST \
     *   --url http://localhost:8091/api/v1/raffle/activity/draw \
     *   --header 'content-type: application/json' \
     *   --data '{
     *     "userId":"xiaofuge",
     *     "activityId": 100301
     * }'
     */
    @RequestMapping(value = "draw", method = RequestMethod.POST)
    @Override
    public Response<ActivityDrawResponseDTO> draw(@RequestBody ActivityDrawRequestDTO request) {
        String userId = request.getUserId();
        Long activityId = request.getActivityId();
        try{
            log.info("活动抽奖 userId:{} activityId:{}", userId, activityId);
            // 1. 参数校验
            if (StringUtils.isBlank(userId) || null == activityId) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), ResponseCode.ILLEGAL_PARAMETER.getInfo());
            }
            // 2. 注意：签到、转发 等操作领取活动，创建活动订单，增加用户活动次数
            // 之后用户参与活动，扣减用户活动余额，生成抽奖订单，准备执行抽奖
            // 最后几步保存聚合对象的时候对用户的活动账户 日/月 余额进行了扣减
            UserRaffleOrderEntity userRaffleOrderEntity = raffleActivityPartakeService.createOrder(userId, activityId);
            log.info("活动抽奖，创建订单 userId:{} activityId:{} orderId:{}",
                    request.getUserId(), request.getActivityId(), userRaffleOrderEntity.getOrderId());
            // 3. 执行抽奖
            RaffleFactorEntity raffleFactorEntity = RaffleFactorEntity.builder()
                    .userId(userId)
                    .strategyId(userRaffleOrderEntity.getStrategyId()).build();
            RaffleAwardEntity raffleAwardEntity = raffleStrategy.performRaffle(raffleFactorEntity);
            // 4. 写入中奖记录表
            UserAwardRecordEntity userAwardRecord = UserAwardRecordEntity.builder()
                    .userId(userRaffleOrderEntity.getUserId())
                    .activityId(userRaffleOrderEntity.getActivityId())
                    .strategyId(userRaffleOrderEntity.getStrategyId())
                    .orderId(userRaffleOrderEntity.getOrderId())
                    .awardId(raffleAwardEntity.getAwardId())
                    .awardTitle(raffleAwardEntity.getAwardTitle())
                    .awardTime(new Date())
                    .awardState(AwardStateVO.create)
                    .build();
            awardService.saveUserAwardRecord(userAwardRecord);
            // 5. 返回结果
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ActivityDrawResponseDTO.builder()
                            .awardId(raffleAwardEntity.getAwardId())
                            .awardTitle(raffleAwardEntity.getAwardTitle())
                            .awardIndex(raffleAwardEntity.getSort())
                            .build())
                    .build();
        } catch (AppException e) {
            log.error("活动抽奖失败 userId:{} activityId:{}", request.getUserId(), request.getActivityId(), e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("活动抽奖失败 userId:{} activityId:{}", userId, activityId, e);
            return Response.<ActivityDrawResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

}
