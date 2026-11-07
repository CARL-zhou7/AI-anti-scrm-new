import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { adminApi } from '../api/admin'
import { authApi } from '../api/auth'
import { ApiError, http } from '../api/http'
import { csrf } from '../api/csrf'
import router from '../router'
import AdminTrustPolicyView from '../views/AdminTrustPolicyView.vue'
import AdminScenariosView from '../views/AdminScenariosView.vue'
import AdminInterventionsView from '../views/AdminInterventionsView.vue'
import AdminSceneRulesView from '../views/AdminSceneRulesView.vue'
import AvatarUploader from '../components/AvatarUploader.vue'
import ProfileFormFields from '../components/ProfileFormFields.vue'
import AdminProfilesView from '../views/AdminProfilesView.vue'
import type { InterventionRecord, ProfileFormValues, SceneRuleView } from '../api/types'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

function waitingRecord(id: string, roundNo: number, content: string): InterventionRecord {
  return { interventionId: id, sessionId: '100', sourceSessionId: '100', roundNo, sceneCode: 'FAKE_INVESTMENT',
    originalContent: '原台词' + id, finalContent: content, interventionNote: '干预要求' + id,
    status: 'ADOPTED', createdAt: '', ruleId: null, previousContext: '骗子：先看流程\n受害者：需要付款吗' }
}

function ruleRow(overrides: Partial<SceneRuleView> = {}): SceneRuleView {
  return { ruleId: '42', scenarioCode: 'FAKE_INVESTMENT', title: '称呼与事实一致', ruleText: '始终沿用既定称谓',
    version: 1, status: 'ENABLED', sourceCount: 2, updatedAt: '', ...overrides }
}

it('separates legacy scammer and victim rules and filters both columns by the same scene', async () => {
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [
    ruleRow(),
    ruleRow({ ruleId: '43', targetRole: 'VICTIM', title: '拒绝真实入金', ruleText: '适用情况：重复拒绝同一笔入金\n行为指导：符合条件时接受并执行' }),
    ruleRow({ ruleId: '44', scenarioCode: 'TASK_REBATE', targetRole: 'VICTIM', title: '重复拒绝垫资' }),
  ] })
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  const left = wrapper.get('[data-testid=scene-rule-column-scammer]')
  const right = wrapper.get('[data-testid=scene-rule-column-victim]')
  expect(left.text()).toContain('称呼与事实一致')
  expect(left.text()).not.toContain('拒绝真实入金')
  expect(right.text()).toContain('符合条件时接受并执行')
  expect(right.find('[data-testid=rule-sources]').exists()).toBe(false)
  await wrapper.get('[data-testid=scene-rule-filter]').setValue('FAKE_INVESTMENT')
  expect(left.findAll('[data-testid=scene-rule-row]')).toHaveLength(1)
  expect(right.findAll('[data-testid=scene-rule-row]')).toHaveLength(1)
  expect(right.text()).not.toContain('重复拒绝垫资')
  await wrapper.get('[data-testid=scene-rule-filter]').setValue('TASK_REBATE')
  expect(left.findAll('[data-testid=scene-rule-row]')).toHaveLength(0)
  expect(right.text()).toContain('重复拒绝垫资')
  wrapper.unmount()
})

