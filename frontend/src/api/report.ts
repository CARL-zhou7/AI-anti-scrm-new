import { http } from './http'
import type { ReportView } from './types'

export const reportApi = {
  view: (id: string) => http.get<ReportView>(`/simulations/${encodeURIComponent(id)}/report`),
  retry: (id: string) => http.post<ReportView>(`/simulations/${encodeURIComponent(id)}/report/retry`),
}
