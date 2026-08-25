import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'

import { AppRoutes, DEMO_SESSION_STORAGE_KEY } from '../router'

describe('AECP frontend shell routes', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('renders the public product home with the migrated product-positioning copy', () => {
    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={['/']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(
      screen.getByRole('heading', { name: '让每一次协同，都能形成闭环。' })
    ).toBeInTheDocument()
    expect(
      screen.getByText('一期聚焦“会议 → 任务 → 文件 → 部件追溯”垂直闭环，STEP 数模支持浏览器端查看。')
    ).toBeInTheDocument()
  })

  it('redirects unauthenticated users from protected routes to login and preserves the target path', async () => {
    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={['/models/FV-2026-001']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(
      await screen.findByRole('heading', { name: '登录 AECP 前端演示壳' })
    ).toBeInTheDocument()
    expect(
      screen.getByText('当前只启用本地 demo-session 适配器，用于 Task 05 前的路由联调。')
    ).toBeInTheDocument()
    expect(screen.getByText('登录后将跳转到：/models/FV-2026-001')).toBeInTheDocument()
  })

  it('creates a local demo session and lands on the originally requested protected route', async () => {
    const user = userEvent.setup()

    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={['/dashboard']}>
        <AppRoutes />
      </MemoryRouter>
    )

    await user.click(screen.getByRole('button', { name: '进入本地演示会话' }))

    expect(window.localStorage.getItem(DEMO_SESSION_STORAGE_KEY)).toBe('active')
    expect(
      await screen.findByRole('heading', { name: '项目总览驾驶舱' })
    ).toBeInTheDocument()
    expect(
      screen.getByText('当前页面只提供壳体、布局与状态组件占位；真实数据与交互行为由后续任务接管。')
    ).toBeInTheDocument()
  })

  it('renders the protected shell navigation and placeholder ownership notice for business pages', async () => {
    window.localStorage.setItem(DEMO_SESSION_STORAGE_KEY, 'active')

    render(
      <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={['/admin/audit']}>
        <AppRoutes />
      </MemoryRouter>
    )

    expect(
      await screen.findByRole('navigation', { name: 'AECP 主导航' })
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '系统审计' })).toHaveAttribute('aria-current', 'page')
    expect(
      screen.getByText('审计日志、筛选器和明细行为将在后续任务中实现；本任务仅交付工程壳与路由骨架。')
    ).toBeInTheDocument()
    expect(screen.getByText('加载中')).toBeInTheDocument()
    expect(screen.getByText('暂无数据')).toBeInTheDocument()
    expect(screen.getByText('示例错误态')).toBeInTheDocument()
  })
})
