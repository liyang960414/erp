export interface User {
  id: number
  username: string
  email: string
  fullName?: string
  enabled: boolean
  accountNonExpired: boolean
  accountNonLocked: boolean
  credentialsNonExpired: boolean
  createdAt?: string
  updatedAt?: string
  roles: RoleSummary[]
}

export interface RoleSummary {
  id: number
  name: string
  description?: string
}

export interface CreateUserRequest {
  username: string
  password: string
  email: string
  fullName?: string
  enabled?: boolean
  roleNames?: string[]
}

export interface UpdateUserRequest {
  email?: string
  fullName?: string
  enabled?: boolean
  accountNonExpired?: boolean
  accountNonLocked?: boolean
  credentialsNonExpired?: boolean
  roleNames?: string[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}
