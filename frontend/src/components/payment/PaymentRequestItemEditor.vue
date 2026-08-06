<script setup lang="ts">
import { computed, watch } from 'vue'
import { useMasterDataStore } from '../../stores/masterData'
import type { CalculationType } from '../../types/master'
import type { PaymentDraftItemForm } from '../../types/payment'

const props = defineProps<{
  item: PaymentDraftItemForm
  index: number
}>()

const emit = defineEmits<{
  remove: [clientId: string]
}>()

const masterDataStore = useMasterDataStore()
const item = props.item

const expenseNatureOptions = [
  { value: '應收', label: '應收' },
  { value: '應付', label: '應付' },
]

const confirmationNatureOptions = [
  { value: '平信', label: '平信' },
  { value: '掛號', label: '掛號' },
  { value: '限掛', label: '限掛' },
  { value: '其他', label: '其他' },
]

const stampSizeOptions = [
  { value: '大', label: '大' },
  { value: '中', label: '中' },
  { value: '小', label: '小' },
]

const expenseType = computed(() =>
  masterDataStore.expenseTypes.find((option) => option.id === item.expenseTypeId),
)

const calculationType = computed<CalculationType | null>(() =>
  expenseType.value?.calculationType ?? null,
)

const prices = computed(() =>
  masterDataStore.pricesForExpenseType(item.expenseTypeId),
)

const priceLoading = computed(() =>
  masterDataStore.isLoadingPrices(item.expenseTypeId),
)

const requiresPrice = computed(() =>
  calculationType.value === 'MEAL'
  || calculationType.value === 'QUANTITY_PRICE'
  || calculationType.value === 'CONFIRMATION',
)

const isManualLike = computed(() =>
  calculationType.value === 'MANUAL' || calculationType.value === 'TRAVEL',
)

const showStampSize = computed(() => {
  const name = `${expenseType.value?.code ?? ''} ${expenseType.value?.name ?? ''}`
  return name.includes('印章') || name.toUpperCase().includes('STAMP')
})

function resetCalculationFields(): void {
  item.priceCode = null
  item.peopleCount = null
  item.days = null
  item.quantity = null
  item.multiplier = null
  item.manualAmount = null
  item.travelFrom = ''
  item.travelTo = ''
  item.expenseNature = ''
  item.confirmationNature = ''
  item.stampSize = ''
}

async function syncExpenseType(expenseTypeId: number | null): Promise<void> {
  resetCalculationFields()

  if (expenseTypeId === null || expenseType.value === undefined) {
    return
  }

  if (!requiresPrice.value) {
    return
  }

  if (calculationType.value === 'QUANTITY_PRICE'
    || calculationType.value === 'CONFIRMATION') {
    item.multiplier = calculationType.value === 'CONFIRMATION' ? 2 : 1
  }

  await masterDataStore.loadExpensePrices(expenseTypeId)
  if (item.expenseTypeId !== expenseTypeId) {
    return
  }

  const loadedPrices = masterDataStore.pricesForExpenseType(expenseTypeId)
  const defaultPrice = loadedPrices.find((price) => price.priceCode === 'DEFAULT')
  item.priceCode = defaultPrice?.priceCode ?? loadedPrices[0]?.priceCode ?? null
}

watch(
  () => item.expenseTypeId,
  (expenseTypeId) => {
    void syncExpenseType(expenseTypeId)
  },
)
</script>

