import { apiBase, http, postKeepalive } from './http'
import { csrf } from './csrf'
import type {
  EffectResult, ExitReason, PresentationReceipt, SimulationEventEnvelope, SimulationState, StartRequest, TimingSample,
} from './types'

const EVENT_TYPES = ['session.started', 'round.started', 'message.saved', 'analysis.saved', 'round.reading', 'session.finished',
  'session.interrupted', 'session.state', 'session.paused', 'session.resumed', 'report.updated'] as const

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
  exit: (id: string, reason: ExitReason) => http.post<SimulationState>(`/simulations/${encodeURIComponent(id)}/exit`, { reason }),
  exitKeepalive: (id: string, reason: ExitReason) =>
    postKeepalive(`/simulations/${encodeURIComponent(id)}/exit`, { reason }, csrf.current()),
  /** Native same-origin EventSource (cookie auth, no token in the URL); returns a closer. */
  openStream(id: string, onEvent: (event: SimulationEventEnvelope) => void, onError: () => void): () => void {
    const source = new EventSource(`${apiBase}/simulations/${encodeURIComponent(id)}/events`)
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
