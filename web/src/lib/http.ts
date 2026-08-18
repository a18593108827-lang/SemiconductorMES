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