<template>
  <el-card shadow="never" class="item-card">
    <template #header>
      <div class="item-header">
        <strong>明細 {{ index + 1 }}</strong>
        <el-button
          type="danger"
          link
          @click="emit('remove', item.clientId)"
        >
          刪除明細
        </el-button>
      </div>
    </template>

    <el-row :gutter="20">
      <el-col :xs="24" :md="10">
        <el-form-item label="費用名稱" required>
          <el-select
            v-model="item.expenseTypeId"
            placeholder="請選擇費用名稱"
            filterable
            clearable
            class="full-width"
          >
            <el-option
              v-for="option in masterDataStore.expenseTypes"
              :key="option.id"
              :label="`${option.code}｜${option.name}`"
              :value="option.id"
            >
              <span>{{ option.code }}｜{{ option.name }}</span>
              <small>{{ option.calculationType }}</small>
            </el-option>
          </el-select>
        </el-form-item>
      </el-col>

      <el-col :xs="24" :md="14">
        <el-form-item label="明細說明">
          <el-input
            v-model="item.description"
            type="textarea"
            :rows="2"
            maxlength="2000"
            show-word-limit
            placeholder="請輸入明細說明（可選）"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row v-if="requiresPrice" :gutter="20">
      <el-col :xs="24" :md="10">
        <el-form-item label="單價（價格設定）" required>
          <el-select
            v-model="item.priceCode"
            placeholder="請選擇單價"
            :loading="priceLoading"
            :disabled="priceLoading || prices.length === 0"
            class="full-width"
          >
            <el-option
              v-for="price in prices"
              :key="price.id"
              :label="`${price.priceName}（${price.unitPrice.toFixed(2)}）`"
              :value="price.priceCode"
            >
              <span>{{ price.priceCode }} - {{ price.priceName }}</span>
              <small>{{ price.unitPrice.toFixed(2) }}</small>
            </el-option>
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-alert
      v-if="requiresPrice && !priceLoading && prices.length === 0"
      title="此費用名稱目前沒有有效單價設定。"
      type="warning"
      :closable="false"
      show-icon
      class="item-alert"
    />

    <el-row v-if="calculationType === 'TRAVEL'" :gutter="20">
      <el-col :xs="24" :md="10">
        <el-form-item label="起點">
          <el-input v-model="item.travelFrom" maxlength="200" placeholder="交通起點" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-form-item label="終點">
          <el-input v-model="item.travelTo" maxlength="200" placeholder="交通終點" />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row v-if="calculationType === 'CONFIRMATION'" :gutter="20">
      <el-col :xs="24" :md="8">
        <el-form-item label="費用性質">
          <el-select
            v-model="item.expenseNature"
            clearable
            placeholder="應收／應付"
            class="full-width"
          >
            <el-option
              v-for="option in expenseNatureOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="函證性質">
          <el-select
            v-model="item.confirmationNature"
            clearable
            placeholder="平信／掛號／限掛"
            class="full-width"
          >
            <el-option
              v-for="option in confirmationNatureOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row v-if="showStampSize" :gutter="20">
      <el-col :xs="24" :md="8">
        <el-form-item label="印章大小">
          <el-select
            v-model="item.stampSize"
            clearable
            placeholder="大／中／小"
            class="full-width"
          >
            <el-option
              v-for="option in stampSizeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row v-if="calculationType === 'MANUAL' || calculationType === 'TRAVEL'" :gutter="20">
      <el-col :xs="24" :md="10">
        <el-form-item label="請款金額" required>
          <el-input-number
            v-model="item.manualAmount"
            :min="0.01"
            :precision="2"
            :step="0.01"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="14">
        <el-text v-if="isManualLike" type="info" class="item-hint">
          {{ calculationType === 'TRAVEL' ? '交通費目前以人工輸入請款金額填寫。' : '此費用以人工輸入請款金額。' }}
        </el-text>
      </el-col>
    </el-row>

    <el-row v-if="calculationType === 'MEAL'" :gutter="20">
      <el-col :xs="24" :md="8">
        <el-form-item label="人數" required>
          <el-input-number
            v-model="item.peopleCount"
            :min="1"
            :precision="0"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="天數" required>
          <el-input-number
            v-model="item.days"
            :min="1"
            :precision="0"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-form-item label="餐數" required>
          <el-input-number
            v-model="item.quantity"
            :min="0.01"
            :precision="2"
            :step="0.01"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row v-if="calculationType === 'QUANTITY_PRICE' || calculationType === 'CONFIRMATION'" :gutter="20">
      <el-col :xs="24" :md="12">
        <el-form-item label="數量" required>
          <el-input-number
            v-model="item.quantity"
            :min="0.01"
            :precision="2"
            :step="0.01"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-form-item
          :label="calculationType === 'CONFIRMATION' ? '來回倍數' : '倍數'"
          required
        >
          <el-input-number
            v-model="item.multiplier"
            :min="0.01"
            :precision="2"
            :step="0.01"
            controls-position="right"
            class="full-width"
          />
        </el-form-item>
      </el-col>
    </el-row>
  </el-card>
</template>

<style scoped>
.item-card {
  margin-top: 18px;
  border-radius: 12px;
}

.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.full-width {
  width: 100%;
}

.item-card small {
  float: right;
  margin-left: 18px;
  color: #909399;
}

.item-alert {
  margin-bottom: 18px;
}

.item-hint {
  display: inline-block;
  margin-top: 34px;
}
</style>
