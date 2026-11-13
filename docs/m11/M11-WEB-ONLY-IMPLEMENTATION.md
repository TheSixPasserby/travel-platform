# M11 · Web-only：图片灯箱、零门槛图文编辑与 uni-app 归档

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

> 仓库：`TheSixPasserby/travel-platform`\
> 基线：先以本机最新 `main` 为准，并和远端核对；截至本次审查远端为 `main@29fa630bf097c2afd45b9711995fe655bfc4f7c6`。\
> 范围：Web 前端和必要的构建/归档配置。保持 Spring Boot/MySQL 既有业务接口、用户资料、文章状态和数据不变。\
> 优先级：Web 端 > 移动原生端；不再维护、构建、测试或发布 uni-app。**保留手机浏览器响应式 Web。**

## 0. 启动与协作要求

- Codex 主代理继续使用用户指定的 `gpt-6.1-sol / ultra`，负责总体设计、审查、跨模块集成与验收；DSH 继续使用 `CodeBuddy deepseek-v4.1-flash / max`，仅在实际可调用时执行边界明确的任务。配置不一致不是错误，不要调整模型。
- 先检查 `git status -sb`、当前分支、最新提交、现有改动和 CI。不得覆盖未提交工作；如果需要建 M11 工作分支，应从实际最新 main 起步。
- 先核查 `GuideEditorView.vue`、`SafeMarkdown.vue`、`utils/markdown.ts`、`GuideDetailView.vue`、`DestinationGallery.vue`、`ImageUpload.vue`、`scripts/release.py`、`scripts/start.py`、`.github/workflows/ci.yml`、`.github/workflows/mobile-release.yml`，以及现有相关测试。
- 保持现有数据、受管上传路径、作者权限、草稿和发布状态。不要清库、重新导入 seed 或删除现有图片。除非必要，不更改数据库结构或 API 契约。
- 无明确授权不要推送、合并、发布；已有 Android Releases 属历史成果，不主动删除或撤销。不得泄露 keystore、DCloud AppKey 和其他 Secrets。

## 1. P0：点击图片预览（Lightbox）

### 覆盖范围

- 攻略详情：封面、Markdown 正文内受管图片、攻略图库；
- 城市及景点详情：封面、实景图库；
- 攻略编辑页：可按需要预览已上传封面/图库，不与上传、删除按钮冲突。
- 不拦截导航卡片的整卡点击；头像、Logo、导航图标不属于内容灯箱。

### 交互标准

1. 点击内容图片打开暗色遮罩灯箱，按视口自适应显示，完整保留图片比例，不拉伸。
2. 多张图片支持上一张/下一张、计数、图片描述（alt）；单图不显示无意义的翻页。
3. `Esc` 退出，左右方向键翻页，移动端支持滑动；有明确关闭按钮。
4. 放大、缩小或双指缩放可作为增强，但不能阻塞首版；至少支持浏览器内的合理缩放与高清图片显示。
5. 打开时锁定背景滚动，关闭后焦点回到原图片触发器；焦点不要跑到被遮罩的页面中。
6. 有加载中、图片加载失败、空图库和切换快速点击保护。主题深浅色兼容，遵从 `prefers-reduced-motion`。
7. 图片 URL 只使用现有 API 提供的受管图片路径或已经过既有白名单验证的来源，不引入任意远程 URL 加载。
8. 对 `SafeMarkdown.vue` 当前 `v-html` 中的图片，可在根容器做事件委托，只处理实际 `IMG` 且经过受管路径检查的元素。**不放开 DOMPurify 的 HTML 或危险属性白名单，不把用户输入注入 `innerHTML`。**
9. 优先封装 `ImageLightbox.vue` + 复用的 composable；可选采用 PhotoSwipe，但必须确认它对尺寸元数据的要求和当前图库 API 是否匹配，避免为了引入库进行大范围数据迁移。

### 验收

- 文章正文有 5 张配图时，点击任意一张可在同组间切换；封面/图库加入同组或独立分组，须一致且合理。
- 城市/景点图库保留原有作者、许可、来源标注，灯箱中显示描述但不丢失正文的署名信息。
- 键盘、手机触摸、深色模式、已损坏图片、跨页面切换和正文含恶意 Markdown 的场景均有测试。

## 2. P0：默认所见即所得，Markdown 仅为可选工具

### 产品目标

默认用户**不需要学习 Markdown**，打开写攻略页面就能像普通文档一样直接输入标题、正文、插图和排版。用户可以选择“Markdown 源码”模式，但它不是必填、不需要预先理解任何语法。

