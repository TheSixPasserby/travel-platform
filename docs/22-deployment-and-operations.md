# 部署、备份与运维

交付形态为 Java 21 可执行 JAR、Vue 生产静态文件、MySQL 和受管图片目录。当前维护 Web 与后端，手机通过浏览器访问。

## 环境和配置

构建需要 Java 21、Node.js 24、Python 3 和 Maven/Wrapper。运行已构建 JAR 需要 Java 21、MySQL 及持久化目录。`backend.py` 读取根 `.env`，直接 `java -jar` 则由服务管理器提供环境变量。

| 配置 | 用途 |
|---|---|
| `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD` | 应用数据库连接 |
| `DB_TEST_NAME` | 独立测试库，名称以 `_test` 结尾且与业务库不同 |
| `SERVER_ADDRESS/SERVER_PORT` | 后端监听地址和端口 |
| `CORS_ALLOWED_ORIGINS` | 允许的准确 Web origin，含协议及端口 |
| `PUBLIC_BASE_URL` | 分享、canonical、OG 和 sitemap 使用的 Web 公共 origin |
| `UPLOAD_DIR` | 直接运行 JAR 时的受管图片目录 |
| `THUMBNAIL_DIR` | 独立缩略图缓存目录 |
| `AI_SETTINGS_FILE` | 服务账号可读写的私有 AI 配置 |
| `TRAVEL_API_TARGET` | Vite 开发或预览代理的后端地址 |

`backend.py` 将上传目录设为项目根 `uploads`。正式服务可通过直接运行 JAR 指定持久化绝对路径。原图和 AI 私有配置随业务一起备份，缩略图缓存可以重新生成。

## 初始化与升级

新安装由数据库操作者建立明确的业务空库和测试空库，再分别执行当前结构和示例数据。下面的名称需要替换为实际目标，密码交互输入。

```bash
mysql --host=127.0.0.1 --port=3307 --user=YOUR_OPERATOR --password --database=YOUR_EMPTY_DB < database/schema.sql
mysql --host=127.0.0.1 --port=3307 --user=YOUR_OPERATOR --password --database=YOUR_EMPTY_DB < database/seed.sql
```

当前空库为 18 表、151 列、31 外键。已具备 M15 结构的库升级到当前版本时，先完成备份及恢复验证，再对所选目标依次执行 V016_01、V017_01。更早版本的差异见 [数据库说明](../database/README.md)。MySQL DDL 隐式提交，操作回执应记录目标、SQL 哈希和执行结果。

```bash
mysql --host=127.0.0.1 --port=3307 --user=YOUR_OPERATOR --password --database=YOUR_PROJECT_DB < database/migrations/V016_01__guide_trip_imports.sql
mysql --host=127.0.0.1 --port=3307 --user=YOUR_OPERATOR --password --database=YOUR_PROJECT_DB < database/migrations/V017_01__community_threads_notifications.sql
```

迁移由操作者执行，服务启动没有自动建表或导入步骤。M17 中的结构验证使用隔离副本，维护机器原业务库尚未应用这两项迁移。

## 构建、运行和交付

```bash
npm --prefix frontend ci
python3 scripts/backend.py package
npm --prefix frontend test
npm --prefix frontend run build
python3 -m unittest discover -s scripts/tests
python3 scripts/backend.py jar
```

`jar` 选择 `backend/target` 下唯一普通 JAR，遇到缺失、多份或符号链接会拒绝。前端生产资源位于 `frontend/dist`。

```bash
python3 scripts/release.py .local/releases/travel-platform-delivery
```

目标目录须尚不存在。脚本先构建后端和 Web，再复制白名单源码、数据库脚本、文档、锁文件及 JAR/dist。存在 M17 论文成果时一并收录五项已知文件，生成 SHA256SUMS。真实上传和本机私有配置由环境操作者单独迁移。校验可在输出目录运行 `shasum -a 256 -c SHA256SUMS` 或 `sha256sum -c SHA256SUMS`。

## 反向代理

示例为 [nginx.conf.example](../deploy/nginx.conf.example)，需要配置实际静态目录、监听地址和后端端口。API、图片、分享、robots 及 sitemap 交由后端，SPA 路由回退到 index.html。`PUBLIC_BASE_URL` 应与实际公开 Web 地址一致。

示例只提供回环 HTTP 站点。目标环境配置 HTTPS 和域名后执行 `nginx -t`，再按服务管理方式加载配置。AI 生成的上游等待可能长于普通查询，代理读取时间须覆盖配置的生成等待。当前仓库示例未在本机 Nginx 实例执行配置加载。

## 管理员首次配置

通过正常页面注册自己的账号，记录其真实 ID 和完整用户名。数据库操作者在明确的项目库内核对该账号为 USER、ACTIVE 且未删除后，将其提升为 ADMIN。以下 SQL 为分步操作模板，占位值需替换。

```sql
START TRANSACTION;
SELECT id FROM users WHERE role='ADMIN' ORDER BY id FOR UPDATE;
SELECT id, username, role, status, is_deleted FROM users
 WHERE id=YOUR_REGISTERED_ID AND username='YOUR_EXACT_USERNAME' FOR UPDATE;
UPDATE users SET role='ADMIN'
 WHERE id=YOUR_REGISTERED_ID AND username='YOUR_EXACT_USERNAME'
 AND role='USER' AND status='ACTIVE' AND is_deleted=0;
SELECT ROW_COUNT();
-- 确认仅修改一行后 COMMIT，核对失败则 ROLLBACK。
COMMIT;
```

现有管理员通过后台治理账号状态。后台保护当前管理员及最后一名有效管理员，行程权限仍按所有者判断。

## AI 服务设置

管理员在 `/admin/ai` 配置供应商、模型、API 根地址、密钥和超时。网页保存值优先于环境默认值，保存在 `AI_SETTINGS_FILE`，后续请求立即使用新配置。

密钥输入留空沿用原值，变更 API 地址需填写对应密钥，清除密钥同时关闭服务。版本冲突时重新加载后保存。私有文件损坏时 AI 关闭，管理员页面给出恢复提示。该文件由服务账号管理，POSIX 权限为 0600。供应商预设是可编辑的初始值，实际可用模型依运营者账户确定。

## 备份、恢复与回退

在维护窗口暂停本项目写入，备份专用数据库、原图与私有配置。SQL 含私人内容和密码哈希，使用私有目录和权限控制。下面命令由操作者提供安全客户端配置、明确库名及新的输出文件路径。

```bash
umask 077
set -C
mysqldump --defaults-file=/ABSOLUTE/OPERATOR-client.cnf --single-transaction --no-tablespaces --set-gtid-purged=OFF YOUR_PROJECT_DB > "$BACKUP_SQL"
tar -czf "$BACKUP_UPLOADS" uploads
```

确认命令成功、文件可读及摘要后，在全新恢复库和独立上传目录验证记录、关系、图片引用、登录与私人资源权限。核对表数采用该备份对应的版本结构。

回退应用时优先保留新增列、表及回复通知数据，先在副本验证旧应用兼容性。完整数据恢复通过已验证的新副本切换。重启单体后内存登录态失效，用户重新登录。

真实执行范围见 [项目状态](../PROJECT_STATUS.md)，正式公网部署、异地环境恢复和生产负载需要目标环境验证。
