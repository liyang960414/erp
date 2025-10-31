import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import MainLayout from '@/layouts/MainLayout.vue'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: MainLayout,
      redirect: '/home',
      meta: { requiresAuth: true },
      children: [
        {
          path: '/home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '首页', requiresAuth: true },
        },
        {
          path: '/users/list',
          name: 'userList',
          component: () => import('@/views/UserListView.vue'),
          meta: { title: '用户列表', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: '/products',
          name: 'products',
          component: () => import('@/views/ProductView.vue'),
          meta: { title: '商品管理', requiresAuth: true },
        },
        {
          path: '/orders',
          name: 'orders',
          component: () => import('@/views/OrderView.vue'),
          meta: { title: '订单管理', requiresAuth: true },
        },
        {
          path: '/system/settings',
          name: 'systemSettings',
          component: () => import('@/views/SystemSettingsView.vue'),
          meta: { title: '系统设置', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: '/system/permissions',
          name: 'permissions',
          component: () => import('@/views/PermissionView.vue'),
          meta: { title: '权限管理', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: '/system/roles',
          name: 'roles',
          component: () => import('@/views/RoleManagementView.vue'),
          meta: { title: '角色管理', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: '/system/audit-logs',
          name: 'auditLogs',
          component: () => import('@/views/AuditLogView.vue'),
          meta: { title: '审计日志', requiresAuth: true, roles: ['ADMIN'] },
        },
      ],
    },
  ],
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // 如果没有初始化用户信息，尝试从localStorage恢复
  if (!authStore.user && authStore.token) {
    authStore.initUser()
  }

  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !authStore.isAuthenticated) {
    // 需要认证但未登录，跳转到登录页
    next('/login')
  } else if (to.path === '/login' && authStore.isAuthenticated) {
    // 已登录访问登录页，跳转到首页
    next('/')
  } else if (requiresAuth && to.meta.roles) {
    // 需要特定角色
    const hasRole = Array.isArray(to.meta.roles)
      ? to.meta.roles.some((role: string) => authStore.hasRole(role))
      : authStore.hasRole(to.meta.roles as string)

    if (hasRole) {
      next()
    } else {
      // 没有权限
      next('/home')
      // 延迟执行以确保 i18n 已初始化
      import('@/locales').then(({ default: i18n }) => {
        ElMessage.error(i18n.global.t('common.noPermission'))
      })
    }
  } else {
    next()
  }
})

export default router
