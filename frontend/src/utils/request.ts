import axios, { type AxiosInstance, type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  timeout: 30000, // 默认30秒（文件上传接口会单独设置更长的超时时间）
})

// 标记是否正在处理登录失效，避免重复处理
let isHandlingAuthError = false

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 如果返回的是成功状态
    if (res.success !== undefined && res.success === true) {
      return res.data
    }

    // 如果返回的是标准的API响应格式
    return response
  },
  async (error: AxiosError) => {
    let message = '请求失败'
    
    if (error.response) {
      const status = error.response.status
      const res = error.response.data as any

      switch (status) {
        case 400:
          message = res.title || '请求参数错误'
          break
        case 401:
          // 登录失效，统一处理退出登录
          if (!isHandlingAuthError) {
            isHandlingAuthError = true
            message = '登录已失效，请重新登录'
            
            // 显示提示信息
            ElMessage.warning(message)
            
            // 动态导入auth store避免循环依赖
            const { useAuthStore } = await import('@/stores/auth')
            const authStore = useAuthStore()
            
            // 调用logout方法统一清理登录状态
            await authStore.logout()
            
            // 跳转到登录页
            router.push('/login').finally(() => {
              isHandlingAuthError = false
            })
          }
          break
        case 403:
          // 如果是认证相关的403错误，也需要退出登录
          const resData = res.detail || res.title || ''
          if (resData.includes('认证') || resData.includes('登录') || resData.includes('权限异常')) {
            if (!isHandlingAuthError) {
              isHandlingAuthError = true
              message = resData || '账户权限异常，请重新登录'
              
              // 显示提示信息
              ElMessage.warning(message)
              
              // 动态导入auth store避免循环依赖
              const { useAuthStore } = await import('@/stores/auth')
              const authStore = useAuthStore()
              
              // 调用logout方法统一清理登录状态
              await authStore.logout()
              
              // 跳转到登录页
              router.push('/login').finally(() => {
                isHandlingAuthError = false
              })
            }
          } else {
            message = resData || '没有权限访问，请联系管理员'
          }
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = res.title || '服务器错误'
          break
        default:
          message = res.title || `请求失败 (${status})`
      }

      // 显示表单验证错误
      if (res.errors && Array.isArray(res.errors)) {
        const errorMessages = res.errors.map((err: any) => err.message).join(', ')
        message = errorMessages || message
      }
    } else if (error.request) {
      // 判断是否为超时错误
      if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
        message = '请求超时，请稍后重试'
      } else {
      message = '网络连接失败，请检查网络'
      }
    } else {
      message = error.message || '请求配置错误'
    }

    // 只在非401/403认证错误时显示错误消息（认证错误已在上面处理并显示了warning）
    if (error.response?.status !== 401 && 
        !(error.response?.status === 403 && isHandlingAuthError)) {
      ElMessage.error(message)
    }
    
    return Promise.reject(error)
  }
)

export default request

