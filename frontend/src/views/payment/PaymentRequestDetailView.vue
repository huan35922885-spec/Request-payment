<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PaymentRequestStatusTag from '../../components/payment/PaymentRequestStatusTag.vue'
import {
  approvePaymentRequestByCashier,
  approvePaymentRequestByManager,
  getPaymentRequestDetail,
  rejectPaymentRequestByCashier,
  rejectPaymentRequestByManager,
  recordPayment,
  submitPaymentDraft,
} from '../../api/paymentRequestApi'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import type {
  PaymentRequestApprovalHistoryDetail,
  PaymentRequestDetail,
} from '../../types/payment'
import {
  getApprovalStatusLabel,
  getPaymentStatusLabel,
  PAYMENT_METHOD_OPTIONS,
  type PaymentMethod,
} from '../../types/workflow'

type ReviewAction = 'approve' | 'reject'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const detail = ref<PaymentRequestDetail | null>(null)
const isLoading = ref(false)
const isSubmitting = ref(false)
const reviewingAction = ref<ReviewAction | null>(null)
const reviewComment = ref('')
const errorMessage = ref('')
const recordingPayment = ref(false)
const paymentForm = ref<{
  paidAt: Date | null
  paymentMethod: PaymentMethod | null
  paymentReference: string
  paymentNote: string
}>({
  paidAt: new Date(),
  paymentMethod: 'BANK_TRANSFER',
  paymentReference: '',
  paymentNote: '',
})

