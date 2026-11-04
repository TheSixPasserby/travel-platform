# M1 工程骨架与认证/资料设计

日期：2026-11-02。依据已确认的 AGENTS、需求和 API 第 11–12 节；用户明确要求继续执行，不重复请求模型、架构或常规实现选择的确认。不自动提交 Git commit。

## 目标与边界

让游客能注册，用户能登录、刷新恢复会话、查看并修改自己的资料、退出；数据库保存 BCrypt，错误与权限在后端生效。M1 是可运行的认证资料闭环，之后继续 M2；不以此代表全部毕业设计完成。

后端提供 POST /api/v1/auth/register、login、logout；GET /api/v1/auth/me 和 /api/v1/users/me；PATCH /api/v1/users/me。前端提供 /、/login、/register、/me。头像在 M1 显示/清空、校验既有受管图片路径，实际上传交互随 M2 图片功能完成。M1 不提供未实现功能的假接口或虚假攻略数据。

## 已定架构与取舍

沿用 Spring Boot 3.5 + Java 21 + MyBatis-Plus + Sa-Token + MySQL 8.4，以及 Vue 3 + Vite 7。不改为 Spring Security 全套认证或 JWT，不加 Redis。实际兼容补丁版本记录在 docs/15-dependency-baseline.md。

比较后选择服务端不透明 token 与 satoken 请求头，复用既定 API；不使用 Cookie 登录，减少跨域凭据配置。浏览器 token 只放 sessionStorage，Pinia 保存运行时用户状态；401 清除会话，其他网络失败保留 token 并给出重试，避免把临时故障误当退出。

Controller 处理请求与响应，UserService 负责注册、凭据验证、资料规则及事务；UserMapper 只访问 users。User 实体字段与现有表一一对应，不返回实体。CurrentUserVO 的 id 为 String，createdAt 为带 +08:00 的时间；分页和计数不会全局字符串化。

## 共享接口

- ApiResponse<T>(int code, String message, T data)，成功 code=0/message=success，data=null 也保留字段。
- ErrorCode 包含 API 已确定的 40001、40101、40102、40301、40401、40901、40902、41301、41501、50001；ApiException(ErrorCode) 和 ApiException(ErrorCode,String)；异常处理不回传堆栈、SQL、密码、token 或内部路径。
- CurrentUserVO(String id,String username,String nickname,String avatarUrl,String bio,String role,String status,OffsetDateTime createdAt)。createdAt 序列化为 ISO8601 毫秒与 +08:00。
- LoginVO(String tokenName,String tokenValue,CurrentUserVO user)，tokenName 固定 satoken。
- RegisterRequest(username,password,nickname)、LoginRequest(username,password)；ProfilePatchRequest 只允许 nickname/avatarUrl/bio，并区分字段缺失与显式 null。
- UserService.register(RegisterRequest)、login(LoginRequest)、current()、updateProfile(ProfilePatchRequest)、logout()。返回前四项分别为 CurrentUserVO、LoginVO、CurrentUserVO、CurrentUserVO；logout 无返回业务值。

## 业务规则

账号 strip 后转小写，再校验 [a-z0-9_]{3,50}，数据库唯一键处理并发冲突。密码不 trim，至少 8 个 Unicode 码点、UTF-8 最多 72 字节且非全空白，BCrypt strength=10。昵称 strip 后 1–50 字符；缺省注册昵称使用规范化账号。简介最多 300 字符。

注册返回 201，不自动登录；登录返回 200。未知账号、错误密码和不可用账号的登录提示统一。角色/状态/删除位由服务端赋值，所有请求拒绝未知或被忽略字段，夹带 role/userId/password_hash 等返回 400；字符串字段不接受数字或布尔值的隐式转换。

PATCH 至少包含一个允许字段：缺失保持原值；nickname=null 拒绝；bio=null、avatarUrl=null 清空。实现可用 @JsonSetter 记录字段存在标记，标记不参与 JSON 请求，Jackson 同时启用 fail-on-unknown-properties 与 fail-on-ignored-properties。禁止以 nullable record 丢失 PATCH 的缺失/null 区别。

