# M2 目的地与图片实施计划（待最终前置复核）

> 执行方式：subagent-driven-development。Codex GPT-6.1-Sol/Ultra决策、整合与真实环境验收，DSH buddy/deepseek-v4.1-flash/max按小任务执行。不自动commit。M1未完整验收前禁止开始本计划的生产实现。

**目标：** 用户能真实浏览目的地和上传头像，管理员能维护目的地及封面；后端权限、图片安全和数据库分页可验证。

**依据：** [设计草案与审查裁定](../specs/2026-11-02-m2-destinations-images-design.md)、API第5/11/14/15节、既有数据库。当前草案的并发策略仍需官方资料与可复现实验确认，再放行实施。

## 通用边界

- 工作区原目录，保留3306和3307数据，不清库、不自动执行DDL；测试只清自己唯一前缀夹具与私有临时目录。
- 任务明确文件所有权；不同代理不得共享源文件写权限。backend构建目标目录共享，主代理串行安排最终构建，避免并发构建误判。
- DSH不读.env/.local/全局配置、不连接真实数据库，围栏拒绝即上报，禁止换工具绕过。每任务直接聊天交付，由主代理归档，不跨cwd写报告，不调用compress工具。
- 只在任务必须读的文档/源文件范围内工作。遇max-tokens/工具停滞保留源码，从缺口续接，不重做已验证工程。
- 共享契约由Codex固定：字符串ID，PageResponse(records,total,current,size,pages)数字页元数据，query page/size默认1/10且size≤50，keyword≤100并字面转义LIKE，HTTP/业务码按API。

## A：契约与前置放行（Codex）

1. M1真实HTTP、package、浏览器和独立认证审查全部通过，记录实际证据。
2. 将设计字段表、PATCH状态、目的地排序/筛选、上传响应201/图片读取错误与尺寸限制补入API。
3. 查官方MyBatis-Plus分页插件安装方式及TwelveMonkeys WebP的兼容版本，固定依赖并记录；不加入无关图像格式插件。
4. 验证目的地事务锁方案（显式RR、全图FOR UPDATE当前读、并发死锁回滚映射40902），修正设计后做独立DSH定点复核。
5. 图片配置的唯一所有者为Codex：在图片有效上传RED确认后、E实现期间修改backend/src/main/resources/application.yml，显式max-file-size=5MB、max-request-size=6MB；最终B的精确5MiB有效文件201、超5MiB及超6MiB请求413/41301都须通过，不以默认1MB误挡所有大图冒充边界正确。

## B：先写HTTP验收（DSH，测试文件专属）

**所有权：** backend/src/test/java/com/example/travelplatform/destination/DestinationApiTest.java 与 file/ImageApiTest.java分属各自测试任务；必要的test-support/HttpTestSupport.java由Codex单独创建维护，必须先建好再交两个任务只读使用，DSH不得写它。不得写生产/旧AuthApiTest。两个测试写任务仅在各自文件范围内并行，不一次产生巨型测试文件。

- 原始JDK HttpClient走RANDOM_PORT、test profile、JdbcTemplate核验；不mock Service/Mapper。
- 清理前必验DB_NAME/DB_TEST_NAME及实际数据库；账号/目的地唯一随机前缀，先删自己目的地关联再删自己用户，断言非本夹具原数据未变。
- 管理员测试仅提升自己夹具账号，不创建默认管理员；普通/匿名边界分别403/401。
- 目的地测试：创建201、安全VO/ID/时间；公开ACTIVE列表和INACTIVE详情；分页/空/超末页、keyword转义/枚举/非法页；唯一名占用；完整PUT与幂等PATCH；受管封面；自指/后代/停用新父引用；两个已有节点并发互设父后最终无环。
- 图片测试：临时上传根，真实JPEG/PNG/WebP字节（WebP采用小型合法自制/有许可证fixture并说明来源）；类型/MIME/内容一致，精确5MiB有效文件201、超5MiB文件413/41301、超6MiB总multipart请求413/41301；空/伪装/损坏/超像素/多个file/匿名；成功URL可读、Content-Type/nosniff正确；路径/编码/符号链接/非图片返回404。失败文件和业务表不新增。先运行红灯套件并如实记录哪些断言失败，已有正确的超限负例可能已绿，禁止补造每条都红的证据。
- 每类完成即返回，不自行读凭据跑真实DB。主代理先选择一个有效创建/上传用例确认404红灯，再放行对应生产实现。

