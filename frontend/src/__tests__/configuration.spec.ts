import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ConfigurationView from '../views/ConfigurationView.vue'
import { contentApi } from '../api/content'
import { ApiError } from '../api/http'
import type { ScammerProfilePayload, VictimProfilePayload } from '../api/types'

const scammer: ScammerProfilePayload = { id: '1', avatar: { key: 's', url: '/anti.scam/v1/avatars/s' }, name: '人物甲', age: 35,
  gender: '男', identity: '身份甲', description: '描述甲', contextTime: '时间甲', contextPlace: '地点甲', currentState: '状态甲',
  fraudExperience: 'HIGH', speechSkill: 'MEDIUM', manipulationSkill: 'LOW', behaviorTraits: '行为甲' }
const victim: VictimProfilePayload = { id: '2', avatar: { key: 'v', url: '/anti.scam/v1/avatars/v' }, name: '人物乙', age: 22,
  gender: '女', identity: '身份乙', description: '描述乙', contextTime: '时间乙', contextPlace: '地点乙', currentState: '状态乙',
  antiFraudAwareness: 'MEDIUM', riskTendency: 'HIGH', personalityTraits: '性格乙' }

beforeEach(() => {
  vi.spyOn(contentApi, 'listScenarios').mockResolvedValue([{ id: '1', code: 'TASK_REBATE', name: '服务器场景', description: '服务器简介', focus: '内容侧重' }])
  vi.spyOn(contentApi, 'listScammerProfiles').mockResolvedValue({ items: [scammer], nextCursor: null, hasMore: false })
  vi.spyOn(contentApi, 'listVictimProfiles').mockResolvedValue({ items: [victim], nextCursor: null, hasMore: false })
  vi.spyOn(contentApi, 'getScammerProfile').mockResolvedValue(scammer)
  vi.spyOn(contentApi, 'getVictimProfile').mockResolvedValue(victim)
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

async function select() {
  const wrapper = mount(ConfigurationView)
  await flushPromises()
  await wrapper.get('[data-testid=scenario-option]').trigger('click')
  for (const card of wrapper.findAll('[data-testid=profile-card]')) await card.trigger('click')
  for (const img of wrapper.findAll('img')) await img.trigger('load')
  return wrapper
}

it('shows complete server content, requires all selections and only rechecks via reads', async () => {
  const wrapper = mount(ConfigurationView)
  await flushPromises()
  expect(wrapper.get('[data-testid=configuration-start]').attributes('disabled')).toBeDefined()
  wrapper.unmount()
  const selected = await select()
  const summary = selected.get('[data-testid=configuration-summary]').text()
  for (const text of ['服务器场景', '描述甲', '行为甲', '时间甲', '地点甲', '状态甲', '描述乙', '性格乙', '时间乙', '地点乙', '状态乙', '高', '中', '低']) expect(summary).toContain(text)
  expect(selected.get('[data-testid=configuration-start]').attributes('disabled')).toBeUndefined()
  await selected.get('[data-testid=configuration-start]').trigger('click')
  await flushPromises()
  expect(contentApi.getScammerProfile).toHaveBeenCalledWith('1')
  expect(contentApi.getVictimProfile).toHaveBeenCalledWith('2')
  expect(selected.text()).toContain('开始演绎将在下一阶段开放')
})

it('clears an unavailable selection and blocks a failed avatar', async () => {
  const wrapper = await select()
  await wrapper.findAll('img')[0]!.trigger('error')
  expect(wrapper.get('[data-testid=configuration-start]').attributes('disabled')).toBeDefined()
  expect(wrapper.text()).toContain('头像加载失败')
  await wrapper.findAll('img')[0]!.trigger('load')
  vi.mocked(contentApi.getScammerProfile).mockRejectedValue(new ApiError(404, 'NOT_FOUND', '资源不存在或不可访问'))
  await wrapper.get('[data-testid=configuration-start]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[data-testid=configuration-reselect-hint]').text()).toContain('所选画像已不可用，请重新选择')
  expect(wrapper.get('[data-testid=configuration-start]').attributes('disabled')).toBeDefined()
  expect(wrapper.find('[data-testid=profile-list-scammer] [aria-pressed=true]').exists()).toBe(false)
})

it('distinguishes request failure from empty lists and supports retry', async () => {
  vi.mocked(contentApi.listScenarios).mockRejectedValueOnce(new Error('服务失败'))
  const wrapper = mount(ConfigurationView)
  await flushPromises()
  expect(wrapper.find('[data-testid=configuration-error]').exists()).toBe(true)
  expect(wrapper.find('[data-testid=profiles-empty]').exists()).toBe(false)
  vi.mocked(contentApi.listScammerProfiles).mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
  await wrapper.get('[data-testid=configuration-retry]').trigger('click')
  await flushPromises()
  expect(wrapper.get('[data-testid=profiles-empty]').text()).toContain('暂无可用骗子画像')
})

it('loads later profile pages', async () => {
  vi.mocked(contentApi.listVictimProfiles).mockResolvedValueOnce({ items: [victim], nextCursor: 'next', hasMore: true })
    .mockResolvedValueOnce({ items: [{ ...victim, id: '3', name: '下一页人物' }], nextCursor: null, hasMore: false })
  const wrapper = mount(ConfigurationView)
  await flushPromises()
  await wrapper.get('[data-testid=profile-list-victim] > button.text-button').trigger('click')
  await flushPromises()
  expect(contentApi.listVictimProfiles).toHaveBeenLastCalledWith({ cursor: 'next' })
  expect(wrapper.text()).toContain('下一页人物')
})

it('uses the five GET endpoints and preserves the avatar URL', async () => {
  vi.restoreAllMocks()
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 'OK', data: [] }))))
  vi.stubGlobal('fetch', fetcher)
  await contentApi.listScenarios()
  await contentApi.listScammerProfiles({ cursor: 'a_b', limit: 1 })
  await contentApi.listVictimProfiles()
  await contentApi.getScammerProfile('1')
  await contentApi.getVictimProfile('2')
  expect(fetcher.mock.calls.map(call => call[0])).toEqual(['/anti.scam/v1/scenarios', '/anti.scam/v1/scammer-profiles?cursor=a_b&limit=1', '/anti.scam/v1/victim-profiles', '/anti.scam/v1/scammer-profiles/1', '/anti.scam/v1/victim-profiles/2'])
  expect(fetcher.mock.calls.every(call => !call[1].method)).toBe(true)
  expect(contentApi.avatarUrl(scammer.avatar)).toBe(scammer.avatar.url)
})

