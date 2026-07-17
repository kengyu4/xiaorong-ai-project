import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/http'
import { clearLegacyApiKeys } from './api/legacyApiKeyCleanup'
import { useAuthStore } from './stores/auth'

if (typeof window !== 'undefined') {
  clearLegacyApiKeys(window.localStorage)
}

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

const auth = useAuthStore(pinia)
setUnauthorizedHandler(() => {
  auth.clearSession()

  const currentRoute = router.currentRoute.value
  if (currentRoute.path === '/login') return

  void router.replace({
    path: '/login',
    query: { redirect: currentRoute.fullPath },
  })
})

app.use(router)

app.mount('#app')
