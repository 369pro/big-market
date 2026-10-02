# 本地 Kibana

`http://localhost:9200` 是 Elasticsearch REST API，返回版本 JSON 正常；可视化界面位于 `http://localhost:5601`。若 9200 可访问而 5601 拒绝连接，先检查 Kibana 容器是否启动。

## 启动

在 big-market 根目录执行：

```bash
docker compose -f docs/dev-ops/kibana-local/docker-compose.yml up -d --wait --wait-timeout 300
```

首次启动需要下载官方镜像。Kibana 固定为 7.17.14，与当前 ES 一致，使用中文界面，仅对本机开放 5601，不重建 ES 或业务容器。

前置条件：现有 ES 已启动，位于 `llm-market-local_default` 网络，网络别名为 `elasticsearch`。网络名变化时可以用 `MARKET_DOCKER_NETWORK` 覆盖。容器内不能用 `localhost:9200` 连接另一个容器。

页面首次打开选择自行探索，不需要导入示例数据。查看抽奖数据时，在 Stack Management 的索引模式中创建 `big_market.user_raffle_order`，再进入 Discover；若启用了时间字段，注意调整时间范围。

## 检查与停止

```bash
curl -fsS http://localhost:5601/api/status
docker compose -f docs/dev-ops/kibana-local/docker-compose.yml logs --tail 100 kibana
docker compose -f docs/dev-ops/kibana-local/docker-compose.yml stop kibana
```

配置了健康检查和 `unless-stopped` 重启策略。首次启动执行内部索引迁移时可能暂时返回未就绪，等待容器 healthy 后再访问。

这是本地开发配置。ES 现有环境未启用认证，不应直接向公网开放。该旧版本用于匹配项目环境，生产环境需单独规划升级及认证。

参考：[Kibana 7.17 Docker 官方文档](https://www.elastic.co/guide/en/kibana/7.17/docker.html)。
