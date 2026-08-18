<template>
  <section class="map-layout" aria-labelledby="user-title">
    <aside class="route-panel">
      <p class="eyebrow">用户端</p>
      <h1 id="user-title">校园路线导览</h1>
      <p class="muted">身份认证已完成。地图和无障碍寻路将在后续 Stage 接入。</p>
      <div class="status-card">
        <span class="status-dot" />
        <div>
          <strong>登录状态正常</strong>
          <p>当前访问令牌由内存保存，刷新凭据使用 HttpOnly Cookie。</p>
        </div>
      </div>
    </aside>
    <div class="map-canvas" role="img" aria-label="地图功能待后续阶段接入的画布区域">
      <div class="campus-grid" />
      <div class="map-empty"><strong>地图画布</strong><span>Stage 2 接入高德底图</span></div>
    </div>
  </section>
</template>