## C：目的地数据与查询基础（DSH，小批次）

**所有权：** 新common/api/PageResponse.java、common/validation/RequestValues.java、destination/entity/Destination.java、mapper/DestinationMapper.java、dto/请求类、vo/DestinationVO.java及纯规则测试。POM/全局配置仅Codex协调。

- 按固定字段表实现；名称码点/枚举/null/数值严格输入，不全局Long字符串化。
- 分页在数据库执行，顺序sort_order/id；LIKE转义值参数化，不能拼接用户SQL。
- Mapper可有常量SQL的有序全图FOR UPDATE读取，不删除数据库约束。
- 先纯规则失败测试后实现；主代理检查diff和单元绿灯后才进入Service批次。

## D：目的地业务与入口（DSH，小批次）

**所有权：** destination/service/DestinationService.java、controller/DestinationController.java、controller/AdminDestinationController.java、security/AdminAccess.java。AuthWebConfig公开GET白名单由Codex统一改。

- 公开读取与管理员写分界；每次管理员入口取当前真实用户并检查ADMIN，不相信前端角色。
- 创建/替换/状态事务与设计一致；父图校验、唯一冲突和并发冲突回滚，不破坏历史关联。
- PUT出现nullable字段真正清空，禁止MyBatis默认忽略null导致契约失效。
- 主代理运行真实DestinationApiTest，检查夹具清理和原数据保留；独立DSH审查权限与锁策略。

## E：本地图片（DSH，小批次）

**所有权：** file/ImageStorageService.java、ImageController.java、ImageReadController.java、ManagedImagePaths.java扩展及对应纯文件单元测试。POM/resources/前端proxy由Codex协调，不与D任务共享写文件。

- 尺寸头部检查再实际解码，类型三方一致与5MiB限制；随机名字、不信任原始文件名，先验证再原子发布，仅清自己的临时文件。
- 安全读取复用真实受管路径，准确媒体类型/nosniff；禁止任意目录公开映射；失败无业务写入，不自动回收旧上传。
- 主代理跑真实ImageApiTest及M1头像路径回归；独立DSH审查路径/解码/失败边界。

## F：目的地与上传前端（DSH，独占frontend）

**所有权：** 新types/destination.ts、api/destinations.ts、api/files.ts、components/ImageUpload.vue、DestinationCard.vue、views/DestinationsView.vue、DestinationDetailView.vue、AdminDestinationsView.vue；受协调的ProfileView/Router/Header/Vite proxy和相关tests。

- 沿用既有风格与真实API；服务端页码/筛选，loading/empty/error/retry；图片占位与失败回退。
- 头像上传成功后才PATCH，失败保持当前资料；管理员封面上传表单清晰，不把密码/工程信息放进界面。
- 新路由ADMIN守卫仅改善体验，后端再校验；不接入未实现攻略导航或伪造统计。
- 先行为测试RED再实现，完整frontend test/build绿；主代理复验diff/类型/契约。

## G：验收与进入攻略（Codex）

串行backend package（含完整HTTP与单元测试）、frontend test/build、相关launcher回归；实际浏览器管理员建目的地/封面、公开分页/详情/停用历史、头像失败保持和成功刷新。检查原服务仍保留、无凭据跟踪。完成独立定点复核、更新PROJECT_STATUS及M2测试报告后继续M3攻略，不重复询问是否继续。

## 前置复核闭环

hub-26-mvzfr3vq给出有条件通过，两项must-fix已由Codex定点修订：共享测试helper唯一归Codex且先建后只读；multipart配置唯一归Codex并在A5/B中明确有效5MiB、超5MiB、超6MiB总请求门槛。负例在旧配置下可能已经绿，如实记录，不能以“所有新测试都红”为条件伪造证据。M1认证最终审查仍是执行前置门槛。
