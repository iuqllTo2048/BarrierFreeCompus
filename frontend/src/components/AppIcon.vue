<script setup lang="ts">
import { computed } from 'vue';
import { appIconPaths, type AppIconName } from '../services/app-icons';

const props = withDefaults(defineProps<{ name: AppIconName; size?: number }>(), { size: 20 });

const iconPaths = computed(() => appIconPaths[props.name]);
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="path in iconPaths" :key="path" :d="path" />
  </svg>
</template>
