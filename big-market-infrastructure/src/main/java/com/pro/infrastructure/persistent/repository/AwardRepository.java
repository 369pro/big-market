package com.pro.infrastructure.persistent.repository;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.alibaba.fastjson.JSON;
import com.pro.domain.award.model.aggregate.UserAwardRecordAggregate;
import com.pro.domain.award.model.entity.TaskEntity;
import com.pro.domain.award.model.entity.UserAwardRecordEntity;
import com.pro.domain.award.repository.IAwardRepository;
import com.pro.infrastructure.event.EventPublisher;
import com.pro.infrastructure.persistent.dao.ITaskDao;
import com.pro.infrastructure.persistent.dao.IUserAwardRecordDao;
import com.pro.infrastructure.persistent.po.Task;
import com.pro.infrastructure.persistent.po.UserAwardRecord;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
@Slf4j
@Repository
public class AwardRepository implements IAwardRepository {
    @Resource
    private ITaskDao taskDao;
    @Resource
    private IUserAwardRecordDao userAwardRecordDao;
    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;

    @Override
    public void saveUserAwardRecord(UserAwardRecordAggregate userAwardRecordAggregate) {
        UserAwardRecordEntity userAwardRecordEntity = userAwardRecordAggregate.getUserAwardRecordEntity();
        TaskEntity taskEntity = userAwardRecordAggregate.getTaskEntity();
        String userId = userAwardRecordEntity.getUserId();
        Long activityId = userAwardRecordEntity.getActivityId();
        Integer awardId = userAwardRecordEntity.getAwardId();
        // 1.根据聚合对象反哺 两张事务表
        UserAwardRecord userAwardRecord = new UserAwardRecord();
        userAwardRecord.setUserId(userAwardRecordEntity.getUserId());
        userAwardRecord.setActivityId(userAwardRecordEntity.getActivityId());
        userAwardRecord.setStrategyId(userAwardRecordEntity.getStrategyId());
        userAwardRecord.setOrderId(userAwardRecordEntity.getOrderId());
        userAwardRecord.setAwardId(userAwardRecordEntity.getAwardId());
        userAwardRecord.setAwardTitle(userAwardRecordEntity.getAwardTitle());
        userAwardRecord.setAwardTime(userAwardRecordEntity.getAwardTime());
        userAwardRecord.setAwardState(userAwardRecordEntity.getAwardState().getCode());

        Task task = new Task();
        task.setUserId(taskEntity.getUserId());
        task.setTopic(taskEntity.getTopic());
        task.setMessageId(taskEntity.getMessageId());
        task.setMessage(JSON.toJSONString(taskEntity.getMessage()));
        task.setState(taskEntity.getState().getCode());

        // 2. 对两张表插入数据
        try{
            dbRouter.doRouter(userId);
            transactionTemplate.execute(status -> {
                try{
                    // 2.1写入用户中奖记录
                    userAwardRecordDao.insert(userAwardRecord);
                    // 2.2写入task任务表
                    taskDao.insert(task);
                    return 1;
                }catch (DuplicateKeyException e){
                    status.setRollbackOnly();
                    log.error("写入中奖记录，唯一索引冲突 userId: {} activityId: {} awardId: {}", userId, activityId, awardId, e);
                    throw new AppException(ResponseCode.INDEX_DUP.getCode(), e);
                }
            });
        }finally {
            dbRouter.clear();
        }

        // 3. 发送MQ消息
        try{
            eventPublisher.publish(task.getTopic(), task.getMessage());
            // 更新task任务表的状态
            taskDao.updateTaskSendMessageCompleted(task);
        } catch (Exception e) {
            log.error("写入中奖记录，发送MQ消息失败 userId: {} topic: {}", userId, task.getTopic());
            taskDao.updateTaskSendMessageFail(task);
        }
    }
}
