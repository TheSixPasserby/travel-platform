# uni-app Actions 分渠道发布实施计划

**目标：** 每次分支 push 为 nightly；手动 beta/正式；产出 Android APK、H5、微信工程并发布 GitHub Releases。App 内填写服务器地址。

**已确认：** 用户选择 Android APK（暂不含 iOS），同时发布 H5/微信资源；nightly/beta 为预发布，release 为正式发布；允许代配签名与仓库配置，DCloud 已由用户登录。保留现有 Web/后端和既有移动页布局。

**方案：** 保留 ci.yml 验证；新增 mobile-release.yml，以独立 metadata/build/android/publish 步骤组织。只有 publish 有 contents:write。PR 不签名/不发布；手动仅 main，channel 只 beta/release。标签推送不触发 nightly；每次 push 的最新提交生成独立、不可覆盖的版本标签。先创建 draft，资产/校验和上传完再发布。

**版本：** 基础版本来自 mobile/package.json；nightly 为 X.Y.Z-nightly.北京时间日期.run.attempt，beta 为 X.Y.Z-beta.run.attempt；正式为 X.Y.Z。Android versionCode=100+workflow run number，跨渠道共用单调序列；现有版本/标签不覆盖。

**原生：** 官方 DCloud Android SDK 5.34（匹配现有编译器），SDK 36/build-tools36/Gradle8.14.3/AGP8.12；固定 PKCS12 签名，秘钥仅本机私有目录和 GitHub Secrets。AppID/AppKey须在本人DCloud账号登记。官方SDK目前只提供网盘，不能臆造下载URL或使用第三方demo身份。

**缺配置行为：** nightly 仍可发布准确标注的 H5/微信/App 资源，并明确 APK 未生成及缺项；beta/正式若原生缺配置则停止，不能发布资源包冒充APK。所有实际APK需验证签名/包名/版本。

## 文件与验收

- [x] `mobile/src/**`、`mobile/tests/**`：Codex协作实现服务器设置，RED→GREEN；设置修改时清会话、隔离迟到响应，uni API跨端；根代理统一构建。
- [x] `scripts/mobile_release.py` 与测试：事件/版本/分支校验、manifest临时注入、bundle/清单/notes；验证非法输入与资源包无敏感文件。
- [ ] `scripts/android_package.py`、`scripts/android-init.gradle` 与测试：受控官方SDK校验/资源导入/固定签名Gradle构建/真实APK核验；缺配置明确阻断。
- [x] `.github/workflows/mobile-release.yml`：push nightly、手动beta/release、权限/配置门禁、构建与Releases；保留ci.yml。
- [ ] 本机生成固定签名并设入仓库Secrets；创建DCloud应用并申请匹配AppKey；取得正版SDK及哈希。若账户/SDK仍受围栏阻碍，保留实际证据与缺项，不冒称已生成APK。
- [ ] Python与mobile安全测试、类型、三目标构建、actionlint、真实Actions/nightly产物；更新状态/发布说明/交付白名单后提交推送。

## 关键边界

持久签名不按每个run随机重建；签名材料、密码和AppKey不进源码/日志/发布包；manifest注入在源安全检查之后。下载的SDK须与公开来源和SHA256匹配。构建失败不发布正式版；标签和已有Release不强制覆盖。
