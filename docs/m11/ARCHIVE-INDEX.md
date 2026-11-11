# 移动平台历史索引（Web 交付附录）

自 M11（2026-11-11）起，本项目只维护 Vue Web 与 Spring Boot 后端，停止 uni-app、微信小程序、Android/iOS 原生应用的开发、自动编译、测试和发布。手机用户继续使用响应式 Web。

Web 交付包保留此索引；归档源码位于仓库的 `deprecated/uni-app/`，不随 Web 交付包构建或安装。

## 仓库归档内容

- `mobile/`：原 Vue 3 / TypeScript / uni-app 九页 MVP，曾输出 H5、微信小程序及 App 资源。
- `scripts/`、`tests/`：移动资源发布、Android SDK/打包/首启脚本及专用测试。属于历史快照，其原相对路径不再作为可运行接口维护。
- `workflows/mobile-release.yml.disabled`：原自动 nightly / 手动 beta / release 工作流，已移出 GitHub Actions 发现目录，不能继续触发。

最后源码基线：`main@29fa630bf097c2afd45b9711995fe655bfc4f7c6`。2026-11-10 已验证固定签名 APK、API35 x86_64 模拟器首启、nightly/beta 预发布；未验证 Android 真机、微信线上发布或 iOS 安装包。

## 历史索引

- [M9 交付记录](../../docs/M9-DELIVERY-STATUS.md)
- [2026-11-10 Android 验收证据](../../docs/verification/2026-11-10-android-releases.json)
- [模拟器首启截图](../../docs/verification/2026-11-10-android-first-launch.png)
- [Nightly 9.1](https://github.com/TheSixPasserby/travel-platform/releases/tag/v0.1.0-nightly.20261110.9.1)
- [Beta 10.1](https://github.com/TheSixPasserby/travel-platform/releases/tag/v0.1.0-beta.10.1)

历史 APK、Release 和验收报告保持保留。本次不读取、删除或修改 GitHub Actions Secrets。

## 恢复边界

如将来明确决定恢复维护，应从上述 Git 提交建立独立分支，在隔离工作区恢复原目录，并重新审查依赖、平台政策、签名和全部测试。归档工作流不得直接复制回活跃目录；恢复构建或发布需要新的明确授权。当前 Web 交付和验收不依赖这些文件，也不运行归档测试。

签名证书、密码、DCloud AppKey、SDK、真实上传和本机 `.env` 继续保存在本机忽略目录或远端 Secrets；不纳入归档源码或交付包。目录中可能存在的本地 `node_modules`、`dist`、缓存均被忽略，不是归档交付物。
