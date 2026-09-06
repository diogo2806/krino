const TOKEN_KEY = 'krino_access_token';

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const apiUrl = (window.__KRINO_CONFIG__?.apiUrl ?? 'http://localhost:8080/api').replace(/\/$/, '');
  const token = getToken();
  const headers = new Headers(init.headers);
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${apiUrl}${path}`, { ...init, headers });
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    if (!response.ok) {
      throw new ApiError(response.status, 'Não foi possível concluir a operação.');
    }
    return undefined as T;
  }

  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, body.message ?? 'Não foi possível concluir a operação.');
  }
  return body as T;
}
