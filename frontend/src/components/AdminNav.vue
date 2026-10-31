<template>
  <nav class="admin-nav" aria-label="后台导航">
    <RouterLink to="/admin" exact-active-class="admin-current">后台首页</RouterLink>
    <RouterLink to="/admin/scenarios" exact-active-class="admin-current">场景管理</RouterLink>
    <RouterLink to="/admin/scammers" exact-active-class="admin-current">骗子画像管理</RouterLink>
    <RouterLink to="/admin/victims" exact-active-class="admin-current">受害者画像管理</RouterLink>
    <RouterLink to="/admin/trust-policy" exact-active-class="admin-current">信任分机制</RouterLink>
    <RouterLink to="/admin/demo" exact-active-class="admin-current" data-testid="admin-nav-demo">演示测试</RouterLink>
    <RouterLink to="/admin/interventions" exact-active-class="admin-current" data-testid="admin-nav-interventions">待入库记录</RouterLink>
    <RouterLink to="/admin/scene-rules" exact-active-class="admin-current" data-testid="admin-nav-scene-rules">场景规则</RouterLink>
  </nav>
</template>
