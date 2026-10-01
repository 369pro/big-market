package com.pro.infrastructure.adapter.repository;

import cn.bugstack.middleware.db.router.DBContextHolder;
import cn.bugstack.middleware.db.router.DBRouterConfig;
import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.pro.adapter.repository.IAwardFeedRepository;
import com.pro.infrastructure.dao.IAwardFeedDao;
import com.pro.infrastructure.dao.po.UserAwardRecord;
import com.pro.model.valobj.AwardFeedRecordVO;
import org.springframework.stereotype.Repository;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class AwardFeedRepository implements IAwardFeedRepository {
    private static final int RECORD_LIMIT = 100;
    @Resource private DBRouterConfig routerConfig;
    @Resource private IDBRouterStrategy dbRouter;
    @Resource private IAwardFeedDao awardFeedDao;
    private final Cache<Long, List<AwardFeedRecordVO>> cache = CacheBuilder.newBuilder()
            .maximumSize(100).expireAfterWrite(10, TimeUnit.SECONDS).build();

    @Override
    public List<AwardFeedRecordVO> queryRecentAwards(Long activityId) {
        try {
            return cache.get(activityId, () -> loadAwards(activityId));
        } catch (ExecutionException | UncheckedExecutionException e) {
            throw new IllegalStateException("Unable to load award feed", e.getCause());
        }
    }

    private List<AwardFeedRecordVO> loadAwards(Long activityId) {
        String previousDb = DBContextHolder.getDBKey();
        String previousTable = DBContextHolder.getTBKey();
        List<UserAwardRecord> records = new ArrayList<>();
        try {
            for (int db = 1; db <= routerConfig.getDbCount(); db++) {
                dbRouter.setDBKey(db);
                for (int table = 0; table < routerConfig.getTbCount(); table++) {
                    dbRouter.setTBKey(table);
                    records.addAll(awardFeedDao.queryRecentAwards(activityId, RECORD_LIMIT));
                }
            }
        } finally {
            dbRouter.clear();
            if (previousDb != null) DBContextHolder.setDBKey(previousDb);
            if (previousTable != null) DBContextHolder.setTBKey(previousTable);
        }
        records.sort(Comparator.comparing(UserAwardRecord::getAwardTime).reversed()
                .thenComparing(UserAwardRecord::getOrderId, Comparator.reverseOrder())
                .thenComparing(UserAwardRecord::getUserId, Comparator.reverseOrder()));
        Set<String> seen = new HashSet<>();
        List<AwardFeedRecordVO> result = records.stream()
                .filter(record -> seen.add(record.getUserId() + "\0" + record.getOrderId()))
                .limit(RECORD_LIMIT)
                .map(record -> new AwardFeedRecordVO(record.getUserId(), record.getOrderId(),
                        record.getAwardId(), record.getAwardTitle(), record.getAwardTime()))
                .collect(Collectors.toList());
        return Collections.unmodifiableList(result);
    }
}
