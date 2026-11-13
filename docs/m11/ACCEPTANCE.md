# M11 Web-only 验收报告

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

日期：2026-11-11（Asia/Shanghai）。基线：本机和远端 `main@29fa630bf097c2afd45b9711995fe655bfc4f7c6` 一致。本轮在原工作区实施，未覆盖用户原始 ZIP 或 M11 要求文件；未提交、推送、合并、部署或发布远端版本。

## 已实现

- 攻略正文、封面、图库以及城市/景点封面、实景图库使用共享灯箱。正文五图同组、封面独立、图库独立；计数/alt、方向键/Esc/关闭/遮罩、触摸滑动、焦点返回、背景锁、失败图、快速切换、重复URL和卸载清理均已覆盖。只接受现有受管路径；原作者、许可和来源链接保留；头像、Logo和导航卡片不进入灯箱。
- 新攻略默认可视化；工具栏包含 H1/H2/H3、粗体、斜体、列表、引用、链接、分隔线、撤销和重做。保留可选源码模式及原 Markdown 数据/API/后端校验。特殊内容完整回退源码，未修改时不重写旧正文。[编辑器兼容性裁定](EDITOR-COMPATIBILITY.md)。
- 两种模式均在当前选区插图，沿用现有上传、类型/5MB/20张约束；可编辑图片说明，上传失败不新增图库记录。图库关联和正文独立维护，移出图库需确认，不删除上传文件。
- 保存防重复、上传中禁止发布/离开，未保存离开和刷新需确认。上传/写请求401清除失效会话，同时保留当前页草稿；读取原文401仍前往登录。取消导航不再改变浏览器标题。
- 50个移动相关跟踪文件通过 `git mv` 移至 `deprecated/uni-app/`（[历史索引](ARCHIVE-INDEX.md)），逐字节保持。独立移动工作流移出 Actions 目录；主 CI、启动器和交付工具移除移动构建及缓存依赖。旧移动参数仅返回弃用报错。
- 原数据库、API、权限、作者/状态/发布时间、互动和上传机制不迁移。没有清库、重导seed或删除历史文件/Release。

## 实际执行的自动化

| 命令或检查 | 结果 |
|---|---|
| `python3 scripts/backend.py package` | 249测试，0失败/错误/跳过；可执行JAR成功 |
| `npm --prefix frontend test` | 37文件，363测试通过 |
| `npm --prefix frontend run build` | vue-tsc与Vite生产构建成功 |
| `python3 -m unittest discover -s scripts/tests -v` | 活跃88/88通过；未运行归档测试 |
| `python3 scripts/release.py .local/m11/web-delivery` | 实际完整构建、517文件交付成功；逐项SHA256核对一致；不含mobile/deprecated/移动工作流、真实上传或本机.env |
| 交付包内Python测试 | 86通过，2个Git仓库归档专属检查按设计跳过（包不含归档工程） |
| `./start.sh` | 实际执行返回0，仅复用既有后端8080/Web5173，输出局域网Web地址 |
| actionlint 1.7.12、`bash -n start.sh`、Git diff whitespace | 通过 |
| `npm --prefix frontend audit --omit=dev --json` | 运行依赖0漏洞；完整开发依赖历史4 moderate不在本轮强制升级 |
| 归档哈希 | 50个移动源码/脚本/测试/工作流与HEAD原文件逐字节相同 |
| 原数据指纹 | 14表135行与44上传文件SHA256前后完全一致 |

文档刷新后的最终交付位于 `.local/m11/web-delivery-verified/`；SHA256SUMS及实际数量见本地交付核验回执。该目录不纳入Git。测试原始日志和私有验收副本保存在忽略目录 `.local/m11/`；公开摘要见 [机器证据](../verification/2026-11-11-m11.json)。

## 真实 Chromium 验收

在新建数据库副本及上传副本中执行业务写入，现有8080开发服务保留。浏览器脚本：[公共灯箱](BROWSER-PUBLIC.js)、[编辑旧文](BROWSER-EDITOR.js)、[城市/景点](BROWSER-DESTINATIONS.js)、[新建攻略](BROWSER-NEW-GUIDE.js)。

