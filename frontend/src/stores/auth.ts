import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as authApi from '../services/auth-api';
import { clearSession, setSession } from '../services/session';
import type { LoginPayload, UserRole } from '../types/auth';

export const useAuthStore = defineStore('auth', () => {
  const username = ref<string | null>(null);
  const role = ref<UserRole | null>(null);
  const restored = ref(false);
  const isAuthenticated = computed(() => username.value !== null && role.value !== null);

  function applySession(session: Awaited<ReturnType<typeof authApi.login>>): void {
    setSession(session);
    username.value = session.username;
    role.value = session.role;
  }

  async function login(payload: LoginPayload): Promise<void> {
    applySession(await authApi.login(payload));
    restored.value = true;
  }

  async function restore(): Promise<void> {
    if (restored.value) return;
    try {
      applySession(await authApi.refresh());
    } catch {
      clearLocalSession();
    } finally {
      restored.value = true;
    }
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout();
    } finally {
      clearLocalSession();
    }
  }

  function clearLocalSession(): void {
    clearSession();
    username.value = null;
    role.value = null;
  }

  window.addEventListener('auth-expired', clearLocalSession);

  return { username, role, restored, isAuthenticated, login, restore, logout, clearLocalSession };
});
