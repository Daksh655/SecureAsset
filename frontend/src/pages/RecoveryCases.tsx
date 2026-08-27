import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getRecoveryCases } from '../api/recoveryCasesApi';
import type { RecoveryCaseSummaryDto, PageResponse } from '../api/recoveryCasesApi';
import { getDatasetStatus, generateRecoveryCases } from '../api/datasetApi';
import type { DatasetStatus } from '../api/datasetApi';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { StatusBadge } from '../components/ui/StatusBadge';
import { PriorityBadge } from '../components/ui/PriorityBadge';
import { formatCurrency, formatDate } from '../utils/formatters';
import './RecoveryCases.css';

export const RecoveryCases: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [data, setData] = useState<PageResponse<RecoveryCaseSummaryDto> | null>(null);
  const [datasetStatus, setDatasetStatus] = useState<DatasetStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      
      const statusData = await getDatasetStatus();
      setDatasetStatus(statusData);

      if (!statusData.active) {
        setLoading(false);
        return; // Don't fetch cases if dataset is inactive
      }

      const params = {
        priority: searchParams.get('priority') || undefined,
        status: searchParams.get('status') || undefined,
        problemType: searchParams.get('problemType') || undefined,
        minAmount: searchParams.has('minAmount') ? Number(searchParams.get('minAmount')) : undefined,
        maxAmount: searchParams.has('maxAmount') ? Number(searchParams.get('maxAmount')) : undefined,
        minScore: searchParams.has('minScore') ? Number(searchParams.get('minScore')) : undefined,
        page: searchParams.has('page') ? Number(searchParams.get('page')) : 0,
        size: searchParams.has('size') ? Number(searchParams.get('size')) : 20,
      };

      const response = await getRecoveryCases(params);
      setData(response);
    } catch (err: any) {
      setError(err.message || 'Failed to load recovery cases');
    } finally {
      setLoading(false);
    }
  }, [searchParams]);

  useEffect(() => {
    fetchData();

    const handleDatasetChanged = () => {
      fetchData();
    };
    
    window.addEventListener('dataset-changed', handleDatasetChanged);
    return () => window.removeEventListener('dataset-changed', handleDatasetChanged);
  }, [fetchData]);

  const handleGenerateCases = async () => {
    setGenerating(true);
    setError(null);
    try {
      await generateRecoveryCases();
      window.dispatchEvent(new Event('dataset-changed'));
      await fetchData();
    } catch (err: any) {
      setError(err.message || 'Failed to generate recovery cases');
    } finally {
      setGenerating(false);
    }
  };

  const handleFilterChange = (key: string, value: string) => {
    setSearchParams(prev => {
      const newParams = new URLSearchParams(prev);
      if (value) {
        newParams.set(key, value);
      } else {
        newParams.delete(key);
      }
      newParams.delete('page'); // Reset pagination
      return newParams;
    });
  };

  const handlePageChange = (newPage: number) => {
    setSearchParams(prev => {
      const newParams = new URLSearchParams(prev);
      newParams.set('page', newPage.toString());
      return newParams;
    });
  };

  const renderContent = () => {
    if (loading) {
      return (
        <div className="skeleton-container">
          <div className="skeleton-row skeleton-pulse"></div>
          <div className="skeleton-row skeleton-pulse"></div>
          <div className="skeleton-row skeleton-pulse"></div>
        </div>
      );
    }

    if (error) {
      return <ErrorState message={error} onRetry={fetchData} />;
    }

    if (datasetStatus && !datasetStatus.active) {
      return (
        <EmptyState 
          title="No active demo dataset" 
          message="Create a new demo dataset from the Overview page or the top navigation bar to begin."
        />
      );
    }

    if (data?.content.length === 0) {
      const hasFilters = Array.from(searchParams.keys()).some(k => k !== 'page' && k !== 'size');
      
      if (hasFilters) {
        return (
          <EmptyState 
            title="No matches found" 
            message="No recovery cases match your current filter criteria."
          />
        );
      }

      return (
        <EmptyState 
          title="No Recovery Cases Generated" 
          message="Your dataset is active, but the AI evaluation has not yet processed the failed payments into actionable recovery cases."
        />
      );
    }

    return (
      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Status</th>
              <th>Priority</th>
              <th>Customer ID</th>
              <th>Amount</th>
              <th>Problem</th>
              <th>Score</th>
              <th>Detected</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map(rc => (
              <tr key={rc.id} onClick={() => navigate(`/recovery-cases/${rc.id}`)} className="clickable-row">
                <td><StatusBadge status={rc.status} /></td>
                <td><PriorityBadge priority={rc.priority} /></td>
                <td>
                  <div className="customer-info">
                    <span className="customer-name">{rc.customerId.substring(0, 8)}...</span>
                  </div>
                </td>
                <td className="amount-cell">{formatCurrency(rc.riskAmount, 'INR')}</td>
                <td>{rc.problemType.replace(/_/g, ' ')}</td>
                <td>
                  {rc.recoveryScore !== null ? (
                    <div className="score-badge">
                      {rc.recoveryScore}
                    </div>
                  ) : (
                    <span className="text-muted">N/A</span>
                  )}
                </td>
                <td>{formatDate(rc.detectedAt)}</td>
                <td>
                  <button className="btn-icon">View →</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        
        {data && data.totalPages > 1 && (
          <div className="pagination">
            <span className="pagination-info">
              Showing page {data.page + 1} of {data.totalPages} ({data.totalElements} total)
            </span>
            <div className="pagination-controls">
              <button 
                disabled={data.page === 0} 
                onClick={() => handlePageChange(data.page - 1)}
                className="btn-page"
              >
                Previous
              </button>
              <button 
                disabled={data.page === data.totalPages - 1} 
                onClick={() => handlePageChange(data.page + 1)}
                className="btn-page"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="page-container">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h1 className="display-heading" style={{ fontSize: '2.5rem' }}>Recovery Cases</h1>
          <p className="hero-subtitle">Monitor, investigate and resolve revenue recovery opportunities.</p>
        </div>
        {datasetStatus?.active && (
          <button 
            className="dataset-action-btn primary" 
            onClick={handleGenerateCases}
            disabled={generating || loading}
          >
            {generating ? 'Generating cases...' : 'Generate Recovery Cases'}
          </button>
        )}
      </div>

      <div className="toolbar">
        <div className="filter-group">
          <label>Status</label>
          <select 
            value={searchParams.get('status') || ''} 
            onChange={(e) => handleFilterChange('status', e.target.value)}
            className="filter-input"
          >
            <option value="">All Statuses</option>
            <option value="NEW">New</option>
            <option value="ANALYZING">Analyzing</option>
            <option value="ACTION_REQUIRED">Action Required</option>
            <option value="PENDING_APPROVAL">Pending Approval</option>
            <option value="EXECUTING">Executing</option>
            <option value="RECOVERED">Recovered</option>
            <option value="FAILED">Failed</option>
            <option value="DISMISSED">Dismissed</option>
            <option value="EXPIRED">Expired</option>
          </select>
        </div>

        <div className="filter-group">
          <label>Priority</label>
          <select 
            value={searchParams.get('priority') || ''} 
            onChange={(e) => handleFilterChange('priority', e.target.value)}
            className="filter-input"
          >
            <option value="">All Priorities</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>

        <div className="filter-group">
          <label>Min Amount (₹)</label>
          <input 
            type="number" 
            placeholder="0"
            className="filter-input input-number"
            value={searchParams.get('minAmount') || ''}
            onChange={(e) => handleFilterChange('minAmount', e.target.value)}
          />
        </div>

        <div className="filter-group">
          <label>Min Score</label>
          <input 
            type="number" 
            placeholder="0-100"
            className="filter-input input-number"
            value={searchParams.get('minScore') || ''}
            onChange={(e) => handleFilterChange('minScore', e.target.value)}
          />
        </div>
      </div>

      <div className="content-section">
        {renderContent()}
      </div>
    </div>
  );
};
