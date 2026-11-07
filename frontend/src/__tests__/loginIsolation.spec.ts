import { afterEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import App from '../App.vue'
import { authApi } from '../api/auth'
import { loginRole } from '../api/loginRole'
import router from '../router'

afterEach(() => { vi.restoreAllMocks(); loginRole.clear(); window.history.replaceState(null, '', '/') })

it('keeps direct registration open when the initial session check returns 401', async () => {
  setActivePinia(createPinia())
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
    JSON.stringify({ code: 'UNAUTHENTICATED', message: '未登录', data: null }), { status: 401 },
  ))
  await router.push('/register')
  expect(router.currentRoute.value.path).toBe('/register')
})

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

it('requires six practice values and reveals the result without sending or keeping them', async () => {
  const pinia = createPinia()
  setActivePinia(pinia)
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(
    JSON.stringify({ code: 'UNAUTHENTICATED', message: '未登录', data: null }), { status: 401 },
  ))
  await router.push('/login')
  const app = mount(App, { global: { plugins: [pinia, router] } })
  expect(app.get('.auth-switch a').attributes('href')).toBe('/register')
  await app.get('a[href="/register/practice"]').trigger('click'); await flushPromises()
  fetch.mockClear()

  const form = app.get<HTMLFormElement>('form').element
  const inputs = app.findAll<HTMLInputElement>('form input')
  expect(inputs).toHaveLength(6)
  form.requestSubmit(); await flushPromises()
  expect(router.currentRoute.value.path).toBe('/register/practice')
  for (const input of inputs) await input.setValue('演示资料')
  expect([...new FormData(form)]).toEqual([])
  form.requestSubmit(); await flushPromises()

  expect(router.currentRoute.value.fullPath).toBe('/register/practice/result')
  expect(app.text()).toContain('哈哈哈！你被骗啦！')
  expect(app.get('.form-submit').attributes('href')).toBe('/register')
  expect(inputs.every(input => input.element.value === '')).toBe(true)
  expect(fetch).not.toHaveBeenCalled()
  app.unmount()
})
