import { http } from './http'
import type { AdminScenario, AdminProfile, AvatarUpload, ProfileKind, ProfilePage, ScammerWrite, VictimWrite } from './types'

export const adminApi = {
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
}
