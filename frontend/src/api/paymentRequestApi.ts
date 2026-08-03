import { http } from './http'
import type {
  CreatePaymentDraftRequest,
  CreatePaymentDraftResponse,
  CashierReviewPaymentRequest,
  CashierReviewPaymentResponse,
  ManagerReviewPaymentRequest,
  ManagerReviewPaymentResponse,
  PaymentRequestDetail,
  PaymentRequestPageResponse,
  RecordPaymentRequest,
  RecordPaymentResponse,
  SubmitPaymentDraftRequest,
  SubmitPaymentDraftResponse,
} from '../types/payment'
import type { ApprovalStatus, PaymentStatus } from '../types/workflow'

export async function createPaymentDraft(
  request: CreatePaymentDraftRequest,
): Promise<CreatePaymentDraftResponse> {
  const response = await http.post<CreatePaymentDraftResponse>(
    '/payment-requests/drafts',
    request,
  )

  return response.data
}

export async function getPaymentRequestDetail(
  id: number,
): Promise<PaymentRequestDetail> {
  const response = await http.get<PaymentRequestDetail>(`/payment-requests/${id}`)
  return response.data
}

export async function submitPaymentDraft(
  id: number,
  request: SubmitPaymentDraftRequest,
): Promise<SubmitPaymentDraftResponse> {
  const response = await http.post<SubmitPaymentDraftResponse>(
    `/payment-requests/${id}/submit`,
    request,
  )
  return response.data
}

export interface PaymentRequestListQuery {
  page: number
  size: number
  scope: 'MY_REQUESTS' | 'MANAGER_PENDING' | 'CASHIER_PENDING' | 'PAYMENT_PENDING'
  requestNo?: string
  approvalStatus?: ApprovalStatus
  paymentStatus?: PaymentStatus
}

export async function getPaymentRequests(
  params: PaymentRequestListQuery,
): Promise<PaymentRequestPageResponse> {
  const response = await http.get<PaymentRequestPageResponse>('/payment-requests', { params })
  return response.data
}

export async function approvePaymentRequestByManager(
  id: number,
  request: ManagerReviewPaymentRequest,
): Promise<ManagerReviewPaymentResponse> {
  const response = await http.post<ManagerReviewPaymentResponse>(
    `/payment-requests/${id}/manager-approve`,
    request,
  )
  return response.data
}

export async function rejectPaymentRequestByManager(
  id: number,
  request: ManagerReviewPaymentRequest,
): Promise<ManagerReviewPaymentResponse> {
  const response = await http.post<ManagerReviewPaymentResponse>(
    `/payment-requests/${id}/manager-reject`,
    request,
  )
  return response.data
}

export async function approvePaymentRequestByCashier(
  id: number,
  request: CashierReviewPaymentRequest,
): Promise<CashierReviewPaymentResponse> {
  const response = await http.post<CashierReviewPaymentResponse>(
    `/payment-requests/${id}/cashier-approve`,
    request,
  )
  return response.data
}

export async function rejectPaymentRequestByCashier(
  id: number,
  request: CashierReviewPaymentRequest,
): Promise<CashierReviewPaymentResponse> {
  const response = await http.post<CashierReviewPaymentResponse>(
    `/payment-requests/${id}/cashier-reject`,
    request,
  )
  return response.data
}

export async function recordPayment(
  id: number,
  request: RecordPaymentRequest,
): Promise<RecordPaymentResponse> {
  const response = await http.post<RecordPaymentResponse>(
    `/payment-requests/${id}/record-payment`,
    request,
  )
  return response.data
}
