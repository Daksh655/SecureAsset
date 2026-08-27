import { fetchClient } from './client';
import type { PageResponse } from './recoveryCasesApi';

export type { PageResponse };

export interface RecoveryActionSummaryDto {
  id: string;
  recoveryCaseId: string;
  actionType: string;
  amount: number;
  status: string;
  approvalStatus: string;
  razorpayReference: string;
  requestedAt: string;
  approvedAt: string;
  executedAt: string;
  completedAt: string;
  errorCode: string;
  errorMessage: string;
}

export interface RecoveryActionsQueryParams {
  status?: string;
  approvalStatus?: string;
  actionType?: string;
  page?: number;
  size?: number;
}

export interface ApiResponse {
  success: boolean;
  message: string;
}

export const getRecoveryActions = async (
  params?: RecoveryActionsQueryParams
): Promise<PageResponse<RecoveryActionSummaryDto>> => {
  const query = new URLSearchParams();
  
  if (params) {
    if (params.status) query.append('status', params.status);
    if (params.approvalStatus) query.append('approvalStatus', params.approvalStatus);
    if (params.actionType) query.append('actionType', params.actionType);
    if (params.page !== undefined) query.append('page', params.page.toString());
    if (params.size !== undefined) query.append('size', params.size.toString());
  }

  const queryString = query.toString();
  const url = queryString ? `/api/recovery-actions?${queryString}` : '/api/recovery-actions';
  
  return fetchClient(url);
};

export const approveRecoveryAction = async (
  caseId: string,
  actionType: string,
  amount: number
): Promise<ApiResponse> => {
  return fetchClient(`/api/recovery-cases/${caseId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ actionType, amount })
  });
};

export const rejectRecoveryAction = async (
  caseId: string,
  reason: string
): Promise<ApiResponse> => {
  return fetchClient(`/api/recovery-cases/${caseId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  });
};
