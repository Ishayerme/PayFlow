// Thin wrapper around fetch: adds the JWT and turns error responses into exceptions
// carrying the server's message (e.g. "Insufficient funds in account PF...").

let token = sessionStorage.getItem('payflow.token')

export function setToken(value) {
  token = value
  if (value) sessionStorage.setItem('payflow.token', value)
  else sessionStorage.removeItem('payflow.token')
}

export function hasToken() {
  return Boolean(token)
}

async function request(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  if (response.status === 401 && token) {
    setToken(null)
    throw new Error('Session expired, please log in again')
  }
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) throw new Error(data?.error ?? `Request failed (${response.status})`)
  return data
}

export const api = {
  register: (email, password, fullName) => request('POST', '/api/auth/register', { email, password, fullName }),
  login: (email, password) => request('POST', '/api/auth/login', { email, password }),
  accounts: () => request('GET', '/api/accounts'),
  openAccount: () => request('POST', '/api/accounts'),
  deposit: (id, amount) => request('POST', `/api/accounts/${id}/deposit`, { amount }),
  transfer: (fromAccountId, toAccountId, amount) =>
    request('POST', '/api/transfers', { fromAccountId, toAccountId, amount }),
  statement: (id) => request('GET', `/api/accounts/${id}/statement?limit=20`),
  notifications: () => request('GET', '/api/notifications'),
  allAccounts: () => request('GET', '/api/admin/accounts'),
  setStatus: (id, status) => request('PATCH', `/api/admin/accounts/${id}/status`, { status }),
}
