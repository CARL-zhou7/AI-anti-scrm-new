<script setup lang="ts">
import { contentApi } from '../api/content'
import type { Profile } from '../api/types'
defineProps<{ profile: Profile; selected: boolean; disabled: boolean; failed: boolean; recommended?: boolean }>()
defineEmits<{ select: []; avatarError: [key: string]; avatarLoaded: [key: string] }>()
</script>

<template>
  <button class="profile-card" data-testid="profile-card" :aria-pressed="selected" :disabled="disabled || (failed && !selected)" @click="$emit('select')">
    <img :key="profile.avatar.key" data-testid="profile-avatar" :src="contentApi.avatarUrl(profile.avatar)" :alt="profile.name + '的头像'"
      @error="$emit('avatarError', profile.avatar.key)" @load="$emit('avatarLoaded', profile.avatar.key)" />
    <span class="profile-card-copy"><strong>{{ profile.name }}</strong><span class="profile-identity">{{ profile.identity }}</span>
      <span v-if="failed" class="field-error" data-testid="profile-avatar-error">头像加载失败，请重新加载页面</span>
      <span v-if="selected || !failed" class="selection-label">{{ selected ? '已选择 ✓ · 再次点击取消' : '查看并选择' }}</span>
    </span>
    <span v-if="recommended" class="recommendation-label" data-testid="profile-recommended">推荐</span>
  </button>
</template>
