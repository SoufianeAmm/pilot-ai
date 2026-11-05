import { apiGet, apiPost, type TokenResponse } from '../../api/client'

export interface User {
  id: string
  email: string
  displayName: string
}

export interface RegisterInput {
  email: string
  password: string
  displayName: string
}

export const PASSWORD_MIN_LENGTH = 12
export const PASSWORD_MAX_LENGTH = 128
export const DISPLAY_NAME_MAX_LENGTH = 80

export function loginRequest(email: string, password: string): Promise<TokenResponse> {
  return apiPost<TokenResponse>('/auth/login', { email, password })
}

export function registerRequest(input: RegisterInput): Promise<User> {
  return apiPost<User>('/auth/register', input)
}

export function fetchMe(): Promise<User> {
  return apiGet<User>('/me')
}
