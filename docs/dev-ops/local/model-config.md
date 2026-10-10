# Local model credentials

Before starting `chatgpt-data`, create `llm.env` beside this file using
`llm.env.example`. Copy the `DEEPSEEK_*` and `ZHIPU_*` settings from your local
provider configuration. For GLM, set `ZHIPU_ENABLED=true` and supply the existing
`ZHIPU_API_KEY`. Do not copy unrelated database or networking overrides.

Compose loads this ignored file on every container creation. Keep credentials out
of Git. After changing it, recreate `chatgpt-data` and verify
`GET http://localhost:8091/api/v1/chatgpt/models` contains the enabled models.
