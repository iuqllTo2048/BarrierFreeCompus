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
