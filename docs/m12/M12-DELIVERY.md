# M12 本地交付与验收（2026-11-11）

山水间已增量升级为 Web 旅游内容分享与发现社区。首页、攻略列表/详情、图片预览、Vditor、公开创作者页、相关推荐、IP 禁显与 Web-only 交付均已实现并完成本地验证。

## 后续内容扩充与网页 AI 设置

用户追加要求已完成，公开攻略增至 20 篇，管理员可在 `/admin/ai` 配置全站行程助手。最新测试、数据增量及截图见 [本轮交付](additions/DELIVERY.md)。下文保留首轮 M12 验收记录。

## Git 与保全基线

- 分支 `main`，HEAD `29fa630bf097c2afd45b9711995fe655bfc4f7c6`；未 fetch，不将本地 origin/main 当成远端实时状态。
- 起始工作区已有 M11 未提交代码及 50 个归档重命名，全部保留。本轮没有 commit、push、部署或发布 Release。
- M12 前源码恢复快照与 SHA256 清单在 `.local/m12/source-baseline.zip`、`source-baseline.json`。M11 原编辑器代码/测试逐文件未变，作为历史回归对照，活跃页面已使用 Vditor。
- 原业务库 **14 表、137 行**与上传目录 **44 个文件**前后逐表/逐文件 SHA256 一致，表结构一致；没有清表、覆盖旧稿或删除真实上传。
- 浏览器所有写入均在全新 `travel_platform_m12_*_test` 和图片副本执行。自动化后端使用独立测试库及带命名空间的夹具；临时 QA 服务已结束，副本和日志保留。

## 完成范围

| 范围 | 实际行为 |
| --- | --- |
| 发现首页 | 首屏真实 API 手记；推荐/最新/热门，真实城市/主题入口；关键词搜索，加载/错误重试/空列表 |
| 攻略列表 | 复用筛选、卡片、网格与分页；每页12条；URL 恢复关键词/地点/标签/排序/页码，后退/刷新可恢复，迟到请求不覆盖当前结果 |
| 社区卡片 | 独立图片/标题导航与作者链接，无嵌套链接；真实互动、地点及有值的天数/预算，懒加载和失败占位；CSS Grid 保持 DOM 阅读顺序 |
| 旅行笔记详情 | 安全图文、作者公开页入口、原点赞/收藏/评论、目的地/景点入口、真实相关推荐 |
| 相关推荐 | 同主目的地或共同标签；城市优先，再按真实点赞/发布时间/ID 排序；排除当前、草稿、隐藏及删除内容 |
| 图片灯箱 | 攻略封面/正文/图库、城市/景点封面/图库及编辑预览；键盘、Esc、触摸翻页、放大/滚动、计数、错误回退、焦点/滚动恢复及路由清理；重复URL按位置定位 |
| 创作者主页 | `/users/:username`；昵称、灰色用户名、头像、简介、公开文章数及后端分页；不返回私人收藏/行程、角色、令牌或密码 |
| IP 属地 | `IpRegionVisibility.ENABLED=false`，公开/本人攻略及评论字段保留但返回 null，界面不显示；历史快照及可信代理解析继续保留 |
| uni-app | 复用 M11 归档；50个文件与原 HEAD 逐字节一致；活跃 CI/start/release 无移动构建或发布；历史 APK/Release/证据保留 |

推荐与热门复用 `popular` 的真实点赞、发布时间排序，不宣称个性化。API 新增三个只读入口，详见 [接口契约](../06-api-contract.md)；没有 DDL 或存储格式迁移。

## Vditor

