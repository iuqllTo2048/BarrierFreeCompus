<script setup lang="ts">
import AppIcon from './AppIcon.vue';
import type { AppIconName } from '../services/app-icons';

withDefaults(
  defineProps<{
    icon?: AppIconName;
    title: string;
    description: string;
    tone?: 'default' | 'warning' | 'danger';
    compact?: boolean;
  }>(),
  { icon: 'route', tone: 'default', compact: false },
);
</script>

<template>
  <section
    class="ui-state-panel"
    :class="[`tone-${tone}`, { compact }]"
    :role="tone === 'danger' ? 'alert' : 'status'"
  >
    <span class="ui-state-panel__icon"><AppIcon :name="icon" :size="22" /></span>
    <div>
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
      <slot />
    </div>
  </section>
</template>
