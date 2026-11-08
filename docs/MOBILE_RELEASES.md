# 移动端构建与发布

本流水线先构建 Android APK，同时提供 H5 站点、微信开发者工具工程和 uni-app App 资源。配置文件为 [mobile-release.yml](../.github/workflows/mobile-release.yml)，原 [Build and verify](../.github/workflows/ci.yml) 验证流水线继续保留。

## 渠道与触发规则

| 操作 | 渠道 | GitHub Releases | Latest |
|---|---|---|---|
| 分支 push | nightly | 预发布 | 不更新 |
| 在 main 手动运行，选择 beta | beta | 预发布 | 不更新 |
| 在 main 手动运行，选择 release | 正式版 | 正式发布 | 更新 |

nightly 表示自动构建渠道，没有每日定时任务。一次 push 构建该次 push 的最新提交；PR 走原验证流程，不签名、不发布。仅分支 push 触发 nightly，发布标签不会再次触发构建。没有会丢弃同分支排队构建的共享并发组。

手动操作：进入仓库 **Actions → Mobile packages and releases → Run workflow**，选择 `main`，选择 `beta` 或 `release`，填写 `X.Y.Z`，例如 `0.1.0`。非 main 的手动 beta／正式构建会被拒绝。

每个版本使用独立标签。已存在的同名 Git tag 或 Release 均拒绝复用、移动或覆盖；无法确认标签是否空闲时也停止发布。先建立 draft，核验并上传全部附件后才正式公开，上传失败只留下 draft，不产生附件不全的正式版。

## 版本与来源

自动 nightly 的基础版本取自 `mobile/package.json`；手动构建使用输入的基础版本，只接受不带 `v`、预发布后缀或前导零的 `X.Y.Z`。

| 渠道 | 标签示例 |
|---|---|
| nightly | `v0.1.0-nightly.20261108.12.1` |
| beta | `v0.1.0-beta.13.1` |
| 正式版 | `v0.1.0` |

nightly 日期按北京时间生成；末尾数字为 workflow run number 和 attempt，重跑不会覆盖既有 nightly／beta 版本。Android 内部 `versionCode = 100 + run number`，与版本名称分开管理。不要删除重建工作流以重置序号，也不要通过修改签名规避升级检查。

附件名称包含版本与源提交短 SHA；`build-info.json` 保留完整源 SHA、分支、渠道、版本、构建序号及 Android 状态。正式版本重跑已有标签会停止，应先核查发布结果，不自动覆盖。

## 下载物的含义

| 附件 | 使用方式 |
|---|---|
| `*-android.apk` | 实际原生编译并验证签名的 Android 安装包；仅构建成功时存在 |
| `*-h5-site.zip` | H5 静态站点，解压后仍需自行部署和配置服务连接 |
| `*-wechat-project.zip` | 微信开发者工具工程，不代表已上传、审核或线上发布小程序 |
| `*-uni-app-resources.zip` | uni-app App 资源，不能作为 APK／IPA 安装 |
| `android-verified.json` | APK 签名验证、包名、内部版本号、文件 SHA256 等核验记录 |
| `build-info.json`、`SHA256SUMS` | 本次来源、状态和附件完整性校验 |

资源和最终包同时保留为 Actions artifacts；资源／APK 保留 14 天，原生构建使用的中间 App 资源保留 1 天。GitHub Release 附件与 Actions 临时 artifacts 是不同存储位置，不能用 artifacts 过期状态推断 Release 是否存在。

## 应用内服务器配置

App／微信未设置默认地址时首次进入服务器设置；以后从 **我的 → 服务器设置** 修改。可填写根地址或完整 `/api/v1` 地址，地址保存在当前设备。只允许结构合法的 HTTP／HTTPS 地址，不接受用户名密码、查询串、片段或任意路径。

切换服务器先清令牌和账号，再失效旧服务器的公共／私有页面；迟到的旧请求不能重新填充旧数据或误清新会话。保存相同地址不退出登录，非法输入不覆盖旧配置。保存并不等于联网探测成功，页面保留正常错误和重试。

H5 默认仍使用本站同源 `/api/v1`，设置页可恢复该默认。自定义跨域地址需要后端配置精确 CORS；小程序仍需真实 AppID、HTTPS 合法域名及平台要求。设置页不会绕过微信域名校验。局域网 HTTP 仅用于可达的可信测试服务，公网使用 HTTPS。

## Android 身份与仓库配置

已登记的公开身份：

- DCloud AppID：`__UNI__284320D`。
- Android 包名：`io.github.thesixpasserby.travel`。
- Android 签名 SHA1：`24:46:D5:21:34:6A:6F:AC:59:15:6B:39:D4:82:2D:0C:A2:5E:27:00`。

固定签名保存在 GitHub Secrets 和忽略的本地目录，不能上传到源码或 Release；后续 APK 升级继续使用同一签名。AppKey 必须由本应用的 AppID、包名与上述证书指纹对应，不能使用 DCloud 示例应用身份替代。

