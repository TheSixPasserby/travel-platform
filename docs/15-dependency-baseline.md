# M1 依赖版本基线

日期：2026-11-02。版本从官方 npm Registry / Maven Central 元数据读取，保留既定主版本并选择稳定补丁；不升级至 Spring Boot 4 或 Vite 8。实际兼容性以构建与测试确认。

| 范围 | 依赖 | 固定版本 | 来源 |
|---|---|---|---|
| npm | `vue` | 3.5.44 | [官方元数据](https://registry.npmjs.org/vue) |
| npm | `vue-router` | 4.6.4 | [官方元数据](https://registry.npmjs.org/vue-router) |
| npm | `pinia` | 3.0.4 | [官方元数据](https://registry.npmjs.org/pinia) |
| npm | `axios` | 1.21.0 | [官方元数据](https://registry.npmjs.org/axios) |
| npm | `typescript` | 5.9.3 | [官方元数据](https://registry.npmjs.org/typescript) |
| npm | `vite` | 7.3.7 | [官方元数据](https://registry.npmjs.org/vite) |
| npm | `@vitejs/plugin-vue` | 6.0.10 | [官方元数据](https://registry.npmjs.org/@vitejs%2Fplugin-vue) |
| npm | `tailwindcss` | 4.1.18 | [官方元数据](https://registry.npmjs.org/tailwindcss) |
| npm | `@tailwindcss/vite` | 4.1.18 | [官方元数据](https://registry.npmjs.org/@tailwindcss%2Fvite) |
| npm | `vitest` | 3.2.7 | [官方元数据](https://registry.npmjs.org/vitest) |
| npm | `@vue/test-utils` | 2.5.1 | [官方元数据](https://registry.npmjs.org/@vue%2Ftest-utils) |
| npm | `jsdom` | 26.1.0 | [官方元数据](https://registry.npmjs.org/jsdom) |
| npm | `vue-tsc` | 3.3.14 | [官方元数据](https://registry.npmjs.org/vue-tsc) |
| maven | `org/springframework/boot/spring-boot-starter-parent` | 3.5.16 | [官方元数据](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml) |
| maven | `com/baomidou/mybatis-plus-spring-boot3-starter` | 3.5.18 | [官方元数据](https://repo.maven.apache.org/maven2/com/baomidou/mybatis-plus-spring-boot3-starter/maven-metadata.xml) |
| maven | `cn/dev33/sa-token-spring-boot3-starter` | 1.46.0 | [官方元数据](https://repo.maven.apache.org/maven2/cn/dev33/sa-token-spring-boot3-starter/maven-metadata.xml) |
| maven | `org/springdoc/springdoc-openapi-starter-webmvc-ui` | 2.8.17 | [官方元数据](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml) |
| npm | `@types/node` | 24.19.2 | [官方元数据](https://registry.npmjs.org/@types%2Fnode) |
| maven | `org/apache/maven/plugins/maven-wrapper-plugin` | 3.3.4 | [官方元数据](https://repo.maven.apache.org/maven2/org/apache/maven/plugins/maven-wrapper-plugin/maven-metadata.xml) |

Java 21.0.12.1、Maven 3.9.16、Node 24.21.0、MySQL 8.4.13 已在 M0 实测。MySQL JDBC 驱动和 Spring Security Crypto 跟随 Spring Boot BOM，不单独叠加 Spring Security Web。

Spring Boot 3.5 支持 Java 21，参考 [系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)；MyBatis-Plus 使用 [boot3 starter](https://baomidou.com/getting-started/install/)；Sa-Token 使用 [boot3 集成包](https://github.com/dromara/Sa-Token)；SpringDoc 保持兼容 Boot 3 的 2.x。

前端 Node 24 满足 Vite 7 与 plugin-vue 的 >=22.12.0 引擎约束；Tailwind Vite 插件 4.1 支持 Vite 7。完整安装结果锁定在 package-lock.json，后续依赖变更同步本文件。

## JDBC 时区兼容性裁定

真实 HTTP 预验发现独立 MySQL 实例没有命名时区表，`forceConnectionTimeZoneToSession=true` 配合 `Asia/Shanghai` 导致连接失败（尚未到达接口断言）。JDBC 改用 URL 编码的固定偏移 `connectionTimeZone=%2B08:00`，继续强制会话 +08:00；应用日期语义仍为 Asia/Shanghai。无需修改原 MySQL 或导入服务器系统表。[Connector/J 官方说明](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html) 明确区分地理时区需系统表、UTC 偏移可直接设置会话。该环境失败不计作认证接口红灯，修复后重新执行。

## M2 增量选型记录（2026-11-03，已整合，见下节）

- `com.baomidou:mybatis-plus-jsqlparser:3.5.18`：与现有MyBatis-Plus版本一致，[官方分页文档](https://baomidou.com/en/plugins/pagination/)说明3.5.9起需独立引入；Maven Central准确版本POM已只读验证HTTP200。
- `com.twelvemonkeys.imageio:imageio-webp:3.15.3`：通过JDK ImageIO提供WebP读取，[官方README](https://github.com/haraldk/TwelveMonkeys)；Maven Central准确版本POM已只读验证HTTP200。仅引入所需WebP插件，不加入额外图像格式/原生转码服务；实际Java21兼容性以M2构建/解码测试为门槛。

## M2 实际整合裁定

新增两项依赖已引入并在Java21下编译：mybatis-plus-jsqlparser3.5.18、imageio-webp3.15.3。实际图像HTTP测试已验证WebP解码，非仅扩展名判断。

- [Spring Boot3.5配置文档](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/)给出Tomcat max-swallow-size默认2MB。实际>6MiB总请求在2MB丢弃上限时连接中断，定点改8MB后返回413/41301通过；业务接受上限仍为文件5MiB/请求6MiB，不使用无限吞读。
- 核对实际Sa-Token1.46.0字节码：默认防火墙checkFailHandle为空时直接写异常文本且不设置HTTP错误状态，导致已拦截的编码穿越返回200。通过其既有策略checkFailHandle将拒绝响应统一为400/40001泛化JSON，不禁用或替换任何校验hook。

## M3 增量与工具链修复（2026-11-03）

从npm官方元数据核实并固定markdown-it15.0.3、DOMPurify3.4.17，均自带类型，不额外添加@types/markdown-it。依据[markdown-it官方](https://github.com/markdown-it/markdown-it)与[DOMPurify官方](https://github.com/cure53/DOMPurify)配置禁止原始HTML和最终允许列表清理；兼容性继续以实际构建验证。

安装审计发现旧测试工具链8项(critical2)：根据[Vitest公告](https://github.com/advisories/GHSA-82fw-gwwq-j7x9)和[Tinypool公告](https://github.com/advisories/GHSA-85c8-ppgw-ccpr)，Codex核实Vitest4.1.12支持现有Vite7/Node24后升级，TestUtils固定2.4.11。未升级Vite/Vue/TypeScript或采用audit fix --force。现有105项测试及类型/生产构建在新工具链下通过，critical/high已为0。

剩余4项moderate均属js-beautify→editorconfig/glob/minimatch开发依赖链；一次不带force的兼容audit fix未能消除，保留真实结果，不强制覆盖跨主版本依赖API。开发工具不部署进dist，使用vitest run而非公开测试服务；最终里程碑复核，不能声称完整审计0漏洞。运行时依赖审计另行验证。M1/M2历史记录的Vitest3.2.7/TestUtils2.5.1代表当时验收版本，不覆写历史证据。

## M11 可视化 Markdown 编辑（2026-11-11）

固定 `@milkdown/kit` **7.23.0**（MIT），通过官方 npm 元数据核实版本，依赖锁定在前端 package-lock.json。[官方文档](https://milkdown.dev/docs/guide/getting-started)、[源码](https://github.com/Milkdown/milkdown)。只使用 core、commonmark、gfm、history 与 ProseMirror 工具；自建轻量 Vue 工具栏，避免额外图片 URL 输入与上传旁路。没有使用 Beta Markdown 适配层，也没有更改后端格式。

PoC 使用 `docs/m8/content/guide-4/5/6.md` 三篇真实旧文及多级标题、列表、链接、引用、受管图片、表格、代码块夹具；语义和图片 URL 往返验证通过。当前数据库这三篇内容与夹具逐字相同。无修改导入不重写原 Markdown；不支持的 HTML、扩展或来源保留源码模式。导入前检查实际解析节点，导入后检查实际序列化语义；首次装载不入撤销历史。

## M12 活跃图文编辑器（2026-11-11）

活跃攻略编辑页改用官方 **Vditor 4.0.1**（MIT，npm 正式版本，精确版本与 lockfile 固定）。[官方源码及许可](https://github.com/Vanessa219/vditor)、[官方配置说明](https://github.com/Vanessa219/vditor#options)。WYSIWYG 默认，IR 与源码可切换；正文仍为原 Markdown。`cache.enable=false`，无默认外部图床或公共 CDN。

`frontend/build/vditorAssets.ts` 按固定白名单提供 `/vendor/vditor/4.0.1/` 下的 Lute、中文语言包、图标、主题和许可；dev 与 dist 使用相同路径，交付脚本收录构建插件。代码高亮、数学、图表、任务列表按钮和媒体能力不启用；粘贴 HTML 先经原有 DOMPurify 白名单。模式切换以原文安全渲染语义校验，无法无损载入时保留源码；M11 原编辑器保留仅供既有回归对照，不被活跃页面导入。
