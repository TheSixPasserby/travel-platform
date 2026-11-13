# M2 目的地与图片设计草案

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../../README.md)。

日期：2026-11-02。本文供 M1 验收期间准备下一阶段，M1 完整测试/审查未通过前不开始 M2 生产代码。Codex 决策、DSH 指定 Flash/Max 执行；不自动 commit、不重建数据库。

## 范围与既有约束

实现公开目的地列表/详情、管理员目的地创建/修改/状态维护、本地图片上传/安全读取、个人资料头像上传。复用既有 destinations 表、统一错误包、字符串 ID 和 M1 登录态。目的地仍为 P0，基础管理员用户/攻略治理留后续阶段，不加入复杂权限系统。

目的地公开列表仅 ACTIVE 且未删除，INACTIVE 的未删除详情仍 200 携带状态。停用保留历史关系。管理员创建/修改须实时检查 ACTIVE ADMIN；普通用户 403、匿名 401。只有 USER/ADMIN 两角色。

## 待前置审查的契约补充

列表请求 page/size/keyword 沿用通用规则，支持 destinationType（CITY/SCENIC_SPOT/OTHER）与 parentId 筛选；管理员列表另支持 status。公开传 status 等未定义查询不用于扩大可见性。排序固定 sort_order 升序、id 升序，目的地不复用攻略 latest/popular 的排序含义。

DestinationVO 字段：id、name、destinationType、parentId、country、province、city、description、coverImageUrl、recommendedSeasons、status、sortOrder、createdAt、updatedAt。ID 为字符串或 null；sortOrder 数字；时间含 +08:00 毫秒；可空值保留。

创建 POST 返回 201，更新 PUT 返回 200；PUT 是完整表单替换。字段白名单为 name/destinationType/parentId/country/province/city/description/coverImageUrl/recommendedSeasons/status/sortOrder。name strip 后 1–120 码点；country 缺省中国，显式值 1–80；province/city 各最多80；recommendedSeasons 最多120；description 最多10000；sortOrder 非负整数；status/type 限枚举。可空字段可为 null，不接受 authorId/isDeleted 等内部字段。默认 CITY/ACTIVE/0。coverImageUrl 复用实际存在的受管路径校验。

parentId 必须存在且未删除，不允许自己/后代；新增或改变父引用要求 ACTIVE，未改变的历史 INACTIVE 引用保留。为防并发互设父节点形成环，采用低频管理员写事务内有序锁定现有目的地图后校验/写入的简单策略，保留 FK 与唯一键，不删除历史数据。前置审查需确认最小锁范围和空表创建行为，不以无锁递归检查声称解决并发。

## 上传与读取

POST /api/v1/files/images，multipart 字段 file，登录用户，201，data={url:'/uploads/...'}。仅 JPEG/PNG/WebP，文件大小1字节至5MiB；扩展名、声明MIME和实际解码格式必须一致；拒绝SVG与伪装文件。实际解码前检查尺寸（单边≤10000，总像素≤2500万），超尺寸40001，超字节41301，不支持/无法解码41501。只接收一个文件，不建立业务表记录。

