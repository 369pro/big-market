# 智谱 GLM-5.3-Flash 接入

## 配置

保留 DeepSeek，通过已有 `ChatProviderRegistry` 和 `OpenAiCompatibleChatProvider` 增加智谱供应商，不恢复旧 ChatGLM SDK。

在同级 `chatgpt-data/.env` 设置：

```properties
ZHIPU_ENABLED=true
ZHIPU_API_KEY=填写智谱普通模型API密钥
ZHIPU_API_HOST=https://open.bigmodel.cn/api/paas/v4/chat/completions
```

默认 `ZHIPU_ENABLED=false`。未启用的供应商不会注册，其模型也不出现在目录中，不影响 DeepSeek。启用但缺失密钥会启动失败并指出供应商名称，不输出密钥。`.env` 被 Git 忽略，模板是 `chatgpt-data/.env.example`。

这是普通模型 API 接入，不使用 Coding Plan 专用端点；API 权限、余额和计费以智谱账户为准。

## 模型与适配

- 对外模型 ID、上游模型 ID 均为 `glm-5.3-flash`，展示名 `GLM-5.3-Flash`，供应商 `zhipu`。
- 配置位于 `chatgpt-data-app/src/main/resources/application-models.yml`。
- 智谱使用完整 `/api/paas/v4/chat/completions` 地址，适配器不会额外拼接 `/v1`。
- `request-options` 提供供应商的额外 JSON 请求参数。模型、消息及 `stream=true` 由服务端请求固定，不能被这些选项覆盖。
- GLM-5.3-Flash 仅支持开启思考，因此配置 `thinking.type=enabled`，MVP 使用 `reasoning_effort=low`、`max_tokens=8192`。现有页面显示最终回答，不显示 `reasoning_content`。
- 复用 Bearer 鉴权、SSE 文本解析、取消和错误处理；浏览器不获取智谱密钥，也不直接请求智谱。
- 前端继续读取 `/api/v1/chatgpt/models`，无需添加硬编码模型选项。

## 账户权限与启动

新建额度账户的默认模型权限包含 `glm-5.3-flash`。若 `.env` 已显式设置 `APP_MODEL_TYPES`，也需把新 ID 加入该列表。

已有账户按业务授权范围执行 `chatgpt-data/docs/dev-ops/environment/sql/zhipu-model-access.sql`。脚本为已有 DeepSeek 或历史 `glm-4` 权限账户追加智谱模型权限，可重复执行，不覆盖旧模型列表，不修改余额或额度。生产环境执行前请确认套餐授权策略。

配置完成后，在 `chatgpt-data` 使用 Java 17 执行 `./run-local.sh`，然后刷新前端模型列表。必须重新构建，避免运行旧 JAR。

## 验证

```bash
mvn -pl chatgpt-data-app -am -DskipTests=false \
  -Dtest=OpenAiCompatibleChatProviderTest,ZhipuProviderConfigTest,SensitiveWordContextTest,ChatQuotaFlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

10 项测试通过，覆盖智谱 V4 路径、强制思考参数、SSE 最终文本、路由字段保护、禁用供应商隔离、缺失密钥校验、敏感词过滤上下文保留及额度扣减流程。

2026-10-02 已在本地忽略的 `.env` 中配置密钥并重启服务。智谱官方接口与项目登录后的 `/api/v1/chatgpt/chat/completions` 均完成真实请求，项目接口返回 HTTP 200 和“GLM 接入成功”。模型目录同时保留两个 DeepSeek 模型及 GLM-5.3-Flash。

浏览器端也已完成验收：在 `http://localhost:3003/#/chat/4` 选择 GLM-5.3-Flash，发送“只回复：GLM 接入成功”，页面显示完整回答并恢复发送状态。

联调同时修复了敏感词过滤器重建请求对象导致 `requestId` 和额度预留上下文丢失的问题。现在只替换原请求的过滤后消息，保留幂等标识和其他上下文，避免有额度账户在调用模型前失败。

官方参考：[GLM-5.3-Flash/FlashX](https://docs.bigmodel.cn/cn/guide/models/vlm/glm-5.3-flash)、[对话补全 API](https://docs.bigmodel.cn/api-reference/模型-api/对话补全)。
