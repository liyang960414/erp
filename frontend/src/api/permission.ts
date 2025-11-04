import request from '@/utils/request'

export interface Permission {
  id: number
  name: string
  description?: string
}

export const permissionApi = {
  // 获取权限列表（分页）
  getPermissions(params: {
    page?: number
    size?: number
    sortBy?: string
    sortDir?: string
  }): Promise<any> {
    return request.get('/permissions', { params })
  },

  // 获取所有权限列表
  getAllPermissions(): Promise<Permission[]> {
    return request.get('/permissions/list')
  },

  // 获取权限详情
  getPermissionById(id: number): Promise<Permission> {
    return request.get(`/permissions/${id}`)
  },
}
