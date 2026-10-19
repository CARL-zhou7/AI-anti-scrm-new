import { http } from './http'
import type { HistoryDetail, HistoryPage } from './types'

export const historyApi = {
  list: (params?: { cursor?: string; limit?: number }) => {
    const query = new URLSearchParams()
    if (params?.cursor !== undefined) query.set('cursor', params.cursor)
    if (params?.limit !== undefined) query.set('limit', String(params.limit))
    return http.get<HistoryPage>('/simulations' + (query.size ? `?${query}` : ''))
  },
  detail: (id: string) => http.get<HistoryDetail>(`/simulations/${encodeURIComponent(id)}/detail`),
}
