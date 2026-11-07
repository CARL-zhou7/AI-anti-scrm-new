<template>
  <div class="home-layout admin-layout">
    <header class="admin-banner admin-row-heading">
      <div>
        <span class="eyebrow">反诈内容与演绎管理</span>
        <h1>演绎工作台</h1>
        <p>维护角色与场景，验证逐轮对话，把有效干预沉淀为规则。</p>
      </div>
      <RouterLink class="button button-primary" to="/admin/demo">开始演示测试</RouterLink>
    </header>
    <div class="admin-entries">
      <section class="admin-panel admin-group">
        <h2>内容配置</h2>
        <RouterLink to="/admin/scenarios"><strong>场景管理</strong><span>维护三个固定场景的名称与简介。</span></RouterLink>
        <RouterLink to="/admin/scammers"><strong>骗子画像管理</strong><span>维护身份、行为参数与头像。</span></RouterLink>
        <RouterLink to="/admin/victims"><strong>受害者画像管理</strong><span>维护身份、性格和各场景的信任参数。</span></RouterLink>
        <RouterLink to="/admin/trust-policy"><strong>信任分机制</strong><span>维护三项行动阈值。</span></RouterLink>
      </section>
      <section class="admin-panel admin-group">
        <h2>演示与规则</h2>
        <RouterLink to="/admin/demo"><strong>演示测试</strong><span>运行管理员场次，逐轮审阅和修改骗子台词。</span></RouterLink>
        <RouterLink to="/admin/interventions"><strong>待入库记录</strong><span>从已采用的干预中归纳场景规则。</span></RouterLink>
        <RouterLink to="/admin/scene-rules"><strong>场景规则</strong><span>编辑、启停规则并查看来源。</span></RouterLink>
      </section>
    </div>
  </div>
</template>
