import type { MobilityMode } from './map';

export interface ConversationView {
  id: string;
  title: string;
  status: string;
  provider: string;
  modelName: string;
  createdAt: string;
  updatedAt: string;
}

export interface MessageView {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  requestId: string | null;
  createdAt: string;
}

export interface AssistantStatus {
  enabled: boolean;
  mode: 'MOCK' | 'REAL';
  provider: string;
  modelName: string;
  degradationMessage: string;
}

export interface RouteComparison {
  recommendedProfile: string | null;
  routes: Array<{
    profile: string;
    distanceM: number;
    estimatedMinutes: number;
    riskLevel: string;
    stairsCount: number;
    warningCount: number;
  }>;
  reasons: string[];
}

export interface BarrierDraft {
  id: string;
  status: string;
  expiresAt: string;
  payload: {
    datasetId: string;
    title: string;
    barrierType: string;
    description: string;
    expectedDurationHours: number;
    lng: number;
    lat: number;
  };
}

export interface AgentStreamEvent {
  name: string;
  data: unknown;
}

export interface AgentMessageRequest {
  datasetId: string;
  content: string;
  mobilityMode: MobilityMode;
}

export interface InvocationLog {
  id: string;
  conversationId: string | null;
  requestId: string;
  username: string;
  provider: string;
  modelName: string;
  latencyMs: number;
  success: boolean;
  errorCode: string | null;
  errorSummary: string | null;
  createdAt: string;
  tools: Array<{
    toolName: string;
    argumentSummary: string;
    resultSummary: string;
    latencyMs: number;
    success: boolean;
    errorSummary: string | null;
  }>;
}
