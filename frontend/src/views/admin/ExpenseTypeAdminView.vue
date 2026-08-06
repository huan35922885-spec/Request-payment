<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  activateExpensePriceSettingAdmin,
  createExpensePriceSettingAdmin,
  createExpenseTypeAdmin,
  deactivateExpensePriceSettingAdmin,
  listExpensePriceSettingsAdmin,
  listExpenseTypesAdmin,
  type ExpensePriceSettingAdminRecord,
  type ExpenseTypeAdminRecord,
} from '../../api/masterAdminApi'
import { getApiErrorMessage } from '../../utils/apiError'
import type { CalculationType } from '../../types/master'

const loading = ref(false)
const creating = ref(false)
const loadingPrices = ref(false)
const creatingPrice = ref(false)
const records = ref<ExpenseTypeAdminRecord[]>([])
const selectedTypeId = ref<number | null>(null)
const prices = ref<ExpensePriceSettingAdminRecord[]>([])
const form = ref({
  code: '',
  name: '',
  calculationType: 'MANUAL' as CalculationType,
})
const priceForm = ref({
  priceCode: 'DEFAULT',
  priceName: '',
  amount: 80,
  effectiveFrom: new Date(),
})

const calculationOptions: Array<{ value: CalculationType; label: string }> = [
  { value: 'MANUAL', label: '人工輸入' },
  { value: 'MEAL', label: '餐費' },
  { value: 'TRAVEL', label: '交通' },
  { value: 'QUANTITY_PRICE', label: '數量單價' },
  { value: 'CONFIRMATION', label: '函證' },
]

