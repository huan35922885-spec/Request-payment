import axios from 'axios'
import type { ApiErrorResponse } from '../types/common'

export function getApiErrorCode(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    return error.response?.data?.code ?? null
  }

  return null
}

export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const data = error.response?.data
    const fieldMessages = data?.fieldErrors
      ?.map((fieldError) => `${fieldError.field}: ${fieldError.message}`)
      .join('；')

    if (fieldMessages) {
      const message = data?.message ?? '資料驗證失敗。'
      return `${message}（${fieldMessages}）`
    }

    return data?.message ?? 'API 請求失敗，請稍後再試。'
  }

  if (error instanceof Error) {
    return error.message
  }

  return '系統發生未知錯誤，請稍後再試。'
}
