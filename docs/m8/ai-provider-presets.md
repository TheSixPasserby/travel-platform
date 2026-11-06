# AI 服务配置

用户选择采用服务预设和自定义兼容端点。当前通过后端环境变量选择服务，登录后的 AI 页面展示可用状态。Key保存在服务端环境中。

| AI_PROVIDER | 默认 Base URL | 默认模型 | 默认响应格式 |
|---|---|---|---|
| openai | https://api.openai.com/v1 | gpt-4.1-mini | json_schema |
| deepseek | https://api.deepseek.com | deepseek-flash | json_object |
| qwen | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus | json_object |
| custom | 操作者填写 | 操作者填写 | json_object |

预设的Base URL、模型和响应格式都可用非空环境变量覆盖。DeepSeek预设使用官方当前文档的deepseek-flash名称，并关闭thinking以控制行程预览延迟。千问预设使用文档仍支持的北京公共域名，也可填业务空间专属域名。Key与地域须匹配。核验日期为2026-11-06。[DeepSeek接入文档](https://api-docs.deepseek.com/)、[千问兼容文档](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope)

OpenAI预设使用支持结构化输出的GPT-4.1 mini。兼容服务若选择json_object，后端仍执行相同的严格结构和业务校验。模型访问权限与账户额度由操作者核对。[模型文档](https://developers.openai.com/api/docs/models/gpt-4.1-mini)、[结构化输出文档](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat)、[DeepSeek JSON文档](https://api-docs.deepseek.com/guides/json_mode/)

配置示例可以写入忽略的本地.env。启用服务前准备对应账户的Key。

```dotenv
AI_ENABLED=true
AI_PROVIDER=deepseek
AI_API_KEY=填入本地有效Key
AI_BASE_URL=
AI_MODEL=
AI_RESPONSE_FORMAT=auto
```

自定义兼容服务填写完整API根地址。程序在根地址后添加/chat/completions。

```dotenv
AI_ENABLED=true
AI_PROVIDER=custom
AI_BASE_URL=https://your-provider.example/v1
AI_MODEL=your-model-name
AI_API_KEY=填入本地有效Key
AI_RESPONSE_FORMAT=json_object
```

OpenAI预设使用max_completion_tokens，其他预设使用max_tokens。代理OpenAI服务时可选openai预设并覆盖Base URL和模型。当前兼容适配器采用非流式Chat Completions，最大输出默认6000 tokens，连接超时5秒，读取超时45秒。客户端一次生成只发起一次调用。上游错误会返回稳定错误码，页面保留当前编辑，用户可自行决定下一次请求。

AI_ENABLED默认false。配置不足时能力接口给出不可用原因，手动行程仍可操作。实际付费供应商调用由操作者配置并授权后联调。本轮自动化和浏览器验收使用本地stub，验收报告记录该边界。
