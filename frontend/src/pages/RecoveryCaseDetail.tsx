 import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, AlertCircle, CheckCircle, FileText, User, CreditCard, ShieldAlert } from 'lucide-react';
import { getRecoveryCase, getRecoveryActions, getCaseAuditLogs, investigateRecoveryCase } from '../api/recoveryCasesApi';
import type { RecoveryCaseDetailDto, RecoveryActionDto, AuditLogDto } from '../api/recoveryCasesApi';
import { ErrorState } from '../components/ui/ErrorState';
import { StatusBadge } from '../components/ui/StatusBadge';
import { PriorityBadge } from '../components/ui/PriorityBadge';
import { formatCurrency, formatDate } from '../utils/formatters';
import './RecoveryCaseDetail.css';

export const RecoveryCaseDetail: React.FC = () => {
  const { caseId } = useParams<{ caseId: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('Overview');
  
  const [caseData, setCaseData] = useState<RecoveryCaseDetailDto | null>(null);
  const [actionsData, setActionsData] = useState<RecoveryActionDto[]>([]);
  const [auditData, setAuditData] = useState<AuditLogDto[]>([]);
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const [isInvestigating, setIsInvestigating] = useState(false);
  const [investigationError, setInvestigationError] = useState<string | null>(null);

  const tabs = ['Overview', 'Investigation', 'Payment', 'Recovery', 'Audit'];

  const fetchData = useCallback(async () => {
    if (!caseId) return;
    
    try {
      setLoading(true);
      setError(null);
      
      const caseResult = await getRecoveryCase(caseId);
      setCaseData(caseResult);
      
      // We can fetch actions and audit logs in parallel or lazily
      // For now, let's fetch them when their tab is clicked or right away
      const [actionsResult, auditResult] = await Promise.all([
        getRecoveryActions(caseId),
        getCaseAuditLogs(caseId)
      ]);
      
      setActionsData(actionsResult);
      setAuditData(auditResult);
      
    } catch (err: any) {
      if (err.status === 404) {
        setError('Case not found');
      } else {
        setError(err.message || 'Failed to load recovery case details');
      }
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  const handleInvestigate = async () => {
    if (!caseId || isInvestigating) return;
    
    try {
      setIsInvestigating(true);
      setInvestigationError(null);
      
      const updatedCase = await investigateRecoveryCase(caseId);
      setCaseData(updatedCase);
      
      // Refresh actions and audit logs in case the agent created actions or logs
      const [actionsResult, auditResult] = await Promise.all([
        getRecoveryActions(caseId),
        getCaseAuditLogs(caseId)
      ]);
      setActionsData(actionsResult);
      setAuditData(auditResult);
      
    } catch (err: any) {
      if (err.status === 404) {
        setInvestigationError('Case not found.');
      } else if (err.status === 409) {
        setInvestigationError('An investigation is already running for this case.');
      } else {
        setInvestigationError(err.message || 'AI Investigation failed.');
      }
    } finally {
      setIsInvestigating(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="page-container case-detail">
        <button onClick={() => navigate(-1)} className="back-link" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
          <ArrowLeft size={16} /> Back to Cases
        </button>
        <div className="case-header">
          <div>
            <div className="skeleton-text-lg" style={{ width: '200px', marginBottom: '1rem' }}></div>
            <div className="case-meta skeleton-pulse">
              <div className="skeleton-badge"></div>
              <div className="skeleton-badge"></div>
            </div>
          </div>
        </div>
        <div className="tab-content skeleton-pulse" style={{ marginTop: '2rem' }}>
          <div className="generic-section">
            <div className="skeleton-row" style={{ height: '200px' }}></div>
          </div>
        </div>
      </div>
    );
  }

  if (error || !caseData) {
    return (
      <div className="page-container case-detail">
        <button onClick={() => navigate(-1)} className="back-link" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, marginBottom: '2rem' }}>
          <ArrowLeft size={16} /> Back to Cases
        </button>
        <ErrorState message={error || 'An unexpected error occurred'} onRetry={fetchData} />
      </div>
    );
  }

  const renderOverviewTab = () => (
    <div className="detail-grid">
      <div className="detail-card">
        <div className="detail-card-header">
          <User size={18} /> Customer Information
        </div>
        <div className="detail-list">
          <div className="detail-item">
            <span className="detail-label">Name</span>
            <span className="detail-value">{caseData.customer.name}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Email</span>
            <span className="detail-value">{caseData.customer.email}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Customer ID</span>
            <span className="detail-value mono">{caseData.customer.id.substring(0, 8)}</span>
          </div>
        </div>
      </div>

      <div className="detail-card">
        <div className="detail-card-header">
          <AlertCircle size={18} /> Issue Details
        </div>
        <div className="detail-list">
          <div className="detail-item">
            <span className="detail-label">Problem Type</span>
            <span className="detail-value">{caseData.problemType.replace(/_/g, ' ')}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Risk Amount</span>
            <span className="detail-value highlight">{formatCurrency(caseData.riskAmount)}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Detected At</span>
            <span className="detail-value">{formatDate(caseData.detectedAt)}</span>
          </div>
        </div>
      </div>
    </div>
  );

  const renderInvestigationTab = () => {
    if (caseData.agentStatus === 'NOT_ANALYZED' || caseData.agentStatus === 'FAILED') {
      return (
        <div className="ai-section" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
          <h2 className="section-heading" style={{ marginBottom: '1rem' }}>AI Investigation</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '1.5rem' }}>
            {caseData.agentStatus === 'FAILED' 
              ? 'The previous AI investigation failed.' 
              : 'This case has not been investigated yet.'}
          </p>
          
          {investigationError && (
            <div className="error-state" style={{ marginBottom: '1.5rem', padding: '1rem', background: 'var(--error-light)', color: 'var(--error-dark)', borderRadius: 'var(--radius-md)' }}>
              <AlertCircle size={18} style={{ display: 'inline-block', verticalAlign: 'middle', marginRight: '0.5rem' }} />
              <span style={{ display: 'inline-block', verticalAlign: 'middle' }}>{investigationError}</span>
            </div>
          )}
          
          <button 
            className="dataset-action-btn primary" 
            style={{ width: 'auto', padding: '0.75rem 1.5rem', opacity: isInvestigating ? 0.7 : 1, cursor: isInvestigating ? 'not-allowed' : 'pointer' }}
            onClick={handleInvestigate}
            disabled={isInvestigating}
          >
            {isInvestigating ? 'Investigating...' : caseData.agentStatus === 'FAILED' ? 'Run Again' : 'Run AI Investigation'}
          </button>
        </div>
      );
    }
    
    if (caseData.agentStatus === 'ANALYZING' || isInvestigating) {
      return (
        <div className="ai-section" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
          <h2 className="section-heading" style={{ marginBottom: '1rem' }}>AI Investigation</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: '1.5rem' }}>
            The AI Agent is currently analyzing this case...
          </p>
          <button 
            className="dataset-action-btn primary" 
            style={{ width: 'auto', padding: '0.75rem 1.5rem', opacity: 0.7, cursor: 'not-allowed' }}
            disabled
          >
            Investigating...
          </button>
        </div>
      );
    }

    return (
      <div className="ai-section">
        <h2 className="section-heading">AI RECOMMENDATION: {caseData.agentRecommendation?.replace(/_/g, ' ') || 'NONE'}</h2>
        <div style={{ marginBottom: '1.5rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <span className="badge-agent-status">{caseData.agentStatus.replace(/_/g, ' ')}</span>
          {caseData.agentConfidence != null && (
            <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
              Confidence Score: <strong style={{ color: 'var(--text-primary)' }}>{caseData.agentConfidence.toFixed(0)}%</strong>
            </span>
          )}
          {caseData.analyzedAt && (
             <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                Analyzed: <strong style={{ color: 'var(--text-primary)' }}>{formatDate(caseData.analyzedAt)}</strong>
             </span>
          )}
        </div>
        <h3 className="subsection-heading">Rationale</h3>
        <p style={{ lineHeight: '1.6', color: 'var(--text-secondary)' }}>
          {caseData.agentReason || 'No detailed rationale provided by the agent.'}
        </p>
      </div>
    );
  };

  const renderPaymentTab = () => {
    if (!caseData.payment && !caseData.order) {
      return (
        <div className="empty-state">
          <p>No payment or order information is available for this recovery case.</p>
        </div>
      );
    }

    return (
      <div className="detail-grid">
        {caseData.payment ? (
          <div className="detail-card">
            <div className="detail-card-header">
              <CreditCard size={18} /> Payment Information
            </div>
            <div className="detail-list">
              <div className="detail-item">
                <span className="detail-label">Payment ID</span>
                <span className="detail-value mono">{caseData.payment.id.substring(0, 8)}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Amount</span>
                <span className="detail-value">{formatCurrency(caseData.payment.amount)}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Status</span>
                <span className="detail-value">{caseData.payment.status}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Failure Reason</span>
                <span className="detail-value">{caseData.payment.failureReason || 'N/A'}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Attempt Number</span>
                <span className="detail-value">{caseData.payment.attemptNumber}</span>
              </div>
            </div>
          </div>
        ) : (
          <div className="detail-card">
            <div className="detail-card-header">
              <CreditCard size={18} /> Payment Information
            </div>
            <div className="empty-state" style={{ minHeight: '150px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <p style={{ color: 'var(--text-secondary)' }}>No payment data available.</p>
            </div>
          </div>
        )}

        {caseData.order ? (
          <div className="detail-card">
            <div className="detail-card-header">
              <FileText size={18} /> Order Information
            </div>
            <div className="detail-list">
              <div className="detail-item">
                <span className="detail-label">Order ID</span>
                <span className="detail-value mono">{caseData.order.id.substring(0, 8)}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Order Amount</span>
                <span className="detail-value">{formatCurrency(caseData.order.amount, caseData.order.currency)}</span>
              </div>
              <div className="detail-item">
                <span className="detail-label">Order Status</span>
                <span className="detail-value">{caseData.order.status}</span>
              </div>
            </div>
          </div>
        ) : (
          <div className="detail-card">
            <div className="detail-card-header">
              <FileText size={18} /> Order Information
            </div>
            <div className="empty-state" style={{ minHeight: '150px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <p style={{ color: 'var(--text-secondary)' }}>No order data available.</p>
            </div>
          </div>
        )}
      </div>
    );
  };

  const renderRecoveryTab = () => {
    if (actionsData.length === 0) {
      return (
        <div className="empty-state">
          <p>No recovery actions have been initiated yet.</p>
        </div>
      );
    }
    
    return (
      <div className="actions-list">
        {actionsData.map(action => (
          <div key={action.id} className="action-card">
            <div className="action-header">
              <span className="action-type">{action.actionType.replace(/_/g, ' ')}</span>
              <span className="action-date">{formatDate(action.requestedAt)}</span>
            </div>
            <div className="action-details">
              <div className="action-detail">
                <span className="detail-label">Amount:</span> {formatCurrency(action.amount)}
              </div>
              <div className="action-detail">
                <span className="detail-label">Status:</span> {action.status}
              </div>
              <div className="action-detail">
                <span className="detail-label">Approval:</span> {action.approvalStatus}
              </div>
              {action.razorpayReference && (
                <div className="action-detail">
                  <span className="detail-label">Reference:</span> {action.razorpayReference}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    );
  };

  const renderAuditTab = () => {
    if (auditData.length === 0) {
      return (
        <div className="empty-state">
          <p>No audit logs available for this case.</p>
        </div>
      );
    }

    return (
      <div className="audit-timeline">
        {auditData.map(log => (
          <div key={log.id} className="audit-event">
            <div className="audit-time">{formatDate(log.createdAt)}</div>
            <div className="audit-content">
              <div className="audit-title">
                <strong>{log.actorType}</strong> - {log.eventType.replace(/_/g, ' ')}
                {log.success ? <CheckCircle size={14} color="var(--accent-green)" /> : <ShieldAlert size={14} color="var(--accent-red)" />}
              </div>
              <div className="audit-message">{log.message}</div>
              {log.toolName && <div className="audit-tool">Tool: {log.toolName}</div>}
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div className="page-container case-detail">
      <button onClick={() => navigate(-1)} className="back-link" style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>
        <ArrowLeft size={16} /> Back to Cases
      </button>
      
      <div className="case-header">
        <div>
          <h1 className="display-heading" style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>
            Case {caseId?.substring(0, 8)}
          </h1>
          <div className="case-meta">
            <StatusBadge status={caseData.status} />
            <PriorityBadge priority={caseData.priority} />
          </div>
        </div>
        <div className="case-metrics">
          <div className="metric-box">
            <div className="metric-label">Recovery Score</div>
            <div className="metric-value">{caseData.recoveryScore}</div>
          </div>
          <div className="metric-box">
            <div className="metric-label">Risk Amount</div>
            <div className="metric-value highlight">{formatCurrency(caseData.riskAmount)}</div>
          </div>
        </div>
      </div>

      <div className="tabs">
        {tabs.map(tab => (
          <button 
            key={tab}
            className={`tab ${activeTab === tab ? 'active' : ''}`}
            onClick={() => setActiveTab(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

      <div className="tab-content">
        {activeTab === 'Overview' && renderOverviewTab()}
        {activeTab === 'Investigation' && renderInvestigationTab()}
        {activeTab === 'Payment' && renderPaymentTab()}
        {activeTab === 'Recovery' && renderRecoveryTab()}
        {activeTab === 'Audit' && renderAuditTab()}
      </div>
    </div>
  );
};
