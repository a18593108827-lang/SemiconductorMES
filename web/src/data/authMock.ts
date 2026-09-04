export type UserStatus = 1 | 0
export type RequestStatus = 'pending' | 'approved' | 'rejected' | 'cancelled'

export interface AuthUser {
  id: string
  userCode: string
  userName: string
  status: UserStatus
  roles: string[]
  mustChangePwd: boolean
  updatedAt: string
}

export interface AuthRole {
  id: string
  roleCode: string
  roleName: string
  remark: string
  status: UserStatus
  permIds: string[]
  userCount: number
}

export interface AuthPerm {
  id: string
  parentId: string
  permType: 1 | 2 | 3
  permCode: string
  permName: string
  path?: string
  sortNo: number
  status: UserStatus
}

export interface PermRequest {
  id: string
  userCode: string
  userName: string
  roleCode: string
  roleName: string
  reason: string
  status: RequestStatus
  createdAt: string
  reviewer?: string
  reviewRemark?: string
}

export const authUsers: AuthUser[] = [
  {
    id: '1',
    userCode: 'admin',
    userName: '系统管理员',
    status: 1,
    roles: ['admin'],
    mustChangePwd: false,
    updatedAt: '2026-07-20 09:12',
  },
  {
    id: '2',
    userCode: 'E1002',
    userName: '李工',
    status: 1,
    roles: ['process_eng'],
    mustChangePwd: false,
    updatedAt: '2026-07-19 16:40',
  },
  {
    id: '3',
    userCode: 'O2003',
    userName: '陈操',
    status: 1,
    roles: ['operator'],
    mustChangePwd: true,
    updatedAt: '2026-07-18 11:02',
  },
  {
    id: '4',
    userCode: 'S3004',
    userName: '张班长',
    status: 1,
    roles: ['supervisor'],
    mustChangePwd: false,
    updatedAt: '2026-07-17 08:55',
  },
  {
    id: '5',
    userCode: 'O2005',
    userName: '王操',
    status: 0,
    roles: ['operator'],
    mustChangePwd: false,
    updatedAt: '2026-07-10 14:20',
  },
]

export const authRoles: AuthRole[] = [
  {
    id: 'r1',
    roleCode: 'admin',
    roleName: '超级管理员',
    remark: '全量权限',
    status: 1,
    permIds: ['*'],
    userCount: 1,
  },
  {
    id: 'r2',
    roleCode: 'operator',
    roleName: '现场操作员',
    remark: 'Track / 扫码',
    status: 1,
    permIds: ['m-dash', 'm-track', 'b-track-in', 'b-track-out', 'b-hold'],
    userCount: 2,
  },
  {
    id: 'r3',
    roleCode: 'process_eng',
    roleName: '工艺工程师',
    remark: 'Route / Recipe / 设备',
    status: 1,
    permIds: [
      'm-dash',
      'm-lots',
      'm-wip',
      'm-eqp',
      'm-dispatch',
      'b-dispatch-reserve',
      'm-route',
      'm-hold',
      'm-alarm',
      'm-hist',
    ],
    userCount: 1,
  },
  {
    id: 'r4',
    roleCode: 'supervisor',
    roleName: '班组长',
    remark: '看板 / Hold / 审批',
    status: 1,
    permIds: ['m-dash', 'm-wip', 'm-hold', 'm-dispatch', 'm-alarm', 'm-auth-approve', 'b-hold-release', 'b-dispatch-reserve'],
    userCount: 1,
  },
]

