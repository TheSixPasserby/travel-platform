# M8 增量升级技术方案

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../../README.md)。

日期：2026-11-06。输入：[正式 M8 规范](../../m8/00-implementation-spec.md)、[Phase 0 实际审计](../../28-m8-phase0-audit.md)。这是待实施方案，不代表接口/字段已存在。

## 1. 目标与取舍

在现有模块化单体上完善三篇原攻略、六城及首批24–36景点，并把真实平台数据用于可编辑、可确认保存的AI行程。保留用户、草稿状态、首次发布时间、互动及私人行程。沿用 Java21/Spring Boot3.5、MyBatis-Plus、MySQL8.4、Vue3/TypeScript、受管图片与现有UI；不新增微服务、Redis、RAG或向量库。

选择复用 destinations 自关联、现有攻略与 itinerary 三表。独立景点表会重复目的地和状态规则，另建AI行程表会割裂所有权与手动编辑，因此不采用。先通过来源/备份/副本验证，再增量实施；AI服务保持可关闭。

## 2. 内容和素材

- 攻略4沿茶馆与街巷叙事写两日；攻略5围绕西湖一天的留白写；攻略6保留大理一日草稿视角。各约1500–2500中文字符，覆盖规范十项内容，不为补字数杜撰商店/交通班次/价格。
- 内容包记录 guideId、原内容SHA-256、原作者/状态/发布时间、完整原字段、Markdown、摘要、封面/正文图、原标签、资料来源和拟关联景点名称。保存前展示 Markdown 预览与逐 ID 变更；不调用 publish。
- 每篇目标1封面和5–8正文图；一图一条 author/sourceUrl/license/licenseUrl/retrievedAt/地点/说明/文件SHA-256。仅取明确开放许可或能证明授权的照片；无法获授权则报告缺口，不冒用官网照片。通过现有上传服务转换为真实 `/uploads/YYYY/MM/<uuid>.(jpg|png|webp)`，遵守5MiB/单边10000/2500万像素限制。
- 图片只在上传成功且路径校验通过后进入内容清单。失败不写攻略记录，已存在文件不清理；本次产生的未引用上传也需精确核查后处理。
- 原攻略PUT是完整替换，必须读取并保留未计划变更的字段/标签；补内容时的系统updated_at是预期变化。受保护字段和互动关系逐行比较。新增景点尚未创建时只按城市+规范名称记录逻辑引用，Phase2映射真实ID后补关联。
- 每次维护写入都重新读当前记录，形成新的预览与基线；在写事务取得攻略锁后复核内容指纹、updated_at、作者、状态和发布时间，漂移则回滚。不只在Phase1做一次检查：Phase2补关联同样执行，且只更改关系，不重写旧内容包里的正文。维护工具使用参数化写入与列白名单，不用无并发保护的完整PUT覆盖新编辑。

## 3. 数据模型与迁移

所有新增字段先可空，旧记录不回填猜测值。ID为signed BIGINT，时间DATETIME(3)，图片仅站内路径。迁移使用独立 `database/migrations/` SQL及元数据预检脚本，不把schema.sql重跑当迁移；无需新增大型迁移框架。执行清单记录文件SHA-256、每步前置/后置条件和环境。重复运行先核对现有定义，匹配则跳过，不匹配则拒绝。

### destinations 增量字段

| 列 | 类型 / 业务限制 |
|---|---|
| address | VARCHAR(300)，可空 |
| longitude / latitude | DECIMAL(9,6) / DECIMAL(8,6)，可空；成对提供，分别[-180,180]/[-90,90]，API用十进制字符串避免静默截断 |
| recommended_duration_minutes | SMALLINT UNSIGNED，可空，1–1440分钟 |
| recommended_days | SMALLINT UNSIGNED，可空，城市建议1–30天 |
| opening_hours / ticket_info | VARCHAR(1000)，可空，纯说明，不承诺实时营业/票价 |
| official_url / source_url | VARCHAR(1000)，可空，仅https/http，拒凭据/控制字符；作为外链展示，不自动抓取任意URL |
| verified_at | DATETIME(3)，资料核验时间；动态说明必须附来源/核验，缺信息显示未核实 |

字段级来源与核验记录还保留在版本化资料清单，不用单个source_url掩盖多来源差异。坐标无法核验就同时为空，不猜坐标，不在地图上自动混用坐标体系；首版只展示核实的值与来源。

### 关系表

- `destination_images`：id、destination_id、image_url、alt_text、sort_order、source_url、author、license、license_url、retrieved_at、created_at、updated_at；唯一(destination_id,image_url)，索引(destination_id,sort_order,id)。图片1–20张，顺序由数组产生，来源/许可长度上限在DTO固定。外键RESTRICT，不级联删除目的地历史资料。
- `guide_destinations`：id、guide_id、destination_id、created_at；唯一(guide_id,destination_id)，反向索引(destination_id,guide_id)，两侧外键RESTRICT。主目的地仍用guides.destination_id，新增关系只承载具体景点，不重复保存主城市。
- `itineraries.client_request_id`：CHAR(36)可空，唯一(user_id,client_request_id)。仅整程导入使用，原手动创建可空；避免保存请求重发创建两份行程。不存在AI专用行程表。

