import { http } from './http'
import type { CalculationType } from '../types/master'

export interface ExpenseTypeAdminRecord {
  id: number
  code: string
  name: string
  calculationType: CalculationType
  active: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface CreateExpenseTypeAdminRequest {
  code: string
  name: string
  calculationType: CalculationType
}

export interface ExpensePriceSettingAdminRecord {
  id: number
  expenseTypeId: number
  expenseTypeCode: string
  expenseTypeName: string
  priceCode: string
  priceName: string
  amount: number
  effectiveFrom: string
  effectiveTo: string | null
  active: boolean
  version: number
  effective: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateExpensePriceSettingAdminRequest {
  priceCode: string
  priceName: string
  amount: number
  effectiveFrom: string
}

export async function listExpenseTypesAdmin(): Promise<ExpenseTypeAdminRecord[]> {
  const response = await http.get<ExpenseTypeAdminRecord[]>('/admin/master/expense-types')
  return response.data
}

export async function createExpenseTypeAdmin(
  request: CreateExpenseTypeAdminRequest,
): Promise<ExpenseTypeAdminRecord> {
  const response = await http.post<ExpenseTypeAdminRecord>(
    '/admin/master/expense-types',
    request,
  )
  return response.data
}

export async function listExpensePriceSettingsAdmin(
  expenseTypeId: number,
): Promise<ExpensePriceSettingAdminRecord[]> {
  const response = await http.get<ExpensePriceSettingAdminRecord[]>(
    `/admin/master/expense-types/${expenseTypeId}/price-settings`,
  )
  return response.data
}

export async function createExpensePriceSettingAdmin(
  expenseTypeId: number,
  request: CreateExpensePriceSettingAdminRequest,
): Promise<ExpensePriceSettingAdminRecord> {
  const response = await http.post<ExpensePriceSettingAdminRecord>(
    `/admin/master/expense-types/${expenseTypeId}/price-settings`,
    request,
  )
  return response.data
}

export async function deactivateExpensePriceSettingAdmin(
  id: number,
  version: number,
  reason: string,
): Promise<ExpensePriceSettingAdminRecord> {
  const response = await http.post<ExpensePriceSettingAdminRecord>(
    `/admin/master/expense-price-settings/${id}/deactivate`,
    { version, reason },
  )
  return response.data
}

export async function activateExpensePriceSettingAdmin(
  id: number,
  version: number,
): Promise<ExpensePriceSettingAdminRecord> {
  const response = await http.post<ExpensePriceSettingAdminRecord>(
    `/admin/master/expense-price-settings/${id}/activate`,
    { version },
  )
  return response.data
}
