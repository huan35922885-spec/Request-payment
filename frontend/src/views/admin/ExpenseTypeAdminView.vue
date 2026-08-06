<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createExpenseTypeAdmin,
  listExpenseTypesAdmin,
  type ExpenseTypeAdminRecord,
} from '../../api/masterAdminApi'
import { getApiErrorMessage } from '../../utils/apiError'
import type { CalculationType } from '../../types/master'

const loading = ref(false)
const creating = ref(false)
const records = ref<ExpenseTypeAdminRecord[]>([])
const form = ref({
  code: '',
  name: '',
  calculationType: 'MANUAL' as CalculationType,
})

const calculationOptions: Array<{ value: CalculationType; label: string }> = [
  { value: 'MANUAL', label: '人工輸入' },
  { value: 'MEAL', label: '餐費' },
  { value: 'TRAVEL', label: '交通' },
  { value: 'QUANTITY_PRICE', label: '數量單價' },
  { value: 'CONFIRMATION', label: '函證' },
]

async function load(): Promise<void> {
  loading.value = true
  try {
    records.value = await listExpenseTypesAdmin()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function createType(): Promise<void> {
  creating.value = true
  try {
    await createExpenseTypeAdmin({
      code: form.value.code.trim().toUpperCase(),
      name: form.value.name.trim(),
      calculationType: form.value.calculationType,
    })
    ElMessage.success('費用類型已建立。')
    form.value.code = ''
    form.value.name = ''
    await load()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section class="page-container">
    <div class="page-title">
      <div>
        <p class="eyebrow">主檔管理</p>
        <h1>費用類型維護</h1>
        <p>供 MASTER_DATA_ADMIN 檢視與新增費用類型（進階價格設定請使用後台 API）。</p>
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
      <el-table :data="records" empty-text="尚無資料">
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
  </section>
</template>

<style scoped>
.page-container {
  max-width: 1100px;
  margin: 0 auto;
}

.page-title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
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
</style>