it('toggles individual choices, resets without fetching, and clears choices on reload', async () => {
  const wrapper = await select()
  const choices = wrapper.findAll('[data-testid=scenario-option], [data-testid=profile-card]')
  for (const choice of choices) {
    await choice.trigger('click')
    expect(choice.attributes('aria-pressed')).toBe('false')
    expect(wrapper.findAll('[aria-pressed=true]')).toHaveLength(2)
    expect(wrapper.get('[data-testid=configuration-start]').attributes('disabled')).toBeDefined()
    await choice.trigger('click')
  }
  await wrapper.get('[data-testid=configuration-reset]').trigger('click')
  expect(wrapper.findAll('[aria-pressed=true]')).toHaveLength(0)
  expect(wrapper.find('[data-testid=profile-recommended]').exists()).toBe(false)
  expect(contentApi.listScenarios).toHaveBeenCalledTimes(1)
  expect(contentApi.listScammerProfiles).toHaveBeenCalledTimes(1)
  expect(contentApi.listVictimProfiles).toHaveBeenCalledTimes(1)
  for (const choice of choices) await choice.trigger('click')
  expect(wrapper.find('[data-testid=configuration-reload]').exists()).toBe(false)
  wrapper.unmount()
  const refreshed = mount(ConfigurationView)
  await flushPromises()
  expect(refreshed.findAll('[aria-pressed=true]')).toHaveLength(0)
  expect(contentApi.listScenarios).toHaveBeenCalledTimes(2)
  expect(contentApi.listScammerProfiles).toHaveBeenCalledTimes(2)
  expect(contentApi.listVictimProfiles).toHaveBeenCalledTimes(2)
  expect(refreshed.text()).not.toContain('重新加载可用内容（清除当前选择）')
})

it('recommends by scenario and age boundaries without changing selections', async () => {
  vi.mocked(contentApi.listScenarios).mockResolvedValue([
    { id: '1', code: 'TASK_REBATE', name: '青年场景', description: '简介', focus: '青年群体' },
    { id: '2', code: 'FAKE_INVESTMENT', name: '中年场景', description: '简介', focus: '中年群体' },
    { id: '3', code: 'IMPERSONATION_LOAN', name: '老年场景', description: '简介', focus: '老年群体' },
  ])
  vi.mocked(contentApi.listVictimProfiles).mockResolvedValue({ items: [34, 35, 59, 60].map(age =>
    ({ ...victim, id: String(age), age, name: `人物${age}` })), nextCursor: null, hasMore: false })
  const wrapper = mount(ConfigurationView)
  await flushPromises()
  const cards = wrapper.findAll('[data-testid=profile-list-victim] [data-testid=profile-card]')
  const scenarios = wrapper.findAll('[data-testid=scenario-option]')
  expect(wrapper.find('[data-testid=profile-recommended]').exists()).toBe(false)
  await cards[0]!.trigger('click')
  const expected = [[true, false, false, false], [false, true, true, false], [false, false, false, true]]
  for (const [index, option] of scenarios.entries()) {
    await option.trigger('click')
    expect(cards.map(card => card.find('[data-testid=profile-recommended]').exists())).toEqual(expected[index])
    expect(cards.filter(card => card.find('[data-testid=profile-recommended]').exists())
      .every(card => card.get(':scope > [data-testid=profile-recommended]').text() === '推荐')).toBe(true)
    expect(cards.map(card => card.attributes('aria-pressed'))).toEqual(['true', 'false', 'false', 'false'])
    expect(wrapper.find('[data-testid=profile-list-scammer] [data-testid=profile-recommended]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('根据当前场景的内容侧重，为匹配年龄的受害者标注推荐')
  }
  await scenarios[2]!.trigger('click')
  expect(wrapper.find('[data-testid=profile-recommended]').exists()).toBe(false)
  expect(cards[0]!.attributes('aria-pressed')).toBe('true')
})
