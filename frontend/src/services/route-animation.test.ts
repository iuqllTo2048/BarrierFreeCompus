import { describe, expect, it } from 'vitest';
import {
  dotProgress,
  measureRoute,
  pointAtProgress,
  ROUTE_CYCLE_MS,
  ROUTE_CYCLES,
  waypointMarkerState,
} from './route-animation';

describe('route animation', () => {
  it('keeps geographic speed constant across uneven vertex spacing', () => {
    const route = measureRoute([
      [104, 31],
      [104.001, 31],
      [104.009, 31],
    ]);
    expect(route).not.toBeNull();
    expect(pointAtProgress(route!, 0.5).lng).toBeCloseTo(104.0045, 4);
  });

  it('spawns five dots in order, clears them at destination, and stops after four cycles', () => {
    expect(dotProgress(0, 0)).toBe(0);
    expect(dotProgress(0, 1)).toBeNull();
    expect(dotProgress(600, 4)).toBe(0);
    expect(dotProgress(5400, 0)).toBeNull();
    expect(dotProgress(5999, 4)).not.toBeNull();
    expect(dotProgress(ROUTE_CYCLE_MS, 0)).toBe(0);
    expect(dotProgress(ROUTE_CYCLE_MS * ROUTE_CYCLES, 0)).toBeNull();
  });

  it('does not animate empty or stationary paths', () => {
    expect(measureRoute([])).toBeNull();
    expect(
      measureRoute([
        [104, 31],
        [104, 31],
      ]),
    ).toBeNull();
  });

  it('preserves a return trip over the same road in its original order', () => {
    const route = measureRoute([
      [104, 31],
      [104.001, 31],
      [104, 31],
    ]);
    expect(route).not.toBeNull();
    expect(pointAtProgress(route!, 0).lng).toBeCloseTo(104);
    expect(pointAtProgress(route!, 0.5).lng).toBeCloseTo(104.001);
    expect(pointAtProgress(route!, 1).lng).toBeCloseTo(104);
  });

  it('highlights only the starting waypoint of the selected leg', () => {
    expect(waypointMarkerState(1, null)).toBe('normal');
    expect(waypointMarkerState(1, 1)).toBe('muted');
    expect(waypointMarkerState(1, 2)).toBe('active');
    expect(waypointMarkerState(2, 2)).toBe('muted');
    expect(waypointMarkerState(2, 3)).toBe('active');
  });
});
