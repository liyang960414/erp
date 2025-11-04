import request from '@/utils/request'
import type { CreateRoleRequest, Role, UpdateRoleRequest } from '@/types/role'

export const roleApi = {
  // 获取角色列表（分页）
  getRoles(params: {
    page?: number
    size?: number
    sortBy?: string
    sortDir?: string
  }): Promise<any> {
    return request.get('/roles', { params })
  },

  // 获取所有角色列表
  getAllRoles(): Promise<Role[]> {
    return request.get('/roles/list')
  },

  // 获取角色详情
  getRoleById(id: number): Promise<Role> {
    return request.get(`/roles/${id}`)
  },

  // 创建角色
  createRole(data: CreateRoleRequest): Promise<Role> {
    return request.post('/roles', data)
  },

  // 更新角色
  updateRole(id: number, data: UpdateRoleRequest): Promise<Role> {
    return request.put(`/roles/${id}`, data)
  },

  // 删除角色
  deleteRole(id: number): Promise<void> {
    return request.delete(`/roles/${id}`)
  },
}
