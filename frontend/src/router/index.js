import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  {
    path: '/join',
    name: 'join',
    component: () => import('../views/JoinView.vue'),
    meta: { requiresKey: false }
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('../views/ChatView.vue'),
    meta: { requiresKey: true }
  },
  {
    path: '/game',
    name: 'game',
    component: () => import('../views/GameView.vue'),
    meta: { requiresKey: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫（简单房间密钥校验）
router.beforeEach((to) => {
  if (to.meta.requiresKey && !localStorage.getItem('roomKey')) {
    return '/join'
  }
  return true
})

export default router
