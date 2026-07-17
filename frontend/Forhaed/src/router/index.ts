import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/app',
      component: () => import('@/components/AppShell.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/app/courses',
        },
        {
          path: 'courses',
          name: 'courses',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'classroom/:sessionId',
          name: 'classroom',
          component: () => import('@/views/ClassroomView.vue'),
        },
        {
          path: 'homework/:sessionId',
          name: 'homework',
          component: () => import('@/views/HomeworkView.vue'),
        },
        {
          path: 'review/:sessionId',
          name: 'review',
          component: () => import('@/views/ReviewView.vue'),
        },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/components/AppShell.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'runtime',
          name: 'runtime',
          component: () => import('@/views/RuntimeStatusView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/login',
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth) {
    if (!auth.token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }

    const validSession = await auth.ensureSession()
    if (!validSession) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  if (to.path === '/login' && auth.isLoggedIn) {
    const validSession = await auth.ensureSession()
    if (validSession) {
      return typeof to.query.redirect === 'string' ? to.query.redirect : '/app/courses'
    }
  }
})

export default router
