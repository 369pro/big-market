package com.pro.trigger.http;

import com.alibaba.fastjson.JSON;
import com.pro.adapter.repository.IActivityDetailQueryRepository;
import com.pro.adapter.repository.IESUserRaffleOrderRepository;
import com.pro.domain.activity.model.entity.RaffleActivityStageEntity;
import com.pro.domain.activity.service.IRaffleActivityStageService;
import com.pro.model.valobj.ESUserRaffleOrderPageVO;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;
import com.pro.model.valobj.ESUserRaffleOrderVO;
import com.pro.model.valobj.RaffleActivityDetailQueryVO;
import com.pro.trigger.api.IErpOperateService;
import com.pro.trigger.api.dto.RaffleActivityDetailResponseDTO;
import com.pro.trigger.api.dto.RaffleActivityPrizeResponseDTO;
import com.pro.trigger.api.dto.RaffleActivitySkuResponseDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderPageResponseDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderQueryRequestDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.pro.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.pro.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.pro.trigger.api.dto.UpdateStageActivityStateRequestDTO;
import com.pro.trigger.api.response.Response;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description ERP 运营接口
 * @create 2024-09-21 12:25
 */
@Slf4j
@RestController()
@CrossOrigin("${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/raffle/erp/")
@DubboService(version = "1.0")
public class ErpOperateController implements IErpOperateService {

