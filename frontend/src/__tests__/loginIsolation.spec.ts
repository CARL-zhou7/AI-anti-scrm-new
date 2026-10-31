import { afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authApi } from '../api/auth'
import { loginRole } from '../api/loginRole'
import router from '../router'

afterEach(() => { vi.restoreAllMocks(); loginRole.clear(); window.history.replaceState(null, '', '/') })

it('lets a fresh login tab choose another account and restores administrator pages in their own scope', async () => {
  setActivePinia(createPinia())
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  loginRole.clear()
  const me = vi.spyOn(authApi, 'me').mockResolvedValue({ id: '102', username: 'admin', role: 'ADMIN' })
  await router.push('/login')
  expect(router.currentRoute.value.path).toBe('/login')
  expect(me).not.toHaveBeenCalled()
  await router.push('/admin/scammers')
  expect(me).toHaveBeenCalledOnce()
  expect(loginRole.current()).toBe('ADMIN')
  expect(router.currentRoute.value.path).toBe('/admin/scammers')
})
