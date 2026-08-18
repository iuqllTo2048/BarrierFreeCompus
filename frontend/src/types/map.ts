export type CoordinateSystem = 'GCJ02';
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';

export interface DatasetView {
  id: string;
  code: string;
  name: string;
  datasetType: 'DEMO' | 'FORMAL';
  coordinateSystem: CoordinateSystem;
  enabled: boolean;
  demo: boolean;
  seed: number | null;
  description: string | null;
  centerLng: number;
  centerLat: number;
}

export interface GeoJsonGeometry {
  type: 'Point' | 'LineString' | 'Polygon';
  coordinates: number[] | number[][] | number[][][];
}

export interface BuildingView {
  id: string;
  externalId: string;
  name: string;
  category: string;
  active: boolean;
  dataSource: string;
  confidenceLevel: ConfidenceLevel;
  geometry: GeoJsonGeometry;
}

export interface EntranceView {
  id: string;
  buildingId: string;
  externalId: string;
  name: string;
  accessible: boolean;
  entranceType: string;
  status: string;
  active: boolean;
  lng: number;
  lat: number;
}

export interface NodeView {
  id: string;
  externalId: string;
  name: string | null;
  nodeType: string;
  active: boolean;
  dataSource: string;
  confidenceLevel: ConfidenceLevel;
  lng: number;
  lat: number;
}

export interface EdgeView {
  id: string;
  externalId: string;
  name: string | null;
  fromNodeId: string;
  toNodeId: string;
  distanceM: number;
  slopeLevel: string;
  hasStairs: boolean;
  stairsCount: number;
  widthLevel: string;
  surfaceType: string;
  lightingLevel: string;
  bidirectional: boolean;
  status: string;
  riskLevel: RiskLevel;
  dataSource: string;
  confidenceLevel: ConfidenceLevel;
  geometry: GeoJsonGeometry;
}

export interface FacilityView {
  id: string;
  buildingId: string | null;
  externalId: string;
  name: string;
  facilityType: string;
  floorLabel: string | null;
  openStatus: string;
  description: string | null;
  active: boolean;
  dataSource: string;
  confidenceLevel: ConfidenceLevel;
  lng: number;
  lat: number;
}

export interface BarrierView {
  id: string;
  externalId: string;
  title: string;
  barrierType: string;
  description: string | null;
  reviewStatus: string;
  active: boolean;
  dataSource: string;
  confidenceLevel: ConfidenceLevel;
  geometry: GeoJsonGeometry;
}

export interface MapSnapshot {
  dataset: DatasetView;
  buildings: BuildingView[];
  entrances: EntranceView[];
  nodes: NodeView[];
  edges: EdgeView[];
  facilities: FacilityView[];
  barriers: BarrierView[];
}

export interface Coordinate {
  lng: number;
  lat: number;
}

export interface NodeRequest {
  externalId: string;
  name: string;
  nodeType: string;
  active: boolean;
  coordinate: Coordinate;
}

export interface EdgeRequest {
  externalId: string;
  name: string;
  fromNodeId: string;
  toNodeId: string;
  distanceM: number;
  slopeLevel: string;
  hasStairs: boolean;
  stairsCount: number;
  widthLevel: string;
  surfaceType: string;
  lightingLevel: string;
  bidirectional: boolean;
  status: string;
  riskLevel: RiskLevel;
  intermediatePoints: Coordinate[];
}

export interface PointCreateRequest {
  externalId: string;
  name?: string;
  title?: string;
  coordinate: Coordinate;
  [key: string]: unknown;
}

export interface GeoJsonFeatureCollection {
  type: 'FeatureCollection';
  datasetId: string;
  coordinateSystem: CoordinateSystem;
  features: unknown[];
}

export type MobilityMode =
  'WHEELCHAIR' | 'CRUTCH' | 'TEMPORARY_INJURY' | 'CART_LUGGAGE' | 'WALKING';
export type RouteProfile = 'SHORTEST' | 'ACCESSIBLE' | 'BALANCED';
export type TravelPeriod = 'DAY' | 'NIGHT';

export interface RoutePreferences {
  avoidStairs: boolean;
  distanceWeight: number;
  slopeWeight: number;
  widthWeight: number;
  restAreaWeight?: number;
  accessibleToiletWeight?: number;
}

export interface RoutePlanRequest {
  datasetId: string;
  startNodeId: string;
  endNodeId: string;
  mobilityMode: MobilityMode;
  travelPeriod: TravelPeriod;
  preferences: RoutePreferences;
}

export interface CostBreakdown {
  distance: number;
  slope: number;
  stairs: number;
  width: number;
  surface: number;
  lighting: number;
  barrier: number;
  uncertainty: number;
  facilityPreference: number;
  total: number;
}

export interface AlgorithmMetrics {
  expandedNodes: number;
  visitedEdges: number;
  queuePeak: number;
  elapsedMicros: number;
  totalCost: number;
}

export interface RouteFacility {
  id: string;
  name: string;
  facilityType: string;
  openStatus: string;
  confidenceLevel: ConfidenceLevel;
  lng: number;
  lat: number;
}

export interface RouteBarrier {
  id: string;
  title: string;
  barrierType: string;
  confidenceLevel: ConfidenceLevel;
  blocking: boolean;
}

export interface RouteResult {
  profile: RouteProfile;
  equivalentProfiles: RouteProfile[];
  geometry: GeoJsonGeometry;
  distanceM: number;
  estimatedMinutes: number;
  riskSummary: {
    level: RiskLevel;
    highRiskEdges: number;
    mediumRiskEdges: number;
    unknownRiskEdges: number;
    fallbackRoute: boolean;
  };
  stairsCount: number;
  slopeSummary: Record<string, number>;
  facilities: RouteFacility[];
  barriers: RouteBarrier[];
  confidence: ConfidenceLevel;
  costBreakdown: CostBreakdown;
  constraints: string[];
  warnings: string[];
  algorithmMetrics: AlgorithmMetrics;
  edgeIds: string[];
}

export interface RoutePlanResponse {
  datasetId: string;
  startNodeId: string;
  endNodeId: string;
  mobilityMode: MobilityMode;
  travelPeriod: TravelPeriod;
  routes: RouteResult[];
  notices: string[];
  historyId: string | null;
}
