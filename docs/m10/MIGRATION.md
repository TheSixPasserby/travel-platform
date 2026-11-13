# M10 数据迁移与回退

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

仅适用于已核对的本机开发/测试 MySQL 8.x。生产库未操作；禁止用 schema.sql 升级现有库。

## 备份与独立恢复验证

安装 `scripts/requirements-maintenance.txt` 的维护依赖。凭据使用本地 MySQL client 文件，绝不放进命令行、日志或 Git。

```bash
python3 scripts/m10_backup.py \
  --target test \
  --expected-server-uuid '<已核对的本机实例UUID>' \
  --admin-client '<本地socket管理员client文件>' \
  --app-client '<同一实例应用client文件>' \
  --mysql-bin '<mysql与mysqldump所在目录>'
```

开发库备份显式改为 `--target development`。脚本从本机 `.env` 选择明确的开发/测试库，拒绝远程 DB_HOST、错误 UUID/端口和不独立的测试库。SQL 与 uploads 放在权限 0700 的 `.local/m10/backups/`，恢复到随机命名的**全新** `_test` 克隆，不覆盖原库；原列行哈希/结构与恢复副本一致、上传 SHA256 一致后才写 `active-backup.json`。所有备份和本地 client 文件均被 Git 排除。

## Dry-run 与增量执行

先在该回执的 restoreDatabase 上执行，再在专用测试库验证后端，最后核对开发库目标及新备份回执。

```bash
python3 scripts/m10_migrate.py \
  --database '<回执允许的目标库>' \
  --expected-server-uuid '<已核对的本机实例UUID>' \
  --admin-client '<本地socket管理员client文件>'
```

默认仅检查。复核输出后加 `--apply`，再次运行应全部显示 `present / executed:false`。

- SQL 只允许两张表 ADD COLUMN `ip_region VARCHAR(64) ... NULL`，受 manifest-m10.json 哈希约束；不会迁移 users。
- 执行前验证两张目标表存在、InnoDB、utf8mb4_0900_ai_ci，列类型/可空性/默认值/排序规则一致，备份含两张表且 SQL/归档哈希匹配。
- 按备份中记录的**原列清单**校验全部原行哈希，执行前后均核对；旧列数据变化即停止。维护窗口内停止业务写入，避免快照偏移；不自动覆盖变更。
- 原始 IP 不进业务表；历史攻略/评论的新字段全部 NULL，不按登录记录或用户资料补填。

## 断点与回退

MySQL DDL 会隐式提交，不能宣称事务回滚两次 ALTER。如果中断，保留已完成新增列，核对元数据后原命令续跑；存在定义漂移则先人工审查。

旧 M9 代码能忽略新可空列，优先保留新列并回退兼容代码。完整数据恢复必须先恢复到新克隆并校验，再由操作者切换连接；本脚本不 DROP 列、不删库、不覆盖源库。

## 本次执行证据（2026-11-07）

- MySQL 8.4.13、端口 3307：先备份/恢复专用测试库，再测试新克隆及 travel_platform_test 的 dry-run、apply、重复 apply。
- 开发库 travel_platform 新备份恢复到独立克隆，14 张表、133 行、44 个上传文件；先在该克隆完成增量迁移，再对开发库执行同样流程。
- 原列行哈希全部一致，原上传文件 SHA256 全部一致，用户名唯一索引保留，历史属地全部 NULL。
- 私有恢复回执：`.local/m10/backups/20261107-114951-fd6f1c/receipt.json`；公开脱敏证据：`docs/verification/2026-11-07-m10-data.json`。
- 本轮未停止或替换原有开发服务；新后端浏览器验收使用独立 18080/15173 与隔离克隆。现有 8080 进程需要重启才能加载 M10 JAR；迁移本身兼容旧进程。
