import { useCallback, useEffect, useState } from 'react'
import { api, hasToken, setToken } from './api.js'

const money = (n) => Number(n).toLocaleString('en-IN', { style: 'currency', currency: 'INR' })
const when = (iso) => new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })

export default function App() {
  const [user, setUser] = useState(() => JSON.parse(sessionStorage.getItem('payflow.user') ?? 'null'))

  const onLogin = (session) => {
    setToken(session.token)
    sessionStorage.setItem('payflow.user', JSON.stringify(session.user))
    setUser(session.user)
  }
  const logout = () => {
    setToken(null)
    sessionStorage.removeItem('payflow.user')
    setUser(null)
  }

  if (!user || !hasToken()) return <AuthPage onLogin={onLogin} />

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">PayFlow</span>
        <span className="who">
          {user.fullName} <span className="badge">{user.role}</span>
          <button className="link" onClick={logout}>Log out</button>
        </span>
      </header>
      {user.role === 'ADMIN' ? <AdminPage /> : <CustomerPage onSessionExpired={logout} />}
    </div>
  )
}

function AuthPage({ onLogin }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ email: '', password: '', fullName: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      if (mode === 'register') await api.register(form.email, form.password, form.fullName)
      onLogin(await api.login(form.email, form.password))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }
  const field = (name) => ({ value: form[name], onChange: (e) => setForm({ ...form, [name]: e.target.value }) })

  return (
    <div className="auth">
      <form className="card auth-card" onSubmit={submit}>
        <h1>PayFlow</h1>
        <p className="muted">{mode === 'login' ? 'Log in to your account' : 'Create an account'}</p>
        {mode === 'register' && <label>Full name<input required {...field('fullName')} /></label>}
        <label>Email<input type="email" required {...field('email')} /></label>
        <label>Password<input type="password" required minLength={8} {...field('password')} /></label>
        {error && <p className="error">{error}</p>}
        <button disabled={busy}>{mode === 'login' ? 'Log in' : 'Sign up'}</button>
        <button type="button" className="link" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'New here? Create an account' : 'Have an account? Log in'}
        </button>
      </form>
    </div>
  )
}

