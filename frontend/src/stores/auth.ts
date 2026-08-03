import { defineStore } from 'pinia'
import * as authApi from '../api/authApi'
import '../api/csrfApi'
import { getApiErrorCode, getApiErrorMessage } from '../utils/apiError'
import { clearCsrfToken, ensureCsrfToken } from '../security/csrfToken'
import type { AuthenticatedUser } from '../types/auth'

interface AuthState {
  user: AuthenticatedUser | null
  initialized: boolean
  loading: boolean
  loggingIn: boolean
  loggingOut: boolean
  errorMessage: string | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    initialized: false,
    loading: false,
    loggingIn: false,
    loggingOut: false,
    errorMessage: null,
  }),

  actions: {
    async login(username: string, password: string): Promise<AuthenticatedUser> {
      this.loggingIn = true
      this.errorMessage = null
      try {
        await ensureCsrfToken()
        const user = await authApi.login({ username, password })
        this.user = user
        this.initialized = true
        return user
      } finally {
        this.loggingIn = false
      }
    },

    async fetchCurrentUser(): Promise<AuthenticatedUser | null> {
      this.loading = true
      this.errorMessage = null
      try {
        await ensureCsrfToken()
        const user = await authApi.getCurrentUser()
        this.user = user
        return user
      } catch (error: unknown) {
        if (getApiErrorCode(error) === 'UNAUTHENTICATED') {
          this.user = null
          return null
        }

        this.user = null
        this.errorMessage = getApiErrorMessage(error)
        throw error
      } finally {
        this.initialized = true
        this.loading = false
      }
    },

    async logout(): Promise<void> {
      this.loggingOut = true
      try {
        await ensureCsrfToken()
        await authApi.logout()
      } finally {
        clearCsrfToken()
        try {
          await ensureCsrfToken()
        } catch {
          // A successful logout should still return the UI to the login page.
        }
        this.user = null
        this.initialized = true
        this.errorMessage = null
        this.loggingOut = false
      }
    },

    clearAuthentication(): void {
      this.user = null
      this.initialized = true
      this.errorMessage = null
    },
  },
})
