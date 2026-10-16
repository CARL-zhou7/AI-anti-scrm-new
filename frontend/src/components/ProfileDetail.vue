<script setup lang="ts">
import type { Profile, Level } from '../api/types'
import { contentApi } from '../api/content'
defineProps<{ profile: Profile }>()
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
      </template>
      <dt>时间</dt><dd>{{ profile.contextTime }}</dd>
      <dt>地点</dt><dd>{{ profile.contextPlace }}</dd>
      <dt>当前状态</dt><dd>{{ profile.currentState }}</dd>
    </dl>
  </article>
</template>
