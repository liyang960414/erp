import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useTabsStore } from '@/stores/tabs'
import MainLayout from '@/layouts/MainLayout.vue'
import LoginView from '@/views/auth/LoginView.vue'

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
          path: 'home',
          name: 'home',
          component: () => import('@/views/home/HomeView.vue'),
          meta: { title: '首页', requiresAuth: true },
        },
        {
          path: 'users/list',
          name: 'userList',
          component: () => import('@/views/users/UserListView.vue'),
          meta: { title: '用户列表', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/products/ProductView.vue'),
          meta: { title: '商品管理', requiresAuth: true },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/orders/OrderView.vue'),
          meta: { title: '销售订单管理', requiresAuth: true },
        },
        {
          path: 'purchase-orders',
          name: 'purchaseOrders',
          component: () => import('@/views/orders/PurchaseOrderView.vue'),
          meta: { title: '采购订单管理', requiresAuth: true },
        },
        {
          path: 'system/settings',
          name: 'systemSettings',
          component: () => import('@/views/system/SystemSettingsView.vue'),
          meta: { title: '系统设置', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: 'system/permissions',
          name: 'permissions',
          component: () => import('@/views/system/PermissionView.vue'),
          meta: { title: '权限管理', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: 'system/roles',
          name: 'roles',
          component: () => import('@/views/system/RoleManagementView.vue'),
          meta: { title: '角色管理', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: 'system/audit-logs',
          name: 'auditLogs',
          component: () => import('@/views/system/AuditLogView.vue'),
          meta: { title: '审计日志', requiresAuth: true, roles: ['ADMIN'] },
        },
        {
          path: 'basic-info/units',
          name: 'units',
          component: () => import('@/views/basic-info/UnitManagementView.vue'),
          meta: { title: '单位管理', requiresAuth: true },
        },
        {
          path: 'basic-info/materials',
          name: 'materials',
          component: () => import('@/views/basic-info/MaterialManagementView.vue'),
          meta: { title: '物料管理', requiresAuth: true },
        },
        {
          path: 'basic-info/suppliers',
          name: 'suppliers',
          component: () => import('@/views/basic-info/SupplierManagementView.vue'),
          meta: { title: '供应商管理', requiresAuth: true },
        },
        {
          path: 'engineering-data/bom-list/boms',
          name: 'boms',
          component: () => import('@/views/engineering-data/bom-list/BomManagementView.vue'),
          meta: { title: '物料清单列表', requiresAuth: true },
        },
        {
          path: 'engineering-data/bom-list/query',
          name: 'bomQuery',
          component: () => import('@/views/engineering-data/bom-list/BomQueryView.vue'),
          meta: { title: '物料清单查询', requiresAuth: true },
        },
      ],
    },
  ],
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  const tabsStore = useTabsStore()

  // 如果没有初始化用户信息，尝试从localStorage恢复
  if (!authStore.user && authStore.token) {
    authStore.initUser()
  }

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (requiresAuth && !authStore.isAuthenticated) {
    // 需要认证但未登录，跳转到登录页
    tabsStore.clearTabs()
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

// 路由后置守卫 - 添加标签页并同步激活状态
router.afterEach((to) => {
  const tabsStore = useTabsStore()
  // 如果是需要认证的路由且不是登录页，添加到标签页
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  if (requiresAuth && to.path !== '/login') {
    // 先添加标签(如果不存在)
    tabsStore.addTab(to)
    // 然后确保激活状态与当前路由同步
    if (tabsStore.activeTab !== to.path) {
      tabsStore.setActiveTab(to.path)
    }
  }
})

export default router
