<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElMessage,
  ElMessageBox,
  type UploadFile,
  type UploadInstance,
  type UploadRawFile,
} from 'element-plus'
import {
  deletePaymentRequestAttachment,
  downloadPaymentRequestAttachment,
  uploadPaymentRequestAttachment,
} from '../../api/paymentRequestApi'
import { getApiErrorCode, getApiErrorMessage } from '../../utils/apiError'
import { getDownloadFilename } from '../../utils/contentDisposition'
import { formatDateTime, formatFileSize } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import type {
  PaymentRequestAttachment,
  PaymentRequestAttachmentType,
} from '../../types/payment'

const props = defineProps<{
  paymentRequestId: number
  attachments: PaymentRequestAttachment[]
  canManageAttachments: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const uploadRef = ref<UploadInstance>()
const selectedAttachmentType = ref<GeneralAttachmentType | null>(null)
const selectedFile = ref<File | null>(null)
const isUploading = ref(false)
const downloadingAttachmentId = ref<number | null>(null)
const deletingAttachmentId = ref<number | null>(null)

type GeneralAttachmentType = Exclude<
  PaymentRequestAttachmentType,
  'PAYMENT_PROOF'
>

const attachmentTypeLabels: Record<PaymentRequestAttachmentType, string> = {
  INVOICE: '發票',
  RECEIPT: '收據',
  REQUEST_PROOF: '請款證明',
  PAYMENT_PROOF: '付款證明',
  OTHER: '其他附件',
}

const uploadAttachmentTypes: Array<{
  value: GeneralAttachmentType
  label: string
}> = [
  { value: 'INVOICE', label: attachmentTypeLabels.INVOICE },
  { value: 'RECEIPT', label: attachmentTypeLabels.RECEIPT },
  { value: 'REQUEST_PROOF', label: attachmentTypeLabels.REQUEST_PROOF },
  { value: 'OTHER', label: attachmentTypeLabels.OTHER },
]

const selectedFileLabel = computed(() => selectedFile.value?.name ?? '尚未選擇檔案')

function attachmentTypeLabel(type: PaymentRequestAttachmentType): string {
  return attachmentTypeLabels[type] ?? type
}

function validateFile(file: File): string | null {
  if (file.size <= 0) {
    return '檔案不可為空。'
  }
  if (file.size > 10 * 1024 * 1024) {
    return '檔案不可超過 10 MB。'
  }

  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['pdf', 'jpg', 'jpeg', 'png'].includes(extension)) {
    return '只允許 PDF、JPG、JPEG、PNG 檔案。'
  }

  if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) {
    return '檔案格式與內容類型不符。'
  }
  return null
}

function clearSelectedFile(): void {
  selectedFile.value = null
}

function selectFile(file: File): void {
  const validationMessage = validateFile(file)
  if (validationMessage) {
    clearSelectedFile()
    uploadRef.value?.clearFiles()
    ElMessage.error(validationMessage)
    return
  }
  selectedFile.value = file
}

function handleFileChange(file: UploadFile): void {
  if (file.raw) {
    selectFile(file.raw)
  }
}

function handleFileExceed(files: File[]): void {
  uploadRef.value?.clearFiles()
  const file = files[0]
  if (file) {
    uploadRef.value?.handleStart(file as UploadRawFile)
  }
}

async function handleAuthenticationError(): Promise<void> {
  authStore.clearAuthentication()
  ElMessage.warning('登入狀態已失效，請重新登入。')
  await router.push({
    name: 'login',
    query: { redirect: route.fullPath },
  })
}

async function handleAttachmentError(error: unknown): Promise<void> {
  const code = getApiErrorCode(error)
  if (code === 'UNAUTHENTICATED') {
    await handleAuthenticationError()
    return
  }

  ElMessage.error(getApiErrorMessage(error))
  if (
    code === 'PAYMENT_REQUEST_ATTACHMENT_DELETE_STATUS_INVALID'
    || code === 'PAYMENT_REQUEST_ATTACHMENT_UPLOAD_STATUS_INVALID'
  ) {
    await emit('changed')
  }
}

async function upload(): Promise<void> {
  if (
    !props.canManageAttachments
    || selectedAttachmentType.value === null
    || selectedFile.value === null
    || isUploading.value
  ) {
    return
  }

  isUploading.value = true
  try {
    await uploadPaymentRequestAttachment(
      props.paymentRequestId,
      selectedAttachmentType.value,
      selectedFile.value,
    )
    ElMessage.success('附件上傳成功。')
    selectedAttachmentType.value = null
    clearSelectedFile()
    uploadRef.value?.clearFiles()
    emit('changed')
  } catch (error: unknown) {
    await handleAttachmentError(error)
  } finally {
    isUploading.value = false
  }
}

