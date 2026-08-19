<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { readApiMessage } from '../services/http';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const form = reactive({ username: 'demo_user', password: 'Demo@12345' });
const loading = ref(false);
const errorMessage = ref('');

async function submit(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    await auth.login(form);
    const requested = typeof route.query.redirect === 'string' ? route.query.redirect : null;
    await router.replace(requested ?? (auth.role === 'ADMIN' ? '/admin' : '/user'));
  } catch (error: unknown) {
    errorMessage.value = readApiMessage(error, '暂时无法登录，请稍后重试');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-context" aria-labelledby="welcome-title">
      <p class="eyebrow">BarrierFreeCampus</p>
      <h1 id="welcome-title">让校园里的每一段路，都更有把握</h1>
      <p class="lead">用真实路网和风险信息，帮助每个人理解路线，而不只是得到一条线。</p>
      <div class="route-legend" aria-label="路线类型图例">
        <span><i class="route-line shortest" />最短路线</span>
        <span><i class="route-line accessible" />无障碍路线</span>
        <span><i class="route-line balanced" />综合路线</span>
      </div>
    </section>

    <section class="login-panel" aria-labelledby="login-title">
      <p class="brand">无碍智行</p>
      <h2 id="login-title">登录校园导览</h2>
      <p class="muted">登录后根据身份进入校园路线服务或数据治理工作台。</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" aria-label="用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            aria-label="密码"
          />
        </el-form-item>
        <el-button native-type="submit" type="primary" :loading="loading"> 登录 </el-button>
      </el-form>
      <p v-if="errorMessage" class="form-error" role="alert">
        {{ errorMessage }}
      </p>
      <p class="demo-hint">用户：demo_user / Demo@12345</p>
      <p class="demo-hint">管理员：demo_admin / Admin@12345</p>
    </section>
  </main>
</template>
