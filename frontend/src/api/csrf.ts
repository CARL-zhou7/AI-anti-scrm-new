import { request } from './http'
import type { CsrfToken } from './types'

let token: string | null = null
let pending: Promise<string> | null = null

export const csrf = {
  ensure(): Promise<string> {
    if (token) return Promise.resolve(token)
    if (!pending) {
      pending = request<CsrfToken>('/auth/csrf').then(data => {
        token = data.token
        return token
      }).finally(() => { pending = null })
    }
    return pending
  },
  refresh(): Promise<string> { this.clear(); return this.ensure() },
  clear(): void { token = null },
}
