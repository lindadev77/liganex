import client from './client';

export interface AppResponse {
  appId: string;
  name: string;
  status: string;
  createdAt: string;
}

export interface AppSecretResponse {
  appId: string;
  appSecret: string;
}

export interface PermissionDTO {
  code: string;
  name: string;
  description: string;
}

export async function createApp(name: string): Promise<AppSecretResponse> {
  return (await client.post<AppSecretResponse>('/v1/open/apps', { name })) as unknown as AppSecretResponse;
}

export async function listMyApps(): Promise<AppResponse[]> {
  return (await client.get<AppResponse[]>('/v1/open/apps')) as unknown as AppResponse[];
}

export async function getApp(appId: string): Promise<AppResponse> {
  return (await client.get<AppResponse>(`/v1/open/apps/${appId}`)) as unknown as AppResponse;
}

export async function bindPermissions(
  appId: string,
  permissionCodes: string[],
): Promise<void> {
  return (await client.post<void>(`/v1/open/apps/${appId}/permissions`, {
    permissionCodes,
  })) as unknown as void;
}

export async function getAppPermissions(appId: string): Promise<string[]> {
  return (await client.get<string[]>(`/v1/open/apps/${appId}/permissions`)) as unknown as string[];
}

export async function listPermissions(): Promise<PermissionDTO[]> {
  return (await client.get<PermissionDTO[]>('/v1/open/permissions')) as unknown as PermissionDTO[];
}
