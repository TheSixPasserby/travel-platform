# M11 变更文件清单

本页为历史阶段记录，日期、测试数量和当时范围按原记录保留。当前使用与开发入口见 [项目文档](../README.md)。

用户原始ZIP和M11规范输入保留原样，未列为代理新增。50个归档文件为100%内容相同的Git移动。

## 已有跟踪文件

```text
M	.github/workflows/ci.yml
M	.gitignore
M	AGENTS.md
M	PACKAGE-MANIFEST.json
M	PROJECT_STATUS.md
M	README.md
R100	mobile/.env.example	deprecated/uni-app/mobile/.env.example
R100	mobile/.gitignore	deprecated/uni-app/mobile/.gitignore
R100	mobile/README.md	deprecated/uni-app/mobile/README.md
R100	mobile/index.html	deprecated/uni-app/mobile/index.html
R100	mobile/package-lock.json	deprecated/uni-app/mobile/package-lock.json
R100	mobile/package.json	deprecated/uni-app/mobile/package.json
R100	mobile/src/App.vue	deprecated/uni-app/mobile/src/App.vue
R100	mobile/src/components/ContentCard.vue	deprecated/uni-app/mobile/src/components/ContentCard.vue
R100	mobile/src/components/PageNavigation.vue	deprecated/uni-app/mobile/src/components/PageNavigation.vue
R100	mobile/src/components/ResourceState.vue	deprecated/uni-app/mobile/src/components/ResourceState.vue
R100	mobile/src/components/SafeContent.vue	deprecated/uni-app/mobile/src/components/SafeContent.vue
R100	mobile/src/components/SafeImage.vue	deprecated/uni-app/mobile/src/components/SafeImage.vue
R100	mobile/src/composables/resource.ts	deprecated/uni-app/mobile/src/composables/resource.ts
R100	mobile/src/env.d.ts	deprecated/uni-app/mobile/src/env.d.ts
R100	mobile/src/main.ts	deprecated/uni-app/mobile/src/main.ts
R100	mobile/src/manifest.json	deprecated/uni-app/mobile/src/manifest.json
R100	mobile/src/pages.json	deprecated/uni-app/mobile/src/pages.json
R100	mobile/src/pages/destinations/detail.vue	deprecated/uni-app/mobile/src/pages/destinations/detail.vue
R100	mobile/src/pages/destinations/index.vue	deprecated/uni-app/mobile/src/pages/destinations/index.vue
R100	mobile/src/pages/favorites/index.vue	deprecated/uni-app/mobile/src/pages/favorites/index.vue
R100	mobile/src/pages/guides/detail.vue	deprecated/uni-app/mobile/src/pages/guides/detail.vue
R100	mobile/src/pages/guides/index.vue	deprecated/uni-app/mobile/src/pages/guides/index.vue
R100	mobile/src/pages/itineraries/detail.vue	deprecated/uni-app/mobile/src/pages/itineraries/detail.vue
R100	mobile/src/pages/itineraries/index.vue	deprecated/uni-app/mobile/src/pages/itineraries/index.vue
R100	mobile/src/pages/login/index.vue	deprecated/uni-app/mobile/src/pages/login/index.vue
R100	mobile/src/pages/me/index.vue	deprecated/uni-app/mobile/src/pages/me/index.vue
R100	mobile/src/pages/settings/server.vue	deprecated/uni-app/mobile/src/pages/settings/server.vue
R100	mobile/src/platform/errors.ts	deprecated/uni-app/mobile/src/platform/errors.ts
R100	mobile/src/platform/navigation.ts	deprecated/uni-app/mobile/src/platform/navigation.ts
R100	mobile/src/platform/request.ts	deprecated/uni-app/mobile/src/platform/request.ts
R100	mobile/src/platform/storage.ts	deprecated/uni-app/mobile/src/platform/storage.ts
R100	mobile/src/platform/upload.ts	deprecated/uni-app/mobile/src/platform/upload.ts
R100	mobile/src/platform/url.ts	deprecated/uni-app/mobile/src/platform/url.ts
R100	mobile/src/services/api.ts	deprecated/uni-app/mobile/src/services/api.ts
R100	mobile/src/services/session.ts	deprecated/uni-app/mobile/src/services/session.ts
R100	mobile/src/services/token.ts	deprecated/uni-app/mobile/src/services/token.ts
R100	mobile/src/types/api.ts	deprecated/uni-app/mobile/src/types/api.ts
R100	mobile/src/utils/content.ts	deprecated/uni-app/mobile/src/utils/content.ts
R100	mobile/tests/checks.ts	deprecated/uni-app/mobile/tests/checks.ts
R100	mobile/tsconfig.json	deprecated/uni-app/mobile/tsconfig.json
R100	mobile/vite.config.ts	deprecated/uni-app/mobile/vite.config.ts
R100	scripts/android-init.gradle	deprecated/uni-app/scripts/android-init.gradle
R100	scripts/android_package.py	deprecated/uni-app/scripts/android_package.py
R100	scripts/android_smoke.py	deprecated/uni-app/scripts/android_smoke.py
R100	scripts/mobile_release.py	deprecated/uni-app/scripts/mobile_release.py
R100	scripts/setup_android.sh	deprecated/uni-app/scripts/setup_android.sh
R100	scripts/tests/test_android_package.py	deprecated/uni-app/tests/test_android_package.py
R100	scripts/tests/test_android_smoke.py	deprecated/uni-app/tests/test_android_smoke.py
R100	scripts/tests/test_mobile_release.py	deprecated/uni-app/tests/test_mobile_release.py
R100	.github/workflows/mobile-release.yml	deprecated/uni-app/workflows/mobile-release.yml.disabled
M	docs/15-dependency-baseline.md
M	docs/22-deployment-and-operations.md
M	docs/23-user-manual.md
M	docs/M9-DELIVERY-STATUS.md
M	frontend/package-lock.json
M	frontend/package.json
M	frontend/src/api/session.ts
M	frontend/src/components/DestinationGallery.vue
M	frontend/src/components/SafeMarkdown.vue
M	frontend/src/router/index.ts
M	frontend/src/utils/markdown.ts
M	frontend/src/views/DestinationDetailView.vue
M	frontend/src/views/GuideDetailView.vue
M	frontend/src/views/GuideEditorView.vue
M	frontend/tests/guide-views.spec.ts
M	frontend/tests/m8-destination-gallery.spec.ts
M	frontend/tests/router-guard.spec.ts
M	frontend/tests/session-errors.spec.ts
M	scripts/release.py
M	scripts/start.py
M	scripts/tests/test_release.py
M	scripts/tests/test_start.py
```

