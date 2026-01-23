package com.pro.domain.rebate.service;

import com.pro.domain.rebate.model.entity.BehaviorEntity;

import java.util.List;

public interface IBehaviorRebateService{
    List<String> createOrder(BehaviorEntity behaviorEntity);
}
