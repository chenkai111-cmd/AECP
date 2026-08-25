import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'

import {
  AppRoutes,
  DEMO_SESSION_STORAGE_KEY
} from '../router'
import {
  PROTECTED_ROUTE_FALLBACK,
  protectedRouteEntries,
  sanitizeProtectedRedirect
} from '../route-config'

function LocationProbe() {
  const location = useLocation()
  return (
    <output data-testid="location-display">
      {location.pathname}
      {location.search}
      {location.hash}
    </output>
  )
}

function renderApp(initialEntries: string[]) {
  return render(
    <MemoryRouter future={{ v7_relativeSplatPath: true, v7_startTransition: true }} initialEntries={initialEntries}>
      <AppRoutes />
      <LocationProbe />
    </MemoryRouter>
  )
}

describe('AECP frontend shell routes', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('renders the public product home with the migrated product-positioning copy', () => {
    renderApp(['/'])

    expect(
      screen.getByRole('heading', { name: '让每一次协同，都能形成闭环。' })
    ).toBeInTheDocument()
    expect(
      screen.getByText('一期聚焦“会议 → 任务 → 文件 → 部件追溯”垂直闭环，STEP 数模支持浏览器端查看。')
    ).toBeInTheDocument()
  })

  it.each([
    ['malicious protocol', 'javascript:alert(1)', PROTECTED_ROUTE_FALLBACK],
    ['external url', 'https://evil.example/attack', PROTECTED_ROUTE_FALLBACK],
    ['protocol relative', '//evil.example/attack', PROTECTED_ROUTE_FALLBACK],
    ['public home', '/', PROTECTED_ROUTE_FALLBACK],
    ['public login', '/login', PROTECTED_ROUTE_FALLBACK],
    ['unknown internal', '/unknown/route', PROTECTED_ROUTE_FALLBACK],
    ['valid nested model', '/models/FV-2026-001?tab=preview#mesh', '/models/FV-2026-001?tab=preview#mesh']
  ])('sanitizes %s redirect targets', (_label, redirect, expected) => {
    expect(sanitizeProtectedRedirect(redirect)).toBe(expected)
  })

  it.each([
    ['external url', 'https://evil.example/attack'],
    ['public home', '/'],
    ['unknown internal', '/unknown/route']
  ])('falls back to workspace when login redirect is %s', async (_label, redirect) => {
    const user = userEvent.setup()

    renderApp([`/login?redirect=${encodeURIComponent(redirect)}`])

    await user.click(screen.getByRole('button', { name: '进入本地演示会话' }))

    expect(screen.getByTestId('location-display')).toHaveTextContent(PROTECTED_ROUTE_FALLBACK)
    expect(await screen.findByRole('heading', { name: '项目空间壳体' })).toBeInTheDocument()
  })

  it('keeps a valid nested model redirect after login', async () => {
    const user = userEvent.setup()

    renderApp([
      `/login?redirect=${encodeURIComponent('/models/FV-2026-001?tab=preview#mesh')}`
    ])

    await user.click(screen.getByRole('button', { name: '进入本地演示会话' }))

    expect(screen.getByTestId('location-display')).toHaveTextContent('/models/FV-2026-001?tab=preview#mesh')
    expect(await screen.findByRole('heading', { name: '数模查看占位页' })).toBeInTheDocument()
  })

  it.each(protectedRouteEntries)(
    'protects %s while logged out',
    async ({ path }) => {
      renderApp([path])

      expect(
        await screen.findByRole('heading', { name: '登录 AECP 前端演示壳' })
      ).toBeInTheDocument()
      expect(screen.getByTestId('location-display')).toHaveTextContent(
        `/login?redirect=${encodeURIComponent(path)}`
      )
    }
  )

  it.each(protectedRouteEntries)(
    'renders shell and target page for %s while demo-authenticated',
    async ({ navLabel, path, title }) => {
      window.localStorage.setItem(DEMO_SESSION_STORAGE_KEY, 'active')

      renderApp([path])

      expect(await screen.findByRole('navigation', { name: 'AECP 主导航' })).toBeInTheDocument()
      expect(screen.getByRole('link', { name: navLabel })).toHaveAttribute('aria-current', 'page')
      expect(screen.getByRole('heading', { name: title })).toBeInTheDocument()
      expect(screen.getByTestId('location-display')).toHaveTextContent(path)
    }
  )
})
