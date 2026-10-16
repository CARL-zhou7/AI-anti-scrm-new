import type { Envelope, ErrorDetail } from './types'
import { csrf } from './csrf'

export const apiBase = import.meta.env.VITE_API_BASE || '/anti.scam/v1'

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string,
    public fieldErrors: ErrorDetail['fieldErrors'] = []) { super(message) }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(apiBase + path, { ...init, credentials: 'same-origin', cache: 'no-store' })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '网络连接失败，请检查连接后重试')
  }
  let body: Envelope<T & ErrorDetail>
  try { body = await response.json() } catch {
    throw new ApiError(response.status, 'INVALID_RESPONSE', '服务暂时不可用，请稍后重试')
  }
  if (!response.ok || body.code !== 'OK') {
    if (response.status === 401 && body.code === 'UNAUTHENTICATED') {
      window.dispatchEvent(new Event('session-expired'))
    }
    throw new ApiError(response.status, body.code, body.message, body.data?.fieldErrors || [])
  }
  return body.data
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  async post<T>(path: string, body?: unknown): Promise<T> {
    const token = await csrf.ensure()
    try {
      return await request<T>(path, { method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
        body: body === undefined ? undefined : JSON.stringify(body) })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'CSRF_INVALID') {
        csrf.clear()
        await csrf.refresh()
      }
      throw error
    }
  },
}
