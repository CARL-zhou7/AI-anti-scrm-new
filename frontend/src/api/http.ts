import type { Envelope, ErrorDetail } from './types'
import { csrf } from './csrf'

export const apiBase = import.meta.env.VITE_API_BASE || '/anti.scam/v1'

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string,
    public fieldErrors: ErrorDetail['fieldErrors'] = [], public currentVersion: number | null = null) { super(message) }
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
    throw new ApiError(response.status, body.code, body.message, body.data?.fieldErrors || [], body.data?.currentVersion ?? null)
  }
  return body.data
}

async function write<T>(path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown): Promise<T> {
    const token = await csrf.ensure()
    const form = body instanceof FormData
    try {
      return await request<T>(path, { method,
        headers: form ? { 'X-CSRF-TOKEN': token } : { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
        body: form ? body : body === undefined ? undefined : JSON.stringify(body) })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'CSRF_INVALID') {
        csrf.clear()
        await csrf.refresh()
      }
      throw error
    }
}

export const http = {
  delete: <T>(path: string, body: unknown) => write<T>(path, 'DELETE', body),
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => write<T>(path, 'POST', body),
  put: <T>(path: string, body: unknown) => write<T>(path, 'PUT', body),
  postForm: <T>(path: string, body: FormData) => write<T>(path, 'POST', body),
}
