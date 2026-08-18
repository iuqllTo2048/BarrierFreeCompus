<template>
  <section class="admin-layout" aria-labelledby="admin-title">
    <div class="admin-heading">
      <div>
        <p class="eyebrow">管理端</p>
        <h1 id="admin-title">校园无障碍治理台</h1>
      </div>
      <span class="permission-badge">ADMIN 权限已验证</span>
    </div>
    <div class="admin-canvas">
      <div class="map-canvas compact" role="img" aria-label="管理地图画布待后续阶段接入">
        <div class="campus-grid" />
        <div class="map-empty">
          <strong>治理地图</strong><span>数据与审核联动将在后续 Stage 建设</span>
        </div>
      </div>
      <aside class="inspector">
        <h2>数据检查器</h2>
        <p class="muted">当前仅展示 Stage 1 权限壳层，不伪造设施或事件数据。</p>
        <div class="status-card">
          <span class="status-dot" />
          <div>
            <strong>管理员会话有效</strong>
            <p>角色路由与后端接口均执行 ADMIN 校验。</p>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>