建议评估 `Milkdown Crepe`（原生 Vue 3 支持、所见即所得 Markdown）或 Tiptap + Markdown 扩展，优先选择能够稳定与既有 Markdown 数据往返转换的方案。需先做一份真实旧攻略的往返兼容性 Proof of Concept，再决定正式引入；Tiptap 官方 Markdown 扩展处于 Beta，不能忽略其语法边界。禁止让编辑器悄悄删除既有内容。

### 数据策略（重点）

- **保持 `guides.content` 为 Markdown 字符串**，既有 `GuideInput.content` / `GuideDetail.content`、`SafeMarkdown` 渲染、数据库和 API 不必迁移。
- 所见即所得仅改变编辑交互；保存时序列化回 Markdown，读取旧攻略时将 Markdown 导入编辑器。
- 保留“可视化 / Markdown 源码”模式切换；切换时保留光标附近上下文、撤销历史（可实现范围内）、上传内容和未保存修改。
- 对不支持的 Markdown 扩展、复杂表格、HTML、代码块或特殊语法，**不得静默丢失**。可在检测时保留源码模式并明确警示，或以安全的原样块保留；保存前做可测的往返差异校验。
- 保留原有后端 100000 Unicode 码点上限、标题/图片数量及服务端校验。正文允许空草稿，但发布必须非空。
- 绝不根据富文本内联 HTML 修改后端白名单为“允许任意 HTML”；不在数据库持久化未经清洗的 HTML。

### 默认编辑交互

- 清晰的正文编辑区域，可直接点击输入；工具栏支持：标题层级、粗体、斜体、列表、引用、链接、分隔线、撤销/重做、插入图片。
- 首版可支持表格，前提是旧 Markdown 表格在导入导出后不被破坏；不要为复杂表格拖延主要功能。
- 提示文案改成“直接输入正文，选中文字即可排版”，不要继续要求用户输入 `#` 或 `-`。
- 图片上传仍调用 `frontend/src/api/files.ts` / 现有 `ImageUpload.vue`：在光标位置插入图片（不是强行追加到文章末尾），`alt` 可编辑，沿用 JPG/PNG/WebP、5MB、受管 URL 和图库最多 20 张限制。
- 图片来源与图库表记录的对应关系、删除图片时的确认逻辑、上传失败时保留未保存文字均应明确处理。
- 编辑器保留文章标题、摘要、主城市、景点、标签、封面、状态、发布/撤回/删除等原业务。
- 保存过程防重复提交；上传中不可误发布；有未保存改动时离开页面进行确认，防止用户输入被意外丢弃。
- 手机浏览器保持可操作，工具栏不横向撑破页面，支持触控选区和软键盘；深浅色主题都可读。

### 回归验证

至少对现有 3 篇图文攻略和一篇带多级标题、列表、链接、引用、图片、表格、代码块的夹具执行：

1. 原 Markdown -> 可视化导入 -> 无改动导出 -> 安全渲染，验证语义、图片 URL 和重要文本均保留；
2. 可视化编辑插入/删除段落、图片、链接 -> 切换 Markdown -> 再切回可视化 -> 保存 -> 刷新后内容一致；
3. 原作者/草稿状态/发布时间/评论/点赞/收藏均不受影响；
4. 非作者不能编辑，匿名不能写入；安全 Markdown 和上传白名单未被弱化；
5. 富文本粘贴未知 HTML 和恶意脚本不会执行，非法图片来源被拒绝或安全降级。

## 3. P0：把 uni-app 整体放入废弃目录并停止所有构建

### 原则

将 uni-app 标记为 **archived / deprecated / unsupported**。保留源码和可追溯 Git 历史，但**绝不继续消耗 CI 资源、自动产出新包或要求 Web 交付依赖移动 SDK**。

建议目录 `deprecated/uni-app/`，按实物盘点后通过 `git mv` 移动：

- 整个 `mobile/` 工程 -> `deprecated/uni-app/mobile/`；
- 仅供 uni-app/Android 使用的脚本（如 `mobile_release.py`、`android_package.py`、`android_smoke.py`、`setup_android.sh`、`android-init.gradle`）及其专用测试、发布说明 -> 归档；
- `.github/workflows/mobile-release.yml` **必须移出 `.github/workflows/`**，例如放到 `deprecated/uni-app/workflows/mobile-release.yml.disabled`，确保不会继续在每次 push 时触发。不要仅在 README 中标注停用；
- 保留历史 `docs/verification/`、已发布 APK/Release 与历史报告的可追溯性，建议在归档 README 中给出索引；不必破坏原位置引用才能达到“不再维护”。