## 本轮新增文件

```text
deprecated/uni-app/README.md
docs/m11/ACCEPTANCE.md
docs/m11/ARCHIVE-INDEX.md
docs/m11/BROWSER-DESTINATIONS.js
docs/m11/BROWSER-EDITOR.js
docs/m11/BROWSER-NEW-GUIDE.js
docs/m11/BROWSER-PUBLIC.js
docs/m11/CHANGED-FILES.md
docs/m11/EDITOR-COMPATIBILITY.md
docs/m11/IMPLEMENTATION-PLAN.md
docs/m11/QA-INVENTORY.md
docs/m11/screenshots/destination-city-dark-390.png
docs/m11/screenshots/destination-city-light-390.png
docs/m11/screenshots/destination-scenic_spot-dark-390.png
docs/m11/screenshots/destination-scenic_spot-light-390.png
docs/m11/screenshots/editor-dark-1440.png
docs/m11/screenshots/editor-dark-390.png
docs/m11/screenshots/editor-light-1440.png
docs/m11/screenshots/editor-light-390.png
docs/m11/screenshots/lightbox-dark-1440.png
docs/m11/screenshots/lightbox-dark-390.png
docs/m11/screenshots/lightbox-desktop-light.png
docs/m11/screenshots/lightbox-failure-390.png
docs/m11/screenshots/lightbox-light-1440.png
docs/m11/screenshots/lightbox-light-390.png
docs/m11/screenshots/new-guide-visual-390.png
docs/m11/screenshots/source-fallback-390.png
docs/verification/2026-11-11-m11.json
frontend/src/components/ContentImage.vue
frontend/src/components/ImageLightbox.vue
frontend/src/components/MarkdownEditor.vue
frontend/src/composables/useImageLightbox.ts
frontend/src/utils/guideEditor.ts
frontend/tests/image-lightbox.spec.ts
frontend/tests/markdown-editor-component.spec.ts
frontend/tests/markdown-editor.spec.ts
scripts/tests/test_web_only.py
```
