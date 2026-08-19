<script setup lang="ts">
import { init, use, type ECharts, type EChartsCoreOption as EChartsOption } from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import {
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    option: EChartsOption;
    description: string;
    height?: number;
  }>(),
  { height: 300 },
);

use([
  BarChart,
  LineChart,
  AriaComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

const emit = defineEmits<{ itemSelect: [index: number] }>();
const element = ref<HTMLElement | null>(null);
let chart: ECharts | null = null;
let observer: ResizeObserver | null = null;

function render(): void {
  if (!chart) return;
  chart.setOption(props.option, { notMerge: true });
}

onMounted(async () => {
  await nextTick();
  if (!element.value) return;
  chart = init(element.value, undefined, { renderer: 'canvas' });
  chart.on('click', (params) => {
    const index = Number(params.dataIndex);
    if (Number.isInteger(index)) emit('itemSelect', index);
  });
  render();
  observer = new ResizeObserver(() => chart?.resize());
  observer.observe(element.value);
});

watch(() => props.option, render, { deep: true });
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>

<template>
  <div
    ref="element"
    class="echart-panel"
    :style="{ height: `${height}px` }"
    role="img"
    :aria-label="description"
  />
</template>
