<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useRouter } from 'vue-router'
import { createPaymentDraft } from '../../api/paymentRequestApi'
import PaymentRequestItemEditor from '../../components/payment/PaymentRequestItemEditor.vue'
import { useAuthStore } from '../../stores/auth'
import { useMasterDataStore } from '../../stores/masterData'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import type { CalculationType, RequestCategory } from '../../types/master'
import type {
  CreatePaymentDraftItemRequest,
  CreatePaymentDraftRequest,
  CreatePaymentDraftResponse,
  PaymentDraftItemForm,
} from '../../types/payment'
import { getApprovalStatusLabel, getPaymentStatusLabel } from '../../types/workflow'

interface PaymentDraftFormModel {
  companyId: number | null
  customerId: number | null
  requestCategory: RequestCategory | null
  reason: string
  items: PaymentDraftItemForm[]
}

const categoryOptions: Array<{ value: RequestCategory; label: string }> = [
  { value: 'EXPENSE', label: '支出' },
  { value: 'ADVANCE', label: '代墊' },
]

const masterDataStore = useMasterDataStore()
const authStore = useAuthStore()
const router = useRouter()
const formRef = ref<FormInstance>()
const isSubmitting = ref(false)
const createdDraft = ref<CreatePaymentDraftResponse | null>(null)
let clientIdSequence = 0

function createEmptyItem(): PaymentDraftItemForm {
  clientIdSequence += 1
  return {
    clientId: 'payment-item-' + Date.now() + '-' + clientIdSequence,
    expenseTypeId: null,
    priceCode: null,
    description: '',
    peopleCount: null,
    days: null,
    quantity: null,
    multiplier: null,
    manualAmount: null,
    travelFrom: '',
    travelTo: '',
    confirmationNature: '',
  }
}

const form = reactive<PaymentDraftFormModel>({
  companyId: null,
  customerId: null,
  requestCategory: null,
  reason: '',
  items: [createEmptyItem()],
})

const rules: FormRules = {
  companyId: [
    { required: true, message: '所屬公司為必填欄位', trigger: 'change' },
  ],
  customerId: [
    { required: true, message: '客戶為必填欄位', trigger: 'change' },
  ],
  requestCategory: [
    { required: true, message: '請款類別為必填欄位', trigger: 'change' },
  ],
  reason: [
    { required: true, message: '請款事由為必填欄位', trigger: 'blur' },
    { max: 2000, message: '請款事由不可超過 2000 個字元', trigger: 'blur' },
  ],
}

const isLoadingMasterData = computed(() =>
  masterDataStore.loadingCompanies
  || masterDataStore.loadingCustomers
  || masterDataStore.loadingExpenseTypes,
)

const isSubmitDisabled = computed(() =>
  isSubmitting.value || isLoadingMasterData.value,
)

function addItem(): void {
  form.items.push(createEmptyItem())
}

function removeItem(clientId: string): void {
  if (form.items.length === 1) {
    ElMessage.warning('至少需要一筆明細。')
    return
  }
  const index = form.items.findIndex((item) => item.clientId === clientId)
  if (index >= 0) {
    form.items.splice(index, 1)
  }
}

function isPositiveNumber(value: number | null): boolean {
  return value !== null && Number.isFinite(value) && value > 0
}

function isPositiveInteger(value: number | null): boolean {
  return isPositiveNumber(value) && Number.isInteger(value)
}

function itemError(index: number, message: string): false {
  ElMessage.error('明細 ' + (index + 1) + '：' + message)
  return false
}

function validateItems(): boolean {
  if (form.items.length === 0) {
    ElMessage.error('至少需要一筆明細。')
    return false
  }

  for (const [index, item] of form.items.entries()) {
    if (item.expenseTypeId === null) {
      return itemError(index, '費用類型為必填欄位')
    }
    const expenseType = masterDataStore.expenseTypes.find(
      (option) => option.id === item.expenseTypeId,
    )
    if (expenseType === undefined) {
      return itemError(index, '費用類型無效')
    }

    const calculationType: CalculationType = expenseType.calculationType
    if (calculationType === 'MANUAL' || calculationType === 'TRAVEL') {
      if (!isPositiveNumber(item.manualAmount)) {
        return itemError(index, '人工輸入金額必須大於 0')
      }
      continue
    }
    if (item.priceCode === null || item.priceCode.trim() === '') {
      return itemError(index, '價格設定為必填欄位')
    }
    if (calculationType === 'MEAL') {
      if (!isPositiveInteger(item.peopleCount)) {
        return itemError(index, '人數必須為正整數')
      }
      if (!isPositiveInteger(item.days)) {
        return itemError(index, '天數必須為正整數')
      }
      if (!isPositiveNumber(item.quantity)) {
        return itemError(index, '餐數必須大於 0')
      }
    }
    if (calculationType === 'QUANTITY_PRICE' || calculationType === 'CONFIRMATION') {
      if (!isPositiveNumber(item.quantity)) {
        return itemError(index, '數量必須大於 0')
      }
      if (!isPositiveNumber(item.multiplier)) {
        return itemError(index, '倍數必須大於 0')
      }
    }
  }
  return true
}

