export type UserRole = 'USER' | 'ADMIN';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface AuthSession {
  username: string;
  role: UserRole;
  accessToken: string;
}

export interface CurrentUser {
  username: string;
  role: UserRole;
}
