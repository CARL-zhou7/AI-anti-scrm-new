<script setup lang="ts">
import { computed } from 'vue'
import type { Profile, Level, Scenario } from '../api/types'
import { contentApi } from '../api/content'
const props = defineProps<{ profile: Profile; selectedScenarioCode?: Scenario['code'] }>()
const selectedProfile = computed(() => 'scenarioProfiles' in props.profile
  ? props.profile.scenarioProfiles?.find(p => p.scenarioCode === props.selectedScenarioCode) : undefined)
const factFields = [{ key: 'familyStatus', label: '家庭生活', money: false }, { key: 'incomeDescription', label: '收入', money: false }, { key: 'monthlyExpenseYuan', label: '每月支出', money: true }, { key: 'initialSavingsYuan', label: '初始存款', money: true }, { key: 'availableFundsYuan', label: '可立即动用资金', money: true }, { key: 'reservedFundsYuan', label: '预留资金', money: true }, { key: 'reservedFundsNote', label: '预留资金安排', money: false }, { key: 'generalDecisionTraits', label: '一般决策特点', money: false }] as const
const scenarioFields = [{ key: 'needs', label: '场景需求' }, { key: 'relevantExperience', label: '相关经验' }, { key: 'judgmentBasis', label: '判断依据' }, { key: 'verificationHabits', label: '核实习惯' }, { key: 'actionBoundaries', label: '行动边界' }, { key: 'stopConditions', label: '停止条件' }, { key: 'recentEvents', label: '近期事件' }, { key: 'consultationsTransactions', label: '咨询或交易经历' }, { key: 'sharedExperiences', label: '共同经历' }, { key: 'contactBackground', label: '接触背景' }, { key: 'initialTrustMode', label: '初始信任方式' }] as const
defineEmits<{ avatarError: [key: string] }>()
const levels: Record<Level, string> = { LOW: '低', MEDIUM: '中', HIGH: '高' }
</script>

<template>
  <article class="profile-detail" data-testid="profile-detail">
    <img :key="profile.avatar.key" class="detail-avatar" :src="contentApi.avatarUrl(profile.avatar)" :alt="profile.name + '的头像'"
      @error="$emit('avatarError', profile.avatar.key)" />
    <h3>{{ profile.name }} <small>{{ profile.age }}岁 · {{ profile.gender }}</small></h3>
    <dl>
      <dt>身份</dt><dd>{{ profile.identity }}</dd>
      <dt>人物描述</dt><dd>{{ profile.description }}</dd>
      <template v-if="'fraudExperience' in profile">
        <dt>诈骗经验</dt><dd>{{ levels[profile.fraudExperience] }}</dd>
        <dt>话术能力</dt><dd>{{ levels[profile.speechSkill] }}</dd>
        <dt>心理操控能力</dt><dd>{{ levels[profile.manipulationSkill] }}</dd>
        <dt>行为特点</dt><dd>{{ profile.behaviorTraits }}</dd>
      </template>
      <template v-else>
        <dt>防骗意识</dt><dd>{{ levels[profile.antiFraudAwareness] }}</dd>
        <dt>风险倾向</dt><dd>{{ levels[profile.riskTendency] }}</dd>
        <dt>性格特点</dt><dd>{{ profile.personalityTraits }}</dd>
        <template v-for="field in factFields" :key="field.key"><dt>{{ field.label }}</dt>
          <dd>{{ profile.profileSchemaVersion === 2 && profile[field.key] != null ? `${profile[field.key]}${field.money ? '元' : ''}` : '未记录' }}</dd></template>
        <template v-if="selectedProfile"><template v-for="field in scenarioFields" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ selectedProfile[field.key] }}</dd></template></template>
      </template>
      <dt>时间</dt><dd>{{ profile.contextTime }}</dd>
      <dt>地点</dt><dd>{{ profile.contextPlace }}</dd>
      <dt>当前状态</dt><dd>{{ profile.currentState }}</dd>
    </dl>
  </article>
</template>
