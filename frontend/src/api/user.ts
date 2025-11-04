import request from '@/utils/request'
import type { CreateUserRequest, PageResponse, UpdateUserRequest, User } from '@/types/user'

export const userApi = {
  // 获取用户列表
  getUsers(params: {
    page?: number
    size?: number
    sortBy?: string
    sortDir?: string
  }): Promise<PageResponse<User>> {
    return request.get('/users', { params })
  },

  // 获取用户详情
  getUserById(id: number): Promise<User> {
    return request.get(`/users/${id}`)
  },

  // 创建用户
  createUser(data: CreateUserRequest): Promise<User> {
    return request.post('/users', data)
  },

  // 更新用户
  updateUser(id: number, data: UpdateUserRequest): Promise<User> {
    return request.put(`/users/${id}`, data)
  },

  // 删除用户
  deleteUser(id: number): Promise<void> {
    return request.delete(`/users/${id}`)
  },

  // 修改密码
  changePassword(id: number, newPassword: string): Promise<void> {
    return request.put(`/users/${id}/password`, newPassword)
  },
}
