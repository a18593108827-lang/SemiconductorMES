import { request } from '../lib/http'

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface SysUserItem {
  id: number | string
  userCode: string
  userName: string
  status: 0 | 1
  mustChangePwd: number
  roles: string[]
  createTime: string | null
  updateTime: string | null
}

export interface UserListQuery {
  keyword?: string
  status?: 0 | 1
  page?: number
  size?: number
}

export function listUsersApi(query: UserListQuery = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status !== undefined) params.set('status', String(query.status))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  const qs = params.toString()
  return request<PageResult<SysUserItem>>(`/system/users?${qs}`, { method: 'GET' })
}

export function updateUserApi(id: number | string, body: { userName: string }) {
  return request<null>(`/system/users/${id}`, { method: 'PUT', body })
}

export function updateUserStatusApi(id: number | string, status: 0 | 1) {
  return request<null>(`/system/users/${id}/status`, {
    method: 'PUT',
    body: { status },
  })
}

export function userRoleIdsApi(userId: number | string) {
  return request<Array<number | string>>(`/system/users/${userId}/roles`, { method: 'GET' })
}

export function assignUserRolesApi(userId: number | string, roleIds: Array<number | string>) {
  return request<null>(`/system/users/${userId}/roles`, {
    method: 'PUT',
    body: { roleIds },
  })
}

export function resetUserPasswordApi(
  userId: number | string,
  body: { password: string; mustChangePwd?: boolean },
) {
  return request<null>(`/system/users/${userId}/password/reset`, {
    method: 'PUT',
    body,
  })
}

export function kickUserApi(userId: number | string) {
  return request<null>(`/system/users/${userId}/kick`, { method: 'POST' })
}

export interface SysRoleItem {
  id: number | string
  roleCode: string
  roleName: string
  remark: string | null
  status: 0 | 1
  userCount: number
  createTime: string | null
  updateTime: string | null
}

export interface RoleListQuery {
  keyword?: string
  status?: 0 | 1
  page?: number
  size?: number
}

export function listRolesApi(query: RoleListQuery = {}) {
  const params = new URLSearchParams()
  if (query.keyword?.trim()) params.set('keyword', query.keyword.trim())
  if (query.status !== undefined) params.set('status', String(query.status))
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 50))
  return request<PageResult<SysRoleItem>>(`/system/roles?${params}`, { method: 'GET' })
}

export function createRoleApi(body: {
  roleCode: string
  roleName: string
  remark?: string
  status: 0 | 1
}) {
  return request<null>('/system/roles', { method: 'POST', body })
}

export function updateRoleApi(
  id: number | string,
  body: { roleName: string; remark?: string; status: 0 | 1 },
) {
  return request<null>(`/system/roles/${id}`, { method: 'PUT', body })
}

export function deleteRoleApi(id: number | string) {
  return request<null>(`/system/roles/${id}`, { method: 'DELETE' })
}

export interface SysPermTreeNode {
  id: number | string
  parentId: number | string
  permType: 1 | 2 | 3
  permCode: string | null
  permName: string
  path: string | null
  icon: string | null
  sortNo: number
  status: 0 | 1
  children: SysPermTreeNode[]
}

export function permTreeApi() {
  return request<SysPermTreeNode[]>('/system/permissions/tree', { method: 'GET' })
}

export function createPermApi(body: {
  parentId: number | string
  permType: 1 | 2 | 3
  permCode?: string
  permName: string
  path?: string
  icon?: string
  sortNo: number
  status: 0 | 1
}) {
  return request<null>('/system/permissions', { method: 'POST', body })
}

export function updatePermApi(
  id: number | string,
  body: {
    parentId: number | string
    permType: 1 | 2 | 3
    permCode?: string
    permName: string
    path?: string
    icon?: string
    sortNo: number
    status: 0 | 1
  },
) {
  return request<null>(`/system/permissions/${id}`, { method: 'PUT', body })
}

export function deletePermApi(id: number | string) {
  return request<null>(`/system/permissions/${id}`, { method: 'DELETE' })
}

export function rolePermissionIdsApi(roleId: number | string) {
  return request<Array<number | string>>(`/system/roles/${roleId}/permissions`, { method: 'GET' })
}

export function assignRolePermissionsApi(roleId: number | string, permissionIds: Array<number | string>) {
  return request<null>(`/system/roles/${roleId}/permissions`, {
    method: 'PUT',
    body: { permissionIds },
  })
}

export type PermRequestStatus = 'pending' | 'approved' | 'rejected' | 'cancelled'

export interface PermRequestItem {
  id: number | string
  requestNo: string
  applicantId: number | string
  applicantCode: string | null
  applicantName: string | null
  roleId: number | string
  roleCode: string | null
  roleName: string | null
  reason: string
  status: PermRequestStatus
  approverId: number | string | null
  approverCode: string | null
  approveOpinion: string | null
  approveTime: string | null
  createTime: string | null
}

export interface ApplyableRoleItem {
  id: number | string
  roleCode: string
  roleName: string
}

export function listApplyableRolesApi() {
  return request<ApplyableRoleItem[]>('/system/perm-requests/applyable-roles', { method: 'GET' })
}

export function listMyPermRequestsApi(query: { status?: string; page?: number; size?: number } = {}) {
  const params = new URLSearchParams()
  if (query.status) params.set('status', query.status)
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<PermRequestItem>>(`/system/perm-requests/mine?${params}`, { method: 'GET' })
}

export function createPermRequestApi(body: { roleId: number | string; reason: string }) {
  return request<null>('/system/perm-requests', { method: 'POST', body })
}

export function cancelPermRequestApi(id: number | string) {
  return request<null>(`/system/perm-requests/${id}/cancel`, { method: 'POST' })
}

export function listTodoPermRequestsApi(query: { page?: number; size?: number } = {}) {
  const params = new URLSearchParams()
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<PermRequestItem>>(`/system/perm-requests/todo?${params}`, { method: 'GET' })
}

export function listDonePermRequestsApi(query: { page?: number; size?: number } = {}) {
  const params = new URLSearchParams()
  params.set('page', String(query.page ?? 1))
  params.set('size', String(query.size ?? 20))
  return request<PageResult<PermRequestItem>>(`/system/perm-requests/done?${params}`, { method: 'GET' })
}

export function approvePermRequestApi(id: number | string, opinion?: string) {
  return request<null>(`/system/perm-requests/${id}/approve`, {
    method: 'POST',
    body: { opinion },
  })
}

export function rejectPermRequestApi(id: number | string, opinion?: string) {
  return request<null>(`/system/perm-requests/${id}/reject`, {
    method: 'POST',
    body: { opinion },
  })
}
