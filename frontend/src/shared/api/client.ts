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

function apiBaseUrl() {
  return (window.__KRINO_CONFIG__?.apiUrl ?? 'http://localhost:8080/api').replace(/\/$/, '');
}

function authenticatedHeaders(headers?: HeadersInit) {
  const result = new Headers(headers);
  const token = getToken();
  if (token) result.set('Authorization', `Bearer ${token}`);
  return result;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = authenticatedHeaders(init.headers);
  if (!headers.has('Content-Type') && init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${apiBaseUrl()}${path}`, { ...init, headers });
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

export async function apiBlob(path: string): Promise<Blob> {
  const response = await fetch(`${apiBaseUrl()}${path}`, { headers: authenticatedHeaders() });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(response.status, body.message ?? 'Não foi possível carregar o arquivo.');
  }
  return response.blob();
}
