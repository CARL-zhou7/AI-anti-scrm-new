import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import { ApiError } from '../api/http'
import { csrf } from '../api/csrf'
import type { Account, LoginRequest } from '../api/types'

export const useAuthStore = defineStore('auth', () => {
  const account = ref<Account | null>(null)
  const ready = ref(false)
  const bootstrapError = ref('')
  const landing = computed(() => account.value?.role === 'ADMIN' ? '/admin' : '/home')
  let pending: Promise<void> | null = null

  function reset() { account.value = null; csrf.clear(); ready.value = true }
  async function bootstrap() {
    if (ready.value) return
    if (!pending) {
      pending = (async () => {
        bootstrapError.value = ''
        try { account.value = await authApi.me(); ready.value = true } catch (error) {
          if (error instanceof ApiError && error.status === 401) reset()
          else bootstrapError.value = error instanceof Error ? error.message : '无法确认登录状态'
        }
      })().finally(() => { pending = null })
    }
    await pending
  }
  async function signIn(body: LoginRequest) {
    account.value = await authApi.login(body)
    ready.value = true
  }
  async function signOut() {
    try { await authApi.logout() } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error
    }
    reset()
  }
  return { account, ready, bootstrapError, landing, bootstrap, signIn, signOut, reset }
})
