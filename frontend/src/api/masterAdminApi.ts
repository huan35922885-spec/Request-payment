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
