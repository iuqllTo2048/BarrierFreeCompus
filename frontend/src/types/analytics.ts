export interface AnalyticsFilter {
  datasetId: string;
  buildingId?: string;
  from?: string;
  to?: string;
  facilityType?: string;
  barrierType?: string;
  confidenceLevel?: string;
}

export interface BuildingScore {
  id: string;
  name: string;
  score: number;
  entranceScore: number;
  elevatorScore: number;
  toiletScore: number;
  roadScore: number;
  barrierScore: number;
  completenessScore: number;
  dataSufficient: boolean;
  reasons: string[];
  lng: number;
  lat: number;
}

export interface DistributionItem {
  key: string;
  label: string;
  count: number;
  percentage: number;
}

export interface AnalyticsBarrierPoint {
  id: string;
  title: string;
  barrierType: string;
  confidenceLevel: string;
  reviewStatus: string;
  lng: number;
  lat: number;
  impactWeight: number;
}

export interface BarrierTrend {
  date: string;
  submitted: number;
  approved: number;
}

export interface RouteRisk {
  profile: 'SHORTEST' | 'ACCESSIBLE' | 'BALANCED';
  sampleCount: number;
  averageDistanceM: number;
  averageMinutes: number;
  averageHighRiskEdges: number;
  averageWarningCount: number;
  fallbackCount: number;
}

export interface ConfidenceDistribution {
  entityType: string;
  entityLabel: string;
  high: number;
  medium: number;
  low: number;
  unknown: number;
}

export interface AnalyticsOverview {
  filter: AnalyticsFilter;
  summary: {
    buildings: number;
    facilities: number;
    effectiveBarriers: number;
    routePlans: number;
    averageBuildingScore: number;
  };
  buildingScores: BuildingScore[];
  facilityDistribution: DistributionItem[];
  barrierPoints: AnalyticsBarrierPoint[];
  barrierTrend: BarrierTrend[];
  routeRisks: RouteRisk[];
  confidenceDistribution: ConfidenceDistribution[];
  generatedAt: string;
}

export interface GovernanceSummary {
  aiEnabled: boolean;
  degraded: boolean;
  generatedBy: 'MODEL' | 'RULES';
  modelName: string;
  text: string;
}