    @Resource
    private IESUserRaffleOrderRepository userRaffleOrderRepository;
    @Resource
    private IActivityDetailQueryRepository activityDetailQueryRepository;
    @Resource
    private IRaffleActivityStageService raffleActivityStageService;
    /**
     * 查询运营数据，用户抽奖单列表
     * curl --request GET --url 'http://localhost:8098/api/v1/raffle/erp/query_user_raffle_order'
     */
    @RequestMapping(value = "query_user_raffle_order", method = RequestMethod.GET)
    @Override
    public Response<List<ESUserRaffleOrderResponseDTO>> queryUserRaffleOrder() {
        try {
            log.info("查询运营数据，用户抽奖单列表");
            List<ESUserRaffleOrderVO> userRaffleOrderVOList = userRaffleOrderRepository.queryESUserRaffleOrderVOList();
            List<ESUserRaffleOrderResponseDTO> userRaffleOrderResponseDTOS = toOrderResponseList(userRaffleOrderVOList);

            return Response.<List<ESUserRaffleOrderResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(userRaffleOrderResponseDTOS)
                    .build();
        } catch (Exception e) {
            log.error("查询运营数据，用户抽奖单列表", e);
            return Response.<List<ESUserRaffleOrderResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 查询运营数据，用户抽奖单分页列表。
     *
     * <p>过滤条件均为可选；分页默认第 1 页、每页 20 条，最多 100 条且 from + size 不超过 10000。</p>
     */
    @RequestMapping(value = "query_user_raffle_order_page", method = RequestMethod.GET)
    @Override
    public Response<ESUserRaffleOrderPageResponseDTO> queryUserRaffleOrderPage(
            @ModelAttribute ESUserRaffleOrderQueryRequestDTO requestDTO) {
        try {
            ESUserRaffleOrderQueryVO query = ESUserRaffleOrderQueryVO.builder()
                    .userId(requestDTO == null ? null : requestDTO.getUserId())
                    .activityId(requestDTO == null ? null : requestDTO.getActivityId())
                    .activityName(requestDTO == null ? null : requestDTO.getActivityName())
                    .orderId(requestDTO == null ? null : requestDTO.getOrderId())
                    .orderState(requestDTO == null ? null : requestDTO.getOrderState())
                    .strategyId(requestDTO == null ? null : requestDTO.getStrategyId())
                    .pageNo(requestDTO == null ? null : requestDTO.getPageNo())
                    .pageSize(requestDTO == null ? null : requestDTO.getPageSize())
                    .build();
            ESUserRaffleOrderPageVO page = userRaffleOrderRepository.queryESUserRaffleOrderPage(query);
            if (page == null) {
                throw new IllegalStateException("订单查询未返回分页结果");
            }

            ESUserRaffleOrderPageResponseDTO data = ESUserRaffleOrderPageResponseDTO.builder()
                    .list(toOrderResponseList(page.getList()))
                    .total(page.getTotal())
                    .pageNo(page.getPageNo())
                    .pageSize(page.getPageSize())
                    .build();
            return Response.<ESUserRaffleOrderPageResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("查询运营数据，用户抽奖单分页参数非法：{}", e.getMessage());
            return Response.<ESUserRaffleOrderPageResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("查询运营数据，用户抽奖单分页列表失败", e);
            return Response.<ESUserRaffleOrderPageResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private List<ESUserRaffleOrderResponseDTO> toOrderResponseList(List<ESUserRaffleOrderVO> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<ESUserRaffleOrderResponseDTO> responseList = new ArrayList<>(orders.size());
        for (ESUserRaffleOrderVO order : orders) {
            ESUserRaffleOrderResponseDTO response = new ESUserRaffleOrderResponseDTO();
            response.setUserId(order.getUserId());
            response.setActivityId(order.getActivityId());
            response.setActivityName(order.getActivityName());
            response.setStrategyId(order.getStrategyId());
            response.setOrderId(order.getOrderId());
            response.setOrderTime(order.getOrderTime());
            response.setOrderState(order.getOrderState());
            response.setCreateTime(order.getCreateTime());
            response.setUpdateTime(order.getUpdateTime());
            responseList.add(response);
        }
        return responseList;
    }

    @RequestMapping(value = "update_stage_activity_2_active", method = RequestMethod.POST)
    @Override
    public Response<Boolean> updateStageActivity2Active(@RequestBody UpdateStageActivity2ActiveRequestDTO requestDTO) {
        try {
            if (requestDTO == null || requestDTO.getId() == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "id must not be null");
            }
            Long id = requestDTO.getId();
            log.info("更新上架活动状态为生效开始 id:{}", id);
            raffleActivityStageService.updateStageActivity2Active(id);
            log.info("更新上架活动状态为生效完成 id:{}", id);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.warn("更新上架活动状态为生效失败 id:{} info:{}", requestDTO == null ? null : requestDTO.getId(), e.getInfo());
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo() == null ? ResponseCode.UN_ERROR.getInfo() : e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("更新上架活动状态为生效失败 id:{}", requestDTO == null ? null : requestDTO.getId(), e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * Create or update an exact activity/channel/source stage state.
     *
     * <p>Activation validates the activity and prepares both activity and
     * strategy caches. Inactive updates only remove that channel/source
     * mapping and never close the activity globally.</p>
     */
    @RequestMapping(value = "update_stage_activity_state", method = RequestMethod.POST)
    @Override
    public Response<Boolean> updateStageActivityState(@RequestBody UpdateStageActivityStateRequestDTO requestDTO) {
        try {
            if (requestDTO == null) {
                throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "request body must not be null");
            }
            raffleActivityStageService.updateStageActivityState(
                    requestDTO.getActivityId(), requestDTO.getChannel(), requestDTO.getSource(), requestDTO.getState());
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (AppException e) {
            log.warn("更新活动展台状态失败 activityId:{} channel:{} source:{} state:{} info:{}",
                    requestDTO == null ? null : requestDTO.getActivityId(),
                    requestDTO == null ? null : requestDTO.getChannel(),
                    requestDTO == null ? null : requestDTO.getSource(),
                    requestDTO == null ? null : requestDTO.getState(), e.getInfo());
            return Response.<Boolean>builder()
                    .code(e.getCode())
                    .info(e.getInfo() == null ? ResponseCode.UN_ERROR.getInfo() : e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("更新活动展台状态失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @RequestMapping(value = "query_raffle_activity_stage_list", method = RequestMethod.GET)
    @Override
    public Response<List<RaffleActivityStageResponseDTO>> queryRaffleActivityStageList() {
        try {
            List<RaffleActivityStageResponseDTO> raffleActivityStageResponseDTOS = new ArrayList<>();
            List<RaffleActivityStageEntity> raffleActivityStageEntities = raffleActivityStageService.queryStageActivityList();
            for (RaffleActivityStageEntity raffleActivityStage : raffleActivityStageEntities) {
                RaffleActivityStageResponseDTO raffleActivityStageResponseDTO = RaffleActivityStageResponseDTO.builder()
                        .id(raffleActivityStage.getId())
                        .channel(raffleActivityStage.getChannel())
                        .source(raffleActivityStage.getSource())
                        .activityId(raffleActivityStage.getActivityId())
                        .state(raffleActivityStage.getState())
                    .activityName(raffleActivityStage.getActivityName())
                    .activityDesc(raffleActivityStage.getActivityDesc())
                    .strategyId(raffleActivityStage.getStrategyId())
                    .activityState(raffleActivityStage.getActivityState())
                    .beginDateTime(raffleActivityStage.getBeginDateTime())
                    .endDateTime(raffleActivityStage.getEndDateTime())
                        .build();
                raffleActivityStageResponseDTOS.add(raffleActivityStageResponseDTO);
            }

            Response<List<RaffleActivityStageResponseDTO>> response = Response.<List<RaffleActivityStageResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(raffleActivityStageResponseDTOS)
                    .build();

            log.info("查询上架活动数据 {}", JSON.toJSONString(response));

            return response;
        } catch (Exception e) {
            log.error("查询上架活动数据失败", e);
            return Response.<List<RaffleActivityStageResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    /**
     * 查询 ERP 活动详情，包括当前策略奖品和可购买 SKU。
     *
     * <p>该接口只读取默认配置库和已装配的库存缓存，不会装配活动、修改库存或变更发布状态。</p>
     *
     * <p>curl --request GET --url
     * 'http://localhost:8092/api/v1/raffle/erp/query_activity_detail?activityId=100401'</p>
     */
    @RequestMapping(value = "query_activity_detail", method = RequestMethod.GET)
    @Override
    public Response<RaffleActivityDetailResponseDTO> queryActivityDetail(
            @RequestParam(value = "activityId", required = false) Long activityId) {
        try {
            if (activityId == null || activityId <= 0) {
                throw new IllegalArgumentException("activityId must be greater than 0");
            }
            RaffleActivityDetailQueryVO detail = activityDetailQueryRepository.queryActivityDetail(activityId);
            if (detail == null) {
                return Response.<RaffleActivityDetailResponseDTO>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("activityId not found: " + activityId)
                        .build();
            }
            return Response.<RaffleActivityDetailResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toActivityDetailResponse(detail))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("查询 ERP 活动详情参数非法 activityId:{} info:{}", activityId, e.getMessage());
            return Response.<RaffleActivityDetailResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("查询 ERP 活动详情失败 activityId:{}", activityId, e);
            return Response.<RaffleActivityDetailResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private RaffleActivityDetailResponseDTO toActivityDetailResponse(RaffleActivityDetailQueryVO detail) {
        List<RaffleActivityPrizeResponseDTO> prizes = new ArrayList<>();
        if (detail.getPrizes() != null) {
            for (RaffleActivityDetailQueryVO.Prize prize : detail.getPrizes()) {
                prizes.add(RaffleActivityPrizeResponseDTO.builder()
                        .awardId(prize.getAwardId())
                        .awardTitle(prize.getAwardTitle())
                        .awardSubtitle(prize.getAwardSubtitle())
                        .awardKey(prize.getAwardKey())
                        .awardConfig(prize.getAwardConfig())
                        .awardRate(prize.getAwardRate())
                        .awardCount(prize.getAwardCount())
                        .awardCountSurplus(prize.getAwardCountSurplus())
                        .awardCountSurplusSource(prize.getAwardCountSurplusSource())
                        .awardCountSurplusFallbackDb(prize.getAwardCountSurplusFallbackDb())
                        .sort(prize.getSort())
                        .ruleModels(prize.getRuleModels())
                        .ruleUnlockCount(prize.getRuleUnlockCount())
                        .build());
            }
        }

        List<RaffleActivitySkuResponseDTO> skus = new ArrayList<>();
        if (detail.getSkus() != null) {
            for (RaffleActivityDetailQueryVO.Sku sku : detail.getSkus()) {
                skus.add(RaffleActivitySkuResponseDTO.builder()
                        .sku(sku.getSku())
                        .activityId(sku.getActivityId())
                        .activityCountId(sku.getActivityCountId())
                        .countQuota(sku.getCountQuota())
                        .dayCount(sku.getDayCount())
                        .monthCount(sku.getMonthCount())
                        .stockCount(sku.getStockCount())
                        .stockCountSurplus(sku.getStockCountSurplus())
                        .stockCountSurplusSource(sku.getStockCountSurplusSource())
                        .stockCountSurplusFallbackDb(sku.getStockCountSurplusFallbackDb())
                        .productAmount(sku.getProductAmount())
                        .build());
            }
        }

        return RaffleActivityDetailResponseDTO.builder()
                .activityId(detail.getActivityId())
                .activityName(detail.getActivityName())
                .activityDesc(detail.getActivityDesc())
                .strategyId(detail.getStrategyId())
                .activityState(detail.getActivityState())
                .beginDateTime(detail.getBeginDateTime())
                .endDateTime(detail.getEndDateTime())
                .prizes(prizes)
                .skus(skus)
                .build();
    }

}
