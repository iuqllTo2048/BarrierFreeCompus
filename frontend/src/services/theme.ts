import { readonly, ref } from 'vue';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'barrier-free-campus-theme';
const theme = ref<ThemeMode>('light');
let initialized = false;

export function resolveTheme(stored: string | null, prefersDark: boolean): ThemeMode {
  if (stored === 'light' || stored === 'dark') return stored;
  return prefersDark ? 'dark' : 'light';
}

function preferredTheme(): ThemeMode {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function applyTheme(value: ThemeMode): void {
  theme.value = value;
  document.documentElement.dataset.theme = value;
  document.documentElement.style.colorScheme = value;
  window.dispatchEvent(new CustomEvent<ThemeMode>('theme-change', { detail: value }));
}

export function initializeTheme(): void {
  if (initialized) return;
  const stored = window.localStorage.getItem(STORAGE_KEY);
  applyTheme(resolveTheme(stored, preferredTheme() === 'dark'));
  initialized = true;
}

export function useTheme() {
  initializeTheme();

  function setTheme(value: ThemeMode): void {
    window.localStorage.setItem(STORAGE_KEY, value);
    applyTheme(value);
  }

  function toggleTheme(): void {
    setTheme(theme.value === 'dark' ? 'light' : 'dark');
  }

  return { theme: readonly(theme), setTheme, toggleTheme };
}
