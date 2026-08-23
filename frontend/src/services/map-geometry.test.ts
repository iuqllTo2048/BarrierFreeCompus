import { describe, expect, it } from 'vitest';
import {
  DEFAULT_CAMPUS_CENTER,
  coordinateDistanceMeters,
  pathDistanceMeters,
} from './map-geometry';

describe('地图几何工具', () => {
  it('使用新的学校示例校园中心', () => {
    expect(DEFAULT_CAMPUS_CENTER).toEqual({ lng: 104.695359, lat: 31.534827 });
  });

  it('按折线逐段累计距离', () => {
    const first = { lng: 104.695359, lat: 31.534827 };
    const middle = { lng: 104.696359, lat: 31.534827 };
    const end = { lng: 104.696359, lat: 31.535827 };

    expect(pathDistanceMeters([first, middle, end])).toBeCloseTo(
      coordinateDistanceMeters(first, middle) + coordinateDistanceMeters(middle, end),
      8,
    );
    expect(pathDistanceMeters([first])).toBe(0);
  });
});
