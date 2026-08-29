import React, { useEffect, useState, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getGlobalAuditLogs, getCaseAuditLogs } from '../api/auditLogsApi';
import type { GlobalAuditLogDto, AuditLogDto } from '../api/auditLogsApi';
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
  'WEBHOOK_RECONCILIATION_FAILED',
];

const eventTypeColor = (eventType: string): string => {
  if (eventType.startsWith('TOOL_FAILED') || eventType.startsWith('RECOVERY_FAILED')) return 'var(--error-color)';
  if (eventType.startsWith('RAZORPAY') || eventType.startsWith('PAYMENT_LINK_PAID') || eventType.startsWith('RECOVERY_CASE_RECOVERED')) return 'var(--success-color)';
  if (eventType.startsWith('ACTION_APPROVED')) return 'var(--success-color)';
  if (eventType.startsWith('ACTION_REJECTED')) return 'var(--error-color)';
  return 'var(--brand-primary)';
};

// ── Case Detail Panel ──────────────────────────────────────────────────────────

interface CaseDetailPanelProps {
  caseId: string;
  onBack: () => void;
}

const CaseDetailPanel: React.FC<CaseDetailPanelProps> = ({ caseId, onBack }) => {
  const [logs, setLogs] = useState<AuditLogDto[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getCaseAuditLogs(caseId)
      .then(setLogs)
      .catch((err: any) => setError(err.message || 'Failed to load audit events'))
      .finally(() => setLoading(false));
  }, [caseId]);

  return (
    <div className="page-container">
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
        <button
          onClick={onBack}
          style={{
            background: 'var(--bg-secondary)',
            border: '1px solid var(--border-color)',
            borderRadius: 'var(--radius-sm)',
            padding: '0.5rem 1rem',
            cursor: 'pointer',
            color: 'var(--text-primary)',
            fontSize: '0.875rem',
          }}
        >
          ← Back to Audit Logs
        </button>
        <div>
          <h1 className="display-heading" style={{ fontSize: '1.75rem', marginBottom: '0.25rem' }}>
            Case Audit History
          </h1>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', fontFamily: 'monospace' }}>
            {caseId}
          </p>
        </div>
      </div>

      {loading && (
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
      )}

      {error && <ErrorState message={error} onRetry={() => {
        setLoading(true);
        setError(null);
        getCaseAuditLogs(caseId).then(setLogs).catch((e: any) => setError(e.message)).finally(() => setLoading(false));
      }} />}

      {!loading && !error && logs !== null && logs.length === 0 && (
        <EmptyState title="No Audit Events" message="No audit events have been recorded for this case." />
      )}

      {!loading && !error && logs !== null && logs.length > 0 && (
        <div className="timeline">
          {logs.map(log => {
            const color = eventTypeColor(log.eventType);
            return (
              <div key={log.id} className="timeline-item">
                <div className="timeline-marker" style={{ borderColor: color, backgroundColor: log.success ? 'var(--success-light)' : 'var(--error-light)' }}></div>
                <div className="timeline-content" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                    <div>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        {formatDate(log.createdAt)}
                      </span>
                      <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color, marginTop: '0.25rem' }}>
                        {log.eventType}
                      </h3>
                    </div>
                    <span style={{ fontSize: '0.75rem', padding: '0.25rem 0.5rem', borderRadius: '4px', background: 'var(--bg-tertiary)', border: '1px solid var(--border-color)', color: 'var(--text-secondary)' }}>
                      {log.actorType}
                    </span>
                  </div>
                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', lineHeight: '1.5', marginBottom: log.toolName ? '0.75rem' : 0 }}>
                    {log.message}
                  </p>
                  {log.toolName && (
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                      Tool: <strong style={{ color: 'var(--text-primary)' }}>{log.toolName}</strong>
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

// ── Main Audit Logs Page ───────────────────────────────────────────────────────

export const AuditLogs: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const eventTypeParam = searchParams.get('eventType') || '';
  const caseIdParam = searchParams.get('caseId') || '';
  const currentPage = parseInt(searchParams.get('page') || '0', 10);
  // When a case is selected for drill-down
  const selectedCaseId = searchParams.get('selectedCase') || '';

  const [data, setData] = useState<PageResponse<GlobalAuditLogDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  const fetchLogs = useCallback(async (page: number, eventType: string, caseId: string) => {
    try {
      setLoading(true);
      setError(null);
      const params: Record<string, any> = { page, size: 20 };
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
    if (!selectedCaseId) {
      fetchLogs(currentPage, eventTypeParam, caseIdParam);
    }
  }, [currentPage, eventTypeParam, caseIdParam, fetchLogs, selectedCaseId]);

  const updateFilters = (key: string, value: string) => {
    const newParams = new URLSearchParams(searchParams);
    if (value) newParams.set(key, value);
    else newParams.delete(key);
    if (key !== 'page') newParams.set('page', '0');
    setSearchParams(newParams);
  };

  const openCaseDetail = (caseId: string) => {
    const newParams = new URLSearchParams(searchParams);
    newParams.set('selectedCase', caseId);
    setSearchParams(newParams);
  };

  const closeCaseDetail = () => {
    const newParams = new URLSearchParams(searchParams);
    newParams.delete('selectedCase');
    setSearchParams(newParams);
  };

  const toggleExpand = (id: string) => {
    setExpandedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  // ── Show Case Detail panel when a case is selected ──
  if (selectedCaseId) {
    return <CaseDetailPanel caseId={selectedCaseId} onBack={closeCaseDetail} />;
  }

  // ── Global audit log list ──
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
      return <EmptyState title="No Events Found" message="No audit events match the current filters." />;
    }

    return (
      <>
        <div className="timeline">
          {data.content.map(log => {
            const isSuccess = log.success;
            const markerColor = isSuccess ? 'var(--success-color)' : 'var(--error-color)';
            const markerBg = isSuccess ? 'var(--success-light)' : 'var(--error-light)';
            const isExpanded = expandedIds.has(log.id);
            const color = eventTypeColor(log.eventType);

            return (
              <div key={log.id} className="timeline-item">
                <div className="timeline-marker" style={{ borderColor: markerColor, backgroundColor: markerBg }}></div>
                <div className="timeline-content" style={{ padding: '1.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                    <div>
                      <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        {formatDate(log.createdAt)}
                      </span>
                      <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color, marginTop: '0.25rem' }}>
                        {log.eventType}
                      </h3>
                    </div>
                    <span style={{ fontSize: '0.75rem', padding: '0.25rem 0.5rem', borderRadius: '4px', background: 'var(--bg-tertiary)', border: '1px solid var(--border-color)', color: 'var(--text-secondary)' }}>
                      {log.actorType}
                    </span>
                  </div>

                  <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '1rem', lineHeight: '1.5' }}>
                    {log.message}
                  </p>

                  <div style={{ display: 'flex', gap: '1rem', fontSize: '0.875rem', flexWrap: 'wrap' }}>
                    {log.recoveryCaseId && (
                      <span>
                        Case:{' '}
                        <button
                          onClick={() => openCaseDetail(log.recoveryCaseId!)}
                          style={{ background: 'none', border: 'none', color: 'var(--brand-primary)', cursor: 'pointer', padding: 0, fontSize: '0.875rem', textDecoration: 'underline' }}
                        >
                          {log.recoveryCaseId.substring(0, 8)}…
                        </button>
                      </span>
                    )}
                    {log.toolName && (
                      <span>Tool: <strong style={{ color: 'var(--text-primary)' }}>{log.toolName}</strong></span>
                    )}
                  </div>

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
              Page {currentPage + 1} of {data.totalPages} · {data.totalElements} events
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
          {EVENT_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
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

      {!loading && data && (
        <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', marginBottom: '1rem' }}>
          Click a <strong>Case ID</strong> link on any event to view the full audit history for that case.
        </p>
      )}

      {renderContent()}
    </div>
  );
};
