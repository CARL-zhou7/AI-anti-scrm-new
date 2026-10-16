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

export type Level = 'LOW' | 'MEDIUM' | 'HIGH'
export interface Scenario {
  id: string
  code: 'TASK_REBATE' | 'FAKE_INVESTMENT' | 'IMPERSONATION_LOAN'
  name: string
  description: string
  focus: string
}
export interface AvatarRef { key: string; url: string }
interface ProfilePayload {
  id: string
  avatar: AvatarRef
  name: string
  age: number
  gender: string
  identity: string
  description: string
  contextTime: string
  contextPlace: string
  currentState: string
}
export interface ScammerProfilePayload extends ProfilePayload {
  fraudExperience: Level
  speechSkill: Level
  manipulationSkill: Level
  behaviorTraits: string
}
export interface VictimProfilePayload extends ProfilePayload {
  antiFraudAwareness: Level
  riskTendency: Level
  personalityTraits: string
}
export type Profile = ScammerProfilePayload | VictimProfilePayload
export interface ProfilePage<T> { items: T[]; nextCursor: string | null; hasMore: boolean }
export type ScammerPagePayload = ProfilePage<ScammerProfilePayload>
export type VictimPagePayload = ProfilePage<VictimProfilePayload>
