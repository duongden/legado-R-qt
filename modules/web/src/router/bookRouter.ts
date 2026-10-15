import { createWebHashHistory, createRouter } from 'vue-router'

export const bookRoutes = [
  { path: '/media', name: 'media', component: () => import('../views/MediaReader.vue') },
  { path: '/downloads', name: 'downloads', component: () => import('../views/BookDownloads.vue') },
  { path: '/discovery', name: 'discovery', component: () => import('../views/BookDiscovery.vue') },
  { path: '/discovery/book/:id', name: 'discoveryBook', component: () => import('../views/DiscoveryBook.vue') },
  {
    path: '/',
    name: 'shelf',
    component: () => import('../views/BookShelf.vue'),
  },
  {
    path: '/chapter',
    name: 'chapter',
    component: () => import('../views/BookChapter.vue'),
  },
]

const router = createRouter({
  // mode: "history",
  history: createWebHashHistory(),
  routes: bookRoutes,
})

export default router
