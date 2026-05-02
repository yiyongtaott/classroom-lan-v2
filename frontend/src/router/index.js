import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表 - 全部使用懒加载（PERF-01 P1）。
 * 不在 beforeEach 守卫里阻止跳转：BUG-04 修复，初始化未完成时也允许导航，
 * 由各页面自行用 v-if / loading 控制内部渲染。
 */
const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue') },
  { path: '/join', name: 'join', component: () => import('../views/JoinView.vue') },
  { path: '/game', name: 'game', component: () => import('../views/GameView.vue') },
  { path: '/game/draw-and-guess', name: 'game-draw',
    component: () => import('../views/games/DrawAndGuessView.vue') },
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
