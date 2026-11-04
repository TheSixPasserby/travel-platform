import type {
  CurrentUser,
  LoginData,
  LoginPayload,
  ProfileUpdatePayload,
  RegisterPayload
} from '@/types/auth'

import { apiClient } from './client'

/**
 * 认证与当前用户接口。
 * 响应拦截器已解包统一响应结构，这里只声明业务数据类型。
 */
export function registerAccount(payload: RegisterPayload): Promise<CurrentUser> {
  return apiClient.post<CurrentUser, CurrentUser>('/auth/register', payload)
}

export function loginAccount(payload: LoginPayload): Promise<LoginData> {
  return apiClient.post<LoginData, LoginData>('/auth/login', payload)
}

export function logoutAccount(): Promise<null> {
  return apiClient.post<null, null>('/auth/logout')
}

export function fetchCurrentUser(): Promise<CurrentUser> {
  return apiClient.get<CurrentUser, CurrentUser>('/auth/me')
}

export function fetchProfile(): Promise<CurrentUser> {
  return apiClient.get<CurrentUser, CurrentUser>('/users/me')
}

export function patchProfile(payload: ProfileUpdatePayload): Promise<CurrentUser> {
  return apiClient.patch<CurrentUser, CurrentUser>('/users/me', payload)
}
