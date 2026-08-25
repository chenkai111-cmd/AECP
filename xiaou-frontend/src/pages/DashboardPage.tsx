import { RouteStateShowcase } from '../app/components/RouteStateShowcase'

export function DashboardPage() {
  return (
    <main className="workspace-page">
      <section className="page-hero">
        <p className="section-kicker">项目总览</p>
        <h1>项目总览驾驶舱</h1>
        <p>当前页面只提供壳体、布局与状态组件占位；真实数据与交互行为由后续任务接管。</p>
      </section>

      <section className="content-panel" aria-label="驾驶舱占位卡片">
        <article className="content-card accent-card">
          <h2>会议 → 任务 → 文件 → 部件追溯</h2>
          <p>这一条主链路已经迁移到前端首页，并在受保护区域保持统一导航与布局语义。</p>
        </article>
        <article className="content-card">
          <h2>后续任务接管数据</h2>
          <p>Task 01 只提供工程壳、路由守卫和页面边界，不提前引入真实接口、鉴权或状态库。</p>
        </article>
      </section>

      <RouteStateShowcase ariaLabel="驾驶舱通用状态示例" />
    </main>
  )
}
