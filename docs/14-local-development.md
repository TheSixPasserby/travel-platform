# 本机开发环境（macOS）

**状态（2026-11-04）：** M1–M6已验收，P0/P1核心可运行；独立MySQL8.4.13保留在3307，原3306不变。当前M7已实际演练JAR8081+生产dist5174与三角色演示，临时演练进程按归属停止，日常仍用8080/5173。交付与运维见[部署说明](22-deployment-and-operations.md)，实际进度见[PROJECT_STATUS](../PROJECT_STATUS.md)。

## 1. 拓扑与端口

| 用途 | 地址 | 说明 |
|---|---|---|
| 用户原有 MySQL | 127.0.0.1:3306 | 版本 26.7，保留原样；仅只读确认版本，不在其中初始化项目 |
| 项目 MySQL 8.4 | 127.0.0.1:3307 | 仅本项目使用，独立数据目录与独立凭据 |
| Spring Boot | 127.0.0.1:8080 | 本地开发后端；认证/目的地/攻略/互动/行程/后台已可用 |
| Vue / Vite | 127.0.0.1:5173 | 本地前端，/api 代理到后端，端口冲突时拒绝自动换端口 |

- 二进制来自 Homebrew `mysql@8.4`（8.4.13，keg-only，安装时使用 `--skip-post-install --skip-link`），统一走 `/opt/homebrew/opt/mysql@8.4/bin/` 下的 `mysql`、`mysqld`、`mysqladmin`，不依赖 PATH。参考 [Homebrew 公式页](https://formulae.brew.sh/formula/mysql@8.4)。
- 不使用 `brew services start mysql@8.4`：该命令按默认数据目录管理服务，与用户原有的 3306 实例冲突。

## 2. 目录与配置（由主代理创建，不得重建）

| 路径 | 内容 | 权限 |
|---|---|---|
| `.local/mysql84/server.cnf` | 服务端配置：bind 127.0.0.1、port 3307、datadir 与 socket 均指向本目录 | 600 |
| `.local/mysql84/admin-client.cnf` | 管理员客户端配置（凭据由配置文件承载） | 600 |
| `.local/mysql84/app-client.cnf` | 应用账号客户端配置 | 600 |
| `.local/mysql84/data` | 本实例独立数据目录 | 700 |
| `.local/mysql84/mysql.sock` | 本实例 socket | — |
| `.local/` | 整体被忽略，不进版本库 | 700 |

数据库为 `travel_platform` 与 `travel_platform_test`；应用账号 `travel_app` 只在这两个库有 SELECT/INSERT/UPDATE/DELETE 权限。随机密码与本地 `.env` 由主代理生成，凭据文件权限 600。数据目录已完成初始化，日常仅启动/停止，不清空或重新初始化。

## 3. 本项目实例的查询 / 启动 / 停止

在项目根目录执行，只作用于本项目实例：

```bash
MYSQL84=/opt/homebrew/opt/mysql@8.4/bin

# 状态：只读查询，返回版本、端口、时区
"$MYSQL84/mysql" --defaults-file="$PWD/.local/mysql84/admin-client.cnf" \
  -e "SELECT VERSION(), @@port, @@time_zone;"

# 启动：只启动本项目实例，不触碰 3306
"$MYSQL84/mysqld" --defaults-file="$PWD/.local/mysql84/server.cnf" --daemonize

# 停止：只关闭本项目实例
"$MYSQL84/mysqladmin" --defaults-file="$PWD/.local/mysql84/admin-client.cnf" shutdown
```

- `--defaults-file` 一律作为**第一个选项**并给出项目根下的完整路径；端口、socket 和凭据全部来自配置文件，命令行不传 `-p` / `--password`。
- 状态查询正常时 `@@port` 为 `3307`，`@@time_zone` 来自 `server.cnf`。启动成功只说明进程在跑，不代表建表或业务可用。
- 建库、授权、初始化均由主代理完成；日常开发只需使用上面的查询、启动、停止命令。

## 4. Java 21：按命令指定，不改全局

机器默认 Java 为 1.8；全局 jenv 与 Codex 配置均不修改，需要时只在当前命令环境显式指定：

```bash
travel_java_home=/Users/hachikei/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
env JAVA_HOME="$travel_java_home" PATH="$travel_java_home/bin:$PATH" java -version
env JAVA_HOME="$travel_java_home" PATH="$travel_java_home/bin:$PATH" mvn -version
```

## 5. 应用命令

在项目根目录运行：

```bash
# .env 由启动器安全解析，进程环境优先；不 source/eval，不打印凭据
python3 scripts/backend.py run
python3 scripts/backend.py test
python3 scripts/backend.py test --test GlobalExceptionHandlerTest
python3 scripts/backend.py package
# 运行已构建唯一JAR，无Maven运行依赖
python3 scripts/backend.py jar

# 前端不加载后端 .env
npm --prefix frontend ci
npm --prefix frontend run dev -- --host 127.0.0.1
npm --prefix frontend test
npm --prefix frontend run build
```

启动器仅为当前子进程选择 Java 21。test/package 强制 test profile，必须同时设置 DB_NAME 和独立的 DB_TEST_NAME（以 `_test` 结尾且不等于开发库）；测试还会验证实际数据库名后才清理自己的夹具。Spring Boot 本身不自动加载根 `.env`，请通过上述入口运行。后端启动不执行 SQL 初始化。

## 6. 边界说明

- `.env` 与 `.local/` 均被忽略；DSH 不读取它们，真实数据库测试由主代理执行。前端不使用 `VITE_` 变量承载密码或令牌。
- 安装背景参考 [MySQL 8.4 数据目录初始化](https://dev.mysql.com/doc/refman/8.4/en/data-directory-initialization.html)；这只是概念说明，不套用其中的默认共享 datadir 命令。
- 已完成：两库均有 12 表；seed 重复执行仍为 6 个目的地、8 个标签；约束与回滚检查通过。实际数据库检查结果见 [验证记录](verification/2026-11-02-m0-database.json)；应用层M1–M6已验收，见各里程碑报告。

本机 `.env` 的 CORS 白名单包含 `http://localhost:5173` 和 `http://127.0.0.1:5173`，便于桌面预览使用两种本地地址；应用默认值仍仅为 localhost，未开放任意来源或凭据。Vite代理请求会保留浏览器Origin，因此这两个入口需要显式列入本机白名单。

测试库与开发库仅大小写不同也会被启动器和HTTP测试守卫拒绝；在不区分库名大小写的MySQL安装中，这样可避免把同一个物理库误认为独立测试库。该规则已用合成配置先红后绿验证，启动器现有24项测试通过。
