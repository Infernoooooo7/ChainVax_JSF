// Auth + data providers for the ChainVax admin console.
// Data: react-admin's own ra-data-simple-rest provider pointed at /api/admin (the backend implements its
// contract: sort/range/filter query params + Content-Range header). Auth: separate admin login and a
// separate token key, so the admin console and the staff app never share a session.
import { fetchUtils, HttpError } from 'react-admin';
import simpleRestProvider from 'ra-data-simple-rest';

const API = '/api/admin';
const TOKEN_KEY = 'chainvax.admin.token';
const SESSION_KEY = 'chainvax.admin.session';

const readSession = () => {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY)) ?? null;
  } catch {
    return null;
  }
};

const clearSession = () => {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(SESSION_KEY);
};

// Spring returns RFC 9457 problem+json ({detail, code, errors:[{field,message}]}); react-admin expects
// error.message and, for inline form errors, body.errors as {field: message}.
const toHttpError = (error) => {
  const body = error?.body;
  if (!body || typeof body !== 'object') return error;
  let message = body.detail || body.title || error.message;
  let errors;
  if (Array.isArray(body.errors) && body.errors.length) {
    errors = Object.fromEntries(body.errors.map((e) => [e.field, e.message]));
    message = body.errors.map((e) => `${e.field}: ${e.message}`).join(' | ');
  }
  return new HttpError(message, error.status, errors ? { ...body, errors } : body);
};

export const httpClient = (url, options = {}) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) options.user = { authenticated: true, token: `Bearer ${token}` };
  return fetchUtils.fetchJson(url, options).catch((error) => {
    throw toHttpError(error);
  });
};

const rest = simpleRestProvider(API, httpClient);

export const dataProvider = {
  ...rest,
  getStats: () => httpClient(`${API}/stats`).then(({ json }) => json),
  verifyLedger: () => httpClient(`${API}/ledger/verify`).then(({ json }) => json),
};

export const authProvider = {
  async login({ username, password }) {
    const response = await fetch(`${API}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(response.status === 401 ? 'Wrong username or password.' : body.detail || `Sign-in failed (${response.status}).`);
    }
    localStorage.setItem(TOKEN_KEY, body.accessToken);
    localStorage.setItem(SESSION_KEY, JSON.stringify({ user: body.user, expiresAt: body.expiresAt }));
  },
  async logout() {
    clearSession();
  },
  async checkAuth() {
    const session = readSession();
    const expired = session?.expiresAt && new Date(session.expiresAt) <= new Date();
    if (!localStorage.getItem(TOKEN_KEY) || !session || expired) {
      clearSession();
      throw new Error('Please sign in.');
    }
  },
  async checkError(error) {
    if (error?.status === 401 || error?.status === 403) {
      clearSession();
      throw new Error('Your admin session ended. Please sign in again.');
    }
  },
  async getIdentity() {
    const user = readSession()?.user ?? {};
    return { id: user.username, fullName: user.fullName ?? user.username };
  },
  async getPermissions() {
    return readSession()?.user?.role;
  },
};
