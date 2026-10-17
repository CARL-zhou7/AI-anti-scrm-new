<script setup lang="ts">
import AdminNav from '../components/AdminNav.vue'
</script>

<template>
  <div class="home-layout admin-layout">
    <AdminNav /><span class="eyebrow">基础内容维护</span><h1>管理后台</h1><p class="admin-intro">维护场景文案与人物画像，让每次情景学习都有完整、可用的内容。</p>
    <div class="admin-entries">
      <RouterLink class="admin-panel" to="/admin/scenarios"><span class="eyebrow">01 · 场景</span><h2>场景管理</h2><p>查看三个固定场景，编辑名称与简介。</p><span>进入管理 →</span></RouterLink>
      <RouterLink class="admin-panel" to="/admin/scammers"><span class="eyebrow">02 · 骗子画像</span><h2>骗子画像管理</h2><p>维护人物信息、行为参数与头像，管理可用状态。</p><span>进入管理 →</span></RouterLink>
      <RouterLink class="admin-panel" to="/admin/victims"><span class="eyebrow">03 · 受害者画像</span><h2>受害者画像管理</h2><p>维护人物信息、性格与风险倾向，管理可用状态。</p><span>进入管理 →</span></RouterLink>
    </div>
  </div>
</template>
