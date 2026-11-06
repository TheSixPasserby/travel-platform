# 数据库脚本说明

- `schema.sql`：当前新空库结构（M8 后 14 张表、20 个外键）；
- `seed.sql`：目的地和标签示例数据（目的地 6 条、标签 8 条）。

## 1. 脚本边界

- 两个脚本**都不创建、不切换数据库**：不含 `CREATE DATABASE` 和 `USE` 语句，只作用于调用方显式选择的那个库。
- 操作者必须**预先创建并确认一个独立的空测试库**（建议命名 `travel_platform_test`，与 `.env.example` 中的 `DB_TEST_NAME` 对应），再用 `mysql --database=<测试库名>` 明确指定目标。
- 不要把这两个脚本指向真实业务库、共享开发库或任何含真实数据的数据库。使用既有实例时，也必须单独确认测试库隔离；正式环境的结构变更走单独评审的迁移流程。
- `CREATE TABLE IF NOT EXISTS` 仅用于**初始建表**，不承担后续结构迁移：已经建好的库不会因为重跑 `schema.sql` 而自动补齐新增列或索引。
- 主键与外键统一使用 **signed `BIGINT`**，与 Java `Long` 一致；金额使用 `DECIMAL`；字符集 `utf8mb4`、引擎 `InnoDB`、时间 `DATETIME(3)`。

## 2. 执行方式

前提：已有 MySQL 8.x 实例可连接，DB_HOST、DB_PORT、DB_USERNAME、DB_TEST_NAME 已在本地进程环境中配置，数据库管理员已建立并确认独立空测试库，且 DB_TEST_NAME 不等于 DB_NAME。`.env.example` 只提供模板，mysql 客户端不会自动读取 `.env`。以下命令交互输入密码，不把密码放入命令行参数。

```bash
# 1) 在已确认的独立空测试库上建表
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password \
  --database="$DB_TEST_NAME" < database/schema.sql

# 2) 导入示例数据；幂等检查时重复本命令一次
mysql --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --password \
  --database="$DB_TEST_NAME" < database/seed.sql
```

## 3. 初始化数据与幂等性

- `seed.sql` 依赖 `uk_destinations_name_type`（`name, destination_type`）和 `uk_tags_name`（`name`）配合 `ON DUPLICATE KEY UPDATE` 去重。
- 使用行别名 `incoming`，替代已弃用的 `VALUES(column)`；写法依据 [MySQL 8.4 官方说明](https://dev.mysql.com/doc/refman/8.4/en/insert-on-duplicate.html)，已在 8.4.13 实测无告警。
- 在一张**空的**测试库中连续执行两次，预期结果仍然是：`destinations` 6 条、`tags` 8 条。
- 不要在 seed 中放置明文默认管理员密码；管理员账号应通过安全首次启动流程或人工命令创建。

## 4. 状态说明

- 2026-11-02 已在独立 MySQL 8.4.13 的开发库、测试库实际执行 DDL/seed，31 项数据库检查通过；seed 别名修订后两库各重跑两次，无错误或告警，条数仍为 6/8。详见 [验证记录](../docs/verification/2026-11-02-m0-database.json)。
- 上述结果仅覆盖数据库，不代表注册登录、所有权、API 幂等或前端测试通过；这些业务验收尚未执行。
- 正式实现时，需检查 DDL 与 Entity、Mapper、API 契约是否一致。

## 5. M8 既有库升级与资料维护

既有库只使用 `migrations/V008_01..03`，由 `scripts/m8_migrate.py` 校验目标实例、备份回执、原记录指纹、SQL 哈希与现有元数据后执行。默认 dry-run；缺列/表/约束才添加，相同结构跳过，结构冲突即停止。MySQL DDL 隐式提交，工具不承诺整批 DDL 回滚；禁止用重跑 schema/seed 代替升级。

2026-11-06 已在新恢复副本、原开发库及独立测试库实际执行：每库首次 17 步、重复 0 步；新空库与升级结构等价，旧 JAR 仍能读取。开发库原三篇攻略和私人行程保留。资料包 `docs/m8/data/` 通过 `scripts/m8_data.py` 另行预览、事务导入与回执复核，每城四处景点，共 24 处；重复执行不覆盖用户后续修改。该维护工具仅针对本次已核实本机数据，不是通用初始化脚本。恢复副本和备份保存在忽略目录 `.local/m8/`，不进入 Git。
