import { FormEvent, useState } from 'react'

import { ApiError, login } from '../lib/api'

type LoginPageProps = {
  onEnter: () => void
}

export function LoginPage({ onEnter }: LoginPageProps) {
  const [username, setUsername] = useState('demo-admin-a')
  const [password, setPassword] = useState('demo-password')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(username.trim(), password)
      onEnter()
    } catch (loginError) {
      setError(loginError instanceof ApiError ? loginError.message : '登录失败，请稍后重试。')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-brand">
          <span className="login-brand-mark">A</span>
          <span>AECP</span>
        </div>
        <p className="section-kicker">航空工程协同平台</p>
        <h1 id="login-title">欢迎回来</h1>
        <p className="login-intro">登录后继续管理组织成员、项目协作与研发资料。</p>
        <form className="login-form" onSubmit={handleLogin}>
          <label>
            用户名
            <input
              autoComplete="username"
              onChange={(event) => setUsername(event.target.value)}
              value={username}
            />
          </label>
          <label>
            密码
            <input
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              value={password}
            />
          </label>
          {error && <p className="inline-notice error-notice" role="alert">{error}</p>}
          <button className="primary-action" disabled={submitting} type="submit">
            {submitting ? '正在登录…' : '登录'}
          </button>
        </form>
        <p className="login-footer">请使用组织管理员提供的账号登录</p>
      </section>
    </main>
  )
}