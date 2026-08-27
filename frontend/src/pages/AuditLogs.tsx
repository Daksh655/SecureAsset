import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getGlobalAuditLogs } from '../api/auditLogsApi';
import type { GlobalAuditLogDto } from '../api/auditLogsApi';
import type { PageResponse } from '../api/recoveryCasesApi';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { formatDate } from '../utils/formatters';
import './AuditLogs.css';

const EVENT_TYPES = [
  'CASE_CREATED', 'CASE_ANALYSIS_STARTED', 'TOOL_CALLED', 'TOOL_FAILED',
  'AGENT_RECOMMENDATION_CREATED', 'POLICY_CHECKED', 'ACTION_APPROVAL_REQUESTED',
  'ACTION_APPROVED', 'ACTION_REJECTED', 'ACTION_BLOCKED', 'RAZORPAY_REQUEST',
  'RAZORPAY_RESPONSE', 'RECOVERY_SUCCEEDED', 'RECOVERY_FAILED', 'CASE_DISMISSED',
  'CASE_EXPIRED', 'WEBHOOK_RECEIVED', 'WEBHOOK_SIGNATURE_VERIFIED',
  'WEBHOOK_DUPLICATE_IGNORED', 'PAYMENT_LINK_PAID', 'PAYMENT_LINK_PARTIALLY_PAID',
  'PAYMENT_LINK_CANCELLED', 'PAYMENT_LINK_EXPIRED', 'RECOVERY_CASE_RECOVERED',
  'WEBHOOK_RECONCILIATION_FAILED'
];

