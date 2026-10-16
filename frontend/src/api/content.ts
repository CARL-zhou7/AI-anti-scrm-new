import { http } from './http'
import type { AvatarRef, Scenario, ScammerProfilePayload, VictimProfilePayload, ScammerPagePayload, VictimPagePayload } from './types'

function query(params?: { cursor?: string; limit?: number }) {
  const search = new URLSearchParams()
  if (params?.cursor !== undefined) search.set('cursor', params.cursor)
  if (params?.limit !== undefined) search.set('limit', String(params.limit))
  return search.size ? `?${search}` : ''
}

export const contentApi = {
  listScenarios: () => http.get<Scenario[]>('/scenarios'),
  listScammerProfiles: (params?: { cursor?: string; limit?: number }) => http.get<ScammerPagePayload>('/scammer-profiles' + query(params)),
  listVictimProfiles: (params?: { cursor?: string; limit?: number }) => http.get<VictimPagePayload>('/victim-profiles' + query(params)),
  getScammerProfile: (id: string) => http.get<ScammerProfilePayload>('/scammer-profiles/' + encodeURIComponent(id)),
  getVictimProfile: (id: string) => http.get<VictimProfilePayload>('/victim-profiles/' + encodeURIComponent(id)),
  avatarUrl: (avatar: AvatarRef) => avatar.url,
}
