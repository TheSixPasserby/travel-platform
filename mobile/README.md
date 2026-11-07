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

## 手机与小程序配置

复制 `.env.example` 为忽略的 `.env.local`，设置公开 `VITE_API_BASE_URL=https://你的服务域名/api/v1`。API 和 `/uploads` 图片同源，客户端仅接受后端受管图片路径。小程序后台配置 request、downloadFile 以及图片相关合法域名。H5 生产环境配置相同来源的 API/图片反向代理，或给后端配置精确的 H5 CORS 来源。

用户目前没有微信 AppID。`src/manifest.json` 的 uni-app AppID 和微信 AppID 均留空。运营者获得对应 AppID 后填写再重新编译。微信开发者工具导入 `dist/build/mp-weixin`，无 AppID 仅能按工具支持的测试能力调试，必须如实记录域名校验和登录条件。正式配置保持 `urlCheck=true`。

Android 真机可用，连接和打包安排在代码完成后。Android/iOS 使用相同 App 资源，需配置可访问的 HTTPS 地址。Android 签名与原生安装包依赖 HBuilderX/SDK 或已授权的打包环境。iOS 另需 Apple 证书、描述文件与 Xcode/对应打包条件。配置文件、签名私钥与账户凭据仅在本机或构建环境维护。

## 范围与安全展示

已编码登录/恢复/退出、分页搜索攻略与目的地、城市下属景点、详情和图片预览、收藏/取消收藏、我的收藏、私人行程列表/每日项目只读。每次进入私有页面重新校验会话，隐藏或切换账号时清空内容并拒绝迟到结果。

正文使用原生文本排版，支持标题、段落、引用、代码与受管图片。HTML 作为文字展示，外部图片降级为文字，链接显示为可复制的来源文本，复杂 Markdown 保留为可读文本。页面组件不使用 DOM、Vue Router、localStorage 或 DOMPurify。

本阶段不包含移动端注册、攻略发布、点赞/评论、行程编辑、AI 和后台，这些按 M9 P1 或现有 Web 入口继续提供。

## 实际验收状态

源码已编码。编译、H5 浏览器、微信开发者工具、Android 真机、APK 和 iOS 签名验收以根目录 `docs/M9-DELIVERY-STATUS.md` 的执行记录为准。

依赖基于 DCloud 官方 Vue3/Vite/TS 模板的同版工具包，所有 DCloud 编译包固定 `3.0.0-5030420261029002`，Vue 3.4.21、Vite 5.2.8、TypeScript 5.9.3。版本来源见 [官方 CLI 指南](https://uniapp.dcloud.net.cn/quickstart-cli.html) 和 [官方 TypeScript 模板](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts)。