在仓库 **Settings → Secrets and variables → Actions** 配置下列项目。这里只列名称和用途，不记录私钥、密码或 AppKey 值。

| Repository Variables | 用途 |
|---|---|
| `DCLOUD_UNI_APPID` | 已登记的本项目 DCloud AppID |
| `ANDROID_PACKAGE_NAME` | 已登记的 Android 包名 |
| `ANDROID_CERT_SHA1` | 固定签名的 SHA1，用于核对 APK 签名 |
| `DCLOUD_ANDROID_SDK_URL` | 有合法使用权、与编译器匹配的 DCloud 5.34 SDK HTTPS 归档地址 |
| `DCLOUD_ANDROID_SDK_SHA256` | 已核对的 SDK 归档 SHA256，64 位十六进制 |
| `WECHAT_APPID` | 可选；取得真实微信 AppID 后用于生成微信工程 |

| Repository Secrets | 用途 |
|---|---|
| `DCLOUD_ANDROID_APPKEY` | 与本项目身份匹配的 Android 离线 AppKey |
| `ANDROID_KEYSTORE_BASE64` | 固定签名证书文件的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 证书库密码 |
| `ANDROID_KEY_ALIAS` | 签名别名 |
| `ANDROID_KEY_PASSWORD` | 签名私钥密码 |

不要求给 APK 配置固定服务器 URL：使用者进入应用后填写。源码安全检查先运行，之后才由构建步骤临时向 manifest 注入真实 AppID 和版本，不需要把 Secrets 写入 manifest 源码。

## 原生构建与发布门禁

原生打包使用 DCloud 官方 `HBuilder-Integrate-AS/simpleDemo` 模板与匹配的 5.34 SDK。工具链使用 Android compileSdk／targetSdk 36、Build Tools 36.0.0、Gradle 8.14.3、Android Gradle Plugin 8.12.0、Java 17 构建环境；资源编译器仍使用项目锁定的 uni-app 版本。

执行前校验完整配置和归档 SHA256，拒绝不安全解压路径、资源符号链接、错误 AppID 或不匹配的资源编译器。构建后通过 `apksigner` 和 `apkanalyzer` 核对真实 APK、签名指纹、包名与内部版本号，再产生 APK SHA256 和核验记录；不能把任意 ZIP 改后缀作为安装包。

- 原生配置未齐：nightly 可以发布 H5、微信工程、App 资源，Summary、Release Notes 和 `build-info.json` 明确标为未生成 APK，并列缺失配置名。
- beta／正式版：原生配置不全或缺少验证后的签名 APK 即停止，不发布只有资源的 beta／正式版。
- 配置齐全但原生构建或校验失败：不会继续公开 Release；Actions 已成功的资源 artifacts 可供排查。
- 构建工作默认只有 `contents: read`；只有 publish job 获得 `contents: write`。签名密码不进入命令行参数或公开构建信息，私有构建诊断不作为发布附件上传。

## 当前状态与待验收

2026-11-08：服务器设置已完成；Python 93/93、移动安全 22/22、类型与三个资源目标构建通过，H5 浏览器 7 项通过。AppID、包名、签名指纹与离线 AppKey 均已在用户本人 DCloud 账号登记；固定签名及 AppKey 已存入 GitHub Secrets。

功能提交 `c8cfa254` 的[完整 CI](https://github.com/TheSixPasserby/travel-platform/actions/runs/42495703282)与[首个 nightly](https://github.com/TheSixPasserby/travel-platform/actions/runs/42495703308)均通过。[实际 Release](https://github.com/TheSixPasserby/travel-platform/releases/tag/v0.1.0-nightly.20261108.1.1)为预发布、非 Latest，含三个资源 ZIP；下载后 5 个附件 SHA256 全部匹配，原生资源内 AppID／版本／编译器及设置路由已核对。该次构建开始时 AppKey 尚未注入，历史缺项清单保留，不改写既有 Release。

[手动 beta 门禁](https://github.com/TheSixPasserby/travel-platform/actions/runs/42495829445)已实际验证：缺原生配置时 plan 按预期失败，其余构建及发布跳过，没有生成不完整 beta。

当前仍缺官方 SDK 归档及其校验值：百度网盘要求客户端下载，中国移动官方分享已达浏览上限，已请求用户下载 `Android-SDK@5.34.82692_20261030.zip` 后提供本机路径。Android Gradle 构建、真实 APK 验签和真机安装均未执行；尚不能声称已产出 APK。

微信 AppID／合法域名和线上发布尚待对应条件。iOS IPA 未纳入当前流水线；需要另行准备 Apple 签名、描述文件和对应构建环境。原 M9、M10 验收历史保持原记录。

官方资料：[DCloud Android 离线 SDK](https://nativesupport.dcloud.net.cn/AppDocs/download/android.html)、[Android 原生工程配置](https://nativesupport.dcloud.net.cn/AppDocs/usesdk/android.html)、[AppKey 申请与匹配规则](https://nativesupport.dcloud.net.cn/AppDocs/usesdk/appkey.html)。
