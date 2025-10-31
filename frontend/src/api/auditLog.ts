import request from '@/utils/request'

export interface AuditLog {
  id: number
  username: string
  userId: number
  action: string
  module: string
  resourceType?: string
  resourceId?: string
  description?: string
  requestMethod?: string
  requestUri?: string
  ipAddress?: string
  status: string
  errorMessage?: string
  createdAt: string
}

export interface AuditLogQuery {
  page?: number
  size?: number
  sortBy?: string
  sortDir?: string
  username?: string
  action?: string
  module?: string
  status?: string
  startTime?: string
  endTime?: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export const auditLogApi = {
  // 获取审计日志列表（支持多条件查询）
  getAuditLogs: (params?: AuditLogQuery) => {
    return request.get<PageResponse<AuditLog>>('/audit-logs', { params })
  },

  // 根据用户名查询审计日志
  getAuditLogsByUsername: (username: string, params?: { page?: number; size?: number }) => {
    return request.get<PageResponse<AuditLog>>(`/audit-logs/username/${username}`, { params })
  },

  // 根据操作类型查询审计日志
  getAuditLogsByAction: (action: string, params?: { page?: number; size?: number }) => {
    return request.get<PageResponse<AuditLog>>(`/audit-logs/action/${action}`, { params })
  },

  // 根据模块查询审计日志
  getAuditLogsByModule: (module: string, params?: { page?: number; size?: number }) => {
    return request.get<PageResponse<AuditLog>>(`/audit-logs/module/${module}`, { params })
  },
}