JDK ImageIO 处理 JPEG/PNG；WebP 仅引入 TwelveMonkeys imageio-webp 读插件，准确版本在实施前查官方/Maven元数据锁定。不引入本机命令行转码或原生服务。[官方 README](https://github.com/haraldk/TwelveMonkeys) 说明插件经 ImageIO 服务发现接入；只加载所需格式依赖。

服务端生成随机名称（按年月目录），不把原始文件名拼接磁盘路径。验证完全成功后在受管根写临时文件并原子发布；失败只清自己的临时文件。上传失败不创建攻略、图片关系或用户更新。返回/日志不含绝对路径、原始文件名或凭据。

GET /uploads/** 仅读取实际存在的受管 JPEG/PNG/WebP；复用 normalize/realPath 防穿越/符号链接越界，设置准确Content-Type和nosniff。不要用能绕过真实路径检查的任意目录静态映射；.env、.gitkeep、SVG和文本不可读取。Vite 增加 /uploads 同源代理。路径无法读取对外404，不回显磁盘路径。

## 前端与管理员准备

公开增加 /destinations 与 /destinations/:id，真实服务端分页、筛选、空/错/加载状态，封面占位与加载失败回退；沿用米白松绿视觉。停用详情显示无法用于新内容的状态。新增简单 /admin/destinations 表格/表单与上传封面组件，路由与入口按ADMIN改善体验，后端再次验证。个人资料复用上传组件，成功得到路径后才提交PATCH，上传失败保持原头像和资料。

不创建默认明文管理员，不在公开注册接收role。可由操作者先注册自己的账号，再通过受限本地SQL精确提升该账号，必须先核对账号/唯一ID；演示账号凭据仅由主代理生成到权限600的本地忽略文件，不写文档或聊天。

## 验证门槛与交付

先补共享API契约并做独立前置审查，再写失败的真实HTTP测试，后实现。覆盖ACTIVE公开筛选/INACTIVE详情、数据库分页和稳定排序、LIKE转义、字符串ID、匿名401/普通403/管理员CRUD、唯一冲突、父级环与并发、受管封面、JPEG/PNG/WebP实际上传/访问、5MiB边界/伪装/穿越/符号链接/匿名/失败无业务记录。测试仅用唯一前缀目的地和用户、私有临时上传目录，验证独立测试库后清理自己的夹具。

主代理执行构建、全部相关测试和浏览器：管理员创建目的地+封面→公开浏览→停用→列表消失但历史详情保留；用户上传头像失败保持原资料、成功更新并刷新保持。独立DSH复核后更新PROJECT_STATUS，再进入攻略模块。未执行项明确保留未执行。

## 前置审查裁定补充（尚未放行实施）

hub-23-mvzeqmxx 有条件通过。接受补齐 DTO、状态端点、唯一键语义和并发/上传验证的要求；不采纳未经依据的“PUT 不得含 status”限制，也不把空表不存在的 parentId 当作可合法互设父级的情形。既有 PATCH /admin/destinations/{id}/status 保留，body 仅含必填非null枚举 status，重复目标状态幂等；PUT 仍可修改 status，两入口使用同一验证与事务策略。

| 字段 | POST | PUT | 规则 |
|---|---|---|---|
| name | 必填 | 必填 | strip后1–120码点 |
| destinationType | 缺省CITY | 必填 | 枚举，null拒绝 |
| country | 缺省中国 | 必填 | strip后1–80码点，null拒绝 |
| status | 缺省ACTIVE | 必填 | ACTIVE/INACTIVE，null拒绝 |
| sortOrder | 缺省0 | 必填 | 非负32位整数，null拒绝 |
| parentId | 缺省null | 缺省null | 正Long范围十进制字符串或null；改变父级要求ACTIVE |
| province/city | 缺省null | 缺省null | 各最多80码点 |
| description | 缺省null | 缺省null | 最多10000码点 |
| coverImageUrl | 缺省null | 缺省null | 实际受管图片路径、最多500字符 |
| recommendedSeasons | 缺省null | 缺省null | 最多120码点 |

PUT省略的可空字段清空；创建缺省值仅用于缺失，不把显式null默认为合法值。未知字段与字符串标量强转拒绝。被逻辑删除的同名同类型目的地仍占唯一键，不修改索引以复用名称。无效/停用新父引用与环返回40001；并发写冲突返回40902并提示重试；唯一键冲突40901；目标不存在40401。

目的地写操作统一使用显式REPEATABLE_READ事务，先按主键顺序对全表（含逻辑删除行）执行FOR UPDATE当前读，再基于锁定图检查/写入；不使用普通快照读。空表首次根节点创建遇并发间隙锁冲突时允许一个事务回滚并返回40902，保持完整事务和已有约束。验证至少包括空表的独立临时测试环境（不清空现有库；若不具备则标记未执行）及两个已有节点并发互设父级：最终必须无环。实际锁范围/死锁错误映射在实施前以MySQL官方资料和可复现测试确认；不使用难以与连接池生命周期绑定的GET_LOCK方案。

multipart明确max-file-size=5MB、max-request-size=6MB，现有MaxUploadSizeExceededException映射41301；头部尺寸检查通过后才实际解码。M1提供ManagedImagePaths校验，M2按文件所有权扩展为可供读取复用的受管路径解析。随机ASCII文件名使图片GET可拒绝残余编码、点段与非白名单路径，无需支持任意用户文件名。孤儿图片在第一版不自动回收，失败只删除本次临时文件，避免误删已有文件。

锁语义依据：[MySQL 8.4 locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) 说明FOR UPDATE是持锁的当前读。主代理已在既有独立测试库用两条随机前缀夹具、两个连接实测：第二写事务在全图FOR UPDATE处阻塞，第一个事务提交父级更新后，第二个当前读看到新父级；夹具全部清理、无DDL。见[设计实验](../../verification/2026-11-02-m2-lock-design.json)。这仅验证锁/当前读语义，不冒充尚未实现的目的地接口并发验收。

分页采用官方[PaginationInnerInterceptor](https://baomidou.com/en/plugins/pagination/)路径，M2需加入与当前MyBatis-Plus同版本的mybatis-plus-jsqlparser（3.5.9起独立模块）；overflow=false，输入层先拒绝size>50，不静默截断。LIKE使用固定SQL的参数占位与显式转义，不把用户输入拼接进apply SQL。
