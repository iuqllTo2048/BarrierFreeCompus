import { describe, expect, it } from 'vitest';
import {
  barrierIcon,
  barrierTypeLabel,
  confidenceLabel,
  facilityIcon,
  facilityTypeLabel,
  profileLabel,
} from './map-visuals';

describe('地图视觉语义', () => {
  it.each([
    ['ACCESSIBLE_ENTRANCE', 'accessible-entrance', '无障碍入口'],
    ['RAMP', 'ramp', '坡道'],
    ['ELEVATOR', 'elevator', '电梯'],
    ['ACCESSIBLE_TOILET', 'toilet', '无障碍卫生间'],
    ['REST_AREA', 'rest-area', '休息点'],
    ['ACCESSIBLE_PARKING', 'parking', '无障碍停车位'],
    ['DROP_OFF_POINT', 'drop-off', '落客点'],
    ['TRANSIT_BOARDING_POINT', 'transit', '公共交通乘车点'],
  ])('设施 %s 同时提供图标与中文标签', (type, icon, label) => {
    expect(facilityIcon(type)).toBe(icon);
    expect(facilityTypeLabel(type)).toBe(label);
  });

  it.each([
    ['STAIRS', '楼梯'],
    ['CONSTRUCTION', '施工'],
    ['TEMPORARY_CLOSURE', '临时封闭'],
    ['DAMAGED_SURFACE', '路面破损'],
    ['NARROW_PATH', '道路狭窄'],
    ['VEHICLE_BLOCKING', '车辆占道'],
    ['STEEP_SLOPE', '陡坡'],
    ['ELEVATOR_OUTAGE', '电梯停运'],
    ['ENTRANCE_CLOSED', '入口关闭'],
    ['WATERLOGGING', '积水'],
  ])('障碍 %s 不只用颜色表达', (type, label) => {
    expect(barrierIcon(type)).toBeTruthy();
    expect(barrierTypeLabel(type)).toBe(label);
  });

  it('未知类型与可信度有安全回退文案', () => {
    expect(facilityIcon('CUSTOM')).toBe('services');
    expect(facilityTypeLabel('CUSTOM')).toBe('无障碍设施');
    expect(barrierIcon('CUSTOM')).toBe('warning');
    expect(barrierTypeLabel('CUSTOM')).toBe('通行障碍');
    expect(confidenceLabel('UNKNOWN')).toBe('未核验');
  });

  it('三条路线均有独立文字名称', () => {
    expect(profileLabel('SHORTEST')).toBe('最短路线');
    expect(profileLabel('ACCESSIBLE')).toBe('无障碍优先');
    expect(profileLabel('BALANCED')).toBe('综合路线');
  });
});
