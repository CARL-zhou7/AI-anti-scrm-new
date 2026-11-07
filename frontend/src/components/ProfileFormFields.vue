<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FieldError, ProfileFormValues, ProfileKind } from '../api/types'
import FormFieldError from './FormFieldError.vue'
const props = defineProps<{ kind: ProfileKind; errors: FieldError[]; disabled: boolean }>()
const model = defineModel<ProfileFormValues>({ required: true })
const textFields = [
  { key: 'name', label: '姓名', multiline: false }, { key: 'gender', label: '性别', multiline: false },
  { key: 'identity', label: '身份', multiline: false }, { key: 'description', label: '人物描述', multiline: true },
  { key: 'contextPlace', label: '地点', multiline: false },
  { key: 'currentState', label: '当前状态', multiline: true },
] as const
const levels = computed(() => props.kind === 'scammer'
  ? [{ key: 'fraudExperience', label: '诈骗经验' }, { key: 'speechSkill', label: '话术能力' }, { key: 'manipulationSkill', label: '心理操控能力' }] as const
  : [{ key: 'antiFraudAwareness', label: '防骗意识' }, { key: 'riskTendency', label: '风险倾向' }] as const)
const activeScenario = ref('TASK_REBATE')
const scenarios = [{ code: 'TASK_REBATE', label: '刷单返利' }, { code: 'FAKE_INVESTMENT', label: '虚假投资' }, { code: 'IMPERSONATION_LOAN', label: '亲友借款' }]
const factText = [{ key: 'familyStatus', label: '家庭生活' }, { key: 'incomeDescription', label: '收入' }, { key: 'reservedFundsNote', label: '预留资金安排' }, { key: 'generalDecisionTraits', label: '一般决策特点' }] as const
const moneyFields = [{ key: 'monthlyExpenseYuan', label: '每月支出' }, { key: 'initialSavingsYuan', label: '初始存款' }, { key: 'availableFundsYuan', label: '可立即动用资金' }, { key: 'generalReserveFundsYuan', label: '普通预留' }, { key: 'emergencyReserveFundsYuan', label: '应急预留' }] as const
const scenarioFields = [{ key: 'needs', label: '场景需求' }, { key: 'relevantExperience', label: '相关经验' }, { key: 'judgmentBasis', label: '判断依据' }, { key: 'verificationHabits', label: '核实习惯' }, { key: 'actionBoundaries', label: '行动边界' }, { key: 'stopConditions', label: '停止条件' }, { key: 'recentEvents', label: '近期事件' }, { key: 'consultationsTransactions', label: '咨询或交易经历' }, { key: 'sharedExperiences', label: '共同经历' }, { key: 'contactBackground', label: '接触背景' }, { key: 'initialTrustMode', label: '初始信任方式' }] as const
watch(() => props.errors, errors => {
  const match = errors.find(e => e.field.startsWith('scenarioProfiles['))?.field.match(/scenarioProfiles\[(\d+)\]/)
  const code = match && model.value.scenarioProfiles[Number(match[1])]?.scenarioCode
  if (code) activeScenario.value = code
})
const trustFields = [{ key: 'initialTrustScore', label: '初始信任分', min: 0, max: 100, step: 1 }, { key: 'positiveFactor', label: '正向系数', min: 0.1, max: 3, step: 0.01 }, { key: 'negativeFactor', label: '负向系数', min: 0.1, max: 3, step: 0.01 }, { key: 'smallAmountCapYuan', label: '小额上限（元）', min: 0, max: 2147483647, step: 1 }] as const
const traits = computed(() => props.kind === 'scammer' ? 'behaviorTraits' : 'personalityTraits')
</script>

