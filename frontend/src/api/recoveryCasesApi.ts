import { fetchClient } from './client';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RecoveryCaseSummaryDto {
  id: string;
  customerId: string;
  orderId: string;
  paymentId: string;
  problemType: string;
  riskAmount: number;
  recoveryScore: number;
  priority: string;
  status: string;
  agentStatus: string;
  detectedAt: string;
}

export interface RecoveryCasesQueryParams {
  priority?: string;
  status?: string;
  problemType?: string;
  minAmount?: number;
  maxAmount?: number;
  minScore?: number;
  page?: number;
  size?: number;
}

export const getRecoveryCases = async (
  params?: RecoveryCasesQueryParams
): Promise<PageResponse<RecoveryCaseSummaryDto>> => {
  const query = new URLSearchParams();
  
  if (params) {
    if (params.priority) query.append('priority', params.priority);
    if (params.status) query.append('status', params.status);
    if (params.problemType) query.append('problemType', params.problemType);
    if (params.minAmount !== undefined) query.append('minAmount', params.minAmount.toString());
    if (params.maxAmount !== undefined) query.append('maxAmount', params.maxAmount.toString());
    if (params.minScore !== undefined) query.append('minScore', params.minScore.toString());
    if (params.page !== undefined) query.append('page', params.page.toString());
    if (params.size !== undefined) query.append('size', params.size.toString());
  }

  const queryString = query.toString();
  const url = queryString ? `/api/recovery-cases?${queryString}` : '/api/recovery-cases';
  
  return fetchClient(url);
};

export interface RecoveryCaseDetailDto {
  id: string;
  customer: {
    id: string;
    name: string;
    email: string;
  };
  order: {
    id: string;
    amount: number;
    currency: string;
    status: string;
  };
  payment: {
    id: string;
    amount: number;
    status: string;
    failureReason: string;
    attemptNumber: number;
  };
  problemType: string;
  riskAmount: number;
  recoveryScore: number;
  priority: string;
  status: string;
  agentStatus: string;
  agentRecommendation: string;
  agentConfidence: number;
  agentReason: string;
  detectedAt: string;
  analyzedAt: string;
}

export interface RecoveryActionDto {
  id: string;
  actionType: string;
  amount: number;
  status: string;
  approvalStatus: string;
  razorpayReference: string;
  result: string;
  requestedAt: string;
  executedAt: string;
}

export interface AuditLogDto {
  id: string;
  eventType: string;
  actorType: string;
  toolName: string;
  message: string;
  success: boolean;
  createdAt: string;
}

export const getRecoveryCase = async (id: string): Promise<RecoveryCaseDetailDto> => {
  return fetchClient(`/api/recovery-cases/${id}`);
};

export const getRecoveryActions = async (id: string): Promise<RecoveryActionDto[]> => {
  return fetchClient(`/api/recovery-cases/${id}/actions`);
};

export const getCaseAuditLogs = async (id: string): Promise<AuditLogDto[]> => {
  return fetchClient(`/api/recovery-cases/${id}/audit`);
};

export const investigateRecoveryCase = async (id: string): Promise<RecoveryCaseDetailDto> => {
  return fetchClient(`/api/recovery-cases/${id}/investigate`, {
    method: 'POST'
  });
};
