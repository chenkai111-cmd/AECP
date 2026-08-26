import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { OrganizationMembersPage } from '../OrganizationMembersPage'

const member = {
  user_id: 'USR-DEMO-ADMIN-A',
  username: 'demo-admin-a',
  display_name: '演示管理员 A',
  role: 'ORGANIZATION_ADMIN' as const,
  joined_at: '2026-08-25T00:00:00Z'
}

function response(data: unknown, status = 200) {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve({ status, message: 'ok', data })
  })
}

describe('OrganizationMembersPage', () => {
  beforeEach(() => {
    window.localStorage.setItem('aecp-auth-token', 'test-token')
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    window.localStorage.clear()
  })

  it('loads and renders members from the F03 API', async () => {
    vi.mocked(fetch).mockReturnValueOnce(
      response({ items: [member], total: 1 }) as unknown as Promise<Response>
    )

    render(<OrganizationMembersPage />)

    expect(screen.getAllByText('正在读取组织成员…').length).toBeGreaterThan(0)
    expect(await screen.findByText('演示管理员 A')).toBeInTheDocument()
    expect(screen.getByText('共 1 名成员')).toBeInTheDocument()
    expect(screen.getByText('组织管理员', { selector: '.role-label' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '成员角色-USR-DEMO-ADMIN-A' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '保存角色-USR-DEMO-ADMIN-A' })).not.toBeInTheDocument()
  })

  it('searches by employee number, selects a user, and adds the member', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockReturnValueOnce(response({ items: [], total: 0 }) as unknown as Promise<Response>)
      .mockReturnValueOnce(response([{ user_id: 'USR-DEMO-ENG-A', employee_no: 'A-1001', display_name: '演示工程师 A' }]) as unknown as Promise<Response>)
      .mockReturnValueOnce(response({ user_id: 'USR-DEMO-ENG-A' }, 201) as unknown as Promise<Response>)
      .mockReturnValueOnce(
        response({
          items: [
            {
              ...member,
              user_id: 'USR-DEMO-ENG-A',
              username: 'demo-engineer-a',
              display_name: '演示工程师 A',
              role: 'ENGINEER'
            }
          ],
          total: 1
        }) as unknown as Promise<Response>
      )

    render(<OrganizationMembersPage />)
    await screen.findByText('暂无成员')

    await user.type(screen.getByLabelText('用户工号'), 'A-1001')
    await user.click(screen.getByRole('button', { name: '搜索用户' }))
    await user.click(await screen.findByRole('button', { name: /演示工程师 A/ }))
    await user.selectOptions(screen.getByLabelText('新成员角色'), 'ENGINEER')
    await user.click(screen.getByRole('button', { name: '添加成员' }))

    expect(await screen.findByText('演示工程师 A')).toBeInTheDocument()
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(4))
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/v1/organizations/ORG-DEMO-COMAC/members/candidates?employee_no=A-1001', expect.anything())
    const addCall = vi.mocked(fetch).mock.calls[2]
    expect(addCall[0]).toBe('/api/v1/organizations/ORG-DEMO-COMAC/members')
    expect(JSON.parse(String((addCall[1] as RequestInit).body))).toEqual({
      user_id: 'USR-DEMO-ENG-A',
      role: 'ENGINEER'
    })
  })

  it('shows member roles as read-only in the directory', async () => {
    vi.mocked(fetch).mockReturnValueOnce(
      response({ items: [member], total: 1 }) as unknown as Promise<Response>
    )

    render(<OrganizationMembersPage />)
    await screen.findByText('演示管理员 A')

    expect(screen.getByText('组织管理员', { selector: '.role-label' })).toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '成员角色-USR-DEMO-ADMIN-A' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '保存角色-USR-DEMO-ADMIN-A' })).not.toBeInTheDocument()
  })

  it('shows a dialog when the searched user is already a member', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockReturnValueOnce(response({ items: [member], total: 1 }) as unknown as Promise<Response>)
      .mockReturnValueOnce(response([{ user_id: member.user_id, employee_no: 'A-1001', display_name: '演示管理员 A', already_member: true }]) as unknown as Promise<Response>)

    render(<OrganizationMembersPage />)
    await screen.findByText('演示管理员 A')
    await user.type(screen.getByLabelText('用户工号'), 'A-1001')
    await user.click(screen.getByRole('button', { name: '搜索用户' }))

    expect(await screen.findByRole('dialog')).toHaveTextContent('演示管理员 A')
    expect(screen.getByRole('dialog')).toHaveTextContent('已是当前组织成员')
  })

  it('opens a login dialog after the session expires and reloads after login', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch)
      .mockReturnValueOnce(response({ message: 'expired' }, 401) as unknown as Promise<Response>)
      .mockReturnValueOnce(response({ token: 'fresh-token', expires_in: 3600 }) as unknown as Promise<Response>)
      .mockReturnValueOnce(response({ items: [member], total: 1 }) as unknown as Promise<Response>)

    render(<OrganizationMembersPage />)
    expect(await screen.findByRole('dialog')).toHaveTextContent('登录会话已失效')
    await user.clear(screen.getByLabelText('登录用户名'))
    await user.type(screen.getByLabelText('登录用户名'), 'demo-admin-a')
    await user.clear(screen.getByLabelText('登录密码'))
    await user.type(screen.getByLabelText('登录密码'), 'demo-password')
    await user.click(screen.getByRole('button', { name: '重新登录' }))

    expect(await screen.findByText('演示管理员 A')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
