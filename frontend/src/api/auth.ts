import request from '@/utils/request'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types/auth'

export const authApi = {
  // 登录
  login(data: LoginRequest): Promise<LoginResponse> {
    return request.post('/auth/login', data)
  },

  // 注册
  register(data: {
    username: string
    password: string
    email: string
    fullName?: string
  }): Promise<any> {
    return request.post('/auth/register', data)
  },

  // 获取当前用户信息
  getCurrentUser(): Promise<UserInfo> {
    return request.get('/users/me')
  },

  // 登出
  logout(): Promise<void> {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    return Promise.resolve()
  },
}
