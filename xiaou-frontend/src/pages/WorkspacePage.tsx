export function WorkspacePage() {
  return (
    <main className="workspace-page">
      <section className="page-hero">
        <p className="section-kicker">项目空间</p>
        <h1>项目空间壳体</h1>
        <p>真实项目列表、最近访问和切换逻辑将在后续任务中实现；本任务仅交付工程壳与路由骨架。</p>
      </section>

      <section className="content-panel">
        <article className="content-card">
          <h2>公开首页与登录页已就位</h2>
          <p>你可以从产品首页进入登录页，再通过本地 demo-session guard 浏览后续业务占位路由。</p>
        </article>
        <article className="content-card">
          <h2>公共状态组件可复用</h2>
          <p>加载、空态和错误态会在后续任务直接复用，不再重复搭基础视觉结构。</p>
        </article>
      </section>
    </main>
  )
}
