import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { csrf } from '../api/csrf'
import { authApi } from '../api/auth'
import { http, ApiError } from '../api/http'
import { homeApi } from '../api/home'
import HomeView from '../views/HomeView.vue'
import RegisterView from '../views/RegisterView.vue'

const success = (data: unknown, status = 200) => new Response(JSON.stringify({
  code: 'OK', message: '成功', data, requestId: 'test-request',
}), { status, headers: { 'Content-Type': 'application/json' } })

beforeEach(() => csrf.clear())
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('phase 1 smoke', () => {
  it('fetches one csrf for concurrent calls and refreshes it after login', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(success({ token: 'before', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(success({ id: '101', username: '用户', role: 'USER' }))
      .mockResolvedValueOnce(success({ token: 'after', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(success({ loggedOut: true }))
    vi.stubGlobal('fetch', fetcher)
    expect(await Promise.all([csrf.ensure(), csrf.ensure()])).toEqual(['before', 'before'])
    expect(fetcher).toHaveBeenCalledTimes(1)
    await authApi.login({ username: '用户', password: ' abc123 ' })
    await http.post('/auth/logout')
    expect(fetcher.mock.calls[1][1].headers['X-CSRF-TOKEN']).toBe('before')
    expect(JSON.parse(fetcher.mock.calls[1][1].body).password).toBe(' abc123 ')
    expect(fetcher.mock.calls[3][1].headers['X-CSRF-TOKEN']).toBe('after')
  })

  it('surfaces field errors without retrying writes', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(success({ token: 'token', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'VALIDATION_FAILED', message: '请检查输入信息',
        data: { fieldErrors: [{ field: 'username', code: 'VALUE_INVALID', message: '用户名格式不正确' }] },
      }), { status: 400 }))
    vi.stubGlobal('fetch', fetcher)
    await expect(http.post('/auth/register', {})).rejects.toMatchObject({
      status: 400, code: 'VALIDATION_FAILED', fieldErrors: [{ field: 'username', code: 'VALUE_INVALID', message: '用户名格式不正确' }],
    })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('renders server content and both entries; a news failure shows retry instead of empty news', async () => {
    vi.spyOn(homeApi, 'getHome').mockResolvedValue({ title: '服务端标题', introduction: '服务端介绍文案',
      newsState: 'EMPTY', entries: ['CONFIGURATION', 'HISTORY'] })
    const news = vi.spyOn(homeApi, 'getNews').mockResolvedValue([])
    const view = mount(HomeView, { global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
    await flushPromises()
    expect(view.get('[data-testid="home-introduction"]').text()).toBe('服务端介绍文案')
    expect(view.find('[data-testid="home-news-empty"]').exists()).toBe(true)
    expect(view.find('[data-testid="home-entry-history"]').exists()).toBe(true)
    expect(view.find('[data-testid="home-entry-configuration"]').exists()).toBe(true)
    view.unmount()
    news.mockRejectedValue(new ApiError(500, 'INTERNAL_ERROR', '资讯加载失败'))
    const failed = mount(HomeView, { global: { stubs: { RouterLink: true } } })
    await flushPromises()
    expect(failed.get('[data-testid="home-error"]').text()).toContain('资讯加载失败')
    expect(failed.find('[data-testid="home-news-empty"]').exists()).toBe(false)
    failed.unmount()
  })

  it('validates confirmation then returns to login without authenticating', async () => {
    const router = createRouter({ history: createMemoryHistory(), routes: [
      { path: '/register', component: RegisterView }, { path: '/login', component: { template: '<div>登录</div>' } },
    ] })
    await router.push('/register')
    const register = vi.spyOn(authApi, 'register').mockResolvedValue({
      account: { id: '101', username: '李雷', role: 'USER' }, nextAction: 'LOGIN',
    })
    const login = vi.spyOn(authApi, 'login')
    const view = mount(RegisterView, { global: { plugins: [createPinia(), router] } })
    await view.get('#username').setValue('李雷')
    await view.get('#password').setValue(' abc123 ')
    await view.get('#confirmPassword').setValue('abc123')
    await view.get('form').trigger('submit')
    expect(view.text()).toContain('两次密码输入不一致')
    expect(register).not.toHaveBeenCalled()
    await view.get('#confirmPassword').setValue(' abc123 ')
    await view.get('form').trigger('submit')
    await flushPromises()
    expect(register).toHaveBeenCalledWith({ username: '李雷', password: ' abc123 ', confirmPassword: ' abc123 ' })
    expect(router.currentRoute.value.fullPath).toBe('/login?registered=1')
    expect(login).not.toHaveBeenCalled()
    view.unmount()
  })
})
