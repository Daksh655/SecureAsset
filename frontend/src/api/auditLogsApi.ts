import { fetchClient } from './client';
import type { PageResponse } from './recoveryCasesApi';

export interface GlobalAuditLogDto {
  id: string;
  eventType: string;
  actorType: string;
  toolName: string | null;
  message: string;
  success: boolean;
  createdAt: string;
  recoveryCaseId: string | null;
  recoveryActionId: string | null;
}

export interface AuditLogQueryParams {
  eventType?: string;
  caseId?: string;
  page?: number;
  size?: number;
}

export const getGlobalAuditLogs = async (
  params?: AuditLogQueryParams
): Promise<PageResponse<GlobalAuditLogDto>> => {
  const query = new URLSearchParams();
  
  if (params) {
    if (params.eventType) query.append('eventType', params.eventType);
    if (params.caseId) query.append('caseId', params.caseId);
    if (params.page !== undefined) query.append('page', params.page.toString());
    if (params.size !== undefined) query.append('size', params.size.toString());
  }

  const queryString = query.toString();
  const url = queryString ? "/api/audit-logs?${queryString}" : '/api/audit-logs';
  
  return fetchClient(url);
};
