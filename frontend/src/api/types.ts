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
  /** Open-mouth portrait frame (V1.1), same pixel size as the closed-mouth avatar. */
  speakingAvatar: AvatarRef
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

export interface ContentVersion { version: number; createdAt: string; updatedAt: string }
export interface AdminScenario extends Scenario, ContentVersion {}
export interface AdminScammerProfile extends ScammerProfilePayload, ContentVersion { enabled: boolean }
export interface AdminVictimProfile extends VictimProfilePayload, ContentVersion { enabled: boolean }
export type AdminProfile = AdminScammerProfile | AdminVictimProfile
export type ProfileKind = 'scammer' | 'victim'
export type ScammerWrite = Omit<ScammerProfilePayload, 'id' | 'avatar' | 'speakingAvatar'> & { avatarKey: string; speakingAvatarKey: string }
export type VictimWrite = Omit<VictimProfilePayload, 'id' | 'avatar' | 'speakingAvatar'> & { avatarKey: string; speakingAvatarKey: string }
export interface AvatarUpload { key: string; previewUrl: string; mediaType: string; sizeBytes: number }
export interface ProfileFormValues {
  name: string; age: number | ''; gender: string; identity: string; description: string
  contextTime: string; contextPlace: string; currentState: string; avatarKey: string; speakingAvatarKey: string
  fraudExperience: Level; speechSkill: Level; manipulationSkill: Level; behaviorTraits: string
  antiFraudAwareness: Level; riskTendency: Level; personalityTraits: string
}

// ---- AI simulation (interface spec V1.1, API-14~21, API-42~44)
export type SimulationStatus = 'RUNNING' | 'FINISHED' | 'INTERRUPTED'
export type RoundPhase = 'SCAMMER_GENERATING' | 'SCAMMER_PRESENTING' | 'VICTIM_GENERATING' | 'VICTIM_PRESENTING'
  | 'ANALYZING' | 'ANALYSIS_PRESENTING' | 'COMPLETE'
export type PresentationStatus = 'PENDING' | 'PRESENTING' | 'COMPLETED' | 'FAILED'
export type Speaker = 'SCAMMER' | 'VICTIM'
export type RiskScore = 0 | 25 | 50 | 75 | 100
export type PresentationError = 'TEXT_RENDER_FAILED' | 'AVATAR_LOAD_FAILED' | 'ANIMATION_FAILED' | 'AUDIO_LOAD_FAILED' | 'AUDIO_PLAYBACK_FAILED'
export type ExitReason = 'NAVIGATION' | 'REFRESH' | 'CLOSE' | 'LOGOUT'
export type NextAction = 'NEXT_ROUND' | 'REPORT'
export type Effect = 'APPLIED' | 'ALREADY_APPLIED' | 'IGNORED_TERMINAL'
export interface TimingSample { sampleId: string; serverSampledAt: string; expiresAt: string; methodVersion: string }
export interface TimingEvidence {
  methodVersion: string; sampleId: string
  clientSendMonotonicMs: number; clientReceiveMonotonicMs: number; clientClickMonotonicMs: number
}
export interface StartRequest {
  startRequestId: string; scenarioId: string; scammerProfileId: string; victimProfileId: string; timing: TimingEvidence
}
export interface Configuration { scenario: Scenario; scammer: ScammerProfilePayload; victim: VictimProfilePayload }
export interface AudioSegment { startMs: number; endMs: number; charStart: number; charEnd: number }
export interface LiveMessage {
  id: string; roundNo: number; speaker: Speaker; content: string; actionText: string | null
  presentationStatus: PresentationStatus; presentationCompletedAt: string | null; presentationErrorCode: string | null
  savedAt: string; audioUrl: string | null; audioDurationMs: number | null; segments: AudioSegment[] | null
}
export interface Analysis {
  fraudStage: string; speechAnalysis: string; methodAnalysis: string; victimPsychology: string
  riskScore: RiskScore; riskReason: string; savedAt: string; presentedAt: string | null
}
export interface LiveRound {
  roundNo: number; phase: RoundPhase; interactionComplete: boolean; presentationComplete: boolean
  messages: LiveMessage[]; analysis: Analysis | null; readingUntil: string | null
}
export interface Outcome { code: 'GOAL_ACHIEVED' | 'LOSS_STOPPED' | 'NOT_DEFRAUDED' | 'UNDETERMINED'; displayText: string; reason: string; limitReason: 'ROUND_LIMIT' | 'TIME_LIMIT' | 'BOTH' | null }
export interface Interruption { code: string; message: string }
export interface ReportSummary { status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'; displayText: string; canRetry: boolean; attemptNo: number; updatedAt: string; error: { code: string; message: string } | null }
export interface SimulationState {
  sessionId: string; status: SimulationStatus; stateVersion: string; startedAt: string; deadlineAt: string; endedAt: string | null
  currentRoundNo: number; configuration: Configuration; currentRound: LiveRound | null; latestRiskScore: RiskScore | null
  outcome: Outcome | null; interruption: Interruption | null; readingUntil: string | null; nextAction: NextAction | null
  paused: boolean; pausedAt: string | null; pausedCreditMs: number; report: ReportSummary | null
}
export interface PresentationReceipt { status: 'COMPLETED' | 'FAILED'; errorCode?: PresentationError }
export interface EffectResult { effect: Effect; state: SimulationState }
export interface RoundStartedData { roundNo: number; phase: RoundPhase; startedAt: string; deadlineAt: string }
export interface AnalysisSavedData { roundNo: number; analysis: Analysis }
export interface RoundReadingData { roundNo: number; readingUntil: string; nextAction: NextAction }
export interface SessionStateData { state: SimulationState; reportView: unknown }
export type SimulationEvent =
  | { type: 'session.started' | 'session.finished' | 'session.interrupted' | 'session.paused' | 'session.resumed'; data: SimulationState }
  | { type: 'session.state'; data: SessionStateData }
  | { type: 'round.started'; data: RoundStartedData }
  | { type: 'message.saved'; data: LiveMessage }
  | { type: 'analysis.saved'; data: AnalysisSavedData }
  | { type: 'round.reading'; data: RoundReadingData }
  | { type: 'report.updated'; data: unknown }
export type SimulationEventEnvelope = SimulationEvent & { eventId: string; sessionId: string; stateVersion: string }
