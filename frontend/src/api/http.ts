import axios from 'axios'
import { clearCsrfToken, ensureCsrfToken } from '../security/csrfToken'
import { getApiErrorCode, getApiErrorMessage } from '../utils/apiError'

export const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
})

const unsafeMethods = new Set(['post', 'put', 'patch', 'delete'])

http.interceptors.request.use(async (config) => {
  const method = (config.method ?? 'get').toLowerCase()
  if (!unsafeMethods.has(method)) {
    return config
  }

  const token = await ensureCsrfToken()
  config.headers.set(token.headerName, token.token)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (
      axios.isAxiosError(error)
      && error.response?.status === 403
      && getApiErrorCode(error) === 'INVALID_CSRF_TOKEN'
    ) {
      clearCsrfToken()
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('csrf:invalid'))
      }
    }

    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const requestUrl = error.config?.url ?? ''
      const isAuthenticationRequest =
        requestUrl.endsWith('/auth/login')
        || requestUrl.endsWith('/auth/me')
        || requestUrl.endsWith('/auth/logout')

      if (!isAuthenticationRequest && typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('auth:unauthorized'))
      }
    }

    return Promise.reject(error)
  },
)

export { getApiErrorMessage }
