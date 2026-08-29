import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const TOKEN_KEY = 'liganex.accessToken';
const REFRESH_KEY = 'liganex.refreshToken';

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}
export function setTokens(access: string, refresh: string): void {
  localStorage.setItem(TOKEN_KEY, access);
  localStorage.setItem(REFRESH_KEY, refresh);
}
export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

/** 统一业务异常：后端 ApiResponse.code != 0 或 Spring 默认错误体均转为 ApiError。 */
export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

/** 从任意错误中提取可读信息。 */
export function extractError(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { code?: number; message?: string } | undefined;
    if (data && typeof data.message === 'string') return data.message;
    return e.message || '网络请求失败';
  }
  if (e instanceof Error) return e.message;
  return '请求失败，请稍后重试';
}

const client = axios.create({ baseURL: '/api' });

// 请求拦截：注入 Bearer
client.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

type Pending = (token: string | null) => void;
let isRefreshing = false;
let pendingQueue: Pending[] = [];

function redirectLogin(): void {
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

// 响应拦截：成功体按 code===0 解包；401 仅对「带鉴权的请求」尝试刷新
client.interceptors.response.use(
  (response): any => {
    const body = response.data as { code?: number; message?: string; data?: unknown };
    if (body && typeof body === 'object' && 'code' in body) {
      const code = body.code ?? -1;
      if (code === 0) return body.data;
      throw new ApiError(code, body.message ?? '业务错误');
    }
    return response.data;
  },
  async (error: AxiosError) => {
    const original = error.config as
      | (InternalAxiosRequestConfig & { _retry?: boolean })
      | undefined;
    const status = error.response?.status;

    if (status === 401 && original && !original._retry && original.headers?.get('Authorization')) {
      original._retry = true;
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push((token) => {
            if (token) {
              original.headers!.set('Authorization', `Bearer ${token}`);
              resolve(client(original));
            } else {
              reject(new ApiError(401, '登录已失效，请重新登录'));
            }
          });
        });
      }
      isRefreshing = true;
      const refresh = getRefreshToken();
      if (!refresh) {
        isRefreshing = false;
        clearTokens();
        redirectLogin();
        return Promise.reject(new ApiError(401, '登录已失效，请重新登录'));
      }
      try {
        const resp = await axios.post<{ data: { accessToken: string; refreshToken?: string } }>(
          '/api/v1/auth/refresh',
          { refreshToken: refresh },
        );
        const d = resp.data.data;
        setTokens(d.accessToken, d.refreshToken ?? refresh);
        isRefreshing = false;
        pendingQueue.forEach((cb) => cb(d.accessToken));
        pendingQueue = [];
        original.headers!.set('Authorization', `Bearer ${d.accessToken}`);
        return client(original);
      } catch (refreshErr) {
        isRefreshing = false;
        pendingQueue.forEach((cb) => cb(null));
        pendingQueue = [];
        clearTokens();
        redirectLogin();
        return Promise.reject(refreshErr);
      }
    }

    return Promise.reject(new ApiError(status ?? -1, extractError(error)));
  },
);

export default client;
