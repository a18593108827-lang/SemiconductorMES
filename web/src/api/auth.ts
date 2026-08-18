import { request } from '../lib/http'

export interface LoginResult {
  token: string
  tokenName: string
}

export interface MenuItem {
  id: number | string
  parentId: number | string
  permType: 1 | 2
  permCode: string | null
  permName: string
  path: string | null
  icon: string | null
  sortNo: number
  children: MenuItem[]
}

export interface UserInfo {
  id: number | string
  userCode: string
  userName: string
  mustChangePwd?: number
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
}

export function loginApi(userCode: string, password: string) {
  return request<LoginResult>('/auth/login', {
    method: 'POST',
    body: { userCode, password },
    auth: false,
  })
}

export function logoutApi() {
  return request<null>('/auth/logout', { method: 'POST' })
}

export function userInfoApi() {
  return request<UserInfo>('/auth/info', { method: 'GET' })
}

export function changePasswordApi(body: { oldPassword: string; newPassword: string }) {
  return request<null>('/auth/password', {
    method: 'PUT',
    body,
  })
}

export function registerApi(body: { userCode: string; userName: string; password: string }) {
  return request<null>('/auth/register', {
    method: 'POST',
    body,
  })
}
