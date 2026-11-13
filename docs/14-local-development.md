# 本地开发

在项目根目录执行下列命令。技术版本见 [依赖基线](15-dependency-baseline.md)，部署与恢复步骤见 [运维说明](22-deployment-and-operations.md)。

## 准备环境

需要 Java 21、Node.js 24、Python 3，以及运行中的 MySQL 8.x。后端使用 Maven 或仓库中的 Maven Wrapper。前端最低 Node 版本为 22.12.0，当前 CI 使用 24。

非 macOS 环境需显式设置 `JAVA_HOME` 指向 JDK 21。macOS 启动器也可通过系统的 `java_home` 查询已安装的 21。

```bash
java -version
node -v
python3 --version
```

准备一个业务库和一个独立测试库，测试库名以 `_test` 结尾，并与业务库不同。新空库执行当前 schema 和 seed，已有数据的库走增量迁移。[数据库操作](../database/README.md)

## 配置与安装

```bash
cp .env.example .env
chmod 600 .env
npm --prefix frontend ci
```

在 `.env` 填写 DB_HOST、DB_PORT、DB_NAME、DB_TEST_NAME、DB_USERNAME、DB_PASSWORD，确认真实端口和库名。启动器按字面解析配置，进程环境变量优先。`.env` 与 `.local` 为本机文件。

当前维护机器的项目 MySQL 使用 3307，另一个 3306 实例有独立用途。这是已有环境记录，新机器应按实际安装填写端口，已有数据目录保持原用途。

## 启动

```bash
./start.sh
```

启动器运行 Web 和后端并打印地址，检查当前 Web 来源的 CORS 预检。默认 Web 为 5173、后端为 8080，支持同局域网手机浏览器访问。端口冲突时先查看占用进程的归属。

也可分别运行。

```bash
python3 scripts/backend.py run
npm --prefix frontend run dev -- --host 127.0.0.1
```

Vite 通过代理访问 `/api`、`/uploads`、`/share`、`/sitemap.xml` 和 `/robots.txt`。单独调整后端端口时，在 Vite 启动环境中设置 `TRAVEL_API_TARGET`。`PUBLIC_BASE_URL` 设置分享所用的 Web 公共 origin。

## 测试与生产构建

```bash
python3 scripts/backend.py package
npm --prefix frontend test
npm --prefix frontend run build
python3 -m unittest discover -s scripts/tests
```

`package` 运行后端全部测试并生成 JAR。测试库必须已经初始化到当前结构。定向运行后端测试可使用 `python3 scripts/backend.py test --test M17CommunityApiTest`。

## 配置 AI

普通行程始终可以手动创建。需要 AI 时由管理员在 `/admin/ai` 设置供应商、模型、API 根地址和密钥。网页保存值优先于启动环境默认值，并存入服务端 `AI_SETTINGS_FILE`。该私有文件需要在重启后保留。[配置和故障处理](22-deployment-and-operations.md)

## 常见问题

| 现象 | 检查 |
|---|---|
| 数据库连接失败 | MySQL 是否运行，端口、账号、权限及库名是否匹配 |
| 提示表或列缺失 | 新空库是否建表，旧库是否完成对应增量迁移 |
| 登录请求被 CORS 拒绝 | 当前协议、主机和端口是否在后端白名单，局域网地址变化后重新核验 |
| 端口被占用 | 确认已有服务及其启动位置，再决定复用或停止 |
| AI 未开启或生成失败 | 查看管理员设置和页面提示，继续手动规划 |
| 后端重启后要求登录 | 当前会话保存在单体内存中，重新登录即可 |

已有维护机器的私有目录、上传和数据库不属于初始化输入。换机运行依据示例配置重新准备环境。