受管图片校验只接受 /uploads/ 内存在的 JPEG/PNG/WebP 文件路径，resolve/normalize/realPath 均需限制在配置根目录；拒绝外链、穿越和不存在路径。上传本身属于 M2，不创建假的上传响应。

Sa-Token 单实例内存会话，超时 86400 秒，允许多会话且不共享 token，只读请求头，禁用从 Cookie/请求参数读 token。受保护接口每次重新确认用户 ACTIVE 且未删除；禁用后的旧 token 返回 401。退出仅使当前 token 失效。

统一拦截 /api/v1/**，仅放行注册/登录、OpenAPI/Swagger 资源和必要的 OPTIONS；M2 新公开接口由主代理同步加入明确白名单。M1 不实现管理员业务。CORS 默认仅 http://localhost:5173，不启用 credentials 或通配来源。

## 运行配置与数据安全

后端 8080，前端 5173，Vite 转发 /api；数据库继续使用项目 3307 实例。配置仅引用环境变量，spring.sql.init.mode=never，不在启动时重建表。test profile 使用 DB_TEST_NAME，不复用 DB_NAME。

scripts/backend.py 为本地入口，安全解析根 .env（不 eval/source），进程环境覆盖文件值，设置 Java 21 与绝对 UPLOAD_DIR，仅将凭据放子进程环境。test/package 必须同时提供 DB_NAME 与 DB_TEST_NAME，测试库名称以 _test 结尾且不得与开发库相同，两者都传入测试 JVM；test profile 无开发库回退。前端命令不加载后端 .env。脚本不打印环境值、不改全局 Java/Codex 配置。

集成测试还需在清理前确认实际 SELECT DATABASE() 等于独立 *_test 库，仅清理本测试生成的唯一前缀用户，不 TRUNCATE、不删库、不清理其他记录。

## 前端设计

现代旅游平台，使用米白 #F9F7F2、松绿 #145C45、浅绿 #DDE6D6、深灰 #25352C、暖橙 #C87545。中文采用系统中文字体，展示标题可配宋体；正文清晰。以原创 SVG 山水/路线插画承接旅行主题，避免依赖不明版权图片。

首页为品牌、旅行主题大标题和真实的注册/资料入口。登录/注册采用留白充足的表单与风景插画分栏；手机改为单列。个人资料为清晰表单，账号只读，昵称/简介可保存，头像有占位与加载失败处理。只展示已实现的导航，不在产品界面暴露技术栈、M1 等工程说明。

所有异步操作具有 loading/error/success；保护重复提交，支持键盘、label、焦点和减少动态效果。请求集中 src/api，业务类型集中 src/types，全局认证放 Pinia。登录后 redirect 只接受内部路径，拒绝 //、反斜杠、控制字符或外部 URL。

## 验证与交付门槛

先写可观察行为测试并确认失败，再实现；不为 getter、常量或配置文本编造测试。后端真实 MySQL HTTP 测试覆盖注册/冲突、BCrypt、登录失败一致性、401、退出、禁用、资料白名单、PATCH null、图片路径、ID 字符串和时间。测试数据均有独立前缀并清理。

前端单元测试覆盖认证状态与 401/网络失败，实际浏览器覆盖注册→登录→资料修改→刷新→退出；检查桌面与手机截图。运行 Maven 全套、前端测试、vue-tsc/build，核对 OpenAPI、错误包和类型，更新状态。没有证据不标记通过。

## 续审裁定与工作区

截停数据库审查已由 hub-8-mvz85moq 补完，没有 M1 结构阻塞。后续逻辑删除需查父资源，M5 改期保留双唯一键并用事务内可证明无冲突的有序更新；不采用固定 +1000 偏移。搜索先采用既定转义 LIKE 与过滤分页，只有实测性能需要时再评估全文索引，不因补审建议扩张架构。

Git 尚无初始 commit，用户禁止自动 commit，因此不为创建 worktree 人为提交基线。使用当前目录的专用 codex/m1-foundation 分支、任务文件所有权和快照 diff；不改动既有本地凭据/数据。前后端可按用户协作规范在不同 cwd 并行，不共享写文件。
