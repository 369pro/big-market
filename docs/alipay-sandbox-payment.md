# 支付宝沙箱支付 MVP

验证日期：2026-10-02。仅接入支付宝沙箱，不涉及真实资金；微信渠道暂不开放。

## 实测结果

- 测试订单：`073714757113`，测试体验包，沙箱金额 9.90 元，购买 30 次额度。
- 在支付宝官方沙箱收银台完成测试支付，不是手动修改订单状态。
- 后端主动查单确认交易成功，订单由 `CREATE` 转为 `WAIT`，再转为 `COMPLETED`。
- MySQL 中账户总额度、剩余额度均由 24 变为 54。
- 支付返回页显示“已支付 · 已到账”；刷新后仍为 54，没有重复增加额度。
- 13 项支付回归测试通过，前端 TypeScript 检查通过。

## 本次故障原因

1. **运行包过旧**：已有沙箱密钥配置，但旧 JAR 没有加载支付配置，后端持续报告渠道未配置。现在通过 Spring Config Import 加载 `.env.payment`；本地启动默认清理构建，`--no-build` 拒绝明显过期的 JAR。
2. **时区不一致**：JDBC 按 `Asia/Shanghai` 写入 `order_time`，MySQL `NOW()` 却是 UTC。原补单、关闭及未支付订单复用查询存在 8 小时偏差。现在由 Java 传入时间边界，以与写入相同的 JDBC 日期转换规则比较，避免依赖数据库当前时区。
3. **返回后丢失订单**：支付宝新标签页返回时，原页面可能已经清除了待支付记录。现在从返回地址提取订单号，再调用后端鉴权查询恢复订单；绝不以 URL 中的金额或状态判定支付成功。
4. **前端支付链接丢失**：查询结果没有 `payUrl` 时会覆盖原链接。现在合并状态时保留已有链接，并使用限定官方沙箱域名的链接打开收银台。

联调期间官方沙箱网关曾短暂返回 404，重新加载同一订单后恢复；没有改用生产网关或绕过 TLS 校验。

## 链路与职责

```text
额度商城
  -> POST /api/v1/sale/create_pay_order
  -> 保存商品价格和额度快照，生成官方签名支付地址
  -> 支付宝沙箱收银台
  -> 支付宝返回商城
  -> GET /api/v1/sale/query_pay_order（校验当前用户拥有订单）
  -> 后端 alipay.trade.query
  -> 核对订单号、金额、支付状态
  -> 数据库条件更新订单状态 + 本地事务内增加额度
```

- `PaymentStrategy` 定义创建支付、查单、关单、通知验签接口；`PaymentStrategies` 按渠道选择实现。
- `AlipayPaymentStrategy` 封装 SDK，网关固定为沙箱，不能通过配置误切换到生产支付。
- `OrderService` 负责订单状态与核验；`OrderRepository` 负责持久化和事务发货。
- 回调入口为 `POST /api/v1/sale/alipay/notify`。验签、校验应用与金额后再次主动查单，不信任客户端“付款成功”声明。

## 幂等与恢复

- 仅 `WAIT + pay_status=1` 的订单能通过 SQL 条件更新进入 `COMPLETED`；更新成功才增加额度。
- 订单更新与额度入账在同一个数据库事务中，额度更新失败会抛出异常并回滚。
- 多次查询、重复回调或并发补单可以重复触发发货，但同一订单只入账一次。
- 创建订单会复用同用户、同商品、同渠道 30 分钟内的未支付订单。此机制用于常规重试复用，不等同于具有请求 ID 唯一约束的并发创建幂等协议。
- 订单有效期 30 分钟；关闭前先查询并关闭支付宝侧交易，失败或结果不确定时保留待确认状态。
- 前端待支付状态约每 4 秒查询一次；后台每 15 秒扫描超过 1 分钟的未支付沙箱订单，每批最多 10 条。
- 已完成/已关闭订单不再持久化为待支付记录。支付结果弹窗支持右上角关闭、“返回商城”和“开始对话”；关闭后刷新或重新进入商城不会再次弹出。未付款订单关闭弹窗后仍保留查询入口。
- 后台每 60 秒扫描过期订单，每批最多 50 条。已有发货补偿任务继续处理已支付未发货订单。
- 本地没有公网回调时，前端查询与后台主动查单仍能完成到账，不需要临时关闭鉴权或暴露整个服务。

## 本地配置

支付代码位于同级 `chatgpt-data` 仓库，前端位于 `chatgpt-web` 仓库。

在 `chatgpt-data/.env.payment` 配置以下键，模板为 `.env.payment.example`：

```properties
payment.alipay.enabled=true
payment.alipay.app-id=你的沙箱APPID
payment.alipay.private-key=单行应用私钥
payment.alipay.public-key=单行支付宝公钥
payment.alipay.seller-id=沙箱商户PID
payment.alipay.notify-url=
payment.alipay.return-url=http://localhost:3003/#/sale
```

使用公钥模式和 RSA2；私钥按 SDK 要求使用 PKCS8 格式，不带 PEM 头尾或换行。不要混淆应用公钥与支付宝公钥。沙箱账号密码不是 API 密钥。

`.env`、`.env.payment` 和 `.run` 已被 Git 忽略；只提交占位模板。不要把配置文件、登录口令或完整签名请求放进文档、截图或提交记录。需要指定其他配置路径时，使用 `PAYMENT_CONFIG_FILE` 环境变量。

数据库需执行 `chatgpt-data/docs/dev-ops/environment/sql/alipay-sandbox-migration.sql`，将 `pay_url` 扩为 `TEXT`，避免签名地址被截断。本机已完成此迁移。

## 启动与验证

使用 Java 17，先启动现有 MySQL、Redis 等本地依赖。后端在 `chatgpt-data` 下执行：

```bash
./run-local.sh
```

前端在 `chatgpt-web` 下执行：

```bash
npm run dev -- --hostname 127.0.0.1
```

本地地址：`http://localhost:3003/#/sale`，支付后端为 `http://127.0.0.1:8093`。不要误开旧 Docker 前端 3002 或旧后端 8091。

支付测试：

```bash
mvn -pl chatgpt-data-app -am -DskipTests=false \
  '-Dtest=*Payment*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

前端检查：

```bash
npx tsc --noEmit --incremental false
```

弹窗回归：已实测“返回商城”和右上角关闭后刷新，均回到套餐列表且不再弹出旧订单；使用已支付订单查询验证，没有再次发起付款。

测试覆盖金额不匹配、通知后可信查单、支付/关闭状态、重复发货 CAS、额度异常与事务注解契约、时间边界绑定。事务回滚相关自动化目前是仓储 Mock/契约测试，不是完整真实 MySQL 故障注入；本次真实数据库验证覆盖成功入账和重复确认不重复入账。

## MVP 边界

尚未接入真实收款、退款、财务对账单和生产回调部署。全量订单列表、跨设备订单恢复、高并发创建幂等键、补单分页与退避、监控告警属于后续生产化工作。不要把本次沙箱通过视为生产支付验收通过。

官方参考：[支付宝沙箱控制台](https://open.alipay.com/develop/sandbox/app)、[支付宝 Java SDK](https://github.com/alipay/alipay-sdk-java-all)。
