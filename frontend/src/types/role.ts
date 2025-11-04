export interface Role {
  id: number
  name: string
  description?: string
  permissions: PermissionSummary[]
}

export interface PermissionSummary {
  id: number
  name: string
  description?: string
}

export interface CreateRoleRequest {
  name: string
  description?: string
  permissionNames?: string[]
}

export interface UpdateRoleRequest {
  description?: string
  permissionNames?: string[]
}
