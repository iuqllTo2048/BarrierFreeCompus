import type { AppIconName } from './app-icons';
import type { RouteResult } from '../types/map';

const facilityIcons: Record<string, AppIconName> = {
  ACCESSIBLE_ENTRANCE: 'accessible-entrance',
  RAMP: 'ramp',
  ELEVATOR: 'elevator',
  ACCESSIBLE_TOILET: 'toilet',
  REST_AREA: 'rest-area',
  ACCESSIBLE_PARKING: 'parking',
  DROP_OFF_POINT: 'drop-off',
  TRANSIT_BOARDING_POINT: 'transit',
};
const facilityLabels: Record<string, string> = {
  ACCESSIBLE_ENTRANCE: '无障碍入口',
  RAMP: '坡道',
  ELEVATOR: '电梯',
  ACCESSIBLE_TOILET: '无障碍卫生间',
  REST_AREA: '休息点',
  ACCESSIBLE_PARKING: '无障碍停车位',
  DROP_OFF_POINT: '落客点',
  TRANSIT_BOARDING_POINT: '公共交通乘车点',
};
const barrierIcons: Record<string, AppIconName> = {
  STAIRS: 'ramp',
  TEMPORARY_CLOSURE: 'close',
  NARROW_PATH: 'accessible-entrance',
  VEHICLE_BLOCKING: 'parking',
  STEEP_SLOPE: 'ramp',
  ELEVATOR_OUTAGE: 'elevator',
  ENTRANCE_CLOSED: 'accessible-entrance',
  CONSTRUCTION: 'barrier',
};
const barrierLabels: Record<string, string> = {
  STAIRS: '楼梯',
  CONSTRUCTION: '施工',
  TEMPORARY_CLOSURE: '临时封闭',
  DAMAGED_SURFACE: '路面破损',
  NARROW_PATH: '道路狭窄',
  VEHICLE_BLOCKING: '车辆占道',
  STEEP_SLOPE: '陡坡',
  ELEVATOR_OUTAGE: '电梯停运',
  ENTRANCE_CLOSED: '入口关闭',
  WATERLOGGING: '积水',
};

export const facilityIcon = (type: string): AppIconName => facilityIcons[type] ?? 'services';
export const facilityTypeLabel = (type: string): string => facilityLabels[type] ?? '无障碍设施';
export const barrierIcon = (type: string): AppIconName => barrierIcons[type] ?? 'warning';
export const barrierTypeLabel = (type: string): string => barrierLabels[type] ?? '通行障碍';
export const confidenceLabel = (level: string): string =>
  ({ HIGH: '高可信', MEDIUM: '中可信', LOW: '低可信', UNKNOWN: '未核验' })[level] ?? level;
export const profileLabel = (profile: RouteResult['profile']): string =>
  ({ SHORTEST: '最短路线', ACCESSIBLE: '无障碍优先', BALANCED: '综合路线' })[profile];
