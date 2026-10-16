import { http } from './http'
import { csrf } from './csrf'
import type { Account, LoginRequest, LogoutResult, RegisterRequest, Registration } from './types'

export const authApi = {
  me: () => http.get<Account>('/auth/me'),
  register: (body: RegisterRequest) => http.post<Registration>('/auth/register', body),
  async login(body: LoginRequest) {
    const account = await http.post<Account>('/auth/login', body)
    csrf.clear()
    await csrf.ensure()
    return account
  },
  logout: () => http.post<LogoutResult>('/auth/logout'),
}
