# 交付、部署与运维

软件形态为Java21可执行JAR、Vue生产dist、MySQL8.x与项目根uploads。已验证本机生产构建和数据库与上传成套备份恢复。Nginx模板、公网域名/TLS、异地恢复和其他操作系统部署尚未实测。

## 1. 环境与数据边界

需Java21、Python3.9或以上、MySQL8.0/8.4；从源码构建还需兼容Node（建议24LTS，最低22.12）、npm和Maven Wrapper网络依赖缓存。版本见[依赖基线](15-dependency-baseline.md)。运行已有JAR不需要Maven/Node。源码交付包不带node_modules、数据库文件或真实上传。

本机项目MySQL为3307独立8.4，3306原有实例保留。已有两个库不重复初始化、不清空。换机时由数据库管理员建立专用**空库**和独立`_test`库；应用账号仅授权这两库SELECT/INSERT/UPDATE/DELETE，初始化DDL使用单独管理员账号。`database/schema.sql`不承担升级迁移，不能用重跑它替代迁移；详情见[数据库说明](../database/README.md)。

仅在已核实的新空库，交互密码，不写命令行密码：

```bash
mysql --host=127.0.0.1 --port=3307 --user=YOUR_OPERATOR --password --database=YOUR_EMPTY_DB < database/schema.sql
mysql --host=127.0.0.1 --port=3307 --user=YOUR_OPERATOR --password --database=YOUR_EMPTY_DB < database/seed.sql
```

测试库也独立初始化；不将上述示例库名直接指向已有真实数据。种子仅6目的地/8标签，无管理员密码。

## 2. 环境变量

复制根`.env.example`为`.env`并本地填写，权限600，不提交。`backend.py`按字面解析，**不source/eval**；当前进程环境优先。直接`java -jar`不会自动加载根.env，需由部署服务管理器安全提供环境变量。

| 名称 | 行为 |
|---|---|
| DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD | 必填，连接专用MySQL8.x |
| DB_TEST_NAME | test/package必填，必须以`_test`结尾且与DB_NAME大小写无关地不同 |
| SERVER_ADDRESS、SERVER_PORT | 本机示例127.0.0.1:8080；按命令可覆盖为演练8081 |
| CORS_ALLOWED_ORIGINS | 精确逗号分隔Origin（含协议和端口）；同源反代也应配置浏览器实际Origin，不使用通配符 |
| JAVA_HOME | 指向Java21；macOS可由启动器查询已安装21，不改全局Java |
| UPLOAD_DIR | 直接运行JAR时须给持久化绝对目录；backend.py固定使用所在项目根uploads |
| TRAVEL_API_TARGET | 仅Vite开发/preview的Node环境，默认http://127.0.0.1:8080；不会进入浏览器配置 |

AI设置见[提供商预设](m8/ai-provider-presets.md)。`AI_ENABLED=false`为默认值，`AI_PROVIDER`支持openai、deepseek、qwen、custom，服务端读取`AI_API_KEY`及可覆盖的`AI_BASE_URL`、`AI_MODEL`。连接超时默认5秒，读取超时45秒。Key与数据库密码只配置到后端环境。管理员使用第6节的人工核对流程。

## 3. 构建与运行

项目根：

```bash
npm --prefix frontend ci
python3 scripts/backend.py package
npm --prefix frontend test
npm --prefix frontend run build
python3 -m unittest discover -s scripts/tests
python3 scripts/backend.py jar
```

package运行完整后端测试并要求独立测试库。jar只运行backend/target内唯一普通.jar；缺失、多份或符号链接拒绝，不自动选择旧包、不自动构建。后端不会自动建表/导入种子。终端前台运行以Ctrl+C停止该进程；服务管理器只管理本项目服务，不使用按端口/名称批量杀进程。

本机生产构建演练使用另外两个终端，保留8080/5173：

```bash
env SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8081 CORS_ALLOWED_ORIGINS=http://127.0.0.1:5174 python3 scripts/backend.py jar
env TRAVEL_API_TARGET=http://127.0.0.1:8081 npm --prefix frontend run preview -- --host 127.0.0.1 --port 5174
```

访问http://127.0.0.1:5174；确认目的地/详情深链刷新、登录和私有入口。端口占用会启动失败，先核实占用者，不自动终止未知进程。Vite当前安装版本源码的preview.proxy继承server.proxy，已实际核对，不依赖浏览器跨域直连。

## 4. 生成交付目录

```bash
python3 scripts/release.py .local/releases/travel-platform-delivery
```

目标必须不存在。脚本先运行完整backend package和frontend build；构建失败不生成成功目录。产物含源码、锁文件、Maven Wrapper、生产dist/JAR、数据库和非秘密文档、`.env.example`、SHA256SUMS，uploads仅空占位。

明确排除.env/.local/.git/.superpowers、真实上传、node_modules、日志、系统缓存、application-local/prod配置；`docs/superpowers`是有意交付的设计/计划，区别于隐藏执行日志。拒绝输入及祖先符号链接，检查JAR内部排除文件及application配置的已识别非空字面密码/API密钥；此有限检查不能发现任意伪装秘密。交付前仍应人工检查要共享的材料。

