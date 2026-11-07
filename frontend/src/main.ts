import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import { connectSessionErrors } from './api/session'
import { router } from './router'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import './styles/main.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
useThemeStore(pinia)
app.use(router)

connectSessionErrors(useAuthStore(pinia), router)

app.mount('#app')
