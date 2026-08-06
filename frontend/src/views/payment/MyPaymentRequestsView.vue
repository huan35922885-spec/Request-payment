<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaymentRequestStatusTag from '../../components/payment/PaymentRequestStatusTag.vue'
import { getPaymentRequests } from '../../api/paymentRequestApi'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import { useAuthStore } from '../../stores/auth'
import { formatCurrency, formatDateTime } from '../../utils/format'
import {
  getApprovalStatusLabel,
  getPaymentStatusLabel,
  type ApprovalStatus,
  type PaymentStatus,
} from '../../types/workflow'
import type { PaymentRequestListItem, PaymentRequestPageResponse } from '../../types/payment'

type ApprovalFilter = ApprovalStatus | 'ALL'
type PaymentFilter = PaymentStatus | 'ALL'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const pageData = ref<PaymentRequestPageResponse | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const approvalFilter = ref<ApprovalFilter>('ALL')
const paymentFilter = ref<PaymentFilter>('ALL')

const approvalOptions: Array<{ value: ApprovalFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'DRAFT', label: getApprovalStatusLabel('DRAFT') },
  { value: 'PENDING_MANAGER', label: getApprovalStatusLabel('PENDING_MANAGER') },
  { value: 'PENDING_CASHIER', label: getApprovalStatusLabel('PENDING_CASHIER') },
  { value: 'APPROVED', label: getApprovalStatusLabel('APPROVED') },
  { value: 'REJECTED_CLOSED', label: getApprovalStatusLabel('REJECTED_CLOSED') },
]

const paymentOptions: Array<{ value: PaymentFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'UNPAID', label: getPaymentStatusLabel('UNPAID') },
  { value: 'PAID', label: getPaymentStatusLabel('PAID') },
]

async function loadMyPaymentRequests(page = currentPage.value): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''

  const params = {
    scope: 'MY_REQUESTS' as const,
    page: page - 1,
    size: pageSize.value,
    ...(approvalFilter.value === 'ALL'
      ? {}
      : { approvalStatus: approvalFilter.value }),
    ...(paymentFilter.value === 'ALL'
      ? {}
      : { paymentStatus: paymentFilter.value }),
  }

  try {
    pageData.value = await getPaymentRequests(params)
    currentPage.value = page
  } catch (error: unknown) {
    pageData.value = null
    if (getApiErrorCode(error) === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
      return
    }
    errorMessage.value = getApiErrorMessage(error)
  } finally {
    isLoading.value = false
  }
}

function handleFilterChange(): void {
  currentPage.value = 1
  void loadMyPaymentRequests(1)
}

function handlePageChange(page: number): void {
  void loadMyPaymentRequests(page)
}

function openDetail(request: PaymentRequestListItem): void {
  void router.push({
    name: 'payment-request-detail',
    params: { id: request.id },
  })
}

function goToCreate(): void {
  void router.push({ name: 'payment-request-create' })
}

onMounted(() => {
  void loadMyPaymentRequests()
})
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">MY PAYMENT REQUESTS</p>
        <h1>我的請款</h1>
        <p>查看目前登入者建立的全部請款案件。</p>
      </div>
      <div class="page-actions">
        <el-button :loading="isLoading" @click="loadMyPaymentRequests()">
          重新整理
        </el-button>
        <el-button type="primary" @click="goToCreate">
          新增請款
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-card v-else shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <strong>請款案件</strong>
          <span class="muted">共 {{ pageData?.totalElements ?? 0 }} 筆</span>
        </div>
      </template>

      <div class="filter-row">
        <el-form-item label="簽核狀態">
          <el-select
            v-model="approvalFilter"
            aria-label="簽核狀態"
            class="filter-select"
            @change="handleFilterChange"
          >
            <el-option
              v-for="option in approvalOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="付款狀態">
          <el-select
            v-model="paymentFilter"
            aria-label="付款狀態"
            class="filter-select"
            @change="handleFilterChange"
          >
            <el-option
              v-for="option in paymentOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </div>

      <el-skeleton v-if="isLoading" :rows="6" animated />

      <el-empty
        v-else-if="(pageData?.content.length ?? 0) === 0"
        description="目前沒有請款案件"
      >
        <el-button type="primary" @click="goToCreate">新增請款</el-button>
      </el-empty>

      <template v-else>
        <el-table
          :data="pageData?.content ?? []"
          stripe
          row-key="id"
          @row-click="openDetail"
        >
          <el-table-column prop="requestNo" label="請款單號" min-width="190" />
          <el-table-column label="支出／代墊" width="110">
            <template #default="scope">
              {{ scope.row.requestCategory === 'EXPENSE' ? '支出' : '代墊' }}
            </template>
          </el-table-column>
          <el-table-column prop="companyName" label="公司" min-width="150" />
          <el-table-column prop="customerName" label="客戶名稱" min-width="150" />
          <el-table-column label="請款金額" min-width="140" align="right">
            <template #default="scope">
              {{ formatCurrency(scope.row.totalAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="簽核狀態" width="140">
            <template #default="scope">
              <PaymentRequestStatusTag :status="scope.row.approvalStatus" kind="approval" />
            </template>
          </el-table-column>
          <el-table-column label="付款狀態" width="110">
            <template #default="scope">
              <PaymentRequestStatusTag :status="scope.row.paymentStatus" kind="payment" />
            </template>
          </el-table-column>
          <el-table-column label="建立時間" min-width="170">
            <template #default="scope">
              {{ formatDateTime(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="送出時間" min-width="170">
            <template #default="scope">
              {{ formatDateTime(scope.row.submittedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click.stop="openDetail(scope.row)">
                查看明細
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="pageData?.totalElements ?? 0"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </el-card>
  </section>
</template>

<style scoped>
.page-container {
  max-width: 1440px;
  margin: 0 auto;
}

.page-title,
.card-header,
.page-actions,
.filter-row,
.pagination-row {
  display: flex;
  align-items: center;
}

.page-title,
.card-header {
  justify-content: space-between;
}

.page-title {
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 24px;
}

.page-actions {
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
.list-card {
  margin-top: 20px;
}

.card-header {
  gap: 16px;
}

.muted {
  color: #909399;
  font-size: 13px;
}

.filter-row {
  gap: 20px;
  margin-bottom: 12px;
}

.filter-row :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-select {
  width: 180px;
}

.pagination-row {
  justify-content: flex-end;
  margin-top: 22px;
}

:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 768px) {
  .page-title,
  .filter-row {
    align-items: stretch;
    flex-direction: column;
  }

  .page-actions {
    justify-content: flex-end;
  }

  .filter-select {
    width: 100%;
  }
}
</style>
