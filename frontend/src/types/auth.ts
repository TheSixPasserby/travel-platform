/** 用户角色，与后端 User.role 对齐。 */
export type UserRole = 'USER' | 'ADMIN'

/** 账号状态，与后端 User.status 对齐。 */
export type UserStatus = 'ACTIVE' | 'DISABLED'

/**
 * 当前登录用户的完整投影（/auth/me 与 /users/me 同构）。
 * 注意：后端主键序列化为字符串，前端一律按字符串处理。
 */
export interface CurrentUser {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
}

/** POST /auth/login 的返回数据。 */
export interface LoginData {
  tokenName: string
  tokenValue: string
  user: CurrentUser
}

/** POST /auth/login 的请求体。 */
export interface LoginPayload {
  username: string
  password: string
}

/** POST /auth/register 的请求体，昵称为可选项。 */
export interface RegisterPayload {
  username: string
  password: string
  nickname?: string
}

/**
 * PATCH /users/me 的部分更新请求体。
 * 未出现的字段保持原值；bio / avatarUrl 显式传 null 表示清空；nickname 不接受 null。
 */
export interface ProfileUpdatePayload {
  nickname?: string
  avatarUrl?: string | null
  bio?: string | null
}
