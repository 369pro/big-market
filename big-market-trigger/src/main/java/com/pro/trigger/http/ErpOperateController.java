package com.pro.trigger.http;

import com.alibaba.fastjson.JSON;
import com.pro.adapter.repository.IESUserRaffleOrderRepository;
import com.pro.domain.activity.model.entity.RaffleActivityStageEntity;
import com.pro.domain.activity.service.IRaffleActivityStageService;
import com.pro.domain.activity.service.armory.IActivityArmory;
import com.pro.model.valobj.ESUserRaffleOrderPageVO;
import com.pro.model.valobj.ESUserRaffleOrderQueryVO;
import com.pro.model.valobj.ESUserRaffleOrderVO;
import com.pro.trigger.api.IErpOperateService;
import com.pro.trigger.api.dto.ESUserRaffleOrderPageResponseDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderQueryRequestDTO;
import com.pro.trigger.api.dto.ESUserRaffleOrderResponseDTO;
import com.pro.trigger.api.dto.RaffleActivityStageResponseDTO;
import com.pro.trigger.api.dto.UpdateStageActivity2ActiveRequestDTO;
import com.pro.trigger.api.response.Response;
import com.pro.types.enums.ResponseCode;
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
    private IRaffleActivityStageService raffleActivityStageService;
    @Resource
    private IActivityArmory activityArmory;

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
            Long id = requestDTO.getId();
            log.info("更新上架活动状态为生效开始 id:{}", id);
            Long activityId = raffleActivityStageService.queryStageActivity2ActiveById(id);
            boolean assembled = activityArmory.assembleActivitySkuByActivityId(activityId);
            log.info("更新上架活动状态为装配完成 activityId:{} assembled:{}", activityId, assembled);

            raffleActivityStageService.updateStageActivity2Active(id);
            log.info("更新上架活动状态为生效完成 activityId:{}", activityId);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(true)
                    .build();
        } catch (Exception e) {
            log.error("更新上架活动状态为生效开始，失败 id:{}", requestDTO.getId(), e);
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

}
