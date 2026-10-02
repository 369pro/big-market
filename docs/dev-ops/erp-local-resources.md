# 本地运营后台资源配置

本机 Docker 的约 8 GiB 内存由 MySQL、Redis、Elasticsearch、Canal、RocketMQ、Nacos、抽奖服务和 ERP 共用。

2026-10-02 排查中，ERP 的 Umi 依赖预编译被 Docker 内存不足终止。处理方式：

- 停止当前项目未使用的 Langfuse 六个容器。
- 当前用户端使用本机 `3003` / `8093`，停止 Docker 中重复的 `3002` / `8091` 服务。
- ERP 禁用 MFSU，避免额外启动依赖编译器。
- 本机 `docker-compose-local.yml` 的 `big-market-erp.environment.NODE_OPTIONS` 设置为 `--max-old-space-size=1536`。768 MiB 不足以完成该项目编译；此设置限制 Node.js 堆，不是修改 Docker 总内存。
- 保留所有数据库卷和停止容器，清理无引用镜像及未使用构建缓存。

修改 ERP 环境参数后，需要重建该容器以生效：

```bash
docker compose -f docs/dev-ops/docker-compose-local.yml up -d --no-deps big-market-erp
```

检查状态时同时看 `docker stats --no-stream`、容器 `OOMKilled` 状态和 ERP 日志。出现 JavaScript heap out of memory 表示 Node.js 堆上限不足；`OOMKilled=true` 表示系统内存压力，不能只循环重启。

如果重新启用 Langfuse 或重复的聊天容器，应重新评估 Docker 的总内存预算。停止的容器可通过 `docker start <容器名>` 恢复；重新启用重复服务前确认本机端口和前端 API 指向。