CITY无父级；SCENIC_SPOT父必须是未删CITY，不能挂SCENIC_SPOT；OTHER保留原语义和防环规则。新建/切换景点要求父CITY为ACTIVE；原停用历史引用保留。被景点引用的城市不得直接改成SCENIC_SPOT/OTHER造成非法父级，返回409。扫描旧异常并逐条报告，不自动重挂未知数据。沿用当前全图锁和统一锁序以保持正确性，首批几十条无需并发架构重写。

仅新增字段/表及受控数据。MySQL DDL隐式提交，失败记录到具体步骤并支持核验后续跑；不声称DDL整体事务回滚。回退优先切回兼容旧代码并保留新增结构/资料，恢复数据仅从已验证的副本按审查方案执行，不自动DROP新列/表。新空库schema与增量迁移结果须一致。

## 4. API 目标契约

前缀 `/api/v1`，整数code/合适HTTP状态、字符串ID、原分页结构、+08:00时间；未知字段与隐式类型转换继续拒绝。共享DTO/VO、frontend/src/types、API文档由主代理统一维护，每个模块实施前冻结精确字段和上限并同步契约测试。

| 入口 | 目标行为 |
|---|---|
| GET /destinations | 复用page/size/keyword/destinationType/parentId；城市列表请求CITY，景点列表请求SCENIC_SPOT+parentId |
| GET /destinations/{id} | 扩展DestinationVO附可空资料与有序images；历史INACTIVE仍可读并明确提示；相关统计为实际查询 |
| GET /destinations/{id}/guides | 新分页入口；城市含主目的地为本市或关联本市景点的公开攻略，景点仅关联自身；去重，只PUBLISHED未删，支持keyword/tagId |
| POST/PUT /admin/destinations[/{id}] | 在原鉴权/完整替换规则上增加资料/图库白名单及父类型校验；图库单事务保存 |
| PATCH /admin/destinations/{id}/status | 原入口保留，停用不删攻略/行程关系 |
| POST/PUT /guides[/{id}] | 增加可选scenicSpotIds（≤20个真实、不重复、同主城市SCENIC_SPOT）；旧请求省略时保留原关系，显式[]清空；响应增加scenicSpots |
| GET /ai/itineraries/capabilities | 登录后返回enabled/available/reason/maxDays，不返回Key/BaseURL内部细节；无Key或禁用时明确不可用 |
| POST /ai/itineraries/preview | 只读检索+模型调用+校验；返回结构化预览，不创建任何行程 |
| POST /ai/itineraries/day-preview | 同一需求、targetDate、其他天已用景点ID；只返回目标日；不得改写其他天 |
| POST /itineraries/import | 明确保存才调用；登录用户新建整程，单事务复用原根/日/项目规则；不接受已有行程ID，不覆盖已保存行程 |

新增可选攻略关联字段必须区分“未提供”和“提供空数组”，避免旧客户端清空新关系。旧攻略主目的地非CITY时保持可读，只有新选具体景点时才要求主目的地CITY。停用景点原关系可以保留，新增/替换必须可用；AI保存则所有景点与城市重新检查ACTIVE。

导入请求：`clientRequestId`（UUID）、title、destinationId、startDate、endDate、notes、days[{travelDate,title,notes,items[{kind,destinationId,placeName,addressText,startTime,endTime,notes}]}]。kind必填SCENIC_SPOT或FREE_TIME：前者destinationId必填、名称/地址以后端数据库为准；后者destinationId必须null、placeName由后端规范为“自由活动”。外部不传父ID/dayNo/sortOrder/userId。通用导入范围沿用1–366天、每天最多100项、整程最多500项；AI生成另受1–14天/每天8项/整程60项限制。首次成功201返回原ItineraryDetailVO；同用户同key重复返回已有行程200，不再插入，已删除目标返回409且不复活，前端生成新编辑版本时换key。事务内创建根/日/项目，任一步失败全部回滚；共享服务抽取最少的规则，不依赖自调用@Transactional生效。

预算在预览中结构化显示，保存时把明确标注的“估算/币种/人数/口径/不确定性”摘要写入现有root.notes，单项估算写入item.notes，保存前展示这份最终文本并执行2000码点上限；超长要求压缩，绝不静默丢弃用户备注。首版不增加无明确需求的预算报表字段。

## 5. AI 检索与校验

```mermaid
flowchart LR
  A[登录用户填写需求] --> B[MySQL只读检索城市/景点/公开攻略]
  B --> C[长度受控上下文与JSON Schema]
  C --> D[后端兼容API调用]
  D --> E[严格解析及业务校验/重查ID]
  E --> F[前端每日预览与编辑]
  F --> G[明确保存]
  G --> H[原行程三表单事务导入]
```

