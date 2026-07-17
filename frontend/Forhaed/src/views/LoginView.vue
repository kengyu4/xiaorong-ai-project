<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import teacherImage from '@/assets/characters/teacher-explain.png'
import baiziImage from '@/assets/characters/baizi-happy.png'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const mode = ref<'login' | 'register'>('login')
const username = ref(auth.user?.username || '')
const nickname = ref(auth.user?.nickname || '')
const password = ref('')
const submitting = ref(false)
const error = ref('')

const isRegister = computed(() => mode.value === 'register')
const submitText = computed(() => {
  if (submitting.value) return isRegister.value ? '注册中...' : '登录中...'
  return isRegister.value ? '注册并进入' : '登录学习'
})

function switchMode(nextMode: 'login' | 'register') {
  mode.value = nextMode
  error.value = ''
}

async function enter() {
  const cleanUsername = username.value.trim()
  const cleanPassword = password.value

  if (!cleanUsername || !cleanPassword) {
    error.value = '请先填写用户名和密码。'
    return
  }

  submitting.value = true
  error.value = ''

  try {
    if (isRegister.value) {
      await auth.register({
        username: cleanUsername,
        password: cleanPassword,
        nickname: nickname.value,
      })
    } else {
      await auth.login({
        username: cleanUsername,
        password: cleanPassword,
      })
    }

    password.value = ''
  } catch (err) {
    error.value = err instanceof Error ? err.message : '认证失败，请稍后再试。'
    return
  } finally {
    submitting.value = false
  }

  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/app/courses'
  router.push(redirect)
}
</script>

<template>
  <main class="login-page">
    <section class="login-copy">
      <div class="eyebrow">小绒老师助教</div>
      <h1>固定题库课堂</h1>
      <p>小绒老师先讲，白子同桌陪你把题讲明白。</p>
      <div class="login-form panel">
        <div class="mode-switch" aria-label="认证模式">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            @click="switchMode('login')"
          >
            登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="switchMode('register')"
          >
            注册
          </button>
        </div>
        <label>
          <span>用户名</span>
          <input
            v-model="username"
            class="field"
            maxlength="32"
            autocomplete="username"
            @keyup.enter="enter"
          />
        </label>
        <label v-if="isRegister">
          <span>昵称</span>
          <input
            v-model="nickname"
            class="field"
            maxlength="16"
            autocomplete="nickname"
            placeholder="选填"
            @keyup.enter="enter"
          />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            class="field"
            type="password"
            autocomplete="current-password"
            @keyup.enter="enter"
          />
        </label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="btn" type="button" :disabled="submitting" @click="enter">
          {{ submitText }}
        </button>
      </div>
    </section>

    <section class="login-visual panel">
      <img class="teacher" :src="teacherImage" alt="小绒老师" />
      <img class="baizi" :src="baiziImage" alt="白子同桌" />
      <div class="bubble teacher-bubble">今天先把第一题讲透。</div>
      <div class="bubble baizi-bubble">我也想听你讲一遍。</div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  width: min(1120px, 100%);
  min-height: 100vh;
  margin: 0 auto;
  padding: 26px;
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(320px, 1.1fr);
  gap: 22px;
  align-items: center;
}

.login-copy {
  display: grid;
  gap: 24px;
}

.eyebrow {
  width: fit-content;
  border-radius: 999px;
  padding: 8px 12px;
  color: var(--mint);
  background: var(--surface-mint);
  font-size: 12px;
  font-weight: 900;
}

h1 {
  margin: 0;
  color: #162839;
  font-size: clamp(42px, 8vw, 76px);
  line-height: 1.04;
}

p {
  margin: 0;
  max-width: 520px;
  color: #4f5f6b;
  font-size: 18px;
  line-height: 1.8;
}

.login-form {
  max-width: 440px;
  padding: 18px;
  display: grid;
  gap: 14px;
}

.mode-switch {
  padding: 4px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  border: 1px solid #d9e4eb;
  border-radius: 999px;
  background: #f3f8fb;
}

.mode-switch button {
  min-height: 38px;
  border-radius: 999px;
  color: var(--muted);
  background: transparent;
  font-weight: 900;
}

.mode-switch button.active {
  color: var(--primary-strong);
  background: #ffffff;
  box-shadow: var(--shadow-soft);
}

.login-form label {
  display: grid;
  gap: 8px;
  color: var(--primary-strong);
  font-weight: 900;
}

.login-form label span {
  padding-left: 4px;
  font-size: 13px;
}

.field {
  width: 100%;
  min-width: 0;
  height: 46px;
  border: 1px solid #ccd6dd;
  border-radius: 18px;
  outline: none;
  padding: 0 14px;
  color: var(--ink);
  background: #fff;
}

.form-error {
  max-width: none;
  border: 1px solid #fecaca;
  border-radius: 16px;
  padding: 10px 12px;
  color: var(--red);
  background: #fef2f2;
  font-size: 13px;
  font-weight: 800;
  line-height: 1.5;
}

.login-visual {
  position: relative;
  min-height: 520px;
  overflow: hidden;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(238, 246, 251, 0.92));
}

.login-visual img {
  position: absolute;
  object-fit: contain;
  filter: drop-shadow(0 26px 24px rgba(72, 97, 116, 0.16));
}

.teacher {
  left: 2%;
  bottom: 12px;
  width: min(52%, 340px);
  height: 400px;
}

.baizi {
  right: 4%;
  bottom: 0;
  width: min(54%, 360px);
  height: 420px;
}

.bubble {
  position: absolute;
  max-width: 260px;
  border: 1px solid rgba(203, 214, 222, 0.8);
  border-radius: 22px 22px 22px 8px;
  background: rgba(255, 255, 255, 0.9);
  padding: 14px 16px;
  color: #42515c;
  box-shadow: var(--shadow-soft);
  font-weight: 900;
  line-height: 1.6;
}

.teacher-bubble {
  left: 34px;
  top: 42px;
}

.baizi-bubble {
  right: 34px;
  top: 128px;
}

@media (max-width: 860px) {
  .login-page {
    grid-template-columns: 1fr;
    padding: 16px;
  }

  .login-visual {
    min-height: 420px;
  }
}
</style>
