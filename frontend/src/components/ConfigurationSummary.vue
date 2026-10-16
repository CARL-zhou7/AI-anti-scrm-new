<script setup lang="ts">
import type { Scenario, ScammerProfilePayload, VictimProfilePayload } from '../api/types'
import ProfileDetail from './ProfileDetail.vue'
defineProps<{ scenario?: Scenario; scammer?: ScammerProfilePayload; victim?: VictimProfilePayload }>()
defineEmits<{ avatarError: [key: string] }>()
</script>

<template>
  <section class="configuration-section" data-testid="configuration-summary">
    <h2>03 确认完整配置</h2>
    <template v-if="scenario"><h3>{{ scenario.name }}</h3><p>{{ scenario.description }}</p><p>内容侧重：{{ scenario.focus }}</p></template>
    <p v-else class="field-help">尚未选择场景</p>
    <div class="profile-columns">
      <div><h3>骗子画像</h3><ProfileDetail v-if="scammer" :profile="scammer" @avatar-error="$emit('avatarError', $event)" /><p v-else>尚未选择</p></div>
      <div><h3>受害者画像</h3><ProfileDetail v-if="victim" :profile="victim" @avatar-error="$emit('avatarError', $event)" /><p v-else>尚未选择</p></div>
    </div>
  </section>
</template>
