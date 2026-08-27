import { fetchClient } from './client';

export interface DashboardMetricsDto {
  transactionsAnalyzed: number;
  recoveryOpportunities: number;
  highPriorityCases: number;
  mediumPriorityCases: number;
  lowPriorityCases: number;
  revenueAtRisk: number;
  potentiallyRecoverable: number;
  recoveredRevenue: number;
  recoveryRate: number;
  currency: string;
}

export const getDashboardMetrics = (): Promise<DashboardMetricsDto> => {
  return fetchClient<DashboardMetricsDto>('/api/dashboard');
};