it('creates a concise victim rule directly without intervention sources and preserves edit and toggle', async () => {
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [] })
  const victim = ruleRow({ ruleId: '50', targetRole: 'VICTIM', scenarioCode: 'TASK_REBATE', sourceCount: 0, title: '接受同一笔投入',
    ruleText: '适用情况：重复拒绝同一笔投入\n行为指导：第三次符合条件时执行付款' })
  const create = vi.spyOn(adminApi, 'createRule').mockResolvedValue(victim)
  const update = vi.spyOn(adminApi, 'updateRule')
    .mockResolvedValueOnce({ ...victim, title: '接受既定投入', version: 2 })
    .mockResolvedValueOnce({ ...victim, title: '接受既定投入', status: 'DISABLED', version: 3 })
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=scene-rule-filter]').setValue('FAKE_INVESTMENT')
  await wrapper.get('[data-testid=victim-rule-add]').trigger('click')
  expect(wrapper.get<HTMLSelectElement>('[data-testid=victim-rule-scene]').element.value).toBe('FAKE_INVESTMENT')
  await wrapper.get('[data-testid=victim-rule-scene]').setValue('TASK_REBATE')
  await wrapper.get('[data-testid=victim-rule-title]').setValue('接受同一笔投入')
  await wrapper.get('[data-testid=victim-rule-condition]').setValue('重复拒绝同一笔投入')
  await wrapper.get('[data-testid=victim-rule-guidance]').setValue('第三次符合条件时执行付款')
  await wrapper.get('[data-testid=victim-rule-create]').trigger('submit')
  await flushPromises()
  expect(create).toHaveBeenCalledWith({ scenarioCode: 'TASK_REBATE', targetRole: 'VICTIM', title: '接受同一笔投入',
    ruleText: victim.ruleText, sourceInterventionIds: [] })
  expect(wrapper.get<HTMLSelectElement>('[data-testid=scene-rule-filter]').element.value).toBe('TASK_REBATE')
  expect(wrapper.get('[data-testid=scene-rule-column-victim]').text()).toContain('第三次符合条件时执行付款')
  await wrapper.get('[data-testid=rule-edit]').trigger('click')
  expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe(victim.ruleText)
  await wrapper.get('input:not([type=checkbox])').setValue('接受既定投入')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(update).toHaveBeenNthCalledWith(1, '50', { title: '接受既定投入', ruleText: victim.ruleText, status: 'ENABLED', version: 1 })
  await wrapper.get('[data-testid=rule-toggle]').trigger('click')
  await flushPromises()
  expect(update).toHaveBeenNthCalledWith(2, '50', { status: 'DISABLED', version: 2 })
  expect(wrapper.get('[data-testid=scene-rule-column-victim]').text()).toContain('已停用')
  wrapper.unmount()
})

it('creates a scammer rule directly without sources and preserves edit and toggle', async () => {
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [] })
  const victim = ruleRow({ ruleId: '50', targetRole: 'SCAMMER', scenarioCode: 'TASK_REBATE', sourceCount: 0, title: '接受同一笔投入',
    ruleText: '适用情况：重复拒绝同一笔投入\n行为指导：第三次符合条件时执行付款' })
  const create = vi.spyOn(adminApi, 'createRule').mockResolvedValue(victim)
  const update = vi.spyOn(adminApi, 'updateRule')
    .mockResolvedValueOnce({ ...victim, title: '接受既定投入', version: 2 })
    .mockResolvedValueOnce({ ...victim, title: '接受既定投入', status: 'DISABLED', version: 3 })
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=scene-rule-filter]').setValue('FAKE_INVESTMENT')
  await wrapper.get('[data-testid=scammer-rule-add]').trigger('click')
  expect(wrapper.get<HTMLSelectElement>('[data-testid=scammer-rule-scene]').element.value).toBe('FAKE_INVESTMENT')
  await wrapper.get('[data-testid=scammer-rule-scene]').setValue('TASK_REBATE')
  await wrapper.get('[data-testid=scammer-rule-title]').setValue('接受同一笔投入')
  await wrapper.get('[data-testid=scammer-rule-condition]').setValue('重复拒绝同一笔投入')
  await wrapper.get('[data-testid=scammer-rule-guidance]').setValue('第三次符合条件时执行付款')
  await wrapper.get('[data-testid=scammer-rule-create]').trigger('submit')
  await flushPromises()
  expect(create).toHaveBeenCalledWith({ scenarioCode: 'TASK_REBATE', targetRole: 'SCAMMER', title: '接受同一笔投入',
    ruleText: victim.ruleText, sourceInterventionIds: [] })
  expect(wrapper.get<HTMLSelectElement>('[data-testid=scene-rule-filter]').element.value).toBe('TASK_REBATE')
  expect(wrapper.get('[data-testid=scene-rule-column-scammer]').text()).toContain('第三次符合条件时执行付款')
  await wrapper.get('[data-testid=rule-edit]').trigger('click')
  expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe(victim.ruleText)
  await wrapper.get('input:not([type=checkbox])').setValue('接受既定投入')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(update).toHaveBeenNthCalledWith(1, '50', { title: '接受既定投入', ruleText: victim.ruleText, status: 'ENABLED', version: 1 })
  await wrapper.get('[data-testid=rule-toggle]').trigger('click')
  await flushPromises()
  expect(update).toHaveBeenNthCalledWith(2, '50', { status: 'DISABLED', version: 2 })
  expect(wrapper.get('[data-testid=scene-rule-column-scammer]').text()).toContain('已停用')
  wrapper.unmount()
})

