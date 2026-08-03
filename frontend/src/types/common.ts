export interface FieldValidationError {
  field: string
  message: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  error: string
  code: string
  message: string
  path: string
  fieldErrors: FieldValidationError[]
}
