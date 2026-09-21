export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

const TOKEN_KEY = 'mes_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  code: number
  constructor(code: number, msg: string) {
    super(msg)
    this.code = code
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown
  auth?: boolean
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, auth = true, headers, ...rest } = options
  const h = new Headers(headers)
  if (!h.has('Content-Type') && body !== undefined) {
    h.set('Content-Type', 'application/json')
  }
  if (auth) {
    const token = getToken()
    if (token) h.set('Authorization', `Bearer ${token}`)
  }

  const res = await fetch(`/api${path}`, {
    ...rest,
    headers: h,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  let json: ApiResult<T>
  try {
    json = (await res.json()) as ApiResult<T>
  } catch {
    throw new ApiError(res.status, '响应解析失败')
  }

  if (json.code === 401) {
    clearToken()
    if (!window.location.pathname.startsWith('/login')) {
      window.location.href = '/login'
    }
    throw new ApiError(401, json.msg || '未登录或登录已过期')
  }

  if (json.code !== 200) {
    throw new ApiError(json.code, json.msg || '请求失败')
  }

  return json.data
}

function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null
  const m = /filename\*=(?:UTF-8''|)([^;]+)/i.exec(header) || /filename=([^;]+)/i.exec(header)
  if (!m) return null
  let name = m[1].trim()
  if (name.startsWith('"') && name.endsWith('"') && name.length >= 2) {
    name = name.slice(1, -1)
  }
  try {
    return decodeURIComponent(name)
  } catch {
    return name
  }
}

/** 二进制下载：JSON（含 charset）当 R<> 错误；否则存盘。401 与 request() 同款。 */
export async function downloadFile(path: string, fallbackName?: string): Promise<void> {
  const h = new Headers()
  const token = getToken()
  if (token) h.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`/api${path}`, { headers: h })
  const ct = (res.headers.get('content-type') || '').toLowerCase()
  if (ct.startsWith('application/json')) {
    let json: ApiResult<unknown>
    try {
      json = (await res.json()) as ApiResult<unknown>
    } catch {
      throw new ApiError(res.status, '响应解析失败')
    }
    if (json.code === 401) {
      clearToken()
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      throw new ApiError(401, json.msg || '未登录或登录已过期')
    }
    throw new ApiError(json.code, json.msg || '请求失败')
  }

  const blob = await res.blob()
  const name = filenameFromDisposition(res.headers.get('content-disposition')) || fallbackName || 'download'
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
