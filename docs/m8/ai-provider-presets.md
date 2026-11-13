# AI 服务配置

管理员可在 `/admin/ai` 选择预设或自定义兼容端点，也可由环境变量提供启动默认值。网页保存的私有配置优先，密钥保存在服务端。下表对应当前 `AiProperties` 中的默认值。

| AI_PROVIDER | 默认 Base URL | 默认模型 | 默认响应格式 |
|---|---|---|---|
| openai | https://api.openai.com/v1 | gpt-4.1-mini | json_schema |
| deepseek | https://api.deepseek.com | deepseek-flash | json_object |
| qwen | https://dashscope.aliyuncs.com/compatible-mode/v1 | qwen-plus | json_object |
| custom | 操作者填写 | 操作者填写 | json_object |

预设地址、模型和响应格式可由非空环境变量或管理员设置覆盖。模型权限、地域和额度依操作者账号确定。兼容服务返回值继续由后端执行结构和业务校验。

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

AI_ENABLED 默认 false。配置不足时能力接口给出不可用原因，手动行程仍可操作。最近 M17 自动化和浏览器验收使用本地合成 HTTP 供应商，外部服务的生成质量和费用单独评价。