it('keeps the manual victim rule draft when the server rejects creation', async () => {
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [] })
  vi.spyOn(adminApi, 'createRule').mockRejectedValue(new ApiError(409, 'RULE_TITLE_CONFLICT', '同一场景已有同名规则'))
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=victim-rule-add]').trigger('click')
  await wrapper.get('[data-testid=victim-rule-title]').setValue('已有规则')
  await wrapper.get('[data-testid=victim-rule-condition]').setValue('拒绝垫资')
  await wrapper.get('[data-testid=victim-rule-guidance]').setValue('第三次符合条件时配合')
  await wrapper.get('[data-testid=victim-rule-create]').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[data-testid=scene-rule-error]').text()).toContain('同名规则')
  expect(wrapper.get<HTMLInputElement>('[data-testid=victim-rule-title]').element.value).toBe('已有规则')
  expect(wrapper.find('[data-testid=victim-rule-create]').exists()).toBe(true)
  expect(wrapper.findAll('[data-testid=scene-rule-row]')).toHaveLength(0)
  wrapper.unmount()
})

it('induces one rule from the selected waiting records and only reports入库 after the server accepts it', async () => {
  const list = vi.spyOn(adminApi, 'interventions').mockResolvedValue([
    waitingRecord('1', 2, '最终台词一'), waitingRecord('2', 3, '最终台词二')])
  const induce = vi.spyOn(adminApi, 'induce').mockResolvedValue({
    scenarioCode: 'FAKE_INVESTMENT', title: '从2条干预归纳的场景规则', ruleText: '· 干预要求1\n· 干预要求2',
    sourceInterventionIds: ['1', '2'] })
  const create = vi.spyOn(adminApi, 'createRule').mockResolvedValue(ruleRow({ ruleId: '9', sourceCount: 2 }))
  const wrapper = mount(AdminInterventionsView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  expect(wrapper.findAll('[data-testid=intervention-row]')).toHaveLength(2)
  expect(wrapper.text()).toContain('原台词1')
  await wrapper.findAll('[data-testid=intervention-row]')[1]!.get('button').trigger('click')
  expect(wrapper.text()).toContain('最终台词二')
  expect(wrapper.text()).toContain('受害者：需要付款吗')
  await wrapper.findAll('[data-testid=intervention-select]')[0]!.setValue(true)
  await wrapper.findAll('[data-testid=intervention-select]')[1]!.setValue(true)
  await wrapper.get('[data-testid=intervention-induce]').trigger('click')
  await flushPromises()
  expect(induce).toHaveBeenCalledWith(['1', '2'])
  expect(wrapper.get<HTMLInputElement>('[data-testid=draft-title]').element.value).toBe('从2条干预归纳的场景规则')
  expect(wrapper.text()).not.toContain('已归入该规则')
  await wrapper.get('[data-testid=rule-draft]').trigger('submit')
  await flushPromises()
  expect(create).toHaveBeenCalledWith({ scenarioCode: 'FAKE_INVESTMENT', title: '从2条干预归纳的场景规则',
    ruleText: '· 干预要求1\n· 干预要求2', sourceInterventionIds: ['1', '2'] })
  expect(wrapper.get('[data-testid=rule-success]').text()).toContain('规则已提交')
  expect(list).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})

it('keeps the draft and never claims入库 when the merge is rejected', async () => {
  vi.spyOn(adminApi, 'interventions').mockResolvedValue([waitingRecord('1', 2, '最终台词一')])
  vi.spyOn(adminApi, 'induce').mockResolvedValue({ scenarioCode: 'FAKE_INVESTMENT', title: '规则草稿',
    ruleText: '正文', sourceInterventionIds: ['1'] })
  vi.spyOn(adminApi, 'createRule').mockRejectedValue(new ApiError(409, 'SOURCE_ALREADY_LINKED', '该来源已归入其他规则'))
  const wrapper = mount(AdminInterventionsView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=intervention-select]').setValue(true)
  await wrapper.get('[data-testid=intervention-induce]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid=rule-draft]').trigger('submit')
  await flushPromises()
  expect(wrapper.get('[data-testid=rule-error]').text()).toContain('该来源已归入其他规则')
  expect(wrapper.find('[data-testid=rule-success]').exists()).toBe(false)
  expect(wrapper.find('[data-testid=rule-draft]').exists()).toBe(true)
  wrapper.unmount()
})

it('lists the sources of one rule by ruleId and toggles its enabled state', async () => {
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [ruleRow()] })
  const sources = vi.spyOn(adminApi, 'ruleSources').mockResolvedValue([{
    ...waitingRecord('3', 4, '最终台词三'), status: 'LINKED', ruleId: '42' }])
  const update = vi.spyOn(adminApi, 'updateRule').mockResolvedValue(ruleRow({ status: 'DISABLED', version: 2 }))
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  expect(wrapper.findAll('[data-testid=scene-rule-row]')).toHaveLength(1)
  expect(wrapper.text()).toContain('已启用')
  await wrapper.get('[data-testid=rule-sources]').trigger('click')
  await flushPromises()
  expect(sources).toHaveBeenCalledWith('42')
  expect(wrapper.get('[data-testid=rule-source-row]').text()).toContain('原台词3')
  expect(wrapper.get('[data-testid=rule-source-row]').text()).toContain('最终台词三')
  await wrapper.get('[data-testid=rule-toggle]').trigger('click')
  await flushPromises()
  expect(update).toHaveBeenCalledWith('42', { status: 'DISABLED', version: 1 })
  expect(wrapper.text()).toContain('已停用')
  expect(wrapper.text()).toContain('进行中的场次不受影响')
  wrapper.unmount()
})

