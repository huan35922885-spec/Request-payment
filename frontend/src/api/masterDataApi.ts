import { http } from './http'
import type {
  CompanyOption,
  CustomerOption,
  ExpensePriceOption,
  ExpenseTypeOption,
} from '../types/master'

export async function getCompanies(): Promise<CompanyOption[]> {
  const response = await http.get<CompanyOption[]>('/master/companies')
  return response.data
}

export async function getCustomers(): Promise<CustomerOption[]> {
  const response = await http.get<CustomerOption[]>('/master/customers')
  return response.data
}

export async function getExpenseTypes(): Promise<ExpenseTypeOption[]> {
  const response = await http.get<ExpenseTypeOption[]>('/master/expense-types')
  return response.data
}

export async function getExpensePrices(
  expenseTypeId: number,
): Promise<ExpensePriceOption[]> {
  const response = await http.get<ExpensePriceOption[]>(
    `/master/expense-types/${expenseTypeId}/prices`,
  )
  return response.data
}
