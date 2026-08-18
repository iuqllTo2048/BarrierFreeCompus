import type { MobilityMode, RoutePlanResponse, RouteProfile } from './map';

export interface UserProfile {
  username: string;
  displayName: string;
  defaultMobilityMode: MobilityMode;
  avoidStairs: boolean;
  distanceWeight: number;
  slopeWeight: number;
  widthWeight: number;
  preferRestArea: boolean;
  preferAccessibleToilet: boolean;
}

export interface FacilityComment {
  id: number;
  username: string;
  content: string;
  createdAt: string;
}

export interface FacilityDetail {
  id: string;
  name: string;
  facilityType: string;
  buildingName: string | null;
  floorLabel: string | null;
  openStatus: string;
  description: string | null;
  dataSource: string;
  confidenceLevel: string;
  photoUrl: string | null;
  updatedAt: string;
  lng: number;
  lat: number;
  averageRating: number;
  ratingCount: number;
  myRating: number | null;
  comments: FacilityComment[];
}

export interface BarrierReport {
  id: string;
  datasetId: string;
  externalId: string;
  title: string;
  barrierType: string;
  description: string;
  reviewStatus: string;
  active: boolean;
  confidenceLevel: string;
  matchedReportId: string | null;
  reporterUsername: string | null;
  expiresAt: string | null;
  createdAt: string;
  reviewedAt: string | null;
  lng: number;
  lat: number;
}

export interface RouteHistory {
  id: string;
  datasetId: string;
  startNodeId: string;
  endNodeId: string;
  startName: string;
  endName: string;
  mobilityMode: MobilityMode;
  travelPeriod: string;
  result: RoutePlanResponse;
  createdAt: string;
}

export interface RouteFavorite {
  id: string;
  historyId: string;
  routeProfile: RouteProfile;
  name: string;
  routeResult: RoutePlanResponse;
  createdAt: string;
}

export interface AdminOverview {
  counts: Record<string, number>;
  pendingBarriers: BarrierReport[];
}

export interface AdminUser {
  id: number;
  username: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}

export interface AuditEntry {
  id: number;
  actor: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  detail: string | null;
  createdAt: string;
}

export interface SystemSetting {
  key: string;
  value: string;
  description: string | null;
  updatedAt: string;
}

export interface FacilitySuggestion {
  id: string;
  facilityId: string | null;
  facilityName: string | null;
  username: string | null;
  suggestionType: string;
  content: string;
  status: string;
  createdAt: string;
}
