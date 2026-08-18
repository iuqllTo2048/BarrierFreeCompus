<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();
const roleLabel = computed(() => (auth.role === 'ADMIN' ? '管理员' : '校园用户'));

async function signOut(): Promise<void> {
  await auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="wordmark" :to="auth.role === 'ADMIN' ? '/admin' : '/user'">
        无碍智行
      </RouterLink>
      <nav aria-label="主导航">
        <RouterLink to="/user"> 用户端 </RouterLink>
        <RouterLink v-if="auth.role === 'ADMIN'" to="/admin"> 管理端 </RouterLink>
      </nav>
      <div class="account">
        <span>{{ auth.username }} · {{ roleLabel }}</span>
        <el-button text @click="signOut"> 退出登录 </el-button>
      </div>
    </header>
    <main class="workspace">
      <RouterView />
    </main>
  </div>
</template>
