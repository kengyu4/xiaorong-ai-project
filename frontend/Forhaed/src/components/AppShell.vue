<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import RuntimeBadge from './RuntimeBadge.vue'

const auth = useAuthStore()
const router = useRouter()
const loggingOut = ref(false)

async function logout() {
  if (loggingOut.value) return

  loggingOut.value = true
  try {
    await auth.logout()
  } finally {
    loggingOut.value = false
    router.push('/login')
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/app/courses">
        <span class="brand-mark">AI</span>
        <span class="brand-copy">
          <strong>小绒老师助教</strong>
          <small>固定题库课堂</small>
        </span>
      </RouterLink>

      <nav class="nav-links">
        <RouterLink to="/app/courses">课程</RouterLink>
        <RouterLink to="/admin/runtime">运行状态</RouterLink>
      </nav>

      <div class="top-actions">
        <RuntimeBadge />
        <span class="chip mint">{{ auth.username || '同学' }}</span>
        <button class="btn ghost" type="button" :disabled="loggingOut" @click="logout">
          {{ loggingOut ? '退出中' : '退出' }}
        </button>
      </div>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  width: min(1220px, 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: 22px;
}

.topbar {
  margin-bottom: 20px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 18px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.brand-mark {
  width: 46px;
  height: 46px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: var(--primary-strong);
  background: linear-gradient(145deg, #ffffff, #dcecf6);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    var(--shadow-soft);
  font-weight: 900;
}

.brand-copy {
  display: grid;
  gap: 2px;
}

.brand-copy strong {
  font-size: 18px;
  white-space: nowrap;
}

.brand-copy small {
  color: var(--muted);
  font-size: 13px;
}

.nav-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.nav-links a {
  border-radius: 999px;
  padding: 9px 14px;
  color: var(--muted);
  font-weight: 900;
}

.nav-links a.router-link-active {
  color: var(--primary-strong);
  background: #edf6fb;
}

.top-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.app-main {
  min-width: 0;
}

@media (max-width: 860px) {
  .topbar {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .nav-links {
    justify-content: flex-start;
    overflow-x: auto;
    width: 100%;
  }

  .top-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 560px) {
  .app-shell {
    padding: 14px;
  }
}
</style>
