# 后端阶段提示词

遵循根目录 `AGENTS.md` 实施后端。

目标：建立 Spring Boot 后端，并按 API/数据库设计逐步实现。

顺序：
1. 检查现有代码和 SQL；
2. 确认 Java 21、Spring Boot 3.x、MyBatis-Plus、Sa-Token 兼容性；
3. 建立统一响应、异常、参数校验和配置；
4. 完成认证垂直切片；
5. 完成目的地；
6. 完成攻略；
7. 完成交互；
8. 完成搜索分页；
9. 完成行程；
10. 最后处理后台和 AI 选做。

约束：Controller 不承载复杂业务；DTO/VO 与 Entity 分离；写操作检查身份和资源所有权；关键写操作使用事务；点赞收藏使用唯一索引防重；数据库层分页；BCrypt；AI Key 只在服务端；上传文件校验；不添加不必要中间件。

DSH 可用于只读审查、独立测试或边界明确的模块，但 DSH Subagent 须使用 CodeBuddy DeepSeek V4.1 Flash / Max，Codex 主代理保持 GPT-6.1-Sol / Ultra（两者无需相同），且不得同时修改同一文件。

交付：API 列表、修改文件、数据库变更、权限规则、真实测试结果和遗留问题。
