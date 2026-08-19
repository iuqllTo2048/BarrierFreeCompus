import type { EChartsCoreOption as EChartsOption } from 'echarts/core';
import type {
  BarrierTrend,
  BuildingScore,
  ConfidenceDistribution,
  DistributionItem,
  RouteRisk,
} from '../types/analytics';

export interface ChartPalette {
  primary: string;
  secondary: string;
  success: string;
  warning: string;
  danger: string;
  unknown: string;
  text: string;
  muted: string;
  border: string;
}

function common(palette: ChartPalette): EChartsOption {
  return {
    backgroundColor: 'transparent',
    animationDuration:
      typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 0
        : 200,
    textStyle: { color: palette.text, fontFamily: 'Microsoft YaHei UI, PingFang SC, sans-serif' },
    aria: { enabled: true, decal: { show: true } },
    tooltip: {
      trigger: 'axis',
      backgroundColor: palette.text,
      borderWidth: 0,
      textStyle: { color: '#ffffff' },
    },
  };
}

export function buildingScoreOption(items: BuildingScore[], palette: ChartPalette): EChartsOption {
  return {
    ...common(palette),
    grid: { left: 112, right: 32, top: 12, bottom: 28 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { color: palette.muted, formatter: '{value} 分' },
      splitLine: { lineStyle: { color: palette.border } },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: items.map((item) => item.name),
      axisLabel: { color: palette.text, width: 96, overflow: 'truncate' },
      axisLine: { lineStyle: { color: palette.border } },
    },
    series: [
      {
        type: 'bar',
        data: items.map((item) => ({
          value: item.score,
          itemStyle: { color: item.dataSufficient ? palette.primary : palette.unknown },
        })),
        barMaxWidth: 22,
        label: { show: true, position: 'right', color: palette.text, formatter: '{c}' },
      },
    ],
  };
}

export function facilityOption(items: DistributionItem[], palette: ChartPalette): EChartsOption {
  return {
    ...common(palette),
    grid: { left: 120, right: 48, top: 12, bottom: 28 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: palette.muted },
      splitLine: { lineStyle: { color: palette.border } },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: items.map((item) => item.label),
      axisLabel: { color: palette.text },
      axisLine: { lineStyle: { color: palette.border } },
    },
    series: [
      {
        type: 'bar',
        data: items.map((item) => item.count),
        itemStyle: { color: palette.secondary },
        barMaxWidth: 20,
        label: { show: true, position: 'right', color: palette.text },
      },
    ],
  };
}

export function trendOption(items: BarrierTrend[], palette: ChartPalette): EChartsOption {
  return {
    ...common(palette),
    color: [palette.danger, palette.success],
    legend: { data: ['新增上报', '审核通过'], textStyle: { color: palette.text } },
    grid: { left: 48, right: 24, top: 44, bottom: 48 },
    xAxis: {
      type: 'category',
      data: items.map((item) => item.date.slice(5)),
      axisLabel: { color: palette.muted, rotate: items.length > 20 ? 35 : 0 },
      axisLine: { lineStyle: { color: palette.border } },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: palette.muted },
      splitLine: { lineStyle: { color: palette.border } },
    },
    series: [
      {
        name: '新增上报',
        type: 'line',
        data: items.map((item) => item.submitted),
        symbol: 'circle',
        lineStyle: { type: 'solid', width: 2 },
      },
      {
        name: '审核通过',
        type: 'line',
        data: items.map((item) => item.approved),
        symbol: 'diamond',
        lineStyle: { type: 'dashed', width: 2 },
      },
    ],
  };
}

export function routeRiskOption(items: RouteRisk[], palette: ChartPalette): EChartsOption {
  const labels: Record<RouteRisk['profile'], string> = {
    SHORTEST: '最短路线',
    ACCESSIBLE: '无障碍优先',
    BALANCED: '综合路线',
  };
  return {
    ...common(palette),
    color: [palette.danger, palette.warning],
    legend: { data: ['平均高风险边', '平均警告'], textStyle: { color: palette.text } },
    grid: { left: 48, right: 24, top: 44, bottom: 36 },
    xAxis: {
      type: 'category',
      data: items.map((item) => labels[item.profile]),
      axisLabel: { color: palette.text },
      axisLine: { lineStyle: { color: palette.border } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: palette.muted },
      splitLine: { lineStyle: { color: palette.border } },
    },
    series: [
      { name: '平均高风险边', type: 'bar', data: items.map((item) => item.averageHighRiskEdges) },
      { name: '平均警告', type: 'bar', data: items.map((item) => item.averageWarningCount) },
    ],
  };
}

export function confidenceOption(
  items: ConfidenceDistribution[],
  palette: ChartPalette,
): EChartsOption {
  return {
    ...common(palette),
    color: [palette.success, palette.secondary, palette.warning, palette.unknown],
    legend: { data: ['HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'], textStyle: { color: palette.text } },
    grid: { left: 72, right: 24, top: 44, bottom: 32 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: palette.muted },
      splitLine: { lineStyle: { color: palette.border } },
    },
    yAxis: {
      type: 'category',
      data: items.map((item) => item.entityLabel),
      axisLabel: { color: palette.text },
      axisLine: { lineStyle: { color: palette.border } },
    },
    series: [
      { name: 'HIGH', type: 'bar', stack: 'confidence', data: items.map((item) => item.high) },
      { name: 'MEDIUM', type: 'bar', stack: 'confidence', data: items.map((item) => item.medium) },
      { name: 'LOW', type: 'bar', stack: 'confidence', data: items.map((item) => item.low) },
      {
        name: 'UNKNOWN',
        type: 'bar',
        stack: 'confidence',
        data: items.map((item) => item.unknown),
      },
    ],
  };
}
