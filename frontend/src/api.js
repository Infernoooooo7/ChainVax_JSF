// Tiny fetch wrapper: adds the JWT, parses problem+json errors into Error objects.
const SESSION_KEY = 'chainvax.session'

export function loadSession() {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw)
    return new Date(session.expiresAt) > new Date() ? session : null
  } catch {
    return null
  }
}

export function saveSession(tokenResponse) {
  const session = {
    token: tokenResponse.accessToken,
    expiresAt: tokenResponse.expiresAt,
    user: tokenResponse.user,
  }
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
  return session
}

export function clearSession() {
  sessionStorage.removeItem(SESSION_KEY)
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

export async function api(path, { method = 'GET', body, token, raw = false } = {}) {
  const session = loadSession()
  const bearer = token ?? session?.token
  const res = await fetch(path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }
  if (raw) return { status: res.status, ok: res.ok, data }
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/api/auth/')) onUnauthorized()
    const err = new Error(
      (data && (data.detail || data.title)) || `${res.status} ${res.statusText || 'error'}`,
    )
    err.status = res.status
    err.code = data?.code
    err.data = data
    throw err
  }
  return data
}

export async function login(username, password) {
  const tokenResponse = await api('/api/auth/login', { method: 'POST', body: { username, password }, token: '' })
  return saveSession(tokenResponse)
}

export async function sha256Hex(text) {
  const bytes = new TextEncoder().encode(text)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}
