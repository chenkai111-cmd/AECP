import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'

import { RouteStateShowcase } from './components/RouteStateShowcase'
import {
  getProtectedRouteForPath,
  protectedRouteEntries,
  sanitizeProtectedRedirect
} from './route-config'
import { DashboardPage } from '../pages/DashboardPage'
import { LoginPage } from '../pages/LoginPage'
import { WorkspacePage } from '../pages/WorkspacePage'

export const DEMO_SESSION_STORAGE_KEY = 'aecp-demo-session'

function hasDemoSession() {
  return window.localStorage.getItem(DEMO_SESSION_STORAGE_KEY) === 'active'
}

function setDemoSessionActive() {
  window.localStorage.setItem(DEMO_SESSION_STORAGE_KEY, 'active')
}

function clearDemoSession() {
  window.localStorage.removeItem(DEMO_SESSION_STORAGE_KEY)
}

function ProductHomePage() {
  return (
    <main className="marketing-page">
      <section className="marketing-hero">
        <div className="marketing-copy">
          <p className="eyebrow">AERO · ENGINE · COLLABORATION</p>
          <h1>让每一次协同，都能形成闭环。</h1>
          <p className="lead">
            面向飞机与发动机联合研发的统一协作空间。围绕
            <strong> 会议决议、任务分发、文件版本和系统部件 </strong>
            ，把跨组织的技术协作沉淀为可追溯的研发过程。
          </p>
          <div className="hero-actions">
            <Link className="primary-action" to="/login">
              进入前端演示壳
            </Link>
            <a className="secondary-action" href="#flow">
              查看协作主链路
            </a>
          </div>
          <p className="hero-note">
            一期聚焦“会议 → 任务 → 文件 → 部件追溯”垂直闭环，STEP 数模支持浏览器端查看。
          </p>
        </div>

        <section className="hero-panel" aria-label="一期协作主链路预览">
          <div className="panel-header">
            <span>协同控制台 / 预览</span>
            <strong>AECP-MVP-01</strong>
          </div>
          <div className="flow-board" id="flow">
            <h2>一条链路，连接每个关键动作。</h2>
            <p>把“会上说了什么、谁来完成、文件用哪个版本、影响哪个部件”放到同一条可回看的线上记录里。</p>
            <ol>
              <li>
                <strong>会议</strong>
                <span>决议沉淀</span>
              </li>
              <li>
                <strong>任务</strong>
                <span>自动分发</span>
              </li>
              <li>
                <strong>文件</strong>
                <span>版本关联</span>
              </li>
              <li>
                <strong>部件</strong>
                <span>状态追溯</span>
              </li>
            </ol>
            <p className="panel-footer">双方项目成员共享同一文件空间</p>
          </div>
        </section>
      </section>

      <section className="capability-section" aria-labelledby="capability-title">
        <div className="section-heading">
          <p className="section-kicker">一期能力边界</p>
          <h2 id="capability-title">围绕主链路交付清晰的工程壳</h2>
        </div>
        <div className="capability-grid">
          <article>
            <h3>会议决议有出处</h3>
            <p>预约、议程、纪要与结构化决议集中沉淀，历史会议可以按时间和参与人追溯。</p>
          </article>
          <article>
            <h3>任务分发有负责人</h3>
            <p>决议确认后形成任务，按部件负责人和职责矩阵进入对应成员的代办列表。</p>
          </article>
          <article>
            <h3>文件版本可复用</h3>
            <p>统一项目文件空间支持双方成员查看与上传，文件版本和部件关联保持一致。</p>
          </article>
          <article>
            <h3>部件状态能追溯</h3>
            <p>部件树、双方负责人、关联文件与数模、变更记录在一个视图中连续呈现。</p>
          </article>
          <article>
            <h3>STEP 数模可在线查看</h3>
            <p>一期支持 STEP 文件在浏览器端解析查看，为文件、数模和部件之间建立直观连接。</p>
          </article>
          <article>
            <h3>项目进展有全局视角</h3>
            <p>项目经理可以从任务完成、部件交付和风险事项等维度掌握协作健康度。</p>
          </article>
        </div>
      </section>
    </main>
  )
}

function RequireDemoSession() {
  const location = useLocation()

  if (!hasDemoSession()) {
    const target = `${location.pathname}${location.search}${location.hash}`
    return <Navigate replace to={`/login?redirect=${encodeURIComponent(target)}`} />
  }

  return <AppShell />
}

function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const activeRoute = getProtectedRouteForPath(location.pathname)
  const activeLabel = activeRoute?.navLabel ?? '工作台'

  return (
    <div className="app-shell">
      <aside className="shell-sidebar">
        <div>
          <p className="shell-mark">AECP FRONTEND SHELL</p>
          <h1>一期路由骨架</h1>
          <p className="shell-summary">公开首页与登录页保持开放，业务路由统一通过一个本地 demo-session guard 受控。</p>
        </div>
        <nav aria-label="AECP 主导航" className="shell-nav">
          {protectedRouteEntries.map((route) => {
            const isCurrent = getProtectedRouteForPath(location.pathname)?.navLabel === route.navLabel

            return (
              <Link
                key={route.path}
                aria-current={isCurrent ? 'page' : undefined}
                className={isCurrent ? 'nav-link current' : 'nav-link'}
                to={route.path}
              >
                {route.navLabel}
              </Link>
            )
          })}
        </nav>
        <button
          className="secondary-action shell-logout"
          onClick={() => {
            clearDemoSession()
            navigate('/login', { replace: true })
          }}
          type="button"
        >
          退出本地演示会话
        </button>
      </aside>

      <div className="shell-main">
        <header className="shell-header">
          <div>
            <p className="section-kicker">当前模块</p>
            <h2>{activeLabel}</h2>
          </div>
          <p className="shell-header-note">PC 优先排版，窄屏下自动折叠为单列布局。</p>
        </header>
        <Outlet />
      </div>
    </div>
  )
}

function PlaceholderPage({ description, title }: { description: string; title: string }) {
  return (
    <main className="workspace-page">
      <section className="page-hero">
        <p className="section-kicker">任务 01 / Placeholder</p>
        <h1>{title}</h1>
        <p>{description}</p>
      </section>

      <RouteStateShowcase ariaLabel="通用界面状态示例" />
    </main>
  )
}

function LoginRoutePage() {
  const navigate = useNavigate()
  const location = useLocation()
  const search = new URLSearchParams(location.search)
  const redirectTo = sanitizeProtectedRedirect(search.get('redirect'))

  return (
    <LoginPage
      onEnter={() => {
        setDemoSessionActive()
        navigate(redirectTo, { replace: true })
      }}
      redirectTo={redirectTo}
    />
  )
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<ProductHomePage />} path="/" />
      <Route element={<LoginRoutePage />} path="/login" />
      <Route element={<RequireDemoSession />}>
        <Route element={<WorkspacePage />} path="/workspace" />
        <Route element={<DashboardPage />} path="/dashboard" />
        {protectedRouteEntries
          .filter((route) => route.path !== '/workspace' && route.path !== '/dashboard')
          .map((route) => (
            <Route
              element={<PlaceholderPage description={route.description} title={route.title} />}
              key={route.path}
              path={route.path === '/models/FV-2026-001' ? '/models/:fileVersionId' : route.path}
            />
          ))}
      </Route>
    </Routes>
  )
}
