import type { Role } from './types'

const key = 'login-role'

export const loginRole = {
  current(): Role | null {
    const role = sessionStorage.getItem(key)
    return role === 'ADMIN' || role === 'USER' ? role : null
  },
  set(role: Role): void { sessionStorage.setItem(key, role) },
  clear(): void { sessionStorage.removeItem(key) },
}