it('saves an edit with the loaded version and reports a version conflict without losing the text', async () => {
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [ruleRow()] })
  const update = vi.spyOn(adminApi, 'updateRule').mockRejectedValue(new ApiError(409, 'VERSION_CONFLICT', '冲突', [], 3))
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=rule-edit]').trigger('click')
  await wrapper.get('textarea').setValue('编辑后的正文')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(update).toHaveBeenCalledWith('42', { title: '称呼与事实一致', ruleText: '编辑后的正文', status: 'ENABLED', version: 1 })
  expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe('编辑后的正文')
  expect(wrapper.find('[data-testid=version-conflict-reload]').exists()).toBe(true)
  wrapper.unmount()
})

it('redirects an ordinary user away from the knowledge base pages', async () => {
  setActivePinia(createPinia())
  vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
  vi.spyOn(authApi, 'me').mockResolvedValue({ id: '7', username: 'user', role: 'USER' })
  await router.push('/admin/interventions')
  expect(router.currentRoute.value.path).toBe('/home')
  await router.push('/admin/scene-rules')
  expect(router.currentRoute.value.path).toBe('/home')
})

it('keeps scenario inputs across three tabs and makes story time read-only', async () => {
  const model = { name: '', age: '', gender: '', identity: '', description: '', contextPlace: '', currentState: '',
    avatarKey: '', speakingAvatarKey: '', fraudExperience: 'MEDIUM', speechSkill: 'MEDIUM', manipulationSkill: 'MEDIUM', behaviorTraits: '',
    antiFraudAwareness: 'MEDIUM', riskTendency: 'MEDIUM', personalityTraits: '', familyStatus: '', incomeDescription: '',
    monthlyExpenseYuan: '', initialSavingsYuan: '', availableFundsYuan: '', generalReserveFundsYuan: '', emergencyReserveFundsYuan: '', reservedFundsNote: '', generalDecisionTraits: '',
    scenarioProfiles: ['TASK_REBATE', 'FAKE_INVESTMENT', 'IMPERSONATION_LOAN'].map(scenarioCode => ({ scenarioCode, needs: '', relevantExperience: '', judgmentBasis: '', verificationHabits: '', actionBoundaries: '', stopConditions: '', recentEvents: '', consultationsTransactions: '', sharedExperiences: '', contactBackground: '', initialTrustMode: '' })),
  } as ProfileFormValues
  const wrapper = mount(ProfileFormFields, { props: { kind: 'victim', errors: [], disabled: false, modelValue: model } })
  expect(wrapper.findAll('[role=tab]')).toHaveLength(3)
  expect(wrapper.get('[data-testid=field-contextTime]').attributes('readonly')).toBeDefined()
  await wrapper.get('[data-testid="scenario-TASK_REBATE-needs"]').setValue('补充生活费')
  await wrapper.get('[data-testid=tab-FAKE_INVESTMENT]').trigger('click')
  await wrapper.get('[data-testid="scenario-FAKE_INVESTMENT-needs"]').setValue('管理积蓄')
  await wrapper.get('[data-testid=tab-TASK_REBATE]').trigger('click')
  expect(wrapper.get<HTMLTextAreaElement>('[data-testid="scenario-TASK_REBATE-needs"]').element.value).toBe('补充生活费')
  expect(wrapper.get('[data-testid=field-availableFundsYuan]').attributes('min')).toBe('0')
  wrapper.unmount()
})

