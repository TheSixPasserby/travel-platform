# 依赖与工具链

版本以 `backend/pom.xml`、`frontend/package.json` 和 `frontend/package-lock.json` 为准。下表列出当前直接依赖，不表示上游最新版本。

## 后端

| 依赖 | 锁定版本 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| MyBatis-Plus | 3.5.18 |
| Sa-Token | 1.46.0 |
| SpringDoc | 2.8.17 |
| TwelveMonkeys WebP | 3.15.3 |

MySQL Connector/J 与 Spring Security Crypto 采用 Spring Boot 依赖管理。Maven Wrapper 的发行版本由 `backend/.mvn/wrapper/maven-wrapper.properties` 固定。

## 前端

| 包 | 锁定版本 | 用途 |
|---|---|---|
| `@milkdown/kit` | 7.23.0 | 历史编辑器回归对照 |
| `axios` | 1.21.0 | HTTP |
| `dompurify` | 3.4.17 | HTML清理 |
| `markdown-it` | 15.0.3 | Markdown解析 |
| `pinia` | 3.0.4 | 跨页面状态 |
| `vditor` | 4.0.1 | 活跃图文编辑器 |
| `vue` | 3.5.44 | 界面 |
| `vue-router` | 4.6.4 | 路由 |
| `@tailwindcss/vite` | 4.1.18 | 样式构建 |
| `@types/node` | 24.19.2 | Node类型 |
| `@vitejs/plugin-vue` | 6.0.10 | Vue构建 |
| `@vue/test-utils` | 2.4.11 | Vue测试 |
| `jsdom` | 26.1.0 | 测试DOM |
| `tailwindcss` | 4.1.18 | 样式 |
| `typescript` | 5.9.3 | 类型检查 |
| `vite` | 7.3.7 | 开发与构建 |
| `vitest` | 4.1.12 | 组件测试 |
| `vue-tsc` | 3.3.14 | Vue类型检查 |

前端声明 Node.js >=22.12.0，项目开发与 CI 使用 Node.js 24。使用 `npm ci` 按 lockfile 安装，依赖更新同时检查类型、测试及生产构建。

## 兼容性说明

JDBC 使用 `connectionTimeZone=%2B08:00` 配合强制会话偏移，适用于未导入命名时区表的 MySQL 实例。应用业务语义继续采用 Asia/Shanghai。

Vditor 4.0.1 所需资源通过本地构建插件同源提供，正文仍保存 Markdown。旧 Milkdown 组件保留为回归对照，当前攻略编辑页使用 Vditor。

历史测试工具升级至 Vitest 4.1.12 与 Vue Test Utils 2.4.11，对应旧报告中的版本只描述当时环境。当前漏洞审计结果需要按实际执行日期另行记录，本页不沿用历史审计数量作为现状。

## 验证入口

```bash
npm --prefix frontend ci
python3 scripts/backend.py package
npm --prefix frontend test
npm --prefix frontend run build
python3 -m unittest discover -s scripts/tests
```

构建环境与操作步骤见 [本地开发](14-local-development.md)。历史升级原因与运行结果见对应 [阶段报告](history/README.md)。
