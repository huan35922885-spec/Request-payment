export type SecurityRole =
  | 'CASHIER'
  | 'PAYMENT_OPERATOR'
  | 'MASTER_DATA_ADMIN'

export interface LoginRequest {
  username: string
  password: string
}

export interface AuthenticatedUser {
  userId: number
  username: string
  displayName: string
  roles: SecurityRole[]
}
