<template>
  <nav class="admin-nav" aria-label="后台导航">
    <RouterLink to="/admin" exact-active-class="admin-current">后台首页</RouterLink>
    <RouterLink to="/admin/scenarios" exact-active-class="admin-current">场景管理</RouterLink>
    <RouterLink to="/admin/scammers" exact-active-class="admin-current">骗子画像管理</RouterLink>
    <RouterLink to="/admin/victims" exact-active-class="admin-current">受害者画像管理</RouterLink>
  </nav>
</template>
