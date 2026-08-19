<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppIcon from '../components/AppIcon.vue';
import type { AppIconName } from '../services/app-icons';
import { useAuthStore } from '../stores/auth';
import { useTheme } from '../services/theme';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const { theme, toggleTheme } = useTheme();
const mobileMenuOpen = ref(false);
const roleLabel = computed(() => (auth.role === 'ADMIN' ? '管理员' : '校园用户'));
const allNavigation: Array<{ to: string; label: string; icon: AppIconName; admin?: boolean }> = [
  { to: '/user', label: '路线规划', icon: 'route' },
  { to: '/user/services', label: '用户服务', icon: 'services' },
  { to: '/user/assistant', label: '智能路线助手', icon: 'assistant' },
  { to: '/admin', label: '地图数据', icon: 'map', admin: true },
  { to: '/admin/governance', label: '治理工作台', icon: 'governance', admin: true },
  { to: '/admin/analytics', label: '治理洞察', icon: 'analytics', admin: true },
];
const navigation = computed<
  Array<{ to: string; label: string; icon: AppIconName; admin?: boolean }>
>(() => allNavigation.filter((item) => !item.admin || auth.role === 'ADMIN'));

watch(
  () => route.fullPath,
  () => (mobileMenuOpen.value = false),
);

async function signOut(): Promise<void> {
  await auth.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="wordmark" :to="auth.role === 'ADMIN' ? '/admin' : '/user'">
        <span class="wordmark-symbol"><AppIcon name="route" :size="18" /></span>
        <span>无碍智行</span>
      </RouterLink>
      <nav class="desktop-navigation" aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.to" :to="item.to">
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="account">
        <span>{{ auth.username }} · {{ roleLabel }}</span>
        <button
          class="icon-button"
          type="button"
          :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          :title="theme === 'dark' ? '浅色模式' : '深色模式'"
          @click="toggleTheme"
        >
          <AppIcon :name="theme === 'dark' ? 'sun' : 'moon'" />
        </button>
        <el-button text @click="signOut"> 退出登录 </el-button>
        <button
          class="icon-button mobile-menu-button"
          type="button"
          :aria-label="mobileMenuOpen ? '关闭主导航' : '打开主导航'"
          :aria-expanded="mobileMenuOpen"
          aria-controls="mobile-navigation"
          @click="mobileMenuOpen = !mobileMenuOpen"
        >
          <AppIcon :name="mobileMenuOpen ? 'close' : 'menu'" />
        </button>
      </div>
    </header>
    <nav
      v-if="mobileMenuOpen"
      id="mobile-navigation"
      class="mobile-navigation"
      aria-label="移动端主导航"
    >
      <RouterLink v-for="item in navigation" :key="item.to" :to="item.to">
        <AppIcon :name="item.icon" />
        <span>{{ item.label }}</span>
      </RouterLink>
      <button class="mobile-signout" type="button" @click="signOut">
        <AppIcon name="close" />
        <span>退出登录</span>
      </button>
    </nav>
    <main class="workspace">
      <RouterView />
    </main>
  </div>
</template>
