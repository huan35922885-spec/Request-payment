<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaymentRequestStatusTag from '../../components/payment/PaymentRequestStatusTag.vue'
import { getPaymentRequests } from '../../api/paymentRequestApi'
import { useAuthStore } from '../../stores/auth'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import { formatCurrency, formatDateTime } from '../../utils/format'
import type {
  PaymentRequestListItem,
  PaymentRequestPageResponse,
} from '../../types/payment'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const pageData = ref<PaymentRequestPageResponse | null>(null)
const isLoading = ref(false)
const errorMessage = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const pendingRequests = computed<PaymentRequestListItem[]>(() =>
  pageData.value?.content ?? [],
)

async function loadPendingRequests(page = currentPage.value): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''

  try {
    pageData.value = await getPaymentRequests({
      scope: 'MANAGER_PENDING',
      page: page - 1,
      size: pageSize.value,
    })
    currentPage.value = page
  } catch (error: unknown) {
    if (getApiErrorCode(error) === 'UNAUTHENTICATED') {
      authStore.clearAuthentication()
      await router.push({
        name: 'login',
        query: { redirect: route.fullPath },
      })
      return
    }

    pageData.value = null
    errorMessage.value = getApiErrorMessage(error)
  } finally {
    isLoading.value = false
  }
}

function openDetail(request: PaymentRequestListItem): void {
  void router.push({
    name: 'manager-payment-request-detail',
    params: { id: request.id },
  })
}

function handlePageChange(page: number): void {
  void loadPendingRequests(page)
}

function reloadPage(): void {
  void loadPendingRequests()
}

onMounted(() => {
  void loadPendingRequests()
})
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">MANAGER WORK QUEUE</p>
        <h1>主管待辦</h1>
        <p>查看目前需要您複核的請款案件。</p>
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

    <el-card v-else shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <div>
            <strong>待主管複核案件</strong>
            <span class="muted">由後端依主管快照與登入身分判定</span>
          </div>
          <el-tag type="warning" effect="plain">待主管複核</el-tag>
        </div>
      </template>

      <el-skeleton v-if="isLoading" :rows="6" animated />

      <el-empty
        v-else-if="pendingRequests.length === 0"
        description="目前沒有待主管複核案件"
      />

      <template v-else>
        <el-table
          :data="pendingRequests"
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
              {{ scope.row.requestCategory === 'EXPENSE' ? '支出' : '差旅' }}
            </template>
          </el-table-column>
          <el-table-column label="請款總金額" width="130" align="right">
            <template #default="scope">
              {{ formatCurrency(scope.row.totalAmount) }}
            </template>
          </el-table-column>
          <el-table-column label="送出時間" min-width="170">
            <template #default="scope">
              {{ formatDateTime(scope.row.submittedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="狀態" width="150">
            <template #default="scope">
              <PaymentRequestStatusTag
                :status="scope.row.approvalStatus"
                kind="approval"
              />
              <PaymentRequestStatusTag
                :status="scope.row.paymentStatus"
                kind="payment"
              />
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

:deep(.el-tag + .el-tag) {
  margin-left: 6px;
}

@media (max-width: 768px) {
  .page-title {
    flex-direction: column;
  }
}
</style>
