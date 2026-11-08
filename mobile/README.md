# 山水间移动端

Vue 3、TypeScript 与 uni-app，攻略、目的地、登录、收藏和私人行程只读共用一套源码。通过 type-only import 复用 `../frontend/src/types` 契约，单独复制 mobile 目录时需同时保留该目录。

## 本地运行

在仓库根目录使用 Node 24 LTS 与 npm。

```bash
npm --prefix mobile ci
npm --prefix mobile run dev:h5
```

H5 开发入口 http://127.0.0.1:5174，默认代理到现有后端 http://127.0.0.1:8080。与 Web 使用同一个账号和 `/api/v1`、`satoken` 契约。移动端无需客户端演示凭据，密码不持久化。

## 编译与核验

```bash
npm --prefix mobile run type-check
npm --prefix mobile run check:safety
npm --prefix mobile run build:h5
npm --prefix mobile run build:mp-weixin
npm --prefix mobile run build:app
```

产物分别位于 `mobile/dist/build/h5`、`mobile/dist/build/mp-weixin`、`mobile/dist/build/app`。App 资源需要 HBuilderX 配套原生运行基座或原生打包链进一步处理。

## 在应用中填写服务器地址

Android App 和微信端未配置默认服务器时，首次进入会打开“服务器设置”。填写后端地址并点“保存并进入”，以后可通过“我的 → 服务器设置”修改。地址保存在当前设备，不需要重新编译安装包。

- 可填写完整接口地址，例如 `https://travel.example/api/v1`，也可填写根地址，由应用补全 `/api/v1`。
- 支持 HTTP／HTTPS、合法域名、IPv4、IPv6 和端口；不接受用户名密码、查询参数、片段或无关路径。非法输入不会覆盖当前地址或清除原登录态。
- 修改为另一个地址会先清除旧服务器的令牌和账号，清空旧页面数据；旧请求迟到也不能恢复旧数据或清除新服务器会话。重新保存相同地址不会退出登录。
- 保存操作只保存地址并进入攻略页；服务器不可达时由页面显示错误和重试，不把“已保存”当作“已连通”。

公网使用 HTTPS。手机连接本机开发服务时可填写电脑的局域网地址，例如 `http://192.168.1.5:8080/api/v1`；手机需能访问该电脑和端口，不能把手机自身的 `127.0.0.1` 当作电脑。此示例不是项目固定服务器地址。

H5 仍默认使用本站 `/api/v1` 和原攻略首页；服务器设置中可选择“使用当前网站接口”。如果 H5 改连其他来源，后端需允许精确的 H5 CORS 来源；HTTPS 页面连接 HTTP 接口还会受到浏览器限制。API 与受管 `/uploads` 图片使用同一服务器，外部图片地址继续被拒绝。

`VITE_API_BASE_URL` 仅作为可选的构建默认值。需要时可在忽略的 `.env.local` 中填写公开地址；本次发布不要求把开发机 IP 或服务器凭据写入安装包。

## AppID 与原生安装包

本项目已登记 DCloud AppID `__UNI__284320D`，Android 包名为 `io.github.thesixpasserby.travel`；固定签名指纹和 Actions 配置见 [移动构建与发布](../docs/MOBILE_RELEASES.md)。签名私钥和密码仅放在 GitHub Secrets 与忽略的本地目录，不放进源码或发布附件。

源码 manifest 保持 AppID 占位，Actions 在源码安全检查通过后从仓库 Variables 注入真实 AppID 和版本。微信 AppID 尚待配置；微信开发者工具可导入 `dist/build/mp-weixin` 工程，无 AppID 不代表已完成线上发布。正式配置保持 `urlCheck=true`，填写服务器地址不能绕过 request、downloadFile 和图片合法域名规则。

当前先接入 Android APK、H5 站点和微信工程产物。App 资源仍需匹配的 DCloud 5.34 离线 SDK、AppKey 和原生签名链生成 APK；iOS IPA 未接入此次流水线，不能用 App 资源 ZIP 代替真机安装包。

## Actions 渠道

- 每次分支 push 的最新提交自动构建 `nightly`，发布为 GitHub 预发布版本。
- 在 `main` 手动运行 **Mobile packages and releases**，选择 `beta` 或 `release`，输入 `X.Y.Z` 基础版本。beta 为预发布，release 为正式发布并更新 Latest。
- 原生配置未齐时，nightly 可发布明确标注“未生成 APK”的资源产物；beta／正式版会停止，不能发布不完整安装包。

完整渠道、版本号、附件说明与配置清单见 [MOBILE_RELEASES.md](../docs/MOBILE_RELEASES.md)。

## 范围与安全展示

已编码服务器设置、登录/恢复/退出、分页搜索攻略与目的地、城市下属景点、详情和图片预览、收藏/取消收藏、我的收藏、私人行程列表/每日项目只读。原九页加一页服务器设置，共十页；既有页面布局保留。每次进入私有页面重新校验会话，隐藏、切换账号或切换服务器时清空内容并拒绝迟到结果。

正文使用原生文本排版，支持标题、段落、引用、代码与受管图片。HTML 作为文字展示，外部图片降级为文字，链接显示为可复制的来源文本，复杂 Markdown 保留为可读文本。页面组件不使用 DOM、Vue Router、localStorage 或 DOMPurify。

本阶段不包含移动端注册、攻略发布、点赞/评论、行程编辑、AI 和后台，这些按 M9 P1 或现有 Web 入口继续提供。

## 实际验收状态

服务器设置源码已编码，移动安全测试 22/22 与类型检查已在本地通过。新的 Actions 发布、DCloud SDK／AppKey 配置和真实 APK 构建仍在验收，不据此声称已生成 APK 或远端 CI 已通过。原 M9 的编译、H5、微信／Android 真机及签名验收记录仍见 [M9 交付状态](../docs/M9-DELIVERY-STATUS.md)；本次发布范围与待办见 [移动构建与发布](../docs/MOBILE_RELEASES.md)。

依赖基于 DCloud 官方 Vue3/Vite/TS 模板的同版工具包，所有 DCloud 编译包固定 `3.0.0-5030420261029002`，Vue 3.4.21、Vite 5.2.8、TypeScript 5.9.3。版本来源见 [官方 CLI 指南](https://uniapp.dcloud.net.cn/quickstart-cli.html) 和 [官方 TypeScript 模板](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts)。
