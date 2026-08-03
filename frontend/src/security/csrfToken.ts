import type { CsrfTokenResponse } from '../types/csrf'

let csrfToken: CsrfTokenResponse | null = null
let csrfLoadPromise: Promise<CsrfTokenResponse> | null = null
let csrfTokenLoader: (() => Promise<CsrfTokenResponse>) | null = null

export function setCsrfToken(value: CsrfTokenResponse): void {
  csrfToken = value
}

export function getCsrfToken(): CsrfTokenResponse | null {
  return csrfToken
}

export function clearCsrfToken(): void {
  csrfToken = null
}

export function configureCsrfTokenLoader(
  loader: () => Promise<CsrfTokenResponse>,
): void {
  csrfTokenLoader = loader
}

export function ensureCsrfToken(): Promise<CsrfTokenResponse> {
  if (csrfToken) {
    return Promise.resolve(csrfToken)
  }

  if (csrfLoadPromise) {
    return csrfLoadPromise
  }

  if (!csrfTokenLoader) {
    return Promise.reject(new Error('CSRF token loader is not configured'))
  }

  csrfLoadPromise = csrfTokenLoader()
    .then((value) => {
      setCsrfToken(value)
      return value
    })
    .finally(() => {
      csrfLoadPromise = null
    })

  return csrfLoadPromise
}
