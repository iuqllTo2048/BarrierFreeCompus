import type { Coordinate } from '../types/map';

export const DEFAULT_CAMPUS_CENTER: Coordinate = {
  lng: 104.695359,
  lat: 31.534827,
};

export function coordinateDistanceMeters(first: Coordinate, second: Coordinate): number {
  const radians = (degrees: number): number => (degrees * Math.PI) / 180;
  const earthRadius = 6_371_000;
  const deltaLat = radians(second.lat - first.lat);
  const deltaLng = radians(second.lng - first.lng);
  const a =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(radians(first.lat)) * Math.cos(radians(second.lat)) * Math.sin(deltaLng / 2) ** 2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function pathDistanceMeters(path: Coordinate[]): number {
  return path.slice(1).reduce((total, point, index) => {
    return total + coordinateDistanceMeters(path[index], point);
  }, 0);
}
