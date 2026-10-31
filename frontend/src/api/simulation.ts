import { apiUrl, http, postKeepalive } from './http'
import { csrf } from './csrf'
import type {
  EffectResult, ExitReason, PresentationReceipt, SimulationEventEnvelope, SimulationState, StartRequest, TimingSample,
  InterventionState, DraftRequest, AdoptRequest,
} from './types'

const EVENT_TYPES = ['session.started', 'round.started', 'message.saved', 'analysis.saved', 'round.reading', 'session.finished',
  'session.interrupted', 'session.state', 'session.paused', 'session.resumed', 'report.updated',
  'intervention.draft'] as const

/** The only entry point for simulation requests; components never call fetch/EventSource directly. */
export const simulationApi = {
  timingSample: () => http.get<TimingSample>('/timing/samples'),
  start: (body: StartRequest) => http.post<SimulationState>('/simulations', body),
  state: (id: string) => http.get<SimulationState>('/simulations/' + encodeURIComponent(id)),
  presentOpening: (id: string, body: PresentationReceipt) =>
    http.post<EffectResult>(`/simulations/${encodeURIComponent(id)}/opening/presented`, body),
  presentMessage: (id: string, messageId: string, body: PresentationReceipt) =>
    http.post<EffectResult>(`/simulations/${encodeURIComponent(id)}/messages/${encodeURIComponent(messageId)}/presentation`, body),
  presentAnalysis: (id: string, roundNo: number, body: PresentationReceipt) =>
    http.post<EffectResult>(`/simulations/${encodeURIComponent(id)}/rounds/${roundNo}/analysis/presented`, body),
  pause: (id: string) => http.post<EffectResult>(`/simulations/${encodeURIComponent(id)}/pause`),
  resume: (id: string) => http.post<EffectResult>(`/simulations/${encodeURIComponent(id)}/resume`),
  intervention: (id: string) =>
    http.get<InterventionState>(`/simulations/${encodeURIComponent(id)}/intervention`),
  adoptDraft: (id: string, body: AdoptRequest) =>
    http.post<SimulationState>(`/simulations/${encodeURIComponent(id)}/intervention/adopt`, body),
  reviseDraft: (id: string, body: DraftRequest) =>
    http.put<InterventionState>(`/simulations/${encodeURIComponent(id)}/intervention`, body),
  regenerateDraft: (id: string, body: DraftRequest) =>
    http.post<InterventionState>(`/simulations/${encodeURIComponent(id)}/intervention/regenerate`, body),
  exit: (id: string, reason: ExitReason) => http.post<SimulationState>(`/simulations/${encodeURIComponent(id)}/exit`, { reason }),
  exitKeepalive: (id: string, reason: ExitReason) =>
    postKeepalive(`/simulations/${encodeURIComponent(id)}/exit`, { reason }, csrf.current()),
  audioUrl: (url: string) => apiUrl(url.replace(/^\/anti\.scam\/v1/, '')),
  /** Native same-origin EventSource (cookie auth, no token in the URL); returns a closer. */
  openStream(id: string, onEvent: (event: SimulationEventEnvelope) => void, onError: () => void): () => void {
    const source = new EventSource(apiUrl(`/simulations/${encodeURIComponent(id)}/events`))
    for (const type of EVENT_TYPES) {
      source.addEventListener(type, message => {
        try {
          const envelope = JSON.parse((message as MessageEvent<string>).data)
          onEvent({ ...envelope, type } as SimulationEventEnvelope)
        } catch { /* malformed frame: ignore, the next session.state will reconcile */ }
      })
    }
    source.onerror = onError
    return () => source.close()
  },
}