export const AuditLogs: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  
  const eventTypeParam = searchParams.get('eventType') || '';
  const caseIdParam = searchParams.get('caseId') || '';
  const currentPage = parseInt(searchParams.get('page') || '0', 10);

  const [data, setData] = useState<PageResponse<GlobalAuditLogDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Expanded details tracking
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const fetchLogs = useCallback(async (page: number, eventType: string, caseId: string) => {
    try {
      setLoading(true);
      setError(null);
      
      const params: Record<string, any> = {
        page,
        size: 20
      };
      if (eventType) params.eventType = eventType;
      if (caseId) params.caseId = caseId;

      const result = await getGlobalAuditLogs(params);
      setData(result);
    } catch (err: any) {
      setError(err.message || 'Failed to load audit logs');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLogs(currentPage, eventTypeParam, caseIdParam);
  }, [currentPage, eventTypeParam, caseIdParam, fetchLogs]);

  const updateFilters = (key: string, value: string) => {
    const newParams = new URLSearchParams(searchParams);
    if (value) {
      newParams.set(key, value);
    } else {
      newParams.delete(key);
    }
    // Reset page to 0 on filter change
    if (key !== 'page') {
      newParams.set('page', '0');
    }
    setSearchParams(newParams);
  };

  const toggleExpand = (id: string) => {
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const renderContent = () => {
    if (loading && !data) {
      return (
        <div className="timeline skeleton-pulse">
          {[1, 2, 3].map(i => (
            <div key={i} className="timeline-item">
              <div className="timeline-marker"></div>
              <div className="timeline-content" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)' }}>
                <div className="skeleton-text-sm"></div>
                <div className="skeleton-text-lg" style={{ marginTop: '0.5rem', height: '1.5rem', width: '50%' }}></div>
              </div>
            </div>
          ))}
        </div>
      );
    }

    if (error) {
      return <ErrorState message={error} onRetry={() => fetchLogs(currentPage, eventTypeParam, caseIdParam)} />;
    }

    if (!data || data.content.length === 0) {
      return (
        <EmptyState 
          title="No Events Found" 
          message="No audit events match the current filters."
        />
      );
    }

    return (
      <>
        <div className="timeline">
          {data.content.map(log => {
            const isSuccess = log.success;
            const markerColor = isSuccess ? 'var(--success-color)' : 'var(--error-color)';
            const markerBg = isSuccess ? 'var(--success-light)' : 'var(--error-light)';
            const isExpanded = expandedIds.has(log.id);

            return (
              <div key={log.id} className="timeline-item">
                <div className="timeline-marker" style={{ borderColor: markerColor, backgroundColor: markerBg }}></div>
                <div className="timeline-content" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                    <div>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        {formatDate(log.createdAt)}
                      </span>
                      <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'var(--text-primary)', marginTop: '0.25rem' }}>
                        {log.eventType}
                      </h3>
                    </div>
                    <span className="badge-agent-status" style={{ fontSize: '0.75rem', padding: '0.25rem 0.5rem', borderRadius: '4px', background: 'var(--bg-tertiary)', border: '1px solid var(--border-color)' }}>
                      {log.actorType}
                    </span>
                  </div>

                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1rem', lineHeight: '1.5' }}>
                    {log.message}
                  </p>

                  <div style={{ display: 'flex', gap: '1rem', fontSize: '0.875rem' }}>
                    {log.recoveryCaseId && (
                      <span>Case: <a href={`/recovery-cases/${log.recoveryCaseId}`} onClick={(e) => { e.preventDefault(); navigate(`/recovery-cases/${log.recoveryCaseId}`); }} style={{ color: 'var(--brand-primary)', textDecoration: 'none' }}>{log.recoveryCaseId.substring(0, 8)}</a></span>
                    )}
                    {log.toolName && (
                      <span>Tool: <strong style={{ color: 'var(--text-primary)' }}>{log.toolName}</strong></span>
                    )}
                  </div>

                  {/* Note: In Phase 2, detailed JSON payloads like inputData/outputData are not fully exposed to avoid leaking secrets, but if we need a details toggle for long messages or future data, it goes here */}
                  {log.message.length > 200 && (
                    <div style={{ marginTop: '1rem', borderTop: '1px solid var(--border-color)', paddingTop: '1rem' }}>
                      <button 
                        onClick={() => toggleExpand(log.id)}
                        style={{ background: 'none', border: 'none', color: 'var(--brand-primary)', cursor: 'pointer', fontSize: '0.875rem', padding: 0 }}
                      >
                        {isExpanded ? 'Hide Details' : 'Show Details'}
                      </button>
                      {isExpanded && (
                        <div style={{ marginTop: '1rem', padding: '1rem', background: 'var(--bg-primary)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-color)', fontSize: '0.875rem', fontFamily: 'monospace', whiteSpace: 'pre-wrap', color: 'var(--text-secondary)', maxHeight: '300px', overflowY: 'auto' }}>
                          {log.message}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {data.totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '2rem', padding: '1rem 0', borderTop: '1px solid var(--border-color)' }}>
            <button 
              className="dataset-action-btn" 
              disabled={currentPage === 0 || loading}
              onClick={() => updateFilters('page', String(currentPage - 1))}
              style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)' }}
            >
              Previous
            </button>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
              Page {currentPage + 1} of {data.totalPages}
            </span>
            <button 
              className="dataset-action-btn" 
              disabled={currentPage >= data.totalPages - 1 || loading}
              onClick={() => updateFilters('page', String(currentPage + 1))}
              style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)' }}
            >
              Next
            </button>
          </div>
        )}
      </>
    );
  };

  return (
    <div className="page-container">
      <div className="page-header" style={{ marginBottom: '2rem' }}>
        <h1 className="display-heading" style={{ fontSize: '2.5rem' }}>Audit Logs</h1>
        <p className="hero-subtitle">A complete record of AI decisions, policy checks, recovery actions, Razorpay activity, and webhook events.</p>
      </div>

      <div className="toolbar" style={{ display: 'flex', gap: '1rem', marginBottom: '2rem', flexWrap: 'wrap' }}>
        <select 
          value={eventTypeParam}
          onChange={(e) => updateFilters('eventType', e.target.value)}
          style={{ padding: '0.5rem', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-color)', background: 'var(--bg-primary)', color: 'var(--text-primary)', minWidth: '200px' }}
        >
          <option value="">All Event Types</option>
          {EVENT_TYPES.map(t => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>

        <input 
          type="text"
          placeholder="Filter by Case ID..."
          value={caseIdParam}
          onChange={(e) => updateFilters('caseId', e.target.value)}
          style={{ padding: '0.5rem', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-color)', background: 'var(--bg-primary)', color: 'var(--text-primary)', minWidth: '300px' }}
        />

        <button 
          className="dataset-action-btn"
          onClick={() => fetchLogs(currentPage, eventTypeParam, caseIdParam)}
          disabled={loading}
          style={{ background: 'var(--bg-secondary)', border: '1px solid var(--border-color)' }}
        >
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      {renderContent()}
    </div>
  );
};
