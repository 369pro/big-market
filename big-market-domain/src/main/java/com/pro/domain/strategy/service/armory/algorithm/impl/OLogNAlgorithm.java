package com.pro.domain.strategy.service.armory.algorithm.impl;

import com.pro.domain.strategy.model.entity.StrategyAwardEntity;
import com.pro.domain.strategy.service.armory.algorithm.AbstractAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 循环、二分、多线程，查找抽奖算法
 * @create 2024-08-24 10:47
 */
@Slf4j
@Component("oLogNAlgorithm")
public class OLogNAlgorithm extends AbstractAlgorithm {

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 预热活动概率值
     * 如概率值为；3、4、2、9，存储为 [1~3]、[4~7]、[8~9]、[10~18]，
     * 抽奖时，for循环匹配 / 二分查找 / 多线程查找。
     *
     * @param key                   为策略ID、权重ID
     * @param strategyAwardEntities 对应的奖品概率
     */
    @Override
    public void armoryAlgorithm(String key, List<StrategyAwardEntity> strategyAwardEntities, BigDecimal rateRange) {
        log.info("抽奖算法 OLog(n) 装配 key:{}", key);
        // 构造区间指针
        int from = 1, to = 0;
        // {{from,to},awardId}
        Map<Map<Integer,Integer>, Integer> table = new HashMap<>();
        for(StrategyAwardEntity strategyAwardEntity : strategyAwardEntities){
            Integer awardId = strategyAwardEntity.getAwardId();
            BigDecimal awardRate = strategyAwardEntity.getAwardRate();
            int intervalLen = rateRange.multiply(awardRate).intValue();
            to += intervalLen;
            table.put(new HashMap<>(from, to), awardId);
            from = to + 1;
        }
        //
        repository.storeStrategyAwardSearchRateTable(key,
                table.size(), table);
    }

    // note: 这里redis那层封装了key前缀，你不用管这些东西
    @Override
    public Integer dispatchAlgorithm(String key) {
        // 1. 从redis获取数据：最大区间值，区间表
        int rateRange = repository.getRateRange(key);
        // 这是项目里因为 Redis JSON 序列化导致的类型偏移，是很常见的坑，
        // Dear Master 以后设计 Redis 缓存结构时，Map 的 key 建议直接用 String 类型，避免这种歧义！
        Map<Map<String,Integer>, Integer> table = repository.getMap(key);
        // 2. 生成随机数
        int rateKey = secureRandom.nextInt(rateRange);
        // 3. 根据tableSize选择查找方式
        if(table.size() <= 16){
            return linearSearch(rateKey, table);
        }else if(table.size() <= 32){
            return binarySearch(rateKey, table);
        }else{
            // 异步网络才用这个，这里复习 CompletableFuture
            return multiThreadSearch(rateKey, table);
        }
    }

    private Integer linearSearch(int rateKey, Map<Map<String, Integer>, Integer> table) {
        // 存储找到的奖品ID
        Integer awardId = null;
        for(Map.Entry<Map<String, Integer>, Integer> tableEntry : table.entrySet()){
            // 获取区间 Map，⽐如：{2, 9}
            Map<String, Integer> rangeMap = tableEntry.getKey();
            // 只有一个元素，更清晰的写法
            Map.Entry<String, Integer> range = rangeMap.entrySet().iterator().next();
            int start = Integer.parseInt(range.getKey());
            int end = range.getValue();
            if(start <= rateKey && rateKey <= end){
                awardId = range.getValue();
                break;
            }
        }
        return awardId;
    }

    private Integer binarySearch(int rateKey, Map<Map<String, Integer>, Integer> table){
        // 存储找到的奖品ID
        Integer awardId = null;
        List<Map.Entry<Map<String, Integer>, Integer>> entries = new ArrayList<>(table.entrySet());
        entries.sort(Comparator.comparingInt(e -> Integer.parseInt(e.getKey().keySet().iterator().next())));
        // 这里是 entries 数组元素的下标，而不是区间下标
        int left = 0;
        int right = entries.size() - 1;
        while(left < right){
            int mid = left + (right-left) / 2;
            Map.Entry<Map<String, Integer>, Integer> entry = entries.get(mid);
            Map<String, Integer> rangeMap = entry.getKey();
            // 只有一个元素，更清晰的写法
            Map.Entry<String, Integer> range = rangeMap.entrySet().iterator().next();
            int start = Integer.parseInt(range.getKey());
            int end = range.getValue();
            if(rateKey < start){
                right = mid-1;
            }else if(rateKey > end){
                left = mid+1;
            }else{
                awardId = entry.getValue();
                break;
            }
        }
        return awardId;
    }

    private Integer multiThreadSearch(int rateKey, Map<Map<String, Integer>, Integer> table) {
        // 为每个区间创建一个异步任务查找
        List<CompletableFuture<Map.Entry<Map<String, Integer>, Integer>>> futures =
                table.entrySet().stream().map(
                        entry-> CompletableFuture.supplyAsync(()->{
                            // 异步检测随机数是否在区间
                            Map<String, Integer> rangeMap = entry.getKey();
                            // 只有一个元素，更清晰的写法
                            Map.Entry<String, Integer> range = rangeMap.entrySet().iterator().next();
                            int start = Integer.parseInt(range.getKey());
                            int end = range.getValue();
                            if(start <= rateKey && rateKey <= end){
                                return entry;
                            }
                            return null;
                        }, threadPoolExecutor)
                ).collect(Collectors.toList());
        // 2. 等待所有异步任务完成
        // allOf() 只接受数组不接受 List，所以要转换一下。new CompletableFuture[0] 是 Java 的惯用写法，
        // 意思是：给我按这个类型转成数组，长度自动算（传 [0] 比传 [n] 更高效）。
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try{
            allFutures.join();
            // 遍历所有的future，获取第一个非null结果
            for(CompletableFuture<Map.Entry<Map<String, Integer>, Integer>> future : futures){
                // future.getNow(null) 立刻返回结果，若未完成，返回null
                Map.Entry<Map<String, Integer>, Integer> result = future.getNow(null);
                if(result != null){
                    return result.getValue();
                }
            }
        } catch (CompletionException e) {
            e.printStackTrace();
        }
        return null;
    }
}