### 必须同步修正的活跃入口

1. `.github/workflows/ci.yml`：删除所有 `npm --prefix mobile ...` 操作，并清理移动端 cache 依赖；Web+后端+仍然有效的 Python 测试继续运行。
2. `scripts/release.py`：删除移动资源构建、移动文件白名单、强制读取旧移动脚本/工作流、移动产物复制；最终 Web 交付不能因为不存在根目录 `mobile/` 失败。
3. `scripts/start.py` 和 `start.sh`：默认只启动 Web+后端。移除 `--mobile` 或保留清楚的弃用报错，不得尝试启动归档工程。
4. `scripts/tests`：将纯移动专用测试归档或从活跃测试发现中隔离；保留覆盖 Web 安全/构建/交付边界的测试。需调整 Python import/路径和断言，避免因改目录产生误报。
5. `README.md`、`PROJECT_STATUS.md`、`AGENTS.md`、`PACKAGE-MANIFEST.json`、部署/交付说明更新为 **Web-only**。旧项目历史可保留，但不得把小程序/Android 作为必过交付门槛。
6. `.gitignore`：为归档构建产物、node_modules 等做好排除，不提交旧大型构建缓存、SDK、APK、密钥。
7. `deprecated/uni-app/README.md`：记载停止维护原因、原技术栈、最后已验证状态、历史版本/Release、恢复方法、秘密信息保存边界；不要求执行归档中的测试。
8. 完成后 `rg 'mobile/|--prefix mobile|build:mp-weixin|build:app|mobile-release|--mobile'` 全仓扫描；区分历史文档与仍然活跃的代码/构建引用，活跃引用必须清零。

### 注意

- 已在 GitHub Actions 配置的 Android/DCloud Secrets 不会因移动代码被归档自动消失；本次不读取或公开这些 Secrets。后续若确认永久弃用，可由仓库所有者另行安全清理。
- GitHub 历史 Releases、签名 APK 和校验记录不必删除。
- 不要为了“全 Web”移除手机浏览器适配，仍要验证 360/390/430px 和桌面布局。

## 4. 建议实施顺序

1. **Web 图片预览**：独立可测试的小功能，优先落地。
2. **富文本编辑器 PoC + 接入**：先保证旧 Markdown 往返安全，再替换默认编辑界面。
3. **uni-app 归档和 CI/交付瘦身**：把工作流等从活跃路径移走，同时修改工具并跑完整测试。
4. **最终回归**：后端权限、图片、文章保存、草稿、移动 Web、生产构建、交付包和远程 CI。

可并行，但归档时不要让其他 Agent 修改同一套脚本和文档。

## 5. 交付验收（不得凭口头宣称）

- [ ] Web 任意攻略正文图片点击后出现灯箱，Esc/键盘/触摸关闭和翻页正常。
- [ ] 城市/景点图库和封面可预览，图片说明/来源信息保留。
- [ ] 创建新攻略默认所见即所得，无需学习 Markdown。
- [ ] 旧攻略能安全往返编辑，Markdown 源码模式仍可选，特殊内容不丢失。
- [ ] 图片上传插入到当前光标处，受管路径/大小/类型限制不变。
- [ ] 已有数据、文章 ID、状态、作者、评论和收藏均保留。
- [ ] `mobile/` 与其专用脚本/工作流已迁至归档目录；根目录无活跃 uni-app 工程。
- [ ] 任意 Web 提交不再触发移动发布；主 CI 不再安装、测试或构建 uni-app。
- [ ] `./start.sh` 正常启动 Web+后端，Web 交付命令不依赖移动目录。
- [ ] 后端测试、Web 测试、Python 活跃工具测试、Web 生产构建均实际通过。
- [ ] 最新 main 或工作分支远端 CI 实际成功（未推送时明确标为尚未验证）。
- [ ] 交付报告区分已编码、已测试、已部署与尚未验证项；保留既有历史产物。

## 6. 给 Codex 的执行指令

按以上规范实施 M11。先快速审计并做编辑器导入/导出的兼容性验证；随后分阶段编码与测试，不停留在纯规划。对用户未授权的远端写操作、生产数据修改和密钥处理保持审慎。把旧数据完整性与 Web 交付可重复性放在首位。完成后提供变更文件清单、实际测试命令结果、浏览器截图和未完成项。