function CustomerPage({ onSessionExpired }) {
  const [accounts, setAccounts] = useState([])
  const [selected, setSelected] = useState(null)
  const [statement, setStatement] = useState([])
  const [notifications, setNotifications] = useState([])
  const [message, setMessage] = useState(null)

  const run = useCallback(async (fn, success) => {
    setMessage(null)
    try {
      await fn()
      if (success) setMessage({ ok: true, text: success })
    } catch (err) {
      if (err.message.startsWith('Session expired')) onSessionExpired()
      setMessage({ ok: false, text: err.message })
    }
  }, [onSessionExpired])

  const refresh = useCallback(async () => {
    const list = await api.accounts()
    setAccounts(list)
    const current = list.find((a) => a.id === selected) ?? list[0]
    if (current) {
      setSelected(current.id)
      setStatement(await api.statement(current.id))
    }
    setNotifications(await api.notifications())
  }, [selected])

  useEffect(() => { run(refresh) }, [selected]) // eslint-disable-line react-hooks/exhaustive-deps

  const account = accounts.find((a) => a.id === selected)

  return (
    <main className="grid">
      <section className="card span-2">
        <div className="row">
          <h2>My accounts</h2>
          <button onClick={() => run(async () => { await api.openAccount(); await refresh() }, 'Account opened')}>
            + Open account
          </button>
        </div>
        {accounts.length === 0 && <p className="muted">No accounts yet. Open one to get started.</p>}
        <div className="accounts">
          {accounts.map((a) => (
            <button key={a.id} className={`account ${a.id === selected ? 'active' : ''}`} onClick={() => setSelected(a.id)}>
              <span className="muted">#{a.id} · {a.accountNumber}</span>
              <strong>{money(a.balance)}</strong>
              {a.status !== 'ACTIVE' && <span className="badge warn">{a.status}</span>}
            </button>
          ))}
        </div>
        {message && <p className={message.ok ? 'success' : 'error'}>{message.text}</p>}
      </section>

      {account && (
        <>
          <MoneyForm
            title="Deposit"
            submitLabel="Deposit"
            onSubmit={({ amount }) => run(async () => { await api.deposit(account.id, amount); await refresh() },
              `Deposited ${money(amount)}`)}
          />
          <MoneyForm
            title="Transfer"
            withTarget
            submitLabel="Send money"
            onSubmit={({ amount, to }) => run(async () => { await api.transfer(account.id, Number(to), amount); await refresh() },
              `Sent ${money(amount)} to account #${to}`)}
          />
          <section className="card span-2">
            <h2>Statement · {account.accountNumber}</h2>
            <table>
              <thead><tr><th>Date</th><th>Details</th><th className="num">Amount</th><th>Status</th></tr></thead>
              <tbody>
                {statement.length === 0 && <tr><td colSpan="4" className="muted">No transactions yet</td></tr>}
                {statement.map((t) => {
                  const outgoing = t.fromAccountId === account.id
                  return (
                    <tr key={t.id}>
                      <td>{when(t.createdAt)}</td>
                      <td>{outgoing ? `To #${t.toAccountId}` : `From #${t.fromAccountId}`}
                        {t.failureReason && <div className="muted small">{t.failureReason}</div>}</td>
                      <td className={`num ${outgoing ? 'out' : 'in'}`}>{outgoing ? '−' : '+'}{money(t.amount)}</td>
                      <td><span className={`badge ${t.status === 'FAILED' ? 'warn' : ''}`}>{t.status}</span></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </section>
        </>
      )}

      <section className="card span-2">
        <h2>Notifications</h2>
        <ul className="notes">
          {notifications.length === 0 && <li className="muted">Nothing yet</li>}
          {notifications.map((n) => <li key={n.id}><span>{n.message}</span><span className="muted small">{when(n.createdAt)}</span></li>)}
        </ul>
      </section>
    </main>
  )
}

function MoneyForm({ title, submitLabel, withTarget, onSubmit }) {
  const [amount, setAmount] = useState('')
  const [to, setTo] = useState('')
  const submit = (e) => {
    e.preventDefault()
    onSubmit({ amount: Number(amount), to })
    setAmount('')
  }
  return (
    <form className="card" onSubmit={submit}>
      <h2>{title}</h2>
      {withTarget && <label>To account #<input type="number" min="1" required value={to} onChange={(e) => setTo(e.target.value)} /></label>}
      <label>Amount (₹)<input type="number" min="0.01" step="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} /></label>
      <button>{submitLabel}</button>
    </form>
  )
}

function AdminPage() {
  const [accounts, setAccounts] = useState([])
  const [error, setError] = useState('')
  const load = () => api.allAccounts().then(setAccounts).catch((e) => setError(e.message))
  useEffect(() => { load() }, [])

  const toggle = async (a) => {
    try {
      await api.setStatus(a.id, a.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE')
      await load()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <main className="grid">
      <section className="card span-2">
        <h2>All accounts</h2>
        {error && <p className="error">{error}</p>}
        <table>
          <thead><tr><th>#</th><th>Number</th><th>Owner</th><th className="num">Balance</th><th>Status</th><th /></tr></thead>
          <tbody>
            {accounts.map((a) => (
              <tr key={a.id}>
                <td>{a.id}</td><td>{a.accountNumber}</td><td>user {a.userId}</td>
                <td className="num">{money(a.balance)}</td>
                <td><span className={`badge ${a.status === 'FROZEN' ? 'warn' : ''}`}>{a.status}</span></td>
                <td><button className="small-btn" onClick={() => toggle(a)}>{a.status === 'ACTIVE' ? 'Freeze' : 'Unfreeze'}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </main>
  )
}
