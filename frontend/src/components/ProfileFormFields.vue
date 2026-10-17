<script setup lang="ts">
import { computed } from 'vue'
import type { FieldError, ProfileFormValues, ProfileKind } from '../api/types'
import FormFieldError from './FormFieldError.vue'
const props = defineProps<{ kind: ProfileKind; errors: FieldError[]; disabled: boolean }>()
const model = defineModel<ProfileFormValues>({ required: true })
const textFields = [
  { key: 'name', label: '姓名', multiline: false }, { key: 'gender', label: '性别', multiline: false },
  { key: 'identity', label: '身份', multiline: false }, { key: 'description', label: '人物描述', multiline: true },
  { key: 'contextTime', label: '时间', multiline: false }, { key: 'contextPlace', label: '地点', multiline: false },
  { key: 'currentState', label: '当前状态', multiline: true },
] as const
const levels = computed(() => props.kind === 'scammer'
  ? [{ key: 'fraudExperience', label: '诈骗经验' }, { key: 'speechSkill', label: '话术能力' }, { key: 'manipulationSkill', label: '心理操控能力' }] as const
  : [{ key: 'antiFraudAwareness', label: '防骗意识' }, { key: 'riskTendency', label: '风险倾向' }] as const)
const traits = computed(() => props.kind === 'scammer' ? 'behaviorTraits' : 'personalityTraits')
</script>

<template>
  <fieldset :disabled="disabled" class="admin-form-fields">
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
  </fieldset>
</template>
