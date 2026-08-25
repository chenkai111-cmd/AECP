type LoginPageProps = {
  onEnter: () => void
  redirectTo: string
}

export function LoginPage({ onEnter, redirectTo }: LoginPageProps) {
  return (
    <main className="login-page">
      <section className="login-card">
        <p className="section-kicker">本地演示适配器</p>
        <h1>登录 AECP 前端演示壳</h1>
        <p>当前只启用本地 demo-session 适配器，用于 Task 05 前的路由联调。</p>
        <p className="login-redirect">登录后将跳转到：{redirectTo}</p>
        <button className="primary-action" onClick={onEnter} type="button">
          进入本地演示会话
        </button>
      </section>
    </main>
  )
}
