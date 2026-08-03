import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  getCompanies,
  getCustomers,
  getExpensePrices,
  getExpenseTypes,
} from '../api/masterDataApi'
import { getApiErrorMessage } from '../api/http'
import type {
  CompanyOption,
  CustomerOption,
  ExpensePriceOption,
  ExpenseTypeOption,
} from '../types/master'

export const useMasterDataStore = defineStore('masterData', () => {
  const companies = ref<CompanyOption[]>([])
  const customers = ref<CustomerOption[]>([])
  const expenseTypes = ref<ExpenseTypeOption[]>([])
  const pricesByExpenseType = ref<Record<number, ExpensePriceOption[]>>({})

  const loadingCompanies = ref(false)
  const loadingCustomers = ref(false)
  const loadingExpenseTypes = ref(false)
  const loadingPrices = ref<Record<number, boolean>>({})
  const errorMessage = ref('')

  const hasLoadedMasterData = computed(
    () => companies.value.length > 0
      || customers.value.length > 0
      || expenseTypes.value.length > 0,
  )

  async function loadCompanies(): Promise<void> {
    loadingCompanies.value = true
    errorMessage.value = ''
    try {
      companies.value = await getCompanies()
    } catch (error: unknown) {
      errorMessage.value = getApiErrorMessage(error)
      throw error
    } finally {
      loadingCompanies.value = false
    }
  }

  async function loadCustomers(): Promise<void> {
    loadingCustomers.value = true
    errorMessage.value = ''
    try {
      customers.value = await getCustomers()
    } catch (error: unknown) {
      errorMessage.value = getApiErrorMessage(error)
      throw error
    } finally {
      loadingCustomers.value = false
    }
  }

  async function loadExpenseTypes(): Promise<void> {
    loadingExpenseTypes.value = true
    errorMessage.value = ''
    try {
      expenseTypes.value = await getExpenseTypes()
    } catch (error: unknown) {
      errorMessage.value = getApiErrorMessage(error)
      throw error
    } finally {
      loadingExpenseTypes.value = false
    }
  }

  async function loadExpensePrices(expenseTypeId: number): Promise<void> {
    if (pricesByExpenseType.value[expenseTypeId]) {
      return
    }

    loadingPrices.value[expenseTypeId] = true
    errorMessage.value = ''
    try {
      pricesByExpenseType.value[expenseTypeId] =
        await getExpensePrices(expenseTypeId)
    } catch (error: unknown) {
      errorMessage.value = getApiErrorMessage(error)
      throw error
    } finally {
      loadingPrices.value[expenseTypeId] = false
    }
  }

  function pricesForExpenseType(expenseTypeId: number | null): ExpensePriceOption[] {
    return expenseTypeId === null
      ? []
      : pricesByExpenseType.value[expenseTypeId] ?? []
  }

  function isLoadingPrices(expenseTypeId: number | null): boolean {
    return expenseTypeId !== null
      && loadingPrices.value[expenseTypeId] === true
  }

  return {
    companies,
    customers,
    expenseTypes,
    errorMessage,
    hasLoadedMasterData,
    loadingCompanies,
    loadingCustomers,
    loadingExpenseTypes,
    loadCompanies,
    loadCustomers,
    loadExpensePrices,
    loadExpenseTypes,
    isLoadingPrices,
    pricesForExpenseType,
  }
})