function cleanDescription(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function buildExtraData(
  item: PaymentDraftItemForm,
  calculationType: CalculationType,
): Record<string, unknown> | null {
  if (calculationType === 'TRAVEL') {
    const from = item.travelFrom.trim()
    const to = item.travelTo.trim()
    if (from === '' && to === '') {
      return null
    }
    return { travelFrom: from || null, travelTo: to || null }
  }
  if (calculationType === 'CONFIRMATION') {
    const nature = item.confirmationNature.trim()
    return nature === '' ? null : { confirmationNature: nature }
  }
  return null
}

function buildItemRequest(
  item: PaymentDraftItemForm,
  sortOrder: number,
): CreatePaymentDraftItemRequest {
  if (item.expenseTypeId === null) {
    throw new Error('費用類型為必填欄位')
  }
  const expenseType = masterDataStore.expenseTypes.find(
    (option) => option.id === item.expenseTypeId,
  )
  if (expenseType === undefined) {
    throw new Error('費用類型無效')
  }

  const base = {
    expenseTypeId: item.expenseTypeId,
    description: cleanDescription(item.description),
    extraData: buildExtraData(item, expenseType.calculationType),
    sortOrder,
  }

  if (expenseType.calculationType === 'MANUAL'
    || expenseType.calculationType === 'TRAVEL') {
    return {
      ...base,
      priceCode: null,
      peopleCount: null,
      days: null,
      quantity: null,
      multiplier: null,
      manualAmount: item.manualAmount,
    }
  }
  if (expenseType.calculationType === 'MEAL') {
    return {
      ...base,
      priceCode: item.priceCode,
      peopleCount: item.peopleCount,
      days: item.days,
      quantity: item.quantity,
      multiplier: null,
      manualAmount: null,
    }
  }
  return {
    ...base,
    priceCode: item.priceCode,
    peopleCount: null,
    days: null,
    quantity: item.quantity,
    multiplier: item.multiplier,
    manualAmount: null,
  }
}

function buildCreateDraftRequest(): CreatePaymentDraftRequest {
  if (form.companyId === null || form.customerId === null || form.requestCategory === null) {
    throw new Error('所屬公司、客戶與請款類別為必填欄位')
  }
  return {
    companyId: form.companyId,
    customerId: form.customerId,
    requestCategory: form.requestCategory,
    reason: form.reason.trim(),
    items: form.items.map((item, index) => buildItemRequest(item, index + 1)),
  }
}

async function loadMasterData(): Promise<void> {
  try {
    await Promise.all([
      masterDataStore.loadCompanies(),
      masterDataStore.loadCustomers(),
      masterDataStore.loadExpenseTypes(),
    ])
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  }
}

async function submit(): Promise<void> {
  if (isSubmitting.value) {
    return
  }
  let formValid = true
  if (formRef.value !== undefined) {
    try {
      await formRef.value.validate()
    } catch {
      formValid = false
    }
  }
  if (!formValid || !validateItems()) {
    return
  }

  isSubmitting.value = true
  try {
    createdDraft.value = await createPaymentDraft(buildCreateDraftRequest())
    ElMessage.success('草稿建立成功。')
    resetForm()
  } catch (error: unknown) {
    if (getApiErrorCode(error) === 'UNAUTHENTICATED') {
      const redirect = router.currentRoute.value.fullPath
      authStore.clearAuthentication()
      await router.replace({ name: 'login', query: { redirect } })
      return
    }
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    isSubmitting.value = false
  }
}

function resetForm(): void {
  form.companyId = null
  form.customerId = null
  form.requestCategory = null
  form.reason = ''
  form.items.splice(0, form.items.length, createEmptyItem())
  formRef.value?.clearValidate()
}

function viewCreatedDraft(): void {
  if (createdDraft.value !== null) {
    void router.push({
      name: 'payment-request-detail',
      params: { id: createdDraft.value.id },
    })
  }
}

watch(
  () => form.customerId,
  (customerId) => {
    const customer = masterDataStore.customers.find((option) => option.id === customerId)
    if (customer?.defaultRequestCategory !== null
      && customer?.defaultRequestCategory !== undefined) {
      form.requestCategory = customer.defaultRequestCategory
    }
  },
)

onMounted(() => {
  void loadMasterData()
})
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">請款單</p>
        <h1>新增請款草稿</h1>
        <p>填寫資料後先儲存為草稿，之後可再送出審核。</p>
      </div>
      <el-tag type="info">草稿</el-tag>
    </div>

    <el-alert
      v-if="authStore.user"
      :title="'申請人：' + authStore.user.displayName + '（' + authStore.user.username + '）'"
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    />
    <el-alert
      v-if="masterDataStore.errorMessage"
      :title="masterDataStore.errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-card shadow="never" class="form-card">
      <template #header>
        <div class="card-header">
          <span>草稿資料</span>
          <el-tag type="success" effect="plain">申請人取自登入工作階段</el-tag>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        v-loading="isLoadingMasterData"
      >
        <el-row :gutter="24">
          <el-col :xs="24" :md="8">
            <el-form-item label="所屬公司" prop="companyId">
              <el-select
                v-model="form.companyId"
                placeholder="請選擇所屬公司"
                filterable
                clearable
                class="full-width"
              >
                <el-option
                  v-for="company in masterDataStore.companies"
                  :key="company.id"
                  :label="company.code + ' - ' + company.name"
                  :value="company.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="客戶" prop="customerId">
              <el-select
                v-model="form.customerId"
                placeholder="請選擇客戶"
                filterable
                clearable
                class="full-width"
              >
                <el-option
                  v-for="customer in masterDataStore.customers"
                  :key="customer.id"
                  :label="customer.code + ' - ' + customer.name"
                  :value="customer.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="請款類別" prop="requestCategory">
              <el-select
                v-model="form.requestCategory"
                placeholder="請選擇請款類別"
                class="full-width"
              >
                <el-option
                  v-for="option in categoryOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="請款事由" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            placeholder="請輸入請款事由"
          />
        </el-form-item>

        <div class="items-heading">
          <div>
            <h2>請款明細</h2>
            <p>新增一筆或多筆費用明細。</p>
          </div>
          <el-button type="primary" plain @click="addItem">新增明細</el-button>
        </div>

        <PaymentRequestItemEditor
          v-for="(item, index) in form.items"
          :key="item.clientId"
          :item="item"
          :index="index"
          @remove="removeItem"
        />

        <div class="submit-row">
          <el-button
            type="primary"
            :loading="isSubmitting"
            :disabled="isSubmitDisabled"
            @click="submit"
          >
            儲存草稿
          </el-button>
        </div>
      </el-form>
    </el-card>

    <el-card v-if="createdDraft !== null" shadow="never" class="success-card">
      <template #header>
        <div class="card-header">
          <span>草稿已建立</span>
          <el-tag type="success">HTTP 201</el-tag>
        </div>
      </template>
      <el-descriptions :column="4" border>
        <el-descriptions-item label="請款單號">{{ createdDraft.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="簽核狀態">{{ getApprovalStatusLabel(createdDraft.approvalStatus) }}</el-descriptions-item>
        <el-descriptions-item label="付款狀態">{{ getPaymentStatusLabel(createdDraft.paymentStatus) }}</el-descriptions-item>
        <el-descriptions-item label="請款總金額">{{ createdDraft.totalAmount.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="資料版本">{{ createdDraft.version }}</el-descriptions-item>
        <el-descriptions-item label="明細筆數">{{ createdDraft.items.length }}</el-descriptions-item>
      </el-descriptions>
      <div class="success-actions">
        <el-button type="primary" @click="viewCreatedDraft">查看草稿</el-button>
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.page-container { max-width: 1120px; margin: 0 auto; }
.page-title { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 24px; }
.eyebrow { margin: 0 0 8px; color: #2563eb; font-size: 12px; font-weight: 700; letter-spacing: .14em; }
h1, h2, p { margin-top: 0; }
h1 { margin-bottom: 8px; color: #1f2937; font-size: 30px; }
.page-title p:last-child { margin-bottom: 0; color: #6b7280; }
.page-alert, .success-card { margin-top: 20px; }
.form-card { border-radius: 12px; }
.card-header, .items-heading { display: flex; align-items: center; justify-content: space-between; }
.items-heading { margin-top: 28px; }
.items-heading h2 { margin-bottom: 6px; color: #1f2937; font-size: 20px; }
.items-heading p { margin-bottom: 0; color: #6b7280; }
.full-width { width: 100%; }
.submit-row, .success-actions { display: flex; justify-content: flex-end; margin-top: 28px; }
.success-actions { margin-top: 20px; }
</style>
