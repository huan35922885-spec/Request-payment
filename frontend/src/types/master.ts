export type RequestCategory = 'EXPENSE' | 'ADVANCE'

export type CalculationType =
  | 'MANUAL'
  | 'MEAL'
  | 'QUANTITY_PRICE'
  | 'TRAVEL'
  | 'CONFIRMATION'

export interface CompanyOption {
  id: number
  code: string
  name: string
}

export interface CustomerOption {
  id: number
  code: string
  name: string
  defaultRequestCategory: RequestCategory | null
}

export interface ExpenseTypeOption {
  id: number
  code: string
  name: string
  calculationType: CalculationType
}

export interface ExpensePriceOption {
  id: number
  priceCode: string
  priceName: string
  unitPrice: number
  effectiveFrom: string
  effectiveTo: string | null
}