校验文件：Linux可在目录内执行`sha256sum -c SHA256SUMS`，macOS执行`shasum -a 256 -c SHA256SUMS`。哈希清单不含自身。换机重新配置环境和数据，不复制本机`.env`、随机演示凭据或`.local`。复制中失败的目录无成功保证；先查看再处理自己生成的目录，不覆盖重试。

## 5. Nginx同源反代示例

模板见[nginx.conf.example](../deploy/nginx.conf.example)，需放入现有http块，并修改root、监听地址/端口与上游；未安装/启动Nginx，当前仅模板。上游仅绑定回环，DB也不需向浏览器开放。`/api/`和`/uploads/`保留原URI转发，SPA深链回index.html，带哈希assets长期缓存；不要缓存index或受保护API。

依据[Nginx proxy_pass官方说明](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_pass)使用不带URI部分的上游，保留`/api/v1`；深链依据[try_files官方说明](https://nginx.org/en/docs/http/ngx_http_core_module.html#try_files)。调整后由实际环境执行`nginx -t`再管理本项目站点。示例只监听回环HTTP，不等于公网HTTPS已完成；实际域名证书与服务管理方式待部署环境确定。

## 6. 管理员首次配置

先通过正常注册创建属于操作者的账号，保存注册响应ID和随机用户名，本地妥善保存随机密码。数据库操作者再用交互密码连接**已确认的项目库**，用实际ID和完整用户名替换下面占位；不凭昵称判断归属，无邮箱字段。

```sql
START TRANSACTION;
SELECT id FROM users WHERE role='ADMIN' ORDER BY id FOR UPDATE;
SELECT id, username, role, status, is_deleted FROM users
 WHERE id=YOUR_REGISTERED_ID AND username='YOUR_EXACT_REGISTERED_USERNAME' FOR UPDATE;
-- 确认是本次自有USER、ACTIVE、is_deleted=0后才执行；不匹配则ROLLBACK。
UPDATE users SET role='ADMIN'
 WHERE id=YOUR_REGISTERED_ID AND username='YOUR_EXACT_REGISTERED_USERNAME'
 AND role='USER' AND status='ACTIVE' AND is_deleted=0;
SELECT ROW_COUNT();
-- 应为1；核实后COMMIT，否则ROLLBACK。
COMMIT;
```

以上是人工分步模板，不是可直接整段粘贴的自动授权脚本。保留审计记录，不降级或删除已有管理员；常规管理只从后台临时启停，不能禁用本人。停用期间原token受保护请求401；重新启用后保留且未过期token可恢复有效，页面收到401则已清本地token。重启当前单体后内存会话丢失，用户重新登录即可。

## 7. 备份、恢复与版本回退

需要同时备份专用数据库与项目根uploads；SQL包含密码哈希和私人内容，备份应在权限600/目录700的本地安全位置，不能放dist、Git或共享交付目录。使用数据库操作者的受限客户端配置，不把密码放参数。

维护窗口暂停本项目写入或停止本项目后端，再备份SQL和uploads以保持引用一致；不要停止3306其他服务。示例在操作者已准备的安全目录执行，`BACKUP_SQL`应指向**不存在**的新文件，使用shell noclobber防止覆盖：

```bash
umask 077
set -C
mysqldump --defaults-file=/ABSOLUTE/OPERATOR-client.cnf --single-transaction --no-tablespaces --set-gtid-purged=OFF YOUR_PROJECT_DB > "$BACKUP_SQL"
tar -czf "$BACKUP_UPLOADS" uploads
```

数据库参数是单个库名，不使用`--databases`，避免备份内嵌USE指回原库；上传压缩包同样须使用新文件名并核对，tar本身不承诺拒绝覆盖。确认两条命令成功、文件哈希和可读性后再恢复服务，失败不得记作有效备份。

恢复先准备独立的全新空库与新的uploads目录，在副本中导入并核对14表、记录数、文件引用、登录和私人资源权限。M8已在本机MySQL8.4.13多次恢复全新副本，核对全部行指纹、列定义和文件SHA256，并验证八项账号/草稿/行程权限。最近用于AI联调的副本恢复了132行业务数据和44个文件，明细见[最终报告](33-m8-final-report.md)。异地目标环境的恢复仍需单独验收。

M8迁移为`database/migrations/V008_01__destination_details.sql`、`V008_02__destination_relations.sql`和`V008_03__itinerary_import_key.sql`。既有库升级使用已核对目标、备份回执与文件哈希的`scripts/m8_migrate.py`，默认dry-run，操作步骤见[数据库说明](../database/README.md)。维护脚本依赖在独立环境安装`scripts/requirements-maintenance.txt`。首次17条DDL，重复执行0条，开发库和测试库分别验证。

版本回退优先切换已验证兼容的旧JAR/dist并保留新增可空列与表。旧JAR在升级副本的八项权限检查已通过。DDL中途失败按元数据定位续跑，MySQL DDL不能依赖事务整体回滚。数据恢复仅在新副本验证后切换，备份回执含目标数据库和服务器UUID。重启会使内存会话失效，用户重新登录即可。
