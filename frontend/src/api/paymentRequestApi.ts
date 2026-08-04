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
  PaymentRequestAttachmentResponse,
  PaymentRequestAttachmentType,
} from '../types/payment'
import type { ApprovalStatus, PaymentStatus } from '../types/workflow'
import type { AxiosResponse } from 'axios'

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

export async function uploadPaymentRequestAttachment(
  paymentRequestId: number,
  attachmentType: PaymentRequestAttachmentType,
  file: File,
): Promise<PaymentRequestAttachmentResponse> {
  const formData = new FormData()
  formData.append('attachmentType', attachmentType)
  formData.append('file', file)

  const response = await http.post<PaymentRequestAttachmentResponse>(
    `/payment-requests/${paymentRequestId}/attachments`,
    formData,
  )
  return response.data
}

export function downloadPaymentRequestAttachment(
  paymentRequestId: number,
  attachmentId: number,
): Promise<AxiosResponse<Blob>> {
  return http.get<Blob>(
    `/payment-requests/${paymentRequestId}/attachments/${attachmentId}/download`,
    { responseType: 'blob' },
  )
}

export async function deletePaymentRequestAttachment(
  paymentRequestId: number,
  attachmentId: number,
): Promise<void> {
  await http.delete(
    `/payment-requests/${paymentRequestId}/attachments/${attachmentId}`,
  )
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
