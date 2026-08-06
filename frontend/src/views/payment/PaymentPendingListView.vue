<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PaymentRequestStatusTag from '../../components/payment/PaymentRequestStatusTag.vue'
import { downloadPaymentResultExport, getPaymentRequests } from '../../api/paymentRequestApi'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import { useAuthStore } from '../../stores/auth'
import { formatCurrency, formatDateTime } from '../../utils/format'
import type { PaymentRequestListItem, PaymentRequestPageResponse } from '../../types/payment'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const pageData = ref<PaymentRequestPageResponse | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const hasCashierAuthority = computed(() =>
  authStore.user?.roles.includes('CASHIER') === true,
)

const exportFrom = ref<Date | null>(null)
const exportTo = ref<Date | null>(null)
const exporting = ref(false)

function formatExportDate(date: Date | null): string | null {
  if (date === null) {
    return null
  }
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

async function exportExcel(): Promise<void> {
  const paidFrom = formatExportDate(exportFrom.value)
  const paidTo = formatExportDate(exportTo.value)
  if (paidFrom === null || paidTo === null) {
    ElMessage.warning('請選擇匯出期間。')
    return
  }
  exporting.value = true
  try {
    const blob = await downloadPaymentResultExport({ paidFrom, paidTo })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `payment-result-${paidFrom}-${paidTo}.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    exporting.value = false
  }
}

async function loadPaymentRequests(page = currentPage.value): Promise<void> {
  if (!hasCashierAuthority.value) {
    pageData.value = null
    errorMessage.value = ''
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    pageData.value = await getPaymentRequests({
      page: page - 1,
      size: pageSize.value,
      scope: 'PAYMENT_PENDING',
    })
    currentPage.value = page
  } catch (error: unknown) {
    pageData.value = null
    if (getApiErrorCode(error) === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
    } else {
      errorMessage.value = getApiErrorMessage(error)
    }
  } finally {
    isLoading.value = false
  }
}

function openDetail(request: PaymentRequestListItem): void {
  void router.push({
    name: 'payment-pending-request-detail',
    params: { id: request.id },
  })
}

function handlePageChange(page: number): void {
  void loadPaymentRequests(page)
}

function reloadPage(): void {
  window.location.reload()
}

onMounted(() => {
  void loadPaymentRequests()
})
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">出納付款登記</p>
        <h1>待付款列表</h1>
        <p>查看已核准且尚未付款的請款案件，完成付款登記。</p>
      </div>
      <el-button @click="reloadPage">重新整理</el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-alert
      v-if="!errorMessage && !hasCashierAuthority"
      title="目前登入者沒有付款待辦查看權限"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-card v-if="hasCashierAuthority && !errorMessage" shadow="never" class="list-card export-card">
      <template #header>
        <strong>結果檔案匯出（Excel）</strong>
      </template>
      <el-form inline label-position="top">
        <el-form-item label="付款日起">
          <el-date-picker v-model="exportFrom" type="date" />
        </el-form-item>
        <el-form-item label="付款日迄">
          <el-date-picker v-model="exportTo" type="date" />
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :loading="exporting" @click="exportExcel">
            匯出 Excel
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="hasCashierAuthority && !errorMessage" shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>待登記付款案件</strong>
            <span class="muted">共 {{ pageData?.totalElements ?? 0 }} 筆</span>
          </div>
          <div class="status-group">
            <PaymentRequestStatusTag status="APPROVED" kind="approval" />
            <PaymentRequestStatusTag status="UNPAID" kind="payment" />
          </div>
        </div>
      </template>

      <el-skeleton v-if="isLoading" :rows="6" animated />

      <el-empty
        v-else-if="(pageData?.content.length ?? 0) === 0"
        description="目前沒有待付款案件"
      />

      <template v-else>
        <el-table
          :data="pageData?.content ?? []"
          stripe
          row-key="id"
          @row-click="openDetail"
        >
          <el-table-column prop="requestNo" label="請款單號" min-width="190" />
          <el-table-column prop="applicantName" label="申請人" min-width="120" />
          <el-table-column prop="departmentName" label="申請部門" min-width="140" />
          <el-table-column prop="companyName" label="所屬公司" min-width="140" />
          <el-table-column prop="customerName" label="客戶" min-width="140" />
          <el-table-column label="請款類別" width="110">
            <template #default="scope">
              {{ scope.row.requestCategory === 'EXPENSE' ? '支出' : '代墊' }}
            </template>
          </el-table-column>
          <el-table-column label="請款總金額" width="130" align="right">
            <template #default="scope">
              {{ formatCurrency(scope.row.totalAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="核准時間" min-width="170">
            <template #default="scope">
              {{ formatDateTime(scope.row.approvedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="狀態" width="150">
            <template #default="scope">
              <PaymentRequestStatusTag :status="scope.row.approvalStatus" kind="approval" />
              <PaymentRequestStatusTag :status="scope.row.paymentStatus" kind="payment" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button type="primary" link @click.stop="openDetail(scope.row)">
                查看
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
.status-group,
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

.list-card {
  border-radius: 12px;
}

.card-header {
  gap: 16px;
}

.status-group {
  gap: 6px;
}

.muted {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}

.pagination-row {
  justify-content: flex-end;
  margin-top: 22px;
}

:deep(.el-table__row) {
  cursor: pointer;
}

@media (max-width: 768px) {
  .page-title {
    flex-direction: column;
  }
}
</style>
