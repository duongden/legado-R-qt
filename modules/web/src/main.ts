import { createApp } from 'vue'
import App from './App.vue'
import router from '@/router'
import store from '@/store'
import { initializeRelaySession } from '@/api/relay'
import 'element-plus/theme-chalk/dark/css-vars.css'

void initializeRelaySession()
  .catch(() => undefined)
  .finally(() => createApp(App).use(store).use(router).mount('#app'))
window.addEventListener('vite:preloadError', event => {
  event.preventDefault()
})
