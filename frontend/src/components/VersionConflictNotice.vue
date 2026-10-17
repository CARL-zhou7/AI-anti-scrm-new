<script setup lang="ts">
defineProps<{ currentVersion: number | null; disabled: boolean }>()
defineEmits<{ reload: [] }>()
</script>

<template>
  <div class="error-message" role="alert" data-testid="version-conflict">
    <p>内容已被修改，请重新读取<span v-if="currentVersion !== null">（当前版本 {{ currentVersion }}）</span>。重新读取将替换表单中的未保存内容。</p>
    <button type="button" class="button button-light" :disabled="disabled" data-testid="version-conflict-reload" @click="$emit('reload')">重新读取</button>
  </div>
</template>
