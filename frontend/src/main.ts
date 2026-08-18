import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import './style.css';

const app = createApp(App);
window.addEventListener('auth-expired', () => {
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({
      name: 'login',
      query: { redirect: router.currentRoute.value.fullPath },
    });
  }
});

app.use(createPinia());
app.use(router);
app.mount('#app');
