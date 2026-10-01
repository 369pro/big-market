package com.pro.infrastructure.adapter.repository;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.redisson.api.RLock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import com.alibaba.fastjson.JSON;
import com.pro.domain.award.model.valobj.AccountStatusVO;
import com.pro.domain.credit.model.aggregate.TradeAggregate;
import com.pro.domain.credit.model.entity.CreditAccountEntity;
import com.pro.domain.credit.model.entity.CreditOrderEntity;
import com.pro.domain.credit.model.entity.TaskEntity;
import com.pro.domain.credit.repository.ICreditRepository;
import com.pro.infrastructure.dao.ITaskDao;
import com.pro.infrastructure.dao.IUserCreditAccountDao;
import com.pro.infrastructure.dao.IUserCreditOrderDao;
import com.pro.infrastructure.dao.po.Task;
import com.pro.infrastructure.dao.po.UserCreditAccount;
import com.pro.infrastructure.dao.po.UserCreditOrder;
import com.pro.infrastructure.event.EventPublisher;
import com.pro.infrastructure.redis.IRedisService;
import com.pro.types.common.Constants;
import com.pro.types.enums.ResponseCode;
import com.pro.types.exception.AppException;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 用户积分仓储
 * @create 2024-06-01 10:07
 */
@Slf4j
@Repository
public class CreditRepository implements ICreditRepository {
    @Resource
    private IRedisService redisService;
    @Resource
    private IUserCreditAccountDao userCreditAccountDao;
    @Resource
    private IUserCreditOrderDao userCreditOrderDao;
    @Resource
    private ITaskDao taskDao;

    @Resource
    private IDBRouterStrategy dbRouter;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private EventPublisher eventPublisher;
    /**
     * 创建积分交易订单的【加锁原因】，详情见飞书部分
     * @param tradeAggregate
     */
    @Override
    public void saveUserCreditTradeOrder(TradeAggregate tradeAggregate) {
        String userId = tradeAggregate.getUserId();
        CreditAccountEntity creditAccountEntity = tradeAggregate.getCreditAccountEntity();
        CreditOrderEntity creditOrderEntity = tradeAggregate.getCreditOrderEntity();
        TaskEntity taskEntity = tradeAggregate.getTaskEntity();

        // 积分账户
        UserCreditAccount userCreditAccountReq = new UserCreditAccount();
        userCreditAccountReq.setUserId(userId);
        userCreditAccountReq.setTotalAmount(creditAccountEntity.getAdjustAmount());
        // 知识；仓储往上有业务语义，仓储往下到 dao 操作是没有业务语义的。所以不用在乎这块使用的字段名称，直接用持久化对象即可。
        userCreditAccountReq.setAvailableAmount(creditAccountEntity.getAdjustAmount());
        userCreditAccountReq.setAccountStatus(AccountStatusVO.open.getCode());

        // 积分订单
        UserCreditOrder userCreditOrderReq = new UserCreditOrder();
        userCreditOrderReq.setUserId(creditOrderEntity.getUserId());
        userCreditOrderReq.setOrderId(creditOrderEntity.getOrderId());
        userCreditOrderReq.setTradeName(creditOrderEntity.getTradeName().getName());
        userCreditOrderReq.setTradeType(creditOrderEntity.getTradeType().getCode());
        userCreditOrderReq.setTradeAmount(creditOrderEntity.getTradeAmount());
        userCreditOrderReq.setOutBusinessNo(creditOrderEntity.getOutBusinessNo());

        Task task = new Task();
        task.setUserId(taskEntity.getUserId());
        task.setTopic(taskEntity.getTopic());
        task.setMessageId(taskEntity.getMessageId());
        task.setMessage(JSON.toJSONString(taskEntity.getMessage()));
        task.setState(taskEntity.getState().getCode());

        RLock lock = redisService.getLock(Constants.RedisKey.USER_CREDIT_ACCOUNT_LOCK + userId);
        try{
            lock.lock();
            dbRouter.doRouter(userId);
            UserCreditOrder existing = userCreditOrderDao.queryByBusinessNo(userCreditOrderReq);
            if (existing != null) {
                if (!existing.getTradeType().equals(userCreditOrderReq.getTradeType())
                        || !existing.getTradeName().equals(userCreditOrderReq.getTradeName())
                        || existing.getTradeAmount().compareTo(userCreditOrderReq.getTradeAmount()) != 0) {
                    throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "Business key reused with a different credit trade");
                }
                creditOrderEntity.setOrderId(existing.getOrderId());
                return;
            }
            transactionTemplate.execute(status -> {
                try{
                    // The unique ledger entry and balance change commit or roll back together.
                    userCreditOrderDao.insert(userCreditOrderReq);
                    UserCreditAccount userCreditAccount = userCreditAccountDao.queryUserCreditAccount(userCreditAccountReq);
                    if (null == userCreditAccount) {
                        if (userCreditAccountReq.getAvailableAmount().signum() < 0) {
                            throw new AppException(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode(), "Insufficient credit");
                        }
                        userCreditAccountDao.insert(userCreditAccountReq);
                    } else {
                        if (userCreditAccountDao.updateAddAmount(userCreditAccountReq) != 1) {
                            throw new AppException(ResponseCode.USER_CREDIT_ACCOUNT_NO_AVAILABLE_AMOUNT.getCode(), "Insufficient credit or account closed");
                        }
                    }
                    // 3. 写入任务
                    taskDao.insert(task);
                }catch (DuplicateKeyException e) {
                    status.setRollbackOnly();
                    log.error("调整账户积分额度异常，唯一索引冲突 userId:{} orderId:{}", userId, creditOrderEntity.getOrderId(), e);
                    throw e; // Only an exact ledger match above is a successful duplicate.
                } catch (Exception e) {
                    status.setRollbackOnly();
                    log.error("调整账户积分额度失败 userId:{} orderId:{}", userId, creditOrderEntity.getOrderId(), e);
                    throw e;
                }
                return 1;
            });
        }finally {
            dbRouter.clear();
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        try {
            // 发送消息【在事务外执行，如果失败还有任务补偿】note topic = 'credit_adjust_success' 消费者接收到了但是不会处理
            eventPublisher.publish(task.getTopic(), task.getMessage());
            // 更新数据库记录，task 任务表
            taskDao.updateTaskSendMessageCompleted(task);
            log.info("调整账户积分记录，发送MQ消息完成 userId: {} orderId:{} topic: {}", userId, creditOrderEntity.getOrderId(), task.getTopic());
        } catch (Exception e) {
            log.error("调整账户积分记录，发送MQ消息失败 userId: {} topic: {}", userId, task.getTopic());
            taskDao.updateTaskSendMessageFail(task);
        }
    }

    @Override
    public CreditAccountEntity queryUserCreditAccount(String userId) {
        UserCreditAccount userCreditAccountReq = new UserCreditAccount();
        userCreditAccountReq.setUserId(userId);
        try {
            dbRouter.doRouter(userId);
            UserCreditAccount userCreditAccount = userCreditAccountDao.queryUserCreditAccount(userCreditAccountReq);
            return CreditAccountEntity.builder().userId(userId)
                    .adjustAmount(userCreditAccount == null ? BigDecimal.ZERO : userCreditAccount.getAvailableAmount()).build();
        } finally {
            dbRouter.clear();
        }

    }
}