输入：cityId、startDate/endDate（二者推导天数）、travelers（1–20）、budgetAmount（非负两位小数）、budgetBasis（PER_PERSON/TOTAL）、currency固定CNY、interests（白名单≤8）、pace（RELAXED/BALANCED/COMPACT）、transport（PUBLIC/WALK/TAXI/MIXED）、preferredIds/mustVisitIds/avoidIds（各≤20个真实本城ID）。想去与必须去可以重叠并去重，但都不能与避免集合相交；必须去为硬约束，想去为软偏好，未安排的想去景点在warnings说明。首版AI范围1–14天，手动行程保留1–366天。需求文本不接受无限自由提示词。

候选最多40个本城ACTIVE景点，先确保包含全部想去/必须去的合法ID，再补其他候选；已停用父城市直接拒绝；已发布攻略最多5篇，各最多1600字符，描述/标签总上下文最多24000字符。数据不足（零景点/缺城市）直接422，不调用模型；偏好ID无效、候选容量无法满足或与避免集合相交则400。检索和最后重查为短只读事务，**模型等待期间不持有数据库事务或行锁**。

模型只输出 `{title,summary,days:[{dayNo,title,items:[{kind,destinationId,startTime,endTime,notes,estimatedCost}]}],budgetEstimate:{currency,perPerson,notes},warnings}`，不输出用户ID、SQL、保存指令、任意名称或链接。kind规则与导入一致。根城市由请求固定，travelDate由startDate+dayNo计算；后端生成最终预览中的名称、地址、图片与来源。自由活动用FREE_TIME/destinationId=null、受限notes表达，不伪装景点。

后端拒绝未知字段、超长输出、非字符串ID、非法/跨城/停用ID、遗漏/重复日、重复景点（整程）、错误时间/重叠时间、负数/超额精度成本和非法币种；规范化后重新查询ID及父城，名称永远以数据库为准。每日最多8项，整程最多60项。mustVisit必须覆盖、avoid不得出现；不能以用户改过预览为理由跳过导入校验。

用户输入、攻略、景点描述均作为不可信资料分区加入上下文，不能修改系统规则；不发送密码/令牌/未知用户信息。模型无工具或数据库连接，不向其发送Key。仍需业务校验，JSON合规不代表旅行事实真实。官方参考：[OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat)区分JSON模式和Schema约束；第三方兼容端点的strict支持未验证，不能假定支持。优先配置json_schema，若运营者选择json_object模式仍用同一后端严格校验，不在失败后自动再发付费请求。

实现`AiModelClient`接口；生产适配器采用现有Spring Web提供的RestClient（当前无外呼客户端封装），不新增SDK。后端环境配置：AI_ENABLED（默认false）、AI_BASE_URL、AI_API_KEY、AI_MODEL、AI_CONNECT_TIMEOUT_SECONDS（5）、AI_TIMEOUT_SECONDS（45）、AI_MAX_OUTPUT_TOKENS（6000）、AI_RESPONSE_FORMAT（json_schema/json_object）。URL仅运营者配置；禁用HTTP wire/body/Authorization日志。不自动回退到其他供应商或模型，不无条件重试。

单体内每用户60秒最多2次、同用户最多1个生成、全局最多2个并发，使用有界且可过期的内存状态；无需Redis。区分配置不可用503/50310、资料不足422/42210、本地限流429/42910、上游429/42911、超时504/50410、上游失败502/50210、非法输出502/50211；客户端不接收上游原始响应/内部URL。CI仅stub，不产生真实API费用。

## 6. 页面与状态

- 复用DestinationsView/DestinationDetailView和同一详情路由，按类型展示城市/景点。CITY展示下属景点分页、推荐天数、真实攻略统计与标签筛选；SCENIC_SPOT展示图库/父城/资料/核验时间/相关攻略。
- 加入行程入口只能选择本人行程并由用户确认项目，复用现有项目接口，未登录回登录页；无可用行程时先创建。INACTIVE不可新增。
- 后台复用AdminDestinationsView，父选择仅CITY并支持搜索/分页；保留原停用父引用；图库管理/排序使用现有ImageUpload。经纬度为空显示未核实，不用0代替。
- AI页新增`/itineraries/ai`，路由守卫要求登录，导航不替换手动入口。表单→结构化时间线→编辑/增删候选景点/调整顺序→保存→原行程详情。loading/empty/error/success和手机布局齐全。
- 单日生成响应只能合并目标日，其他天的用户编辑不变；用需求版本+日编辑版本拒绝迟到响应。失败保留预览和输入，表单换城市/日期后明确作废旧预览；不跨账号保存浏览器草稿。费用请求不自动重试。
- AI关闭、没Key或调用失败只影响AI页，手动行程继续可用。保存期间按钮禁用并使用导入key防重复；刷新后通过原行程API读取持久结果。

## 7. 放行标准

每阶段代码/来源/diff审查、相关测试、契约同步、开发库不变量、状态记录齐全后再推进。Phase1先预览和保护性备份；Phase2必须证明副本迁移/重复执行/恢复及旧数据兼容；Phase4须stub完成预览零写入、恶意输出/费用错误降级、单日不覆盖与导入原子回滚；Phase5执行完整回归、真实浏览器/手机和权限。真实供应商调用只有取得费用授权后执行，未配置不冒充AI成功。
