export type Role = 'USER' | 'ADMIN'
export interface Account { id: string; username: string; role: Role }
export interface CsrfToken { token: string; headerName: 'X-CSRF-TOKEN' }
export interface RegisterRequest { username: string; password: string; confirmPassword: string }
export interface LoginRequest { username: string; password: string }
export interface Registration { account: Account; nextAction: 'LOGIN' }
export interface LogoutResult { loggedOut: true }
export interface HomePayload {
  title: string
  introduction: string
  newsState: 'EMPTY'
  entries: ('CONFIGURATION' | 'HISTORY')[]
}
export type NewsPayload = []
export interface FieldError { field: string; code: string; message: string }
export interface ErrorDetail { fieldErrors: FieldError[]; activeSessionId: string | null; currentVersion: number | null }
export interface Envelope<T> { code: string; message: string; data: T; requestId: string }