it('blocks an incomplete third scenario and sends a complete write without story time', async () => {
  const scenarioProfiles = (['TASK_REBATE', 'FAKE_INVESTMENT', 'IMPERSONATION_LOAN'] as const).map(scenarioCode => ({ scenarioCode,
    needs: '需求', relevantExperience: '经历', judgmentBasis: '依据', verificationHabits: '核实', actionBoundaries: '边界', stopConditions: '停止',
    recentEvents: '昨天', consultationsTransactions: '咨询', sharedExperiences: '共同经历', contactBackground: '背景', initialTrustMode: '信任', initialTrustScore: 50, positiveFactor: 1, negativeFactor: 1, smallAmountCapYuan: 200 }))
  const row = { id: '1', name: '人物', age: 30, gender: '女', identity: '身份', description: '描述', contextTime: '昨天20:00', contextPlace: '房间', currentState: '查看消息',
    avatar: { key: 'a', url: '/a' }, speakingAvatar: { key: 'b', url: '/b' }, enabled: true, version: 0, createdAt: '时间', updatedAt: '时间',
    antiFraudAwareness: 'MEDIUM' as const, riskTendency: 'MEDIUM' as const, personalityTraits: '性格', familyStatus: '家庭', incomeDescription: '收入',
    monthlyExpenseYuan: 1000, initialSavingsYuan: 2000, availableFundsYuan: 0, reservedFundsYuan: 2000, generalReserveFundsYuan: 1500, emergencyReserveFundsYuan: 500, reservedFundsNote: '安排', generalDecisionTraits: '特点', profileSchemaVersion: 2, scenarioProfiles }
  vi.spyOn(adminApi, 'listProfiles').mockResolvedValue({ items: [row], nextCursor: null, hasMore: false })
  vi.spyOn(adminApi, 'getProfile').mockResolvedValue(row)
  const save = vi.spyOn(adminApi, 'updateProfile').mockResolvedValue({ ...row, version: 1 })
  const wrapper = mount(AdminProfilesView, { props: { kind: 'victim' }, global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=profile-edit]').trigger('click'); await flushPromises()
  await wrapper.get('[data-testid=avatar-preview]').trigger('load')
  await wrapper.get('[data-testid=speaking-avatar-preview]').trigger('load')
  await wrapper.get('[data-testid=tab-IMPERSONATION_LOAN]').trigger('click')
  await wrapper.get('[data-testid=scenario-IMPERSONATION_LOAN-needs]').setValue('')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(save).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('请完整填写此场景资料')
  await wrapper.get('[data-testid=scenario-IMPERSONATION_LOAN-needs]').setValue('帮助朋友')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(save).toHaveBeenCalledOnce()
  const sent = save.mock.calls[0]![2]
  expect(sent).not.toHaveProperty('contextTime')
  expect(sent).not.toHaveProperty('profileSchemaVersion')
  expect(sent).not.toHaveProperty('reservedFundsYuan')
  expect(sent).toMatchObject({ generalReserveFundsYuan: 1500, emergencyReserveFundsYuan: 500 })
  expect(sent).toMatchObject({ availableFundsYuan: 0, scenarioProfiles: expect.arrayContaining([expect.objectContaining({ needs: '帮助朋友' })]) })
  wrapper.unmount()
})

it('keeps unsaved scenario text on conflict until explicit reload', async () => {
  const row = { id: '1', code: 'TASK_REBATE' as const, name: '原名称', description: '简介', focus: '青年', version: 0, createdAt: '2026-10-17T00:00:00.000Z', updatedAt: '2026-10-17T00:00:00.000Z' }
  const list = vi.spyOn(adminApi, 'listScenarios').mockResolvedValue([row])
  const save = vi.spyOn(adminApi, 'updateScenario').mockRejectedValue(new ApiError(409, 'VERSION_CONFLICT', '冲突', [], 1))
  const wrapper = mount(AdminScenariosView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  await wrapper.get('[data-testid=scenario-edit]').trigger('click')
  await wrapper.get('#scenario-name').setValue('未保存名称')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
  expect(save).toHaveBeenCalledExactlyOnceWith('1', { name: '未保存名称', description: '简介', version: 0 })
  expect(wrapper.get<HTMLInputElement>('#scenario-name').element.value).toBe('未保存名称')
  expect(wrapper.get('[data-testid=scenario-save]').attributes('disabled')).toBeDefined()
  list.mockResolvedValue([{ ...row, name: '最新名称', version: 1 }])
  await wrapper.get('[data-testid=version-conflict-reload]').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('最新名称')
  expect(save).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})

it('requires upload and successful server preview before avatar is ready', async () => {
  vi.stubGlobal('URL', { createObjectURL: () => 'blob:preview', revokeObjectURL: vi.fn() })
  vi.spyOn(adminApi, 'uploadAvatar').mockResolvedValue({ key: 'hash', previewUrl: '/admin/avatar/hash', mediaType: 'image/png', sizeBytes: 3 })
  const wrapper = mount(AvatarUploader, { props: { avatarKey: '', previewUrl: '', disabled: false } })
  const input = wrapper.get('input')
  Object.defineProperty(input.element, 'files', { value: [new File(['png'], 'avatar.png', { type: 'image/png' })] })
  await input.trigger('change')
  await wrapper.get('img').trigger('load')
  expect(wrapper.emitted('ready')?.at(-1)).toEqual([false])
  await wrapper.get('[data-testid=avatar-upload]').trigger('click')
  await flushPromises()
  expect(wrapper.emitted('uploaded')).toHaveLength(1)
  await wrapper.setProps({ avatarKey: 'hash', previewUrl: '/admin/avatar/hash' })
  await wrapper.get('img').trigger('load')
  expect(wrapper.emitted('ready')?.at(-1)).toEqual([true])
  await wrapper.get('img').trigger('error')
  expect(wrapper.emitted('ready')?.at(-1)).toEqual([false])
  wrapper.unmount()
})

it('sends CSRF on PUT and multipart without replaying a failed write', async () => {
  vi.spyOn(csrf, 'ensure').mockResolvedValue('csrf-token')
  const refresh = vi.spyOn(csrf, 'refresh').mockResolvedValue('new-token')
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OK', data: {} }), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'CSRF_INVALID', message: '重试', data: {} }), { status: 403 }))
  vi.stubGlobal('fetch', fetchMock)
  await http.put('/admin/scenarios/1', { version: 0 })
  expect(fetchMock.mock.calls[0]![1]).toMatchObject({ method: 'PUT', headers: { 'X-CSRF-TOKEN': 'csrf-token', 'Content-Type': 'application/json' } })
  const body = new FormData()
  body.append('file', new File(['png'], 'avatar.png'))
  await expect(http.postForm('/admin/avatars', body)).rejects.toMatchObject({ code: 'CSRF_INVALID' })
  expect(fetchMock.mock.calls[1]![1]).toMatchObject({ method: 'POST', body, headers: { 'X-CSRF-TOKEN': 'csrf-token' } })
  expect(fetchMock.mock.calls[1]![1].headers).not.toHaveProperty('Content-Type')
  expect(refresh).toHaveBeenCalledTimes(1)
  expect(fetchMock).toHaveBeenCalledTimes(2)
})

it('opens file selection from upload and cancels the pending preview without uploading', async () => {
  const revoke = vi.fn()
  vi.stubGlobal('URL', { createObjectURL: () => 'blob:pending', revokeObjectURL: revoke })
  const upload = vi.spyOn(adminApi, 'uploadAvatar')
  const wrapper = mount(AvatarUploader, { props: { avatarKey: 'original', previewUrl: '/original.png', disabled: false } })
  const input = wrapper.get<HTMLInputElement>('input')
  const pick = vi.spyOn(input.element, 'click').mockImplementation(() => {})
  expect(input.attributes('hidden')).toBeDefined()
  await wrapper.get('[data-testid=avatar-upload]').trigger('click')
  expect(pick).toHaveBeenCalledTimes(1)
  Object.defineProperty(input.element, 'files', { value: [new File(['png'], 'new.png', { type: 'image/png' })] })
  await input.trigger('change')
  expect(wrapper.get('img').attributes('src')).toBe('blob:pending')
  expect(wrapper.get('[data-testid=avatar-upload]').text()).toBe('确认上传')
  await wrapper.get('[data-testid=avatar-cancel]').trigger('click')
  expect(wrapper.get('img').attributes('src')).toBe('/original.png')
  await wrapper.get('img').trigger('load')
  expect(wrapper.emitted('ready')?.at(-1)).toEqual([true])
  expect(wrapper.get('[data-testid=avatar-upload]').text()).toBe('上传头像')
  expect(revoke).toHaveBeenCalledWith('blob:pending')
  expect(upload).not.toHaveBeenCalled()
  expect(wrapper.emitted('uploaded')).toBeUndefined()
  wrapper.unmount()
})


it('validates increasing policy thresholds and submits the loaded version', async () => {
  vi.spyOn(adminApi, 'listProfiles').mockResolvedValue({ items: [], nextCursor: null, hasMore: false })
  const policy = { thresholdAccept: 60, thresholdSmall: 80, thresholdLarge: 90, version: 3, rulesetVersion: 'trust-v1' as const, updatedAt: '' }
  vi.spyOn(adminApi, 'getTrustPolicy').mockResolvedValue(policy)
  const save = vi.spyOn(adminApi, 'updateTrustPolicy').mockResolvedValue({ ...policy, thresholdSmall: 81, version: 4 })
  const wrapper = mount(AdminTrustPolicyView, { global: { stubs: { RouterLink: true } } }); await flushPromises()
  await wrapper.get('[data-testid=thresholdSmall]').setValue(59)
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(save).not.toHaveBeenCalled(); expect(wrapper.text()).toContain('严格递增')
  await wrapper.get('[data-testid=thresholdSmall]').setValue(81)
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(save).toHaveBeenCalledWith({ thresholdAccept: 60, thresholdSmall: 81, thresholdLarge: 90, version: 3 })
  expect(wrapper.text()).toContain('仅影响新场次'); wrapper.unmount()
})

it('estimates effective progress from live thresholds without promising a payment round', async () => {
  vi.spyOn(adminApi, 'getTrustPolicy').mockResolvedValue({ thresholdAccept: 40, thresholdSmall: 70, thresholdLarge: 85, version: 0, rulesetVersion: 'trust-v2', updatedAt: '' })
  const scenes = [
    { scenarioCode: 'TASK_REBATE', initialTrustScore: 50, positiveFactor: 1.2 },
    { scenarioCode: 'FAKE_INVESTMENT', initialTrustScore: 40, positiveFactor: 0.1 },
    { scenarioCode: 'IMPERSONATION_LOAN', initialTrustScore: 80, positiveFactor: 1 },
  ]
  vi.spyOn(adminApi, 'listProfiles').mockResolvedValue({ items: [{ id: '1', name: '人物', scenarioProfiles: scenes }] as import('../api/types').AdminVictimProfile[], nextCursor: null, hasMore: false })
  const wrapper = mount(AdminTrustPolicyView, { global: { stubs: { RouterLink: true } } }); await flushPromises()
  const rows = wrapper.findAll('[data-testid=progress-estimate]')
  expect(rows).toHaveLength(3)
  expect(rows[0]!.text()).toContain('5次')
  expect(rows[1]!.text()).toContain('取整后不加分')
  expect(rows[2]!.text()).toContain('初始分已达到小额门槛')
  await wrapper.get('[data-testid=thresholdSmall]').setValue(90)
  expect(wrapper.findAll('[data-testid=progress-estimate]')[0]!.text()).toContain('10次')
  expect(wrapper.text()).toContain('不是保证付款的轮次')
  wrapper.unmount()
})


it('shows saved explanations and invalidates them after editing the full rule', async () => {
  const text = '适用情况：亲友借款中已建立关系后。\n话术指导：保持身份和称谓一致，避免关系错乱。'
  vi.spyOn(adminApi, 'listRules').mockResolvedValue({ items: [ruleRow({ ruleText: text })] })
  const update = vi.spyOn(adminApi, 'updateRule').mockResolvedValue(ruleRow({ ruleText: '修改后的完整执行规则', version: 2 }))
  const wrapper = mount(AdminSceneRulesView, { global: { stubs: { RouterLink: true } } })
  await flushPromises()
  expect(wrapper.get('.rule-overview').text()).toContain('保持身份和称谓一致')
  expect(wrapper.get('details .admin-description').text()).toBe(text)
  await wrapper.get('[data-testid=rule-edit]').trigger('click')
  expect(wrapper.get<HTMLTextAreaElement>('textarea').element.value).toBe(text)
  await wrapper.get('textarea').setValue('修改后的完整执行规则')
  await wrapper.get('form').trigger('submit'); await flushPromises()
  expect(update).toHaveBeenCalledWith('42', expect.objectContaining({ ruleText: '修改后的完整执行规则', version: 1 }))
  expect(wrapper.get('.rule-overview').text()).toContain('未保存简短作用说明')
  expect(wrapper.get('.rule-overview').text()).not.toContain('保持身份和称谓一致')
  expect(wrapper.get('details .admin-description').text()).toBe('修改后的完整执行规则')
  wrapper.unmount()
})
