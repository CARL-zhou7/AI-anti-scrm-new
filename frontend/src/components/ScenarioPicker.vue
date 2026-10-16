<script setup lang="ts">
import type { Scenario } from '../api/types'
defineProps<{ scenarios: Scenario[]; selected: string | null; disabled: boolean }>()
defineEmits<{ select: [id: string] }>()
</script>

<template>
  <div class="scenario-options" data-testid="scenario-list">
    <button v-for="scenario in scenarios" :key="scenario.id" class="scenario-option" data-testid="scenario-option"
      :aria-pressed="selected === scenario.id" :disabled="disabled" @click="$emit('select', scenario.id)">
      <span class="quiet-label">内容侧重：{{ scenario.focus }}</span>
      <h3>{{ scenario.name }}</h3><p>{{ scenario.description }}</p>
      <span class="selection-label">{{ selected === scenario.id ? '已选择 ✓ · 再次点击取消' : '选择此场景' }}</span>
    </button>
  </div>
</template>
