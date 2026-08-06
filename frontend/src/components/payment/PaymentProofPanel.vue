<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deletePaymentProof,
  downloadPaymentRequestAttachment,
  uploadPaymentProofs,
} from '../../api/paymentRequestApi'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import type { PaymentRequestAttachment, PaymentRequestDetail } from '../../types/payment'

const props = defineProps<{
  detail: PaymentRequestDetail
  canMaintain: boolean
}>()

const emit = defineEmits<{
  refreshed: []
}>()

const uploading = ref(false)
const deletingId = ref<number | null>(null)

const proofs = computed(() =>
  props.detail.attachments.filter((item) => item.attachmentType === 'PAYMENT_PROOF'),
)

async function downloadProof(attachment: PaymentRequestAttachment): Promise<void> {
  try {
    const response = await downloadPaymentRequestAttachment(
      props.detail.id,
      attachment.id,
    )
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = attachment.originalFilename
    link.click()
    URL.revokeObjectURL(url)
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  }
}

async function onUploadChange(uploadFile: { raw?: File }): Promise<void> {
  const file = uploadFile.raw
  if (file === undefined) {
    return
  }
  uploading.value = true
  try {
    await uploadPaymentProofs(props.detail.id, [file])
    ElMessage.success('付款證明已上傳。')
    emit('refreshed')
  } catch (error: unknown) {
    const code = getApiErrorCode(error)
    if (code === 'PAYMENT_PROOF_REQUIRED') {
      ElMessage.warning('請選擇有效檔案。')
    } else {
      ElMessage.error(getApiErrorMessage(error))
    }
  } finally {
    uploading.value = false
  }
}

async function removeProof(attachment: PaymentRequestAttachment): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `確定刪除「${attachment.originalFilename}」？`,
      '刪除付款證明',
      { type: 'warning', confirmButtonText: '刪除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  deletingId.value = attachment.id
  try {
    await deletePaymentProof(props.detail.id, attachment.id)
    ElMessage.success('付款證明已刪除。')
    emit('refreshed')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <div class="proof-panel">
    <div class="proof-header">
      <strong>付款證明</strong>
      <span>{{ proofs.length }} 筆</span>
    </div>

    <el-table :data="proofs" empty-text="尚無付款證明" size="small">
      <el-table-column prop="originalFilename" label="檔名" min-width="200" />
      <el-table-column label="大小" width="100">
        <template #default="scope">
          {{ Math.round(scope.row.fileSize / 1024) }} KB
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="downloadProof(scope.row)">
            下載
          </el-button>
          <el-button
            v-if="canMaintain"
            link
            type="danger"
            :loading="deletingId === scope.row.id"
            @click="removeProof(scope.row)"
          >
            刪除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-upload
      v-if="canMaintain"
      class="proof-upload"
      :auto-upload="false"
      :show-file-list="false"
      :disabled="uploading"
      accept=".pdf,.jpg,.jpeg,.png"
      @change="onUploadChange"
    >
      <el-button type="primary" plain :loading="uploading">
        上傳付款證明
      </el-button>
    </el-upload>
  </div>
</template>

<style scoped>
.proof-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.proof-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.proof-upload {
  margin-top: 4px;
}
</style>
