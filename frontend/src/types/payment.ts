import type { CalculationType, RequestCategory } from './master'
import type { ApprovalAction, ApprovalStatus, PaymentMethod, PaymentStatus } from './workflow'

export interface CreatePaymentDraftItemRequest {
  expenseTypeId: number
  priceCode: string | null
  description: string | null
  peopleCount: number | null
  days: number | null
  quantity: number | null
  multiplier: number | null
  manualAmount: number | null
  extraData: Record<string, unknown> | null
  sortOrder: number
}

export interface CreatePaymentDraftRequest {
  companyId: number
  customerId: number
  requestCategory: RequestCategory
  reason: string
  items: CreatePaymentDraftItemRequest[]
}

export interface CreatePaymentDraftItemResponse {
  id: number
  expenseTypeId: number
  expenseTypeCode: string
  expenseTypeName: string
  calculationType: CalculationType
  priceSettingId: number | null
  priceCode: string | null
  priceName: string | null
  description: string | null
  peopleCount: number | null
  days: number | null
  quantity: number | null
  unitPrice: number | null
  multiplier: number | null
  amount: number
  extraData: Record<string, unknown> | null
  sortOrder: number
}

export interface CreatePaymentDraftResponse {
  id: number
  requestNo: string
  applicantId: number
  departmentId: number
  companyId: number
  customerId: number
  requestCategory: RequestCategory
  reason: string
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  totalAmount: number
  items: CreatePaymentDraftItemResponse[]
  createdAt: string
  version: number
}

export interface PaymentDraftItemForm {
  clientId: string
  expenseTypeId: number | null
  priceCode: string | null
  description: string
  peopleCount: number | null
  days: number | null
  quantity: number | null
  multiplier: number | null
  manualAmount: number | null
}

export interface PaymentRequestUserSummary {
  id: number
  username: string
  displayName: string
}

export interface PaymentRequestDepartmentSummary {
  id: number
  code: string
  name: string
}

export interface PaymentRequestCompanySummary {
  id: number
  code: string
  name: string
}

export interface PaymentRequestCustomerSummary {
  id: number
  code: string
  name: string
}

export interface PaymentRequestItemDetail extends CreatePaymentDraftItemResponse {}

export interface PaymentRequestApprovalHistoryDetail {
  id: number
  actor: PaymentRequestUserSummary
  action:
    | 'SUBMIT'
    | 'MANAGER_APPROVE'
    | 'MANAGER_REJECT'
    | 'CASHIER_APPROVE'
    | 'CASHIER_REJECT'
    | 'PAYMENT_RECORDED'
  fromApprovalStatus: ApprovalStatus
  toApprovalStatus: ApprovalStatus
  fromPaymentStatus: PaymentStatus
  toPaymentStatus: PaymentStatus
  comment: string | null
  actedAt: string
}

export interface PaymentRequestAttachmentDetail {
  id: number
  attachmentType: string
  originalFilename: string
  contentType: string
  fileSize: number
  createdAt: string
}

export interface PaymentRequestDetail {
  id: number
  requestNo: string
  applicant: PaymentRequestUserSummary
  department: PaymentRequestDepartmentSummary | null
  supervisor: PaymentRequestUserSummary | null
  company: PaymentRequestCompanySummary
  customer: PaymentRequestCustomerSummary
  requestCategory: RequestCategory
  reason: string
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  totalAmount: number
  submittedAt: string | null
  approvedAt: string | null
  approvedBy: PaymentRequestUserSummary | null
  rejectedAt: string | null
  closedAt: string | null
  paidAt: string | null
  paidBy: PaymentRequestUserSummary | null
  paymentMethod: PaymentMethod | null
  paymentReference: string | null
  paymentNote: string | null
  items: PaymentRequestItemDetail[]
  approvalHistories: PaymentRequestApprovalHistoryDetail[]
  attachments: PaymentRequestAttachmentDetail[]
  createdAt: string
  updatedAt: string
  version: number
}

export interface SubmitPaymentDraftRequest {
  version: number
}

export interface SubmitPaymentDraftResponse {
  id: number
  requestNo: string
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  supervisorId: number | null
  supervisorName: string | null
  submittedAt: string
  version: number
}

export interface PaymentRequestListItem {
  id: number
  requestNo: string
  applicantId: number
  applicantName: string
  departmentId: number
  departmentName: string
  supervisorId: number | null
  supervisorName: string | null
  companyId: number
  companyName: string
  customerId: number
  customerName: string
  requestCategory: RequestCategory
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  totalAmount: number
  submittedAt: string | null
  approvedAt: string | null
  paidAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export interface PaymentRequestPageResponse {
  content: PaymentRequestListItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ManagerReviewPaymentRequest {
  version: number
  comment: string | null
}

export interface ManagerReviewPaymentResponse {
  id: number
  requestNo: string
  action: ApprovalAction
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  managerId: number
  managerName: string
  comment: string | null
  actedAt: string
  version: number
}

export interface CashierReviewPaymentRequest {
  version: number
  comment: string | null
}

export interface CashierReviewPaymentResponse {
  id: number
  requestNo: string
  action: ApprovalAction
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  cashierId: number
  cashierName: string
  comment: string | null
  actedAt: string
  version: number
}

export interface RecordPaymentRequest {
  version: number
  paidAt: string
  paymentMethod: PaymentMethod | null
  paymentReference: string | null
  paymentNote: string | null
}

export interface RecordPaymentResponse {
  id: number
  requestNo: string
  action: ApprovalAction
  approvalStatus: ApprovalStatus
  paymentStatus: PaymentStatus
  paidById: number
  paidByName: string
  paidAt: string
  paymentMethod: PaymentMethod | null
  paymentReference: string | null
  paymentNote: string | null
  recordedAt: string
  version: number
}
