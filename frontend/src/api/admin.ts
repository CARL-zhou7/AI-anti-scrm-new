import { http } from './http'
import type { AdminScenario, AdminProfile, AvatarUpload, InterventionRecord, ProfileKind, ProfilePage, RuleDraft, SceneRulePage, SceneRuleUpdate, SceneRuleView, SceneRuleWrite, ScammerWrite, VictimWrite, TrustPolicy } from './types'

export const adminApi = {
  getTrustPolicy: () => http.get<TrustPolicy>('/admin/trust-policy'),
  updateTrustPolicy: (body: Pick<TrustPolicy, 'thresholdAccept' | 'thresholdSmall' | 'thresholdLarge' | 'version'>) => http.put<TrustPolicy>('/admin/trust-policy', body),
  listScenarios: () => http.get<AdminScenario[]>('/admin/scenarios'),
  updateScenario: (id: string, body: { name: string; description: string; version: number }) =>
    http.put<AdminScenario>('/admin/scenarios/' + encodeURIComponent(id), body),
  listProfiles: (kind: ProfileKind, cursor?: string) => http.get<ProfilePage<AdminProfile>>(
    `/admin/${kind}-profiles` + (cursor ? '?' + new URLSearchParams({ cursor }) : '')),
  getProfile: (kind: ProfileKind, id: string) => http.get<AdminProfile>(`/admin/${kind}-profiles/${encodeURIComponent(id)}`),
  createProfile: (kind: ProfileKind, body: ScammerWrite | VictimWrite) => http.post<AdminProfile>(`/admin/${kind}-profiles`, body),
  updateProfile: (kind: ProfileKind, id: string, body: (ScammerWrite | VictimWrite) & { version: number }) =>
    http.put<AdminProfile>(`/admin/${kind}-profiles/${encodeURIComponent(id)}`, body),
  setEnabled: (kind: ProfileKind, id: string, enabled: boolean, version: number) =>
    http.post<AdminProfile>(`/admin/${kind}-profiles/${encodeURIComponent(id)}/${enabled ? 'enable' : 'disable'}`, { version }),
  uploadAvatar(file: File) {
    const body = new FormData(); body.append('file', file)
    return http.postForm<AvatarUpload>('/admin/avatars', body)
  },
  // ---- V1.4 knowledge base (API-50~54): waiting records, induction, rules and their sources.
  interventions(filter: { ruleId?: string; sceneCode?: string } = {}) {
    const params = new URLSearchParams()
    if (filter.ruleId) params.set('ruleId', filter.ruleId)
    if (filter.sceneCode) params.set('sceneCode', filter.sceneCode)
    const query = params.toString()
    return http.get<InterventionRecord[]>('/admin/interventions' + (query ? '?' + query : ''))
  },
  induce: (interventionIds: string[]) => http.post<RuleDraft>('/admin/interventions/induce', { interventionIds }),
  listRules: () => http.get<SceneRulePage>('/admin/scene-rules'),
  createRule: (body: SceneRuleWrite) => http.post<SceneRuleView>('/admin/scene-rules', body),
  updateRule: (id: string, body: SceneRuleUpdate) =>
    http.put<SceneRuleView>('/admin/scene-rules/' + encodeURIComponent(id), body),
  ruleSources: (id: string) => http.get<InterventionRecord[]>('/admin/scene-rules/' + encodeURIComponent(id) + '/sources'),
}