- 固定 **4.0.1 / MIT**，版本、许可来自官方 npm 包；[官方项目](https://github.com/Vanessa219/vditor)。默认 `wysiwyg`，可切 `ir` / `sv`。
- `GuideRichEditor.vue` 管理挂载/卸载、主题、禁用、三模式、上传、复制源码与安全回退；攻略元数据/状态/图库职责仍在 `GuideEditorView.vue`。
- `/vendor/vditor/4.0.1/` 同源提供 Lute、中文、图标、主题和许可；dev 与 dist 同一白名单。图标随包导入，避免原生同步 XHR。实际编辑器网络观察无站外请求，`cache.enable=false`。
- 保持 Markdown；沿用严格 DOMPurify、链接与受管图片规则。不支持的原样 HTML/扩展或转换语义差异明确回退完整源码，不静默丢失正文。
- 图片选择、工具栏、粘贴和拖入均通过现有认证 API；JPG/PNG/WebP、5MB、20张限额。源码保存也拦截 Base64/blob/非受管图片。删除正文/移出图库不删除磁盘文件。
- 增补跨账号/令牌代际检查、保存/复制/未保存离开时同步读取最后输入（含会话过期）、独立上传 busy 时序回归；网络失败保留文字。编辑器按钮不会提交外层攻略表单。
- 三篇原稿 **4/5/6** 实测加载→三模式→无修改保存→小幅编辑→保存→重开→恢复；正文原文、ID、作者、首次发布时间、互动、图库、景点与状态保留，6仍为 DRAFT。综合标题/列表/引用/链接/表格/代码/受管图与100000字符导入有真实 Vditor 运行时测试。

## 测试结果

| 命令/检查 | 结果 |
| --- | --- |
| `python3 scripts/backend.py package` | 252通过，0失败，0跳过；Java21，MySQL8.4.13隔离测试库 |
| `npm --prefix frontend ci` | 通过，锁文件可重现安装 |
| `npm --prefix frontend test` | 40文件、389通过，0失败 |
| `npm --prefix frontend run build` | TypeScript 与 Vite 生产构建通过，同源资源进入 dist |
| `python3 -m unittest discover -s scripts/tests` | 92通过，0失败 |
| Chromium 页面矩阵 | 匿名/作者、浅色/深色、360/390/430/768/1440px，共110项；无横向溢出、脚本错误，路由清理通过 |
| 浏览器专项 | 排序/搜索后退与刷新、旧稿往返、上传四入口、图库关联、草稿、100001字符拒绝、撤销/重做、Word表格/中文及恶意HTML粘贴通过 |
| Markdown/权限 | 原白名单、XSS/非法URL、A不能写B、匿名写入拒绝、赞藏唯一约束、私人行程、AI不可用降级等相关回归通过 |
| `./start.sh` | 默认8080/5173本地就绪；重启前核实进程主类/路径/端口，未知进程未操作；`--mobile` 明确不支持 |
| Web-only 交付 | 实际 `release.py` 候选构建567文件逐项SHA256通过；Vditor构建插件已收录，最终目录使用同一已验证构建并附SHA256SUMS |
| 依赖审计 | 运行时0漏洞；完整安装仍提示基线已有4项 moderate 开发依赖，未强制跨版本升级 |
| Git/数据/归档 | staged/unstaged diff检查通过；业务行/DDL/上传前后指纹一致；50个归档文件逐字节一致 |

默认 [Web入口](http://127.0.0.1:5173/) 已读取原业务数据，公开攻略4/5可见，新创作者与相关推荐接口返回200。仅重启已核实本项目的本地进程；后端重启前的登录会话需重新登录。

测试日志、原始浏览器结果、备份/隔离配置保存在 `.local/m12/`。共享机器证据：[M12 JSON](../verification/2026-11-11-m12.json)。最终交付目录与校验回执见 `.local/m12/delivery-paths.json`、`delivery-receipt.json`。

## 截图

- [桌面发现](screenshots/home-light-1440-anonymous.png)、[深色发现](screenshots/home-dark-1440-anonymous.png)、[手机发现](screenshots/home-light-390-anonymous.png)
- [笔记详情](screenshots/detail-light-1440.png)、[创作者主页](screenshots/creator-light-1440.png)、[图片灯箱](screenshots/lightbox-1440.png)
- [桌面Vditor](screenshots/editor-light-1440-author.png)、[手机深色Vditor](screenshots/editor-dark-390-author.png)

## 已知限制与未执行项

- 切换模式会重建 Vditor，撤销栈不跨模式保留；正文原文与语义往返已经验证。跨模式撤销历史恢复尚未实现。
- 未执行 Safari/Firefox、物理手机浏览器和原生系统输入法组合输入验收；已执行 Chromium 中的中文输入、粘贴、触摸事件及响应式检查。
- 没有真实 GeoIP 来源，继续全局禁显。收费 AI 供应商、远端新版 CI、公网部署/发布未执行；既有 AI 可用/不可用规则通过本地测试，未伪造外部联调。
- M11 原 Milkdown 参考代码/依赖仍保留；活跃编辑页只使用 Vditor。后续可单独整理历史参考与开发依赖告警。

## 协作与风险闭合

Codex 当前元数据 `gpt-6.1-sol / ultra`；DSH config/probe 与交付记录为 `buddy / deepseek-v4.1-flash / max`，未切模型或修改全局配置。DSH 完成归档/灯箱审查与编辑器专项审查；首页编码任务未产生文件，已取消并由 Codex 接管，没有虚记子代理实现贡献。

已闭合审查发现：交付遗漏构建插件、灯箱缩放、旧IP断言、重复图片索引、独立正文上传锁定时序。主代理复核 diff、运行测试和真实浏览器验收。新实现相对 M12 起点的文件清单见 [修改清单](M12-CHANGES.md)。
