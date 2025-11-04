export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  tokenType: string
  userId: number
  username: string
  email: string
  fullName: string
  roles: string[]
}

export interface UserInfo {
  id: number
  username: string
  email: string
  fullName: string
  enabled: boolean
  accountNonExpired: boolean
  accountNonLocked: boolean
  credentialsNonExpired: boolean
  roles: Role[]
}

export interface Role {
  id: number
  name: string
  description: string
  permissions?: Permission[]
}

export interface Permission {
  id: number
  name: string
  description: string
}