<template>
  <fieldset :disabled="disabled" class="admin-form-fields">
    <div><label for="profile-contextTime">故事时间</label><input id="profile-contextTime" value="昨天20:00" readonly data-testid="field-contextTime" /></div>
    <div v-for="field in textFields" :key="field.key">
      <label :for="'profile-' + field.key">{{ field.label }}</label>
      <textarea v-if="field.multiline" :id="'profile-' + field.key" v-model="model[field.key]" required rows="3" :data-testid="'field-' + field.key" :aria-describedby="field.key + '-error'" :aria-invalid="errors.some(e => e.field === field.key)" />
      <input v-else :id="'profile-' + field.key" v-model="model[field.key]" required :data-testid="'field-' + field.key" :aria-describedby="field.key + '-error'" :aria-invalid="errors.some(e => e.field === field.key)" />
      <FormFieldError :field="field.key" :errors="errors" />
    </div>
    <div><label for="profile-age">年龄</label><input id="profile-age" v-model.number="model.age" type="number" min="1" max="65535" step="1" required data-testid="field-age" aria-describedby="age-error" :aria-invalid="errors.some(e => e.field === 'age')" /><FormFieldError field="age" :errors="errors" /></div>
    <div v-for="field in levels" :key="field.key"><label :for="'profile-' + field.key">{{ field.label }}</label>
      <select :id="'profile-' + field.key" v-model="model[field.key]" required :data-testid="'field-' + field.key" :aria-describedby="field.key + '-error'"><option value="LOW">低</option><option value="MEDIUM">中</option><option value="HIGH">高</option></select><FormFieldError :field="field.key" :errors="errors" /></div>
    <div><label :for="'profile-' + traits">{{ kind === 'scammer' ? '行为特点' : '性格特点' }}</label><textarea :id="'profile-' + traits" v-model="model[traits]" required rows="3" :data-testid="'field-' + traits" :aria-describedby="traits + '-error'" /><FormFieldError :field="traits" :errors="errors" /></div>
    <template v-if="kind === 'victim'">
      <div v-for="field in factText" :key="field.key"><label :for="'profile-' + field.key">{{ field.label }}</label>
        <textarea :id="'profile-' + field.key" v-model="model[field.key]" rows="3" required :data-testid="'field-' + field.key" :aria-describedby="field.key + '-error'" :aria-invalid="errors.some(e => e.field === field.key)" />
        <FormFieldError :field="field.key" :errors="errors" /></div>
      <div v-for="field in moneyFields" :key="field.key"><label :for="'profile-' + field.key">{{ field.label }}（元）</label>
        <input :id="'profile-' + field.key" v-model.number="model[field.key]" type="number" min="0" max="2147483647" step="1" required :data-testid="'field-' + field.key" :aria-describedby="field.key + '-error'" :aria-invalid="errors.some(e => e.field === field.key)" />
        <FormFieldError :field="field.key" :errors="errors" /></div>
      <p class="field-help">预留合计：{{ Number(model.generalReserveFundsYuan) + Number(model.emergencyReserveFundsYuan) }} 元（自动相加）</p>
      <div class="scenario-tabs" role="tablist" aria-label="场景资料">
        <button v-for="s in scenarios" :id="'tab-' + s.code" :key="s.code" type="button" role="tab" :aria-selected="activeScenario === s.code" :aria-controls="'panel-' + s.code" :data-testid="'tab-' + s.code" @click="activeScenario = s.code">{{ s.label }}</button>
      </div>
      <FormFieldError field="scenarioProfiles" :errors="errors" />
      <section v-for="(profile, index) in model.scenarioProfiles" v-show="activeScenario === profile.scenarioCode" :id="'panel-' + profile.scenarioCode" :key="profile.scenarioCode" role="tabpanel" :aria-labelledby="'tab-' + profile.scenarioCode">
        <div v-for="field in scenarioFields" :key="field.key"><label :for="'scenario-' + profile.scenarioCode + '-' + field.key">{{ field.label }}</label>
          <textarea :id="'scenario-' + profile.scenarioCode + '-' + field.key" v-model="profile[field.key]" rows="3" :data-testid="'scenario-' + profile.scenarioCode + '-' + field.key" :aria-describedby="`scenarioProfiles[${index}].${field.key}-error`" :aria-invalid="errors.some(e => e.field === `scenarioProfiles[${index}].${field.key}`)" />
          <FormFieldError :field="`scenarioProfiles[${index}].${field.key}`" :errors="errors" /></div>
        <div v-for="field in trustFields" :key="field.key"><label :for="'scenario-' + profile.scenarioCode + '-' + field.key">{{ field.label }}</label>
          <input :id="'scenario-' + profile.scenarioCode + '-' + field.key" v-model.number="profile[field.key]" type="number" :min="field.min" :max="field.max" :step="field.step" required :data-testid="'scenario-' + profile.scenarioCode + '-' + field.key" :aria-describedby="`scenarioProfiles[${index}].${field.key}-error`" :aria-invalid="errors.some(e => e.field === `scenarioProfiles[${index}].${field.key}`)" />
          <FormFieldError :field="`scenarioProfiles[${index}].${field.key}`" :errors="errors" /></div>
      </section>
    </template>
  </fieldset>
</template>

<style scoped>
.scenario-tabs { display:flex; flex-wrap:wrap; gap:8px; margin:16px 0; }
.scenario-tabs button { padding:8px 12px; border:1px solid var(--border); border-radius:6px; background:white; color:inherit; cursor:pointer; }
.scenario-tabs [aria-selected=true] { border-color:var(--navy); background:#e6f0f7; }
</style>
