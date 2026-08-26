export const AUTH_TOKEN_STORAGE_KEY = 'aecp-auth-token'

export type OrganizationRole =
  | 'ORGANIZATION_ADMIN'
  | 'PROJECT_MANAGER'
  | 'ENGINEER'
  | 'AUDITOR'

export type ApiResponse<T> = {
  status: number
  message: string
  data: T | null
}

export type OrganizationUserCandidate = {
  user_id: string
  employee_no: string
  display_name: string
  already_member: boolean
}

export type OrganizationMember = {
  user_id: string
  username: string
  display_name: string
  role: OrganizationRole
  joined_at: string
}

export type OrganizationMemberList = {
  items: OrganizationMember[]
  total: number
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export function getAuthToken() {
  return window.localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)
}

export function setAuthToken(token: string) {
  window.localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token)
}

export function clearAuthToken() {
  window.localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY)
}

async function request<T>(path: string, init: RequestInit = {}) {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) {
    headers.set('Content-Type', 'application/json')
  }

  const token = getAuthToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(path, { ...init, headers })
  const body = (await response.json()) as ApiResponse<T>
  if (!response.ok || body.status >= 400) {
    throw new ApiError(body.message || `请求失败（${response.status}）`, response.status)
  }

  return body
}

export async function login(username: string, password: string) {
  const result = await request<{ token: string; expires_in: number }>('/api/v1/auth/login', {
    body: JSON.stringify({ username, password }),
    method: 'POST'
  })
  if (!result.data?.token) {
    throw new ApiError('登录响应缺少会话令牌', 500)
  }
  setAuthToken(result.data.token)
  return result.data
}

export async function logout() {
  try {
    await request('/api/v1/auth/logout', { method: 'POST' })
  } finally {
    clearAuthToken()
  }
}

const membersPath = (organizationId: string) =>
  `/api/v1/organizations/${encodeURIComponent(organizationId)}/members`

export async function listOrganizationMembers(organizationId: string) {
  const result = await request<OrganizationMemberList>(membersPath(organizationId))
  return result.data ?? { items: [], total: 0 }
}


export async function searchOrganizationMemberCandidates(organizationId: string, employeeNo: string) {
  const result = await request<OrganizationUserCandidate[]>(`${membersPath(organizationId)}/candidates?employee_no=${encodeURIComponent(employeeNo)}`)
  return result.data ?? []
}

export async function addOrganizationMember(
  organizationId: string,
  userId: string,
  role: OrganizationRole
) {
  const result = await request<OrganizationMember>(membersPath(organizationId), {
    body: JSON.stringify({ role, user_id: userId }),
    method: 'POST'
  })
  return result.data
}

export async function updateOrganizationMemberRole(
  organizationId: string,
  userId: string,
  role: OrganizationRole
) {
  const result = await request<OrganizationMember>(
    `${membersPath(organizationId)}/${encodeURIComponent(userId)}`,
    {
      body: JSON.stringify({ role }),
      method: 'PATCH'
    }
  )
  return result.data
}

export async function removeOrganizationMember(organizationId: string, userId: string) {
  await request(`${membersPath(organizationId)}/${encodeURIComponent(userId)}`, {
    method: 'DELETE'
  })
}
