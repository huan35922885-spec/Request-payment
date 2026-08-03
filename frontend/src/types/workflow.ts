export type ApprovalStatus =
  | 'DRAFT'
  | 'PENDING_MANAGER'
  | 'PENDING_CASHIER'
  | 'APPROVED'
  | 'REJECTED_CLOSED'

export type PaymentStatus = 'UNPAID' | 'PAID'

export type PaymentMethod = 'CASH' | 'BANK_TRANSFER' | 'OTHER'

export const PAYMENT_METHOD_OPTIONS: Array<{ value: PaymentMethod; label: string }> = [
  { value: 'BANK_TRANSFER', label: '銀行轉帳' },
  { value: 'CASH', label: '現金' },
  { value: 'OTHER', label: '其他' },
]

export type ApprovalAction =
  | 'SUBMIT'
  | 'MANAGER_APPROVE'
  | 'MANAGER_REJECT'
  | 'CASHIER_APPROVE'
  | 'CASHIER_REJECT'
  | 'PAYMENT_RECORDED'

export function getApprovalStatusLabel(status: ApprovalStatus): string {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'PENDING_MANAGER':
      return '待主管複核'
    case 'PENDING_CASHIER':
      return '待出納確認'
    case 'APPROVED':
      return '已核准'
    case 'REJECTED_CLOSED':
      return '已退回結案'
  }
}

export function getPaymentStatusLabel(status: PaymentStatus): string {
  switch (status) {
    case 'UNPAID':
      return '未付款'
    case 'PAID':
      return '已付款'
  }
}
