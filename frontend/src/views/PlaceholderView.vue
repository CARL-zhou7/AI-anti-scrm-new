<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
defineProps<{ title: string }>()
const auth = useAuthStore()
</script>

<template><section class="placeholder-card" data-testid="placeholder-view"><span class="eyebrow">后续阶段</span><h1>{{ title }}</h1><p>该模块将在后续阶段实现。</p><RouterLink v-if="auth.account?.role === 'USER'" to="/home" class="button button-primary">返回首页</RouterLink><p v-else>管理员账号已登录，可通过右上角退出登录。</p></section></template>
