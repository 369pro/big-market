package com.pro.test.infrastructure;

import cn.bugstack.middleware.db.router.strategy.IDBRouterStrategy;
import com.pro.domain.award.model.valobj.TaskStateVO;
import com.pro.domain.credit.model.aggregate.TradeAggregate;
import com.pro.domain.credit.model.entity.CreditAccountEntity;
import com.pro.domain.credit.model.entity.CreditOrderEntity;
import com.pro.domain.credit.model.entity.TaskEntity;
import com.pro.domain.credit.model.valobj.TradeNameVO;
import com.pro.domain.credit.model.valobj.TradeTypeVO;
import com.pro.infrastructure.adapter.repository.CreditRepository;
import com.pro.infrastructure.dao.*;
import com.pro.infrastructure.event.EventPublisher;
import com.pro.infrastructure.redis.IRedisService;
import org.junit.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.redisson.api.RLock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Opt-in real MySQL test. Creates and drops only its own random test schema. */
public class CreditIdempotencyDatabaseTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String schema;
    private CreditRepository repository;

    @Before
    public void setUp() throws Exception {
        String url = System.getenv("IDEMPOTENCY_JDBC_URL");
        Assume.assumeNotNull(url, System.getenv("MYSQL_PASSWORD"));
        String user = System.getenv().getOrDefault("MYSQL_USER", "root");
        String password = System.getenv("MYSQL_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "idempotency_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE DATABASE " + schema);
        String base = url.substring(0, url.indexOf('/', "jdbc:mysql://".length()));
        DriverManagerDataSource dataSource = new DriverManagerDataSource(base + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai", user, password);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE user_credit_order LIKE big_market_02.user_credit_order_002");
        jdbc.execute("CREATE TABLE user_credit_account LIKE big_market_02.user_credit_account");
        jdbc.execute("CREATE TABLE task LIKE big_market_02.task");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mybatis/mapper/mysql/user_credit_order_mapper.xml"),
                new ClassPathResource("mybatis/mapper/mysql/user_credit_account_mapper.xml"),
                new ClassPathResource("mybatis/mapper/mysql/task_mapper.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        repository = new CreditRepository();
        ReflectionTestUtils.setField(repository, "userCreditOrderDao", session.getMapper(IUserCreditOrderDao.class));
        ReflectionTestUtils.setField(repository, "userCreditAccountDao", session.getMapper(IUserCreditAccountDao.class));
        ReflectionTestUtils.setField(repository, "taskDao", session.getMapper(ITaskDao.class));
        ReflectionTestUtils.setField(repository, "dbRouter", mock(IDBRouterStrategy.class));
        ReflectionTestUtils.setField(repository, "transactionTemplate", new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        ReflectionTestUtils.setField(repository, "eventPublisher", mock(EventPublisher.class));
        // Disable advisory locking deliberately: database constraints must still prevent double effects.
        IRedisService redis = mock(IRedisService.class);
        when(redis.getLock(anyString())).thenReturn(mock(RLock.class));
        ReflectionTestUtils.setField(repository, "redisService", redis);
    }

    @After
    public void tearDown() {
        if (schema != null && admin != null) admin.execute("DROP DATABASE " + schema);
    }

    @Test
    public void concurrentDuplicateAndOverdraftUseRealTransactions() throws Exception {
        List<Callable<Boolean>> credits = new ArrayList<>();
        for (int i = 0; i < 12; i++) credits.add(() -> apply("grant-1", "10"));
        runTogether(credits);
        assertTrue(apply("grant-1", "10")); // Retry after an ambiguous response.
        assertEquals(1, count("user_credit_order"));
        assertEquals(1, count("task"));
        assertEquals(0, balance().compareTo(new BigDecimal("10")));

        List<Boolean> debits = runTogether(Arrays.asList(() -> apply("pay-a", "-8"), () -> apply("pay-b", "-8")));
        assertEquals(1, Collections.frequency(debits, true));
        assertEquals(2, count("user_credit_order"));
        assertEquals(2, count("task"));
        assertEquals(0, balance().compareTo(new BigDecimal("2")));
        assertFalse(apply("grant-1", "11"));
        assertEquals(0, balance().compareTo(new BigDecimal("2")));
    }

    private List<Boolean> runTogether(List<Callable<Boolean>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : pool.invokeAll(calls)) results.add(future.get());
            return results;
        } finally { pool.shutdownNow(); }
    }

    private boolean apply(String businessNo, String amount) {
        BigDecimal value = new BigDecimal(amount);
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            repository.saveUserCreditTradeOrder(TradeAggregate.builder().userId("test-user")
                    .creditAccountEntity(CreditAccountEntity.builder().userId("test-user").adjustAmount(value).build())
                    .creditOrderEntity(CreditOrderEntity.builder().userId("test-user").orderId(id)
                            .outBusinessNo(businessNo).tradeName(TradeNameVO.REBATE)
                            .tradeType(value.signum() > 0 ? TradeTypeVO.FORWARD : TradeTypeVO.REVERSE).tradeAmount(value).build())
                    .taskEntity(TaskEntity.builder().userId("test-user").messageId(id.substring(0, 11)).topic("test")
                            .state(TaskStateVO.create).build()).build());
            return true;
        } catch (RuntimeException expectedConflict) { return false; }
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private BigDecimal balance() { return jdbc.queryForObject("SELECT available_amount FROM user_credit_account WHERE user_id='test-user'", BigDecimal.class); }
}
