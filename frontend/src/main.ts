import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { useAuthStore } from './stores/auth';
import { initializeTheme } from './services/theme';
import './style.css';

initializeTheme();
const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
window.addEventListener('auth-expired', () => {
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({
      name: 'login',
      query: { redirect: router.currentRoute.value.fullPath },
    });
  }
});

// 先恢复会话，再安装路由并挂载，保证根路径按角色正确跳转（管理员 → /admin，用户 → /user）
void useAuthStore(pinia)
  .restore()
  .finally(() => {
    app.use(router);
    app.mount('#app');
  });
