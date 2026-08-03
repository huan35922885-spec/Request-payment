import { http } from './http'
import type { AuthenticatedUser, LoginRequest } from '../types/auth'

export async function login(request: LoginRequest): Promise<AuthenticatedUser> {
  const response = await http.post<AuthenticatedUser>('/auth/login', request)
  return response.data
}

export async function getCurrentUser(): Promise<AuthenticatedUser> {
  const response = await http.get<AuthenticatedUser>('/auth/me')
  return response.data
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout')
}
