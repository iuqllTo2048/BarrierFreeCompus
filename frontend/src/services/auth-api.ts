import { http } from './http';
import type { ApiResponse, AuthSession, CurrentUser, LoginPayload } from '../types/auth';

export async function login(payload: LoginPayload): Promise<AuthSession> {
  const response = await http.post<ApiResponse<AuthSession>>('/auth/login', payload);
  return response.data.data;
}

export async function refresh(): Promise<AuthSession> {
  const response = await http.post<ApiResponse<AuthSession>>('/auth/refresh');
  return response.data.data;
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout');
}

export async function currentUser(): Promise<CurrentUser> {
  const response = await http.get<ApiResponse<CurrentUser>>('/auth/me');
  return response.data.data;
}
