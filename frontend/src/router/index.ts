import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import type { UserRole } from '../types/auth';

const LoginView = () => import('../views/LoginView.vue');
const UserHomeView = () => import('../views/UserHomeView.vue');
const AdminDashboardView = () => import('../views/AdminDashboardView.vue');
const UserServicesView = () => import('../views/UserServicesView.vue');
const AdminGovernanceView = () => import('../views/AdminGovernanceView.vue');
const AppShell = () => import('../layouts/AppShell.vue');

declare module 'vue-router' {
  interface RouteMeta {
    guestOnly?: boolean;
    roles?: UserRole[];
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', redirect: '/user' },
        {
          path: 'user',
          name: 'user-home',
          component: UserHomeView,
          meta: { roles: ['USER', 'ADMIN'] },
        },
        {
          path: 'user/services',
          name: 'user-services',
          component: UserServicesView,
          meta: { roles: ['USER', 'ADMIN'] },
        },
        {
          path: 'admin',
          name: 'admin-dashboard',
          component: AdminDashboardView,
          meta: { roles: ['ADMIN'] },
        },
        {
          path: 'admin/governance',
          name: 'admin-governance',
          component: AdminGovernanceView,
          meta: { roles: ['ADMIN'] },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

function defaultPath(role: UserRole | null): string {
  return role === 'ADMIN' ? '/admin' : '/user';
}

router.beforeEach(async (to: RouteLocationNormalized) => {
  const auth = useAuthStore();
  await auth.restore();
  if (to.meta.guestOnly && auth.isAuthenticated) return defaultPath(auth.role);
  if (to.meta.roles) {
    if (!auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } };
    if (!auth.role || !to.meta.roles.includes(auth.role)) return defaultPath(auth.role);
  }
  return true;
});

export default router;