const requestId = computed<number | null>(() => {
  const parsed = Number(route.params.id)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

const isManagerView = computed(() => route.name === 'manager-payment-request-detail')
const isCashierView = computed(() => route.name === 'cashier-payment-request-detail')
const isPaymentView = computed(() => route.name === 'payment-pending-request-detail')

const managerId = computed<number | null>(() => authStore.user?.userId ?? null)

const managerIdentityError = computed(() =>
  managerId.value === null
    ? '目前沒有有效的登入使用者，無法進行主管複核。'
    : '',
)

const cashierAuthorityError = computed(() =>
  !authStore.user?.roles.includes('CASHIER')
    ? '目前登入者沒有出納操作權限。'
    : '',
)

const paymentAuthorityError = computed(() =>
  !authStore.user?.roles.includes('PAYMENT_OPERATOR')
    ? '目前登入者沒有付款登記權限'
    : '',
)

const isDraft = computed(() => detail.value?.approvalStatus === 'DRAFT')

const isSubmitOwner = computed(() =>
  detail.value?.applicant?.id === authStore.user?.userId,
)

const canSubmit = computed(() =>
  !isManagerView.value && isDraft.value && isSubmitOwner.value,
)

const canReview = computed(() =>
  isManagerView.value
  && detail.value?.approvalStatus === 'PENDING_MANAGER'
  && managerId.value !== null
  && detail.value.supervisor?.id === managerId.value,
)

const canCashierReview = computed(() =>
  isCashierView.value
  && detail.value?.approvalStatus === 'PENDING_CASHIER'
  && authStore.user?.roles.includes('CASHIER') === true,
)

const canRecordPayment = computed(() =>
  isPaymentView.value
  && detail.value?.approvalStatus === 'APPROVED'
  && detail.value?.paymentStatus === 'UNPAID'
  && authStore.user?.roles.includes('PAYMENT_OPERATOR') === true,
)

function formatUser(user: PaymentRequestDetail['applicant'] | null): string {
  return user?.displayName ?? '—'
}

function formatDepartment(department: PaymentRequestDetail['department']): string {
  return department === null ? '—' : `${department.code} · ${department.name}`
}

function getRequestCategoryLabel(category: PaymentRequestDetail['requestCategory']): string {
  return category === 'EXPENSE' ? '支出' : '代墊'
}

function getActionLabel(action: PaymentRequestApprovalHistoryDetail['action']): string {
  switch (action) {
    case 'SUBMIT':
      return '送出請款'
    case 'MANAGER_APPROVE':
      return '主管核准'
    case 'MANAGER_REJECT':
      return '主管退回'
    case 'CASHIER_APPROVE':
      return '出納確認'
    case 'CASHIER_REJECT':
      return '出納退回'
    case 'PAYMENT_RECORDED':
      return '登記付款'
  }
}

async function loadDetail(): Promise<void> {
  const id = requestId.value
  if (id === null) {
    detail.value = null
    errorMessage.value = '請款單 ID 無效。'
    return
  }

  isLoading.value = true
  errorMessage.value = ''
  try {
    detail.value = await getPaymentRequestDetail(id)
  } catch (error: unknown) {
    detail.value = null
    errorMessage.value = getApiErrorCode(error) === 'PAYMENT_REQUEST_NOT_FOUND'
      ? '案件不存在或目前登入者沒有查看權限。'
      : getApiErrorMessage(error)
  } finally {
    isLoading.value = false
  }
}

async function submitDraft(): Promise<void> {
  if (detail.value === null || requestId.value === null || isSubmitting.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '送出後將進入主管待辦，確定要送出這張請款單嗎？',
      '送出請款草稿',
      {
        confirmButtonText: '送出審核',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  isSubmitting.value = true
  try {
    await submitPaymentDraft(requestId.value, { version: detail.value.version })
    ElMessage.success('請款草稿已送出審核。')
    await loadDetail()
  } catch (error: unknown) {
    const code = getApiErrorCode(error)
    if (code === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      ElMessage.warning('登入狀態已失效，請重新登入')
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
    } else if (code === 'PAYMENT_REQUEST_SUBMIT_FORBIDDEN') {
      ElMessage.error(getApiErrorMessage(error))
      await loadDetail()
    } else if (code === 'PAYMENT_REQUEST_VERSION_CONFLICT' || code === 'PAYMENT_REQUEST_NOT_DRAFT') {
      ElMessage.warning('請款單狀態已變更，頁面將重新載入。')
      await loadDetail()
    } else {
      ElMessage.error(getApiErrorMessage(error))
    }
  } finally {
    isSubmitting.value = false
  }
}

function normalizedComment(): string | null {
  const normalized = reviewComment.value.trim()
  return normalized === '' ? null : normalized
}

function normalizedText(value: string): string | null {
  const normalized = value.trim()
  return normalized === '' ? null : normalized
}

function toTaipeiOffsetDateTime(value: Date | null): string | null {
  if (value === null || Number.isNaN(value.getTime())) {
    return null
  }

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(value)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}:${values.second}+08:00`
}

async function reviewAsManager(action: ReviewAction): Promise<void> {
  if (!canReview.value || detail.value === null || requestId.value === null) {
    if (managerIdentityError.value) {
      ElMessage.warning(managerIdentityError.value)
    } else {
      ElMessage.warning('目前請款單不是可主管複核的狀態，頁面將重新載入。')
      await loadDetail()
    }
    return
  }

  const isApprove = action === 'approve'
  try {
    await ElMessageBox.confirm(
      isApprove
        ? '核准後請款單將進入出納待辦，確定要核准嗎？'
        : '退回後請款單將關閉且不可重新送審，確定要退回嗎？',
      isApprove ? '主管核准' : '主管退回',
      {
        confirmButtonText: isApprove ? '確認核准' : '確認退回',
        cancelButtonText: '取消',
        type: isApprove ? 'warning' : 'error',
      },
    )
  } catch {
    return
  }

  reviewingAction.value = action
  try {
    const request = {
      version: detail.value.version,
      comment: normalizedComment(),
    }

    if (isApprove) {
      await approvePaymentRequestByManager(requestId.value, request)
      ElMessage.success('請款單已核准，已進入出納待辦。')
    } else {
      await rejectPaymentRequestByManager(requestId.value, request)
      ElMessage.success('請款單已退回並關閉。')
    }

    reviewComment.value = ''
    await loadDetail()
  } catch (error: unknown) {
    const code = getApiErrorCode(error)
    if (code === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      ElMessage.warning('登入狀態已失效，請重新登入。')
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
    } else if (
      code === 'PAYMENT_REQUEST_VERSION_CONFLICT'
      || code === 'PAYMENT_REQUEST_NOT_PENDING_MANAGER'
    ) {
      ElMessage.warning('請款單已被其他人處理，頁面將重新載入。')
      await loadDetail()
    } else if (
      code === 'PAYMENT_REQUEST_MANAGER_FORBIDDEN'
      || code === 'MANAGER_NOT_AUTHORIZED'
      || code === 'SUPERVISOR_SNAPSHOT_MISSING'
    ) {
      ElMessage.error(getApiErrorMessage(error))
      await loadDetail()
    } else {
      ElMessage.error(getApiErrorMessage(error))
    }
  } finally {
    reviewingAction.value = null
  }
}

async function reviewAsCashier(action: ReviewAction): Promise<void> {
  if (!canCashierReview.value || detail.value === null || requestId.value === null) {
    if (cashierAuthorityError.value) {
      ElMessage.warning(cashierAuthorityError.value)
    } else {
      ElMessage.warning('此案件目前不可由出納處理，畫面將重新載入。')
      await loadDetail()
    }
    return
  }

  const isApprove = action === 'approve'
  try {
    await ElMessageBox.confirm(
      isApprove
        ? '確認出納核准此請款單？核准後案件將進入已核准、未付款狀態。'
        : '確認退回此請款單？退回後案件將結案，且不可再次處理。',
      isApprove ? '出納確認' : '出納退回',
      {
        confirmButtonText: isApprove ? '確認核准' : '確認退回',
        cancelButtonText: '取消',
        type: isApprove ? 'warning' : 'error',
      },
    )
  } catch {
    return
  }

  reviewingAction.value = action
  try {
    const request = {
      version: detail.value.version,
      comment: normalizedComment(),
    }

    if (isApprove) {
      await approvePaymentRequestByCashier(requestId.value, request)
      ElMessage.success('出納已確認，請款單已進入已核准狀態。')
    } else {
      await rejectPaymentRequestByCashier(requestId.value, request)
      ElMessage.success('出納已退回，請款單已退回結案。')
    }

    reviewComment.value = ''
    await loadDetail()
  } catch (error: unknown) {
    const code = getApiErrorCode(error)
    if (code === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      ElMessage.warning('登入狀態已失效，請重新登入。')
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
    } else if (code === 'ACCESS_DENIED') {
      ElMessage.error(getApiErrorMessage(error))
      await loadDetail()
    } else if (code === 'INVALID_CSRF_TOKEN') {
      ElMessage.warning('CSRF 驗證已失效，請重新操作。')
    } else if (
      code === 'PAYMENT_REQUEST_VERSION_CONFLICT'
      || code === 'PAYMENT_REQUEST_NOT_PENDING_CASHIER'
    ) {
      ElMessage.warning('案件狀態已變更，畫面將重新載入。')
      await loadDetail()
    } else if (
      code === 'CASHIER_NOT_FOUND'
      || code === 'CASHIER_INACTIVE'
      || code === 'CASHIER_NOT_AUTHORIZED'
    ) {
      ElMessage.error(getApiErrorMessage(error))
      await loadDetail()
    } else {
      ElMessage.error(getApiErrorMessage(error))
    }
  } finally {
    reviewingAction.value = null
  }
}

async function recordPaymentAction(): Promise<void> {
  if (!canRecordPayment.value || detail.value === null || requestId.value === null) {
    if (paymentAuthorityError.value) {
      ElMessage.warning(paymentAuthorityError.value)
    } else {
      ElMessage.warning('此案件目前不可登記付款，畫面將重新載入。')
      await loadDetail()
    }
    return
  }

  const paidAt = toTaipeiOffsetDateTime(paymentForm.value.paidAt)
  if (paidAt === null) {
    ElMessage.warning('請輸入有效的付款時間。')
    return
  }

  try {
    await ElMessageBox.confirm(
      '確認登記此筆付款？案件將由 APPROVED / UNPAID 變更為 APPROVED / PAID。',
      '登記付款確認',
      {
        confirmButtonText: '確認付款',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  recordingPayment.value = true
  try {
    await recordPayment(requestId.value, {
      version: detail.value.version,
      paidAt,
      paymentMethod: paymentForm.value.paymentMethod,
      paymentReference: normalizedText(paymentForm.value.paymentReference),
      paymentNote: normalizedText(paymentForm.value.paymentNote),
    })
    ElMessage.success('付款登記成功。')
    await loadDetail()
  } catch (error: unknown) {
    const code = getApiErrorCode(error)
    if (code === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      ElMessage.warning('登入已失效，請重新登入。')
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
    } else if (code === 'ACCESS_DENIED') {
      ElMessage.error(getApiErrorMessage(error))
      await loadDetail()
    } else if (code === 'INVALID_CSRF_TOKEN') {
      ElMessage.warning('CSRF 驗證已失效，請重新操作。')
    } else if (
      code === 'PAYMENT_REQUEST_VERSION_CONFLICT'
      || code === 'PAYMENT_REQUEST_NOT_APPROVED'
      || code === 'PAYMENT_REQUEST_ALREADY_PAID'
    ) {
      ElMessage.warning('案件狀態已變更，畫面將重新載入。')
      await loadDetail()
    } else if (
      code === 'PAID_BY_NOT_FOUND'
      || code === 'PAID_BY_INACTIVE'
      || code === 'INVALID_PAYMENT_DATE'
    ) {
      ElMessage.error(getApiErrorMessage(error))
    } else {
      ElMessage.error(getApiErrorMessage(error))
    }
  } finally {
    recordingPayment.value = false
  }
}

function goBack(): void {
  router.back()
}

watch(requestId, () => {
  void loadDetail()
}, { immediate: true })
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">PAYMENT REQUEST DETAIL</p>
        <h1>請款單詳情</h1>
        <p>查看請款內容、目前狀態與簽核歷程。</p>
      </div>
      <div class="page-actions">
        <el-button @click="goBack">返回</el-button>
        <el-button :loading="isLoading" @click="loadDetail">重新載入</el-button>
        <el-button
          v-if="canSubmit"
          type="primary"
          :loading="isSubmitting"
          @click="submitDraft"
        >
          送出審核
        </el-button>
      </div>
    </div>

    <el-skeleton v-if="isLoading" :rows="8" animated />

    <el-alert
      v-else-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <template v-else-if="detail !== null">
      <el-alert
        v-if="isDraft && !isSubmitOwner"
        title="只有原申請人可以送出此草稿"
        type="warning"
        show-icon
        :closable="false"
        class="page-alert"
      />

      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-header">
            <strong>{{ detail.requestNo }}</strong>
            <div class="status-group">
              <PaymentRequestStatusTag :status="detail.approvalStatus" kind="approval" />
              <PaymentRequestStatusTag :status="detail.paymentStatus" kind="payment" />
            </div>
          </div>
        </template>

        <el-descriptions :column="3" border>
          <el-descriptions-item label="請款單號">{{ detail.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="請款類別">{{ getRequestCategoryLabel(detail.requestCategory) }}</el-descriptions-item>
          <el-descriptions-item label="簽核狀態">{{ getApprovalStatusLabel(detail.approvalStatus) }}</el-descriptions-item>
          <el-descriptions-item label="付款狀態">{{ getPaymentStatusLabel(detail.paymentStatus) }}</el-descriptions-item>
          <el-descriptions-item label="資料版本">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="申請人">{{ formatUser(detail.applicant) }}</el-descriptions-item>
          <el-descriptions-item label="申請部門">{{ formatDepartment(detail.department) }}</el-descriptions-item>
          <el-descriptions-item label="複核主管快照">{{ formatUser(detail.supervisor) }}</el-descriptions-item>
          <el-descriptions-item label="所屬公司">{{ detail.company.code }} · {{ detail.company.name }}</el-descriptions-item>
          <el-descriptions-item label="客戶">{{ detail.customer.code }} · {{ detail.customer.name }}</el-descriptions-item>
          <el-descriptions-item label="請款總金額">{{ formatCurrency(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="建立時間">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="送出時間">{{ formatDateTime(detail.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="核准時間">{{ formatDateTime(detail.approvedAt) }}</el-descriptions-item>
          <el-descriptions-item label="核准人">{{ formatUser(detail.approvedBy) }}</el-descriptions-item>
          <el-descriptions-item label="退回時間">{{ formatDateTime(detail.rejectedAt) }}</el-descriptions-item>
          <el-descriptions-item label="關閉時間">{{ formatDateTime(detail.closedAt) }}</el-descriptions-item>
          <el-descriptions-item label="實際付款時間">{{ formatDateTime(detail.paidAt) }}</el-descriptions-item>
          <el-descriptions-item label="付款登記人">{{ formatUser(detail.paidBy) }}</el-descriptions-item>
          <el-descriptions-item label="付款方式">{{ detail.paymentMethod ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="付款參考號碼">{{ detail.paymentReference ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="付款備註" :span="3">{{ detail.paymentNote ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="請款事由" :span="3">{{ detail.reason }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card v-if="isManagerView" shadow="never" class="detail-card manager-action-card">
        <template #header>
          <div class="card-header">
            <strong>主管複核</strong>
            <el-tag type="info" effect="plain">複核主管 ID：{{ managerId ?? '未設定' }}</el-tag>
          </div>
        </template>

        <el-alert
          v-if="managerIdentityError"
          :title="managerIdentityError"
          type="warning"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="detail.supervisor?.id !== managerId"
          title="目前登入的開發主管與主管快照不符，無法操作此案件。"
          type="error"
          show-icon
          :closable="false"
        />
        <template v-else-if="detail.approvalStatus === 'PENDING_MANAGER'">
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="請輸入主管意見（可留空）"
          />
          <div class="review-actions">
            <el-button
              type="danger"
              :loading="reviewingAction === 'reject'"
              :disabled="reviewingAction !== null"
              @click="reviewAsManager('reject')"
            >
              退回
            </el-button>
            <el-button
              type="primary"
              :loading="reviewingAction === 'approve'"
              :disabled="reviewingAction !== null"
              @click="reviewAsManager('approve')"
            >
              核准
            </el-button>
          </div>
        </template>
        <el-alert
          v-else
          title="此案件已不在主管待辦中，操作按鈕已隱藏。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-card>

      <el-card v-if="isCashierView" shadow="never" class="detail-card cashier-action-card">
        <template #header>
          <div class="card-header">
            <strong>出納確認</strong>
          <el-tag type="info" effect="plain">
            {{ authStore.user?.roles.includes('CASHIER') ? 'CASHIER' : '無出納權限' }}
          </el-tag>
          </div>
        </template>

        <el-alert
          v-if="cashierAuthorityError"
          :title="cashierAuthorityError"
          type="warning"
          show-icon
          :closable="false"
        />
        <template v-else-if="detail.approvalStatus === 'PENDING_CASHIER'">
          <el-input
            v-model="reviewComment"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="請輸入出納意見（可留空）"
          />
          <div class="review-actions">
            <el-button
              type="danger"
              :loading="reviewingAction === 'reject'"
              :disabled="reviewingAction !== null"
              @click="reviewAsCashier('reject')"
            >
              退回
            </el-button>
            <el-button
              type="primary"
              :loading="reviewingAction === 'approve'"
              :disabled="reviewingAction !== null"
              @click="reviewAsCashier('approve')"
            >
              確認核准
            </el-button>
          </div>
        </template>
        <el-alert
          v-else
          title="此案件目前不可由出納再次處理。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-card>

      <el-card v-if="isPaymentView" shadow="never" class="detail-card payment-action-card">
        <template #header>
          <div class="card-header">
            <strong>登記付款</strong>
            <el-tag type="info" effect="plain">
              {{ authStore.user?.roles.includes('PAYMENT_OPERATOR') ? 'PAYMENT_OPERATOR' : '無付款登記權限' }}
            </el-tag>
          </div>
        </template>

        <el-alert
          v-if="paymentAuthorityError"
          :title="paymentAuthorityError"
          type="warning"
          show-icon
          :closable="false"
        />
        <template v-else-if="detail.approvalStatus === 'APPROVED' && detail.paymentStatus === 'UNPAID'">
          <el-form label-position="top" class="payment-form">
            <el-row :gutter="16">
              <el-col :xs="24" :md="12">
                <el-form-item label="實際付款時間" required>
                  <el-date-picker
                    v-model="paymentForm.paidAt"
                    type="datetime"
                    format="YYYY-MM-DD HH:mm"
                    placeholder="選擇付款時間"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="付款方式">
                  <el-select
                    v-model="paymentForm.paymentMethod"
                    clearable
                    placeholder="選擇付款方式"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="option in PAYMENT_METHOD_OPTIONS"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="付款參考號碼">
              <el-input
                v-model="paymentForm.paymentReference"
                maxlength="100"
                show-word-limit
                placeholder="例如：匯款帳號末五碼或交易序號"
              />
            </el-form-item>
            <el-form-item label="付款備註">
              <el-input
                v-model="paymentForm.paymentNote"
                type="textarea"
                :rows="4"
                placeholder="可輸入付款備註"
              />
            </el-form-item>
            <div class="review-actions">
              <el-button
                type="primary"
                :loading="recordingPayment"
                :disabled="recordingPayment"
                @click="recordPaymentAction"
              >
                確認付款
              </el-button>
            </div>
          </el-form>
        </template>
        <el-alert
          v-else
          title="此案件目前不可再次登記付款。"
          type="info"
          show-icon
          :closable="false"
        />
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-header">
            <strong>請款明細</strong>
            <span>{{ detail.items.length }} 筆</span>
          </div>
        </template>

        <el-table :data="detail.items" empty-text="沒有明細">
          <el-table-column label="費用類型" min-width="180">
            <template #default="scope">
              {{ scope.row.expenseTypeCode }} · {{ scope.row.expenseTypeName }}
            </template>
          </el-table-column>
          <el-table-column prop="calculationType" label="計算類型" width="150" />
          <el-table-column label="單價快照" min-width="170">
            <template #default="scope">
              <span>{{ scope.row.priceCode ?? '人工輸入' }}</span>
              <small>{{ scope.row.unitPrice === null ? '—' : formatCurrency(scope.row.unitPrice) }}</small>
            </template>
          </el-table-column>
          <el-table-column label="計算資料" min-width="220">
            <template #default="scope">
              <span v-if="scope.row.calculationType === 'MEAL'">
                人數 {{ scope.row.peopleCount ?? '—' }} × 天數 {{ scope.row.days ?? '—' }} × 餐數 {{ scope.row.quantity ?? '—' }} × 單價快照
              </span>
              <span v-else-if="scope.row.calculationType === 'MANUAL' || scope.row.calculationType === 'TRAVEL'">
                人工輸入金額
              </span>
              <span v-else>
                數量 {{ scope.row.quantity ?? '—' }} × 單價快照 × 倍數 {{ scope.row.multiplier ?? '—' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="明細說明" min-width="180" />
          <el-table-column label="明細金額" width="130" align="right">
            <template #default="scope">
              {{ formatCurrency(scope.row.amount) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="card-header">
            <strong>簽核歷程</strong>
            <span>{{ detail.approvalHistories.length }} 筆</span>
          </div>
        </template>

        <el-empty
          v-if="detail.approvalHistories.length === 0"
          description="目前沒有簽核歷程"
        />
        <el-timeline v-else>
          <el-timeline-item
            v-for="history in detail.approvalHistories"
            :key="history.id"
            :timestamp="'操作時間：' + formatDateTime(history.actedAt)"
            placement="top"
          >
            <div class="history-title">
              <strong>{{ getActionLabel(history.action) }}</strong>
              <span>操作人：{{ history.actor.displayName }}</span>
            </div>
            <p>
              {{ getApprovalStatusLabel(history.fromApprovalStatus) }} →
              {{ getApprovalStatusLabel(history.toApprovalStatus) }} ·
              {{ getPaymentStatusLabel(history.fromPaymentStatus) }} →
              {{ getPaymentStatusLabel(history.toPaymentStatus) }}
            </p>
            <p v-if="history.comment" class="history-comment">{{ history.comment }}</p>
          </el-timeline-item>
        </el-timeline>
      </el-card>
    </template>
  </section>
</template>

<style scoped>
.page-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-title,
.page-actions,
.status-group,
.card-header,
.history-title,
.review-actions {
  display: flex;
  align-items: center;
}

.page-title {
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.page-actions,
.status-group,
.review-actions {
  gap: 10px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

h1,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 8px;
  color: #1f2937;
  font-size: 30px;
}

.page-title p:last-child {
  margin-bottom: 0;
  color: #6b7280;
}

.page-alert,
.detail-card {
  margin-top: 20px;
}

.detail-card {
  border-radius: 12px;
}

.card-header {
  justify-content: space-between;
  gap: 16px;
}

.card-header > span {
  color: #6b7280;
}

.review-actions {
  justify-content: flex-end;
  margin-top: 16px;
}

.history-title {
  justify-content: space-between;
  max-width: 520px;
}

.history-title span,
.history-comment {
  color: #6b7280;
}

.history-title + p {
  margin: 8px 0 0;
  color: #606266;
}

.history-comment {
  margin: 6px 0 0;
}

small {
  display: block;
  color: #909399;
}

@media (max-width: 768px) {
  .page-title {
    flex-direction: column;
  }

  .page-actions {
    flex-wrap: wrap;
  }
}
</style>
