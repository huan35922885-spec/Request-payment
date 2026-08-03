<script setup lang="ts">
import { computed } from 'vue'
import type { TagProps } from 'element-plus'
import {
  getApprovalStatusLabel,
  getPaymentStatusLabel,
  type ApprovalStatus,
  type PaymentStatus,
} from '../../types/workflow'

const props = defineProps<{
  status: ApprovalStatus | PaymentStatus
  kind: 'approval' | 'payment'
}>()

const label = computed(() =>
  props.kind === 'approval'
    ? getApprovalStatusLabel(props.status as ApprovalStatus)
    : getPaymentStatusLabel(props.status as PaymentStatus),
)

const tagType = computed<TagProps['type']>(() => {
  if (props.kind === 'payment') {
    return props.status === 'PAID' ? 'success' : 'warning'
  }

  switch (props.status) {
    case 'DRAFT':
      return 'info'
    case 'PENDING_MANAGER':
      return 'warning'
    case 'PENDING_CASHIER':
      return 'primary'
    case 'APPROVED':
      return 'success'
    case 'REJECTED_CLOSED':
      return 'danger'
  }
})
</script>

<template>
  <el-tag :type="tagType">
    {{ label }}
  </el-tag>
</template>