export const authPerms: AuthPerm[] = [
  { id: 'd-ops', parentId: '0', permType: 1, permCode: '', permName: '生产执行', sortNo: 10, status: 1 },
  { id: 'm-dash', parentId: 'd-ops', permType: 2, permCode: 'dashboard:view', permName: '看板', path: '/app/dashboard', sortNo: 11, status: 1 },
  { id: 'm-lots', parentId: 'd-ops', permType: 2, permCode: 'lot:list', permName: '批次', path: '/app/lots', sortNo: 12, status: 1 },
  { id: 'm-wip', parentId: 'd-ops', permType: 2, permCode: 'wip:list', permName: '在制', path: '/app/wip', sortNo: 13, status: 1 },
  { id: 'm-eqp', parentId: 'd-ops', permType: 2, permCode: 'eqp:list', permName: '设备', path: '/app/equipment', sortNo: 14, status: 1 },
  { id: 'b-eqp-add', parentId: 'm-eqp', permType: 3, permCode: 'eqp:add', permName: '设备新增', sortNo: 141, status: 1 },
  { id: 'b-eqp-edit', parentId: 'm-eqp', permType: 3, permCode: 'eqp:edit', permName: '设备编辑', sortNo: 142, status: 1 },
  { id: 'b-eqp-status', parentId: 'm-eqp', permType: 3, permCode: 'eqp:status', permName: '设备改态', sortNo: 143, status: 1 },
  { id: 'm-dispatch', parentId: 'd-ops', permType: 2, permCode: 'dispatch:view', permName: '派工', path: '/app/dispatch', sortNo: 145, status: 1 },
  { id: 'b-dispatch-reserve', parentId: 'm-dispatch', permType: 3, permCode: 'dispatch:reserve', permName: '设备预约', sortNo: 1451, status: 1 },
  { id: 'm-route', parentId: 'd-ops', permType: 2, permCode: 'route:list', permName: '路线', path: '/app/route', sortNo: 15, status: 1 },
  { id: 'm-hold', parentId: 'd-ops', permType: 2, permCode: 'hold:list', permName: '锁批', path: '/app/hold', sortNo: 16, status: 1 },
  { id: 'm-alarm', parentId: 'd-ops', permType: 2, permCode: 'alarm:view', permName: '报警', path: '/app/alarm', sortNo: 17, status: 1 },
  { id: 'm-hist', parentId: 'd-ops', permType: 2, permCode: 'history:list', permName: '追溯', path: '/app/history', sortNo: 18, status: 1 },
  { id: 'm-track', parentId: 'd-ops', permType: 2, permCode: 'track:view', permName: '现场台', path: '/track', sortNo: 19, status: 1 },
  { id: 'b-track-in', parentId: 'm-track', permType: 3, permCode: 'track:track-in', permName: 'Track In', sortNo: 191, status: 1 },
  { id: 'b-track-out', parentId: 'm-track', permType: 3, permCode: 'track:track-out', permName: 'Track Out', sortNo: 192, status: 1 },
  { id: 'b-hold', parentId: 'm-hold', permType: 3, permCode: 'hold:create', permName: '发起锁批', sortNo: 161, status: 1 },
  { id: 'b-hold-release', parentId: 'm-hold', permType: 3, permCode: 'hold:release', permName: '解锁', sortNo: 162, status: 1 },
  { id: 'd-sys', parentId: '0', permType: 1, permCode: '', permName: '系统', sortNo: 90, status: 1 },
  { id: 'm-auth', parentId: 'd-sys', permType: 2, permCode: 'user:list', permName: '权限', path: '/app/auth', sortNo: 91, status: 1 },
  { id: 'm-auth-users', parentId: 'm-auth', permType: 3, permCode: 'user:list', permName: '用户管理', sortNo: 911, status: 1 },
  { id: 'm-auth-roles', parentId: 'm-auth', permType: 3, permCode: 'role:list', permName: '角色管理', sortNo: 912, status: 1 },
  { id: 'm-auth-perms', parentId: 'm-auth', permType: 3, permCode: 'perm:list', permName: '权限树', sortNo: 913, status: 1 },
  { id: 'm-auth-apply', parentId: 'm-auth', permType: 3, permCode: 'perm:apply', permName: '发起申请', sortNo: 914, status: 1 },
  { id: 'm-auth-approve', parentId: 'm-auth', permType: 3, permCode: 'perm:approve', permName: '审批申请', sortNo: 915, status: 1 },
]

export const permRequests: PermRequest[] = [
  {
    id: 'req1',
    userCode: 'O2003',
    userName: '陈操',
    roleCode: 'supervisor',
    roleName: '班组长',
    reason: '代班需要查看 WIP 与审批权限',
    status: 'pending',
    createdAt: '2026-07-21 10:18',
  },
  {
    id: 'req2',
    userCode: 'E1002',
    userName: '李工',
    roleCode: 'admin',
    roleName: '超级管理员',
    reason: '临时维护菜单权限',
    status: 'rejected',
    createdAt: '2026-07-15 13:40',
    reviewer: 'admin',
    reviewRemark: '权限过大，改申请 supervisor',
  },
  {
    id: 'req3',
    userCode: 'O2005',
    userName: '王操',
    roleCode: 'operator',
    roleName: '现场操作员',
    reason: '复岗开通现场权限',
    status: 'approved',
    createdAt: '2026-07-08 09:02',
    reviewer: 'lead.zhang',
    reviewRemark: '已复岗培训',
  },
]

export const roleNameMap = Object.fromEntries(authRoles.map((r) => [r.roleCode, r.roleName]))

export const permTypeLabel: Record<1 | 2 | 3, string> = {
  1: '目录',
  2: '菜单',
  3: '按钮',
}