- 攻略/灯箱8组、编辑器8组、城市/景点16组：浅/深色 × 360/390/430/1440px，无横向溢出；减少动效已启用。
- 旧文无改动保存逐字一致；视觉编辑→源码→视觉→保存→刷新保持内容；新建页面默认可视化，真实工具栏、撤销/重做、保存草稿/发布/撤回经过界面操作。作者和首次发布时间保持。
- 源码及默认可视化的真实上传均按浏览器实际光标插入；图片alt可编辑；503和401保留未保存内容；恶意HTML通过真实剪贴板粘贴，不执行脚本、不加载外链图。
- 五图任意索引、快速切图、键盘焦点循环、Esc、遮罩、关闭按钮、失败图和跨页面清理通过；手机滑动及点击关闭使用Chromium CDP触摸输入，非JavaScript伪造事件。
- 原生confirm与beforeunload分别触发，使用浏览器原生取消操作后核实草稿完整。匿名写入401，非作者更新被拒绝。
- 全部批量浏览器结果未捕获pageerror。控制台中503/401为刻意注入的失败请求，不表述为“没有任何网络错误”。

已目视检查：手机与桌面灯箱、编辑器深浅主题、新建可视化、源码回退和目的地署名。没有发现图片拉伸、控件遮挡、文字不可读或工具栏撑破页面；长正文通过页面滚动阅读。截图：

- [390px灯箱](screenshots/lightbox-light-390.png)、[桌面灯箱](screenshots/lightbox-dark-1440.png)、[失败图](screenshots/lightbox-failure-390.png)
- [新建可视化](screenshots/new-guide-visual-390.png)、[手机编辑器](screenshots/editor-light-390.png)、[桌面编辑器](screenshots/editor-dark-1440.png)、[源码回退](screenshots/source-fallback-390.png)
- [城市图库](screenshots/destination-city-light-390.png)、[景点图库](screenshots/destination-scenic_spot-light-390.png)

## 数据保护及审查记录

三篇旧攻略4/5/6与既有内容夹具逐字相同；PoC使用真实Milkdown解析和序列化，不用模拟编辑器替代。无修改导出保留原始Markdown；首次撤销清空、特殊链接导入拒绝、标题内部id误判、空段落HTML占位、引用定义与行内空格保留均有实际失败测试和修复后的通过记录。

验收启动初期，旧后端启动器强制使用根上传目录，3张合成WebP因此落到原目录。已逐一核对为测试夹具哈希，精确移至验收副本，原44文件和业务表未改。随后以隔离JAR实例明确绑定副本上传目录，重新完成全部上传流程；最终前后原库和原文件SHA一致。没有删除原业务数据。

Codex本机配置核实为 `gpt-6.1-sol / ultra`，未修改全局模型设置。DSH配置/探测为 `buddy（CodeBuddy）/deepseek-v4.1-flash/max`；正式任务 `hub-5-mwaxnyj2` 因 Max 参数准备时序报错，0工具调用，无源码交付，由主代理接管。另有继承主代理配置的Codex只读辅助审查，发现均经主代理裁定和测试闭合；不虚记DSH贡献。

## 远端与未验证边界

- GitHub独立移动工作流 **411250960** 已实际停用，读取状态为 **disabled_manually**。不读取/删除Secrets；现有6个Release和37个资产保留。
- [现有main CI](https://github.com/TheSixPasserby/travel-platform/actions/runs/42795466035) 成功对应旧基线29fa630，**不能算作M11远端CI通过**。本轮没有推送，新的Web-only ci.yml尚未在远端执行；远端综合CI仍是历史配置，需将来经授权同步本地修改后生效。
- 未部署外部环境；未在物理手机、Safari或微信浏览器执行本轮验收。触摸为真实Chromium浏览器协议输入，不声称真机验收。
- 本轮不恢复归档平台构建、不触发移动发布、不删除历史APK/Release。

## 规范验收勾选

- [x] 正文/封面/图库灯箱、键盘/触摸/关闭、署名保留。
- [x] 新攻略默认可视化；旧文安全往返，源码可选且特殊内容不丢失。
- [x] 原上传约束、业务元数据与权限保留；未保存修改保护。
- [x] 移动源码/专用脚本/测试/工作流归档；本地活跃CI/交付不依赖移动工程。
- [x] 远端独立移动发布工作流停用；历史产物保留。
- [x] 后端/Web/Python测试、生产构建、启动器和真实交付通过。
- [ ] M11远端CI：未推送，尚未验证。
- [ ] 部署和物理手机：本轮未执行。

完整变更文件见 [CHANGED-FILES.md](CHANGED-FILES.md)。