function formatDate(value: Date | null): string | null {
  if (value === null) {
    return null
  }
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

async function load(): Promise<void> {
  loading.value = true
  try {
    records.value = await listExpenseTypesAdmin()
    if (selectedTypeId.value === null && records.value.length > 0) {
      selectedTypeId.value = records.value[0].id
    }
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function loadPrices(): Promise<void> {
  if (selectedTypeId.value === null) {
    prices.value = []
    return
  }
  loadingPrices.value = true
  try {
    prices.value = await listExpensePriceSettingsAdmin(selectedTypeId.value)
  } catch (error: unknown) {
    prices.value = []
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    loadingPrices.value = false
  }
}

async function createType(): Promise<void> {
  creating.value = true
  try {
    const created = await createExpenseTypeAdmin({
      code: form.value.code.trim().toUpperCase(),
      name: form.value.name.trim(),
      calculationType: form.value.calculationType,
    })
    ElMessage.success('費用類型已建立。')
    form.value.code = ''
    form.value.name = ''
    await load()
    selectedTypeId.value = created.id
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    creating.value = false
  }
}

async function createPrice(): Promise<void> {
  if (selectedTypeId.value === null) {
    ElMessage.warning('請先選擇費用類型。')
    return
  }
  const effectiveFrom = formatDate(priceForm.value.effectiveFrom)
  if (effectiveFrom === null) {
    ElMessage.warning('請選擇生效日。')
    return
  }
  creatingPrice.value = true
  try {
    await createExpensePriceSettingAdmin(selectedTypeId.value, {
      priceCode: priceForm.value.priceCode.trim().toUpperCase(),
      priceName: priceForm.value.priceName.trim(),
      amount: priceForm.value.amount,
      effectiveFrom,
    })
    ElMessage.success('價格設定已建立。')
    priceForm.value.priceName = ''
    await loadPrices()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    creatingPrice.value = false
  }
}

async function togglePriceActive(row: ExpensePriceSettingAdminRecord): Promise<void> {
  try {
    if (row.active) {
      const { value } = await ElMessageBox.prompt('請輸入停用原因', '停用價格設定', {
        confirmButtonText: '停用',
        cancelButtonText: '取消',
        inputPattern: /.+/,
        inputErrorMessage: '原因不可空白',
      })
      await deactivateExpensePriceSettingAdmin(row.id, row.version, value)
      ElMessage.success('已停用。')
    } else {
      await activateExpensePriceSettingAdmin(row.id, row.version)
      ElMessage.success('已啟用。')
    }
    await loadPrices()
  } catch (error: unknown) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(getApiErrorMessage(error))
  }
}

function selectType(row: ExpenseTypeAdminRecord | undefined): void {
  selectedTypeId.value = row?.id ?? null
}

watch(selectedTypeId, () => {
  void loadPrices()
})

onMounted(async () => {
  await load()
  await loadPrices()
})
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">主檔管理</p>
        <h1>費用類型與單價維護</h1>
        <p>MASTER_DATA_ADMIN：維護費用名稱主檔與固定單價（餐費／郵資等）。</p>
      </div>
      <el-button :loading="loading" @click="load">重新載入</el-button>
    </div>

    <el-card shadow="never" class="card">
      <template #header>
        <strong>新增費用類型</strong>
      </template>
      <el-form label-position="top" inline>
        <el-form-item label="代碼">
          <el-input v-model="form.code" placeholder="例如 MEAL_FEE" maxlength="50" />
        </el-form-item>
        <el-form-item label="名稱">
          <el-input v-model="form.name" placeholder="費用名稱" maxlength="100" />
        </el-form-item>
        <el-form-item label="計算類型">
          <el-select v-model="form.calculationType" style="width: 180px">
            <el-option
              v-for="option in calculationOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label=" ">
          <el-button type="primary" :loading="creating" @click="createType">
            建立
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="loading" shadow="never" class="card">
      <template #header>
        <strong>費用類型列表</strong>
      </template>
      <el-table
        :data="records"
        empty-text="尚無資料"
        highlight-current-row
        @current-change="selectType"
      >
        <el-table-column prop="code" label="代碼" width="140" />
        <el-table-column prop="name" label="名稱" min-width="160" />
        <el-table-column prop="calculationType" label="計算類型" width="140" />
        <el-table-column label="狀態" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.active ? 'success' : 'info'">
              {{ scope.row.active ? '啟用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
      </el-table>
    </el-card>

    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-header">
          <strong>單價設定</strong>
          <el-select
            v-model="selectedTypeId"
            placeholder="選擇費用類型"
            filterable
            style="width: 280px"
          >
            <el-option
              v-for="item in records"
              :key="item.id"
              :label="`${item.code}｜${item.name}`"
              :value="item.id"
            />
          </el-select>
        </div>
      </template>

      <el-form label-position="top" inline class="price-form">
        <el-form-item label="價格代碼">
          <el-input v-model="priceForm.priceCode" maxlength="50" />
        </el-form-item>
        <el-form-item label="價格名稱">
          <el-input v-model="priceForm.priceName" maxlength="100" placeholder="例如 預設餐費" />
        </el-form-item>
        <el-form-item label="單價">
          <el-input-number v-model="priceForm.amount" :min="0.01" :precision="2" />
        </el-form-item>
        <el-form-item label="生效日">
          <el-date-picker v-model="priceForm.effectiveFrom" type="date" />
        </el-form-item>
        <el-form-item label=" ">
          <el-button
            type="primary"
            :loading="creatingPrice"
            :disabled="selectedTypeId === null"
            @click="createPrice"
          >
            新增單價
          </el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loadingPrices"
        :data="prices"
        empty-text="此費用尚無價格設定"
      >
        <el-table-column prop="priceCode" label="代碼" width="120" />
        <el-table-column prop="priceName" label="名稱" min-width="140" />
        <el-table-column label="單價" width="110" align="right">
          <template #default="scope">
            {{ Number(scope.row.amount).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="effectiveFrom" label="生效日" width="120" />
        <el-table-column label="失效日" width="120">
          <template #default="scope">
            {{ scope.row.effectiveTo ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column label="狀態" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.active ? 'success' : 'info'">
              {{ scope.row.active ? '啟用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目前有效" width="100">
          <template #default="scope">
            {{ scope.row.effective ? '是' : '否' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="togglePriceActive(scope.row)">
              {{ scope.row.active ? '停用' : '啟用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<style scoped>
.page-container {
  max-width: 1200px;
  margin: 0 auto;
}

.page-title,
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.page-title {
  margin-bottom: 20px;
}

.card-header {
  align-items: center;
  width: 100%;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.card {
  margin-bottom: 20px;
  border-radius: 12px;
}

.price-form {
  margin-bottom: 16px;
}
</style>
