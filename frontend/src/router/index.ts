import { createRouter, createWebHistory, type Router, type RouterHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { resolveRedirectTarget } from '@/utils/redirect'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录才能进入，未登录会被引导到登录页。 */
    requiresAuth?: boolean
    requiresAdmin?: boolean
    /** 已登录用户不应停留的页面，例如登录/注册。 */
    guestOnly?: boolean
  }
}

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '山水间 · 旅行手记' }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guestOnly: true, title: '登录 · 山水间' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guestOnly: true, title: '注册 · 山水间' }
  },
  {
    path: '/me',
    name: 'me',
    component: () => import('@/views/ProfileView.vue'),
    meta: { requiresAuth: true, title: '我的资料 · 山水间' }
  },
  {
    path: '/destinations',
    name: 'destinations',
    component: () => import('@/views/DestinationsView.vue'),
    meta: { title: '探索目的地 · 山水间' }
  },
  {
    path: '/destinations/:id',
    name: 'destination-detail',
    component: () => import('@/views/DestinationDetailView.vue'),
    meta: { title: '目的地 · 山水间' }
  },
  {
    path: '/admin',
    name: 'admin-home',
    component: () => import('@/views/AdminHomeView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '后台概览 · 山水间' }
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('@/views/AdminUsersView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '用户管理 · 山水间' }
  },
  {
    path: '/admin/guides',
    name: 'admin-guides',
    component: () => import('@/views/AdminModerationView.vue'),
    props: { resource: 'guides' },
    meta: { requiresAuth: true, requiresAdmin: true, title: '攻略治理 · 山水间' }
  },
  {
    path: '/admin/comments',
    name: 'admin-comments',
    component: () => import('@/views/AdminModerationView.vue'),
    props: { resource: 'comments' },
    meta: { requiresAuth: true, requiresAdmin: true, title: '评论治理 · 山水间' }
  },
  {
    path: '/admin/destinations',
    name: 'admin-destinations',
    component: () => import('@/views/AdminDestinationsView.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '目的地管理 · 山水间' }
  },
  {
    path: '/guides',
    name: 'guides',
    component: () => import('@/views/GuidesView.vue'),
    meta: { title: '旅行攻略 · 山水间' }
  },
  {
    path: '/guides/new',
    name: 'guide-new',
    component: () => import('@/views/GuideEditorView.vue'),
    meta: { requiresAuth: true, title: '写篇攻略 · 山水间' }
  },
  {
    path: '/guides/:id/edit',
    name: 'guide-edit',
    component: () => import('@/views/GuideEditorView.vue'),
    meta: { requiresAuth: true, title: '编辑攻略 · 山水间' }
  },
  {
    path: '/guides/:id',
    name: 'guide-detail',
    component: () => import('@/views/GuideDetailView.vue'),
    meta: { title: '旅行手记 · 山水间' }
  },
  {
    path: '/me/guides',
    name: 'my-guides',
    component: () => import('@/views/MyGuidesView.vue'),
    meta: { requiresAuth: true, title: '我的攻略 · 山水间' }
  },
  {
    path: '/me/favorites',
    name: 'my-favorites',
    component: () => import('@/views/MyFavoritesView.vue'),
    meta: { requiresAuth: true, title: '我的收藏 · 山水间' }
  },
  {
    path: '/me/itineraries',
    name: 'my-itineraries',
    component: () => import('@/views/MyItinerariesView.vue'),
    meta: { requiresAuth: true, title: '我的行程 · 山水间' }
  },
  {
    path: '/itineraries/ai',
    name: 'ai-itinerary',
    component: () => import('@/views/AiItineraryView.vue'),
    meta: { requiresAuth: true, title: 'AI 行程规划 · 山水间' }
  },
  {
    path: '/itineraries/:id',
    name: 'itinerary-detail',
    component: () => import('@/views/ItineraryDetailView.vue'),
    meta: { requiresAuth: true, title: '私人旅行计划 · 山水间' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面未找到 · 山水间' }
  }
]

/**
 * 路由工厂：便于在测试中注入内存历史，同时供应用入口使用浏览器历史。
 * 守卫只负责体验层拦截，接口权限始终由后端再校验。
 */
export function createAppRouter(history: RouterHistory): Router {
  const router = createRouter({ history, routes })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()

    if (to.meta.requiresAuth || to.meta.requiresAdmin) {
      const status = await auth.ensureSession()
      if (status === 'anonymous') {
        return { name: 'login', query: { redirect: resolveRedirectTarget(to.fullPath, '/') } }
      }
      if (status === 'authenticated' && to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
        return { name: 'home' }
      }
      // 网络不可用时不判定为登出，允许进入页面并由页面给出重试入口。
      return true
    }

    if (to.meta.guestOnly && auth.token) {
      const status = await auth.ensureSession()
      if (status === 'authenticated') {
        const target = resolveRedirectTarget(to.query.redirect)
        return router.resolve(target).meta.guestOnly ? { name: 'me' } : target
      }
    }

    return true
  })

  router.afterEach((to) => {
    const title = to.meta.title
    if (typeof title === 'string') {
      document.title = title
    }
  })

  return router
}

export const router = createAppRouter(createWebHistory())
