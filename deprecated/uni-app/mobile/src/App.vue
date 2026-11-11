<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { restoreSession } from './services/session'
import { hasApiBase } from './platform/url'
import { openServerSettings } from './platform/navigation'
onLaunch(() => {
  if (!hasApiBase()) { openServerSettings(true); return }
  void restoreSession().catch(() => undefined)
})
</script>

<style>
page { background: #f9f7f2; color: #25352c; font-family: 'PingFang SC', system-ui, sans-serif; font-size: 16px; line-height: 1.65; }
view, input, textarea, button { box-sizing: border-box; }
.screen { padding: 20px 18px calc(32px + env(safe-area-inset-bottom)); overflow-wrap: anywhere; }
.card { margin: 16px 0; padding: 18px; border: 1px solid #dce3d8; border-radius: 18px; background: #fffdf8; overflow: hidden; }
.title { display: block; color: #0d3f2f; font-size: 26px; font-weight: 600; line-height: 1.4; margin: 8px 0 16px; }
.subtitle { display: block; font-size: 20px; color: #145c45; font-weight: 600; margin-bottom: 8px; }
.muted { color: #55645b; font-size: 14px; }
.error { color: #a65028; }
.row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.screen button { min-height: 44px; margin: 8px 0; padding: 8px 18px; border-radius: 24px; font-size: 15px; line-height: 1.8; background: #145c45; color: #fffdf8; }
.screen button::after { border: none; }
.screen button[disabled] { opacity: .55; }
.screen button.secondary { background: #e1e9dc; color: #0d3f2f; }
input { min-height: 46px; width: 100%; padding: 10px 12px; margin: 8px 0 16px; border: 1px solid #94ac9c; border-radius: 12px; background: #fffdf8; font-size: 16px; }
.label { display: block; font-size: 14px; color: #55645b; }
.pager { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 20px; }
.pager button { font-size: 14px; padding-inline: 14px; }
.body-text { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
