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
    return user.value?.roles?.some(role => role.name === roleName) || false
  })
  const hasPermission = computed(() => (permissionName: string) => {
    if (!user.value) return false
    return user.value.roles.some(role =>
      role.permissions?.some(perm => perm.name === permissionName)
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
        roles: response.roles.map(name => ({ 
          id: 0, 
          name, 
          description: '' 
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
      // 如果是 403 错误，说明账户可能被禁用或没有权限，需要重新登录
      if (error?.response?.status === 403) {
        ElMessage.error(error?.response?.data?.detail || '账户权限异常，请重新登录')
        logout()
        // 跳转到登录页
        window.location.href = '/login'
      } else if (error?.response?.status === 401) {
        // 401 错误已在请求拦截器中处理
        logout()
      } else {
        // 其他错误，尝试使用已保存的用户信息
        const storedUser = localStorage.getItem('user')
        if (storedUser) {
          try {
            user.value = JSON.parse(storedUser)
          } catch (e) {
            console.error('解析已保存的用户信息失败:', e)
            logout()
          }
        } else {
          logout()
        }
      }
    }
  }

  // 登出
  async function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    await authApi.logout()
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

