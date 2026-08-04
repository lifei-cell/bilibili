import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/search', name: 'search', component: () => import('@/views/SearchView.vue') },
    { path: '/video/:id', name: 'video', component: () => import('@/views/VideoView.vue') },
    { path: '/auth', name: 'auth', component: () => import('@/views/AuthView.vue') },
    { path: '/upload', name: 'upload', component: () => import('@/views/UploadView.vue'), meta: { auth: true } },
    { path: '/space/:id?', name: 'profile', component: () => import('@/views/ProfileView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue') },
  ],
})

router.beforeEach((to) => {
  if (to.meta.auth && !localStorage.getItem('bili_token')) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }
})

export default router
