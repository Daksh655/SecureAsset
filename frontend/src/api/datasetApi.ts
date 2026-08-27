import { fetchClient } from './client';

export interface DatasetStatus {
  active: boolean;
  datasetId?: string;
  generatedAt?: string;
  status?: string;
  transactionCount?: number;
  recoveryOpportunityCount?: number;
}

export async function getDatasetStatus(): Promise<DatasetStatus> {
  return fetchClient('/api/dataset/status');
}

export async function generateDataset(size: 'SMALL' | 'MEDIUM' | 'LARGE'): Promise<{ message: string }> {
  return fetchClient('/api/dataset/generate', {
    method: 'POST',
    body: JSON.stringify({ size }),
  });
}

export async function resetDataset(): Promise<{ message: string }> {
  return fetchClient('/api/dataset/reset', {
    method: 'POST',
  });
}

export async function generateRecoveryCases(): Promise<{ message: string }> {
  return fetchClient('/api/recovery-cases/generate', {
    method: 'POST',
  });
}