async function download(attachment: PaymentRequestAttachment): Promise<void> {
  if (downloadingAttachmentId.value !== null) {
    return
  }

  downloadingAttachmentId.value = attachment.id
  let objectUrl: string | null = null
  try {
    const response = await downloadPaymentRequestAttachment(
      props.paymentRequestId,
      attachment.id,
    )
    const header = response.headers['content-disposition']
    const filename = getDownloadFilename(
      typeof header === 'string' ? header : undefined,
      `attachment-${attachment.id}`,
    )
    objectUrl = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    link.setAttribute('aria-label', `下載附件 ${attachment.originalFilename}`)
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } catch (error: unknown) {
    await handleAttachmentError(error)
  } finally {
    if (objectUrl !== null) {
      URL.revokeObjectURL(objectUrl)
    }
    downloadingAttachmentId.value = null
  }
}

async function remove(attachment: PaymentRequestAttachment): Promise<void> {
  if (
    !props.canManageAttachments
    || attachment.attachmentType === 'PAYMENT_PROOF'
    || deletingAttachmentId.value !== null
  ) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `確定要刪除「${attachment.originalFilename}」嗎？此操作無法復原。`,
      '刪除附件',
      {
        confirmButtonText: '確認刪除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deletingAttachmentId.value = attachment.id
  try {
    await deletePaymentRequestAttachment(props.paymentRequestId, attachment.id)
    ElMessage.success('附件刪除成功。')
    emit('changed')
  } catch (error: unknown) {
    await handleAttachmentError(error)
  } finally {
    deletingAttachmentId.value = null
  }
}
</script>

<template>
  <el-card shadow="never" class="detail-card attachment-card">
    <template #header>
      <div class="card-header">
        <strong>附件</strong>
        <span>{{ attachments.length }} 筆</span>
      </div>
    </template>

    <div v-if="canManageAttachments" class="attachment-upload-panel">
      <div class="upload-controls">
        <el-select
          v-model="selectedAttachmentType"
          :disabled="isUploading"
          clearable
          placeholder="選擇附件類型"
          aria-label="附件類型"
        >
          <el-option
            v-for="option in uploadAttachmentTypes"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>

        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :show-file-list="false"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          :disabled="isUploading"
          @change="handleFileChange"
          @exceed="handleFileExceed"
          @remove="clearSelectedFile"
        >
          <el-button :disabled="isUploading">選擇檔案</el-button>
        </el-upload>

        <el-button
          type="primary"
          :loading="isUploading"
          :disabled="selectedAttachmentType === null || selectedFile === null"
          @click="upload"
        >
          上傳附件
        </el-button>
      </div>
      <div class="upload-file-name">{{ selectedFileLabel }}</div>
      <small class="upload-hint">支援 PDF、JPG、JPEG、PNG，單檔上限 10 MB</small>
    </div>

    <el-empty
      v-if="attachments.length === 0"
      description="目前沒有附件"
    />
    <el-table v-else :data="attachments" row-key="id">
      <el-table-column label="附件類型" min-width="130">
        <template #default="scope">
          {{ attachmentTypeLabel(scope.row.attachmentType) }}
        </template>
      </el-table-column>
      <el-table-column prop="originalFilename" label="原始檔名" min-width="220" />
      <el-table-column prop="contentType" label="內容類型" min-width="160" />
      <el-table-column label="上傳人" min-width="140">
        <template #default="scope">
          {{ scope.row.uploadedByDisplayName || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="檔案大小" width="120">
        <template #default="scope">
          {{ formatFileSize(scope.row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="上傳時間" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button
            text
            type="primary"
            :loading="downloadingAttachmentId === scope.row.id"
            :disabled="downloadingAttachmentId !== null"
            :aria-label="`下載附件 ${scope.row.originalFilename}`"
            @click="download(scope.row)"
          >
            下載
          </el-button>
          <el-button
            v-if="canManageAttachments && scope.row.attachmentType !== 'PAYMENT_PROOF'"
            text
            type="danger"
            :loading="deletingAttachmentId === scope.row.id"
            :disabled="deletingAttachmentId !== null"
            :aria-label="`刪除附件 ${scope.row.originalFilename}`"
            @click="remove(scope.row)"
          >
            刪除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.attachment-upload-panel {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid #dbe5f0;
  border-radius: 8px;
  background: #f8fbff;
}

.upload-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.upload-controls .el-select {
  width: 180px;
}

.upload-file-name {
  margin-top: 12px;
  color: #334155;
  overflow-wrap: anywhere;
}

.upload-hint {
  display: block;
  margin-top: 6px;
  color: #64748b;
}

@media (max-width: 760px) {
  .upload-controls,
  .upload-controls .el-select,
  .upload-controls .el-upload,
  .upload-controls .el-button {
    width: 100%;
  }
}
</style>
