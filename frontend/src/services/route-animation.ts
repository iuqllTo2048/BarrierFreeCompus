import { coordinateDistanceMeters } from './map-geometry';
import type { Coordinate } from '../types/map';

export const ROUTE_CYCLE_MS = 6000;
export const ROUTE_SWEEP_MS = 5400;
export const ROUTE_DOT_INTERVAL_MS = 150;
export const ROUTE_DOT_COUNT = 5;
export const ROUTE_CYCLES = 4;

/** 第 n 段从第 n-1 个途经点出发；首段的起点是“起”标记。 */
export function waypointMarkerState(
  waypointIndex: number,
  activeSegmentIndex: number | null,
): 'normal' | 'active' | 'muted' {
  if (activeSegmentIndex === null) return 'normal';
  return waypointIndex === activeSegmentIndex - 1 ? 'active' : 'muted';
}

export interface MeasuredRoute {
  points: Coordinate[];
  cumulativeMeters: number[];
  totalMeters: number;
}

/** 按真实地理长度累计，避免弯道顶点密度改变动画速度。 */
export function measureRoute(raw: number[][]): MeasuredRoute | null {
  const points = raw
    .filter((point) => point.length >= 2 && Number.isFinite(point[0]) && Number.isFinite(point[1]))
    .map((point) => ({ lng: point[0], lat: point[1] }));
  if (points.length < 2) return null;
  const cumulativeMeters = [0];
  for (let index = 1; index < points.length; index++) {
    cumulativeMeters.push(
      cumulativeMeters[index - 1] + coordinateDistanceMeters(points[index - 1], points[index]),
    );
  }
  const totalMeters = cumulativeMeters.at(-1) ?? 0;
  return totalMeters > 0 ? { points, cumulativeMeters, totalMeters } : null;
}

export function pointAtProgress(route: MeasuredRoute, progress: number): Coordinate {
  const target = Math.max(0, Math.min(1, progress)) * route.totalMeters;
  let low = 1;
  let high = route.cumulativeMeters.length - 1;
  while (low < high) {
    const middle = Math.floor((low + high) / 2);
    if (route.cumulativeMeters[middle] < target) low = middle + 1;
    else high = middle;
  }
  const previous = route.cumulativeMeters[low - 1];
  const span = route.cumulativeMeters[low] - previous;
  const ratio = span > 0 ? (target - previous) / span : 0;
  return {
    lng: route.points[low - 1].lng + (route.points[low].lng - route.points[low - 1].lng) * ratio,
    lat: route.points[low - 1].lat + (route.points[low].lat - route.points[low - 1].lat) * ratio,
  };
}

/** null 表示该点尚未从起点出现、已在终点消失，或四轮播放结束。 */
export function dotProgress(elapsedMs: number, index: number): number | null {
  if (elapsedMs < 0 || elapsedMs >= ROUTE_CYCLE_MS * ROUTE_CYCLES) return null;
  const inCycle = elapsedMs % ROUTE_CYCLE_MS;
  const dotElapsed = inCycle - index * ROUTE_DOT_INTERVAL_MS;
  return dotElapsed >= 0 && dotElapsed < ROUTE_SWEEP_MS ? dotElapsed / ROUTE_SWEEP_MS : null;
}
