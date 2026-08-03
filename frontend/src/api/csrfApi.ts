import { http } from './http'
import type { CsrfTokenResponse } from '../types/csrf'
import { configureCsrfTokenLoader, setCsrfToken } from '../security/csrfToken'

export async function getCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await http.get<CsrfTokenResponse>('/auth/csrf')
  setCsrfToken(response.data)
  return response.data
}

configureCsrfTokenLoader(getCsrfToken)
