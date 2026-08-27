import React, { useEffect, useState } from 'react';
import { getDashboardMetrics } from '../api/dashboardApi';
import type { DashboardMetricsDto } from '../api/dashboardApi';
import { ApiError } from '../api/client';
import { formatCurrency, formatPercentage, formatNumber } from '../utils/formatters';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { DatasetDialog } from '../components/layout/DatasetDialog';
import './Overview.css';

export const Overview: React.FC = () => {
  const [metrics, setMetrics] = useState<DashboardMetricsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchMetrics = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getDashboardMetrics();
      setMetrics(data);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('An unexpected error occurred while fetching dashboard metrics.');
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMetrics();
  }, []);

  const renderContent = () => {
    if (loading) {
      return (
        <div className="metrics-grid">
          {Array.from({ length: 9 }).map((_, i) => (
            <div key={i} className="metric-card skeleton-pulse">
              <div className="skeleton-text-sm" style={{ width: '60%' }}></div>
              <div className="skeleton-text-lg" style={{ marginTop: '0.5rem', width: '80%' }}></div>
            </div>
          ))}
        </div>
      );
    }

    if (error) {
      return <ErrorState message={error} onRetry={fetchMetrics} />;
    }

    if (!metrics) {
      return <EmptyState title="No Dashboard Data" message="There are currently no metrics available to display." />;
    }

    if (metrics.transactionsAnalyzed === 0) {
      return <EmptyState title="No Activity Yet" message="The system hasn't analyzed any transactions yet. Data will appear here once payments are processed." />;
    }

    return (
      <div className="metrics-grid">
        {/* ROW 1 */}
        <div className="metric-card">
          <p className="metric-label">Revenue at Risk</p>
          <p className="metric-value text-accent-red">{formatCurrency(metrics.revenueAtRisk, metrics.currency)}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Potentially Recoverable</p>
          <p className="metric-value text-accent-orange">{formatCurrency(metrics.potentiallyRecoverable, metrics.currency)}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Recovered Revenue</p>
          <p className="metric-value text-accent-green">{formatCurrency(metrics.recoveredRevenue, metrics.currency)}</p>
        </div>

        {/* ROW 2 */}
        <div className="metric-card">
          <p className="metric-label">Transactions Analyzed</p>
          <p className="metric-value">{formatNumber(metrics.transactionsAnalyzed)}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Recovery Opportunities</p>
          <p className="metric-value">{formatNumber(metrics.recoveryOpportunities)}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Recovery Rate</p>
          <p className="metric-value text-accent-blue">{formatPercentage(metrics.recoveryRate)}</p>
        </div>

        {/* ROW 3 */}
        <div className="metric-card">
          <p className="metric-label">High Priority</p>
          <p className="metric-value text-accent-red">{formatNumber(metrics.highPriorityCases)}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Medium Priority</p>
          <p className="metric-value text-accent-orange">{formatNumber(metrics.mediumPriorityCases)}</p>
        </div>
        <div className="metric-card">
          <p className="metric-label">Low Priority</p>
          <p className="metric-value">{formatNumber(metrics.lowPriorityCases)}</p>
        </div>
      </div>
    );
  };

  const [isResetDialogOpen, setIsResetDialogOpen] = useState(false);
  const [isGenerateDialogOpen, setIsGenerateDialogOpen] = useState(false);

  const isDatasetActive = metrics && metrics.transactionsAnalyzed > 0;

  return (
    <div className="page-container">
      <div className="hero-section">
        <div className="hero-content">
          <h1 className="display-heading">Recover revenue intelligently.</h1>
          <p className="hero-subtitle">
            SecureAsset identifies payment failures, evaluates recovery risk, investigates cases using AI, and executes governed recovery actions.
          </p>
        </div>
        
        <div className="hero-dataset-card">
          <div className="dataset-card-header">
            <span className="dataset-label">DEMO DATASET</span>
          </div>
          
          {isDatasetActive ? (
            <div className="dataset-stats">
              <div className="dataset-stat-row">
                <span className="dataset-stat-value">{formatNumber(metrics.transactionsAnalyzed)}</span>
                <span className="dataset-stat-label">transactions</span>
              </div>
              <div className="dataset-stat-row">
                <span className="dataset-stat-value">{formatNumber(metrics.recoveryOpportunities)}</span>
                <span className="dataset-stat-label">recovery opportunities</span>
              </div>
              <div className="dataset-stat-row">
                <span className="dataset-stat-label">Generated today</span>
              </div>
              <button 
                className="dataset-action-btn secondary"
                onClick={() => setIsResetDialogOpen(true)}
              >
                Reset Dataset
              </button>
            </div>
          ) : (
            <div className="dataset-stats empty">
              <div className="dataset-stat-row">
                <span className="dataset-stat-label">Transactions generated</span>
                <span className="dataset-stat-value">0</span>
              </div>
              <div className="dataset-stat-row">
                <span className="dataset-stat-label">Recovery opportunities</span>
                <span className="dataset-stat-value">0</span>
              </div>
              <button 
                className="dataset-action-btn primary"
                onClick={() => setIsGenerateDialogOpen(true)}
              >
                Generate New Dataset
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="content-section">
        {renderContent()}
      </div>

      <DatasetDialog 
        isOpen={isResetDialogOpen} 
        onClose={() => setIsResetDialogOpen(false)} 
        isReset={true}
      />
      <DatasetDialog 
        isOpen={isGenerateDialogOpen} 
        onClose={() => setIsGenerateDialogOpen(false)} 
        isReset={false}
      />
    </div>
  );
};
