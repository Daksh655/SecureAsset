import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getRecoveryActions, approveRecoveryAction, rejectRecoveryAction } from '../api/recoveryActionsApi';
import type { RecoveryActionSummaryDto, PageResponse } from '../api/recoveryActionsApi';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { StatusBadge } from '../components/ui/StatusBadge';
import { formatCurrency, formatDate } from '../utils/formatters';
import './RecoveryActions.css';

export const RecoveryActions: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  
  const activeTab = searchParams.get('tab') || 'Pending Approval';
  const currentPage = parseInt(searchParams.get('page') || '0', 10);
  
  const [data, setData] = useState<PageResponse<RecoveryActionSummaryDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Dialog state
  const [selectedAction, setSelectedAction] = useState<RecoveryActionSummaryDto | null>(null);
  const [reviewMode, setReviewMode] = useState<'APPROVE' | 'REJECT' | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [dialogError, setDialogError] = useState<string | null>(null);

  const tabs = ['Pending Approval', 'Executing', 'Completed', 'Failed', 'Rejected'];

  const fetchActions = useCallback(async (page: number, tab: string) => {
    try {
      setLoading(true);
      setError(null);
      
      const params: Record<string, any> = {
        page,
        size: 20
      };

      if (tab === 'Pending Approval') {
        params.approvalStatus = 'PENDING';
      } else if (tab === 'Executing') {
        params.status = 'EXECUTING';
      } else if (tab === 'Completed') {
        params.status = 'SUCCESS';
      } else if (tab === 'Failed') {
        params.status = 'FAILED';
      } else if (tab === 'Rejected') {
        params.approvalStatus = 'REJECTED';
      }

      const result = await getRecoveryActions(params);
      setData(result);
    } catch (err: any) {
      setError(err.message || 'Failed to load recovery actions');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchActions(currentPage, activeTab);
  }, [currentPage, activeTab, fetchActions]);

  const handleTabChange = (tab: string) => {
    setSearchParams({ tab, page: '0' });
  };

  const handleApprove = async () => {
    if (!selectedAction) return;
    try {
      setIsSubmitting(true);
      setDialogError(null);
      await approveRecoveryAction(selectedAction.recoveryCaseId, selectedAction.actionType, selectedAction.amount);
      closeDialog();
      fetchActions(currentPage, activeTab);
    } catch (err: any) {
      setDialogError(err.message || 'Approval failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReject = async () => {
    if (!selectedAction) return;
    try {
      setIsSubmitting(true);
      setDialogError(null);
      await rejectRecoveryAction(selectedAction.recoveryCaseId, rejectReason);
      closeDialog();
      fetchActions(currentPage, activeTab);
    } catch (err: any) {
      setDialogError(err.message || 'Rejection failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const closeDialog = () => {
    setSelectedAction(null);
    setReviewMode(null);
    setRejectReason('');
    setDialogError(null);
  };

  const renderContent = () => {
    if (loading && !data) {
      return (
        <div className="actions-queue skeleton-pulse">
          <div className="action-card">
            <div className="skeleton-text-lg"></div>
            <div className="skeleton-text-sm" style={{ marginTop: '1rem', width: '30%' }}></div>
            <div className="skeleton-row" style={{ height: '60px', marginTop: '1.5rem' }}></div>
          </div>
          <div className="action-card">
            <div className="skeleton-text-lg"></div>
            <div className="skeleton-text-sm" style={{ marginTop: '1rem', width: '30%' }}></div>
            <div className="skeleton-row" style={{ height: '60px', marginTop: '1.5rem' }}></div>
          </div>
        </div>
      );
    }

    if (error) {
      return <ErrorState message={error} onRetry={() => fetchActions(currentPage, activeTab)} />;
    }

    if (!data || data.content.length === 0) {
      return (
        <EmptyState 
          title="No Actions Found" 
          message="No recovery actions match the current filter." 
        />
      );
    }

    return (
      <>
        <div className="actions-queue">
          {data.content.map((action) => (
            <div key={action.id} className="action-card">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
                <div>
                  <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.25rem' }}>
                    {action.actionType.replace(/_/g, ' ')}
                  </h3>
                  <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', display: 'flex', gap: '1rem' }}>
                    <span>Amount: <strong style={{ color: 'var(--text-primary)' }}>{formatCurrency(action.amount)}</strong></span>
                    <span>
                      Case ID: <a 
                        href={`/recovery-cases/${action.recoveryCaseId}`} 
                        onClick={(e) => { e.preventDefault(); navigate(`/recovery-cases/${action.recoveryCaseId}`); }}
                        style={{ color: 'var(--brand-primary)', textDecoration: 'none', cursor: 'pointer' }}
                      >{action.recoveryCaseId.substring(0, 8)}</a>
                    </span>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <StatusBadge status={action.status} />
                  {action.approvalStatus !== 'NOT_REQUIRED' && (
                    <span className="badge-agent-status" style={{ fontSize: '0.75rem', padding: '0.25rem 0.5rem', borderRadius: '4px', background: 'var(--bg-tertiary)', border: '1px solid var(--border-color)' }}>
                      Approval: {action.approvalStatus}
                    </span>
                  )}
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem', padding: '1rem', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)' }}>
                {action.razorpayReference && (
                  <div>
                    <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Razorpay Ref</span>
                    <span style={{ fontSize: '0.875rem', fontFamily: 'monospace' }}>{action.razorpayReference}</span>
                  </div>
                )}
                {action.requestedAt && (
                  <div>
                    <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Requested</span>
                    <span style={{ fontSize: '0.875rem' }}>{formatDate(action.requestedAt)}</span>
                  </div>
                )}
                {action.approvedAt && (
                  <div>
                    <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Approved</span>
                    <span style={{ fontSize: '0.875rem' }}>{formatDate(action.approvedAt)}</span>
                  </div>
                )}
                {action.executedAt && (
                  <div>
                    <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Executed</span>
                    <span style={{ fontSize: '0.875rem' }}>{formatDate(action.executedAt)}</span>
                  </div>
                )}
                {action.completedAt && (
                  <div>
                    <span style={{ display: 'block', fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Completed</span>
                    <span style={{ fontSize: '0.875rem' }}>{formatDate(action.completedAt)}</span>
                  </div>
                )}
              </div>

              {action.errorMessage && (
                <div style={{ marginTop: '1rem', padding: '0.75rem', background: 'var(--error-light)', color: 'var(--error-dark)', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}>
                  <strong>Error:</strong> {action.errorMessage}
                </div>
              )}

              {activeTab === 'Pending Approval' && action.approvalStatus === 'PENDING' && (
                <div style={{ marginTop: '1.5rem', display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
                  <button 
                    className="dataset-action-btn" 
                    style={{ background: 'transparent', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }}
                    onClick={() => { setSelectedAction(action); setReviewMode('REJECT'); }}
                  >
                    Reject
                  </button>
                  <button 
                    className="dataset-action-btn primary"
                    onClick={() => { setSelectedAction(action); setReviewMode('APPROVE'); }}
                  >
                    Review
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>

        {data.totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '2rem', padding: '1rem 0', borderTop: '1px solid var(--border-color)' }}>
            <button 
              className="dataset-action-btn" 
              disabled={currentPage === 0 || loading}
              onClick={() => setSearchParams({ tab: activeTab, page: String(currentPage - 1) })}
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
              onClick={() => setSearchParams({ tab: activeTab, page: String(currentPage + 1) })}
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
      <div className="page-header">
        <h1 className="display-heading" style={{ fontSize: '2.5rem' }}>Recovery Actions</h1>
        <p className="hero-subtitle">Operations queue for manual approval and automated executions.</p>
      </div>

      <div className="tabs" style={{ marginBottom: '2rem' }}>
        {tabs.map(tab => (
          <button 
            key={tab}
            className={`tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => handleTabChange(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

      {renderContent()}

      {/* Dialog overlay for Approval/Rejection */}
      {selectedAction && reviewMode && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: 'var(--bg-primary)', padding: '2rem', borderRadius: 'var(--radius-lg)', width: '100%', maxWidth: '500px', boxShadow: 'var(--shadow-lg)' }}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '1rem' }}>
              {reviewMode === 'APPROVE' ? 'Approve Action' : 'Reject Action'}
            </h2>
            <p style={{ color: 'var(--text-secondary)', marginBottom: '1.5rem' }}>
              Action: <strong style={{ color: 'var(--text-primary)' }}>{selectedAction.actionType.replace(/_/g, ' ')}</strong><br/>
              Amount: <strong style={{ color: 'var(--text-primary)' }}>{formatCurrency(selectedAction.amount)}</strong><br/>
              Case ID: {selectedAction.recoveryCaseId}
            </p>

            {reviewMode === 'REJECT' && (
              <div style={{ marginBottom: '1.5rem' }}>
                <label style={{ display: 'block', fontSize: '0.875rem', marginBottom: '0.5rem', color: 'var(--text-secondary)' }}>Reason (optional)</label>
                <textarea 
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  style={{ width: '100%', padding: '0.75rem', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-color)', background: 'var(--bg-secondary)', color: 'var(--text-primary)', minHeight: '80px' }}
                  placeholder="Enter rejection reason..."
                />
              </div>
            )}

            {dialogError && (
              <div style={{ marginBottom: '1.5rem', padding: '0.75rem', background: 'var(--error-light)', color: 'var(--error-dark)', borderRadius: 'var(--radius-md)', fontSize: '0.875rem' }}>
                {dialogError}
              </div>
            )}

            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end' }}>
              <button 
                className="dataset-action-btn" 
                onClick={closeDialog}
                disabled={isSubmitting}
                style={{ background: 'transparent', border: '1px solid var(--border-color)', color: 'var(--text-primary)' }}
              >
                Cancel
              </button>
              <button 
                className={`dataset-action-btn primary`}
                onClick={reviewMode === 'APPROVE' ? handleApprove : handleReject}
                disabled={isSubmitting}
                style={reviewMode === 'REJECT' ? { background: 'var(--error-color)', borderColor: 'var(--error-color)' } : {}}
              >
                {isSubmitting ? 'Processing...' : reviewMode === 'APPROVE' ? 'Confirm Approval' : 'Confirm Rejection'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
