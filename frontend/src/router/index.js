import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/join', name: 'join', component: () => import('../views/JoinView.vue') },
  { path: '/game', name: 'game', component: () => import('../views/GameView.vue') },
  { path: '/files', name: 'files', component: () => import('../views/FileView.vue') },
  // 兼容旧入口
  { path: '/chat', redirect: '/' },
  { path: '/settings', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
