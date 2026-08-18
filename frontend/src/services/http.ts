import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { clearSession, getSession, setSession } from './session';
import type { ApiResponse, AuthSession } from '../types/auth';

interface RetryableRequest extends InternalAxiosRequestConfig {
  _authRetried?: boolean;
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
});

const refreshClient = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
});

let refreshPromise: Promise<AuthSession> | null = null;

async function refreshSession(): Promise<AuthSession> {
  if (!refreshPromise) {
    refreshPromise = refreshClient
      .post<ApiResponse<AuthSession>>('/auth/refresh')
      .then((response) => {
        setSession(response.data.data);
        return response.data.data;
      })
      .catch((error: unknown) => {
        clearSession();
        window.dispatchEvent(new Event('auth-expired'));
        throw error;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

http.interceptors.request.use((config) => {
  const accessToken = getSession()?.accessToken;
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const request = error.config as RetryableRequest | undefined;
    const isAuthRequest = request?.url?.startsWith('/auth/') ?? false;
    if (error.response?.status !== 401 || !request || request._authRetried || isAuthRequest) {
      throw error;
    }
    request._authRetried = true;
    const session = await refreshSession();
    request.headers.Authorization = `Bearer ${session.accessToken}`;
    return http(request);
  },
);

export function readApiMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiResponse<null>>(error)) {
    return error.response?.data?.message ?? fallback;
  }
  return fallback;
}
