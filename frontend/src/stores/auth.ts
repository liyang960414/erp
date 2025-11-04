import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types/auth'
import { authApi } from '@/api/auth'
import { ElMessage } from 'element-plus'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  // 计算属性
  const isAuthenticated = computed(() => !!token.value)
  const hasRole = computed(() => (roleName: string) => {
    return user.value?.roles?.some((role) => role.name === roleName) || false
  })
  const hasPermission = computed(() => (permissionName: string) => {
    if (!user.value) return false
    return user.value.roles.some((role) =>
      role.permissions?.some((perm) => perm.name === permissionName),
    )
  })

  // 登录
  async function login(loginData: LoginRequest) {
    loading.value = true
    try {
      const response = await authApi.login(loginData)
      token.value = response.token
      localStorage.setItem('token', response.token)

      // 保存用户基本信息
      const basicUser: Partial<UserInfo> = {
        id: response.userId,
        username: response.username,
        email: response.email,
        fullName: response.fullName,
        roles: response.roles.map((name) => ({
          id: 0,
          name,
          description: '',
        })),
      }
      localStorage.setItem('user', JSON.stringify(basicUser))

      // 获取完整用户信息
      await fetchUserInfo()

      ElMessage.success('登录成功')
      return true
    } catch (error) {
      console.error('登录失败:', error)
      return false
    } finally {
      loading.value = false
    }
  }

  // 获取用户信息
  async function fetchUserInfo() {
    if (!token.value) return

    try {
      user.value = await authApi.getCurrentUser()
      localStorage.setItem('user', JSON.stringify(user.value))
    } catch (error: any) {
      console.error('获取用户信息失败:', error)
      const status = error?.response?.status

      // 401和403错误已在请求拦截器中统一处理（会自动调用logout和跳转）
      if (status === 401 || status === 403) {
        // 直接调用logout清除状态（请求拦截器已经处理了跳转）
        await logout()
        return
      }

      // 其他错误，尝试使用已保存的用户信息
      const storedUser = localStorage.getItem('user')
      if (storedUser) {
        try {
          user.value = JSON.parse(storedUser)
        } catch (e) {
          console.error('解析已保存的用户信息失败:', e)
          await logout()
        }
      } else {
        await logout()
      }
    }
  }

  // 登出
  async function logout() {
    // 先清除状态，确保即使API调用失败也能正常退出
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')

    // 尝试调用登出API，但不阻塞退出流程
    try {
      await authApi.logout()
    } catch (error) {
      // 即使登出API调用失败（比如网络问题或token已失效），也不影响退出流程
      console.warn('登出API调用失败，但已清除本地状态:', error)
    }
  }

  // 初始化用户信息
  function initUser() {
    const storedUser = localStorage.getItem('user')
    if (storedUser && token.value) {
      try {
        user.value = JSON.parse(storedUser)
      } catch (error) {
        console.error('解析用户信息失败:', error)
      }
    }
  }

  return {
    token,
    user,
    loading,
    isAuthenticated,
    hasRole,
    hasPermission,
    login,
    logout,
    fetchUserInfo,
    initUser,
  }
})
