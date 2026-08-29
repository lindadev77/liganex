import client from './client';

export interface UserResponse {
  id: number;
  email: string;
  displayName: string;
  status: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export async function register(
  email: string,
  password: string,
  displayName: string,
): Promise<UserResponse> {
  return (await client.post<UserResponse>('/v1/auth/register', {
    email,
    password,
    displayName,
  })) as unknown as UserResponse;
}

export async function login(email: string, password: string): Promise<TokenResponse> {
  return (await client.post<TokenResponse>('/v1/auth/login', {
    email,
    password,
  })) as unknown as TokenResponse;
}
