import React, { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import './RecoveryCaseDetail.css';

export const RecoveryCaseDetail: React.FC = () => {
  const { caseId } = useParams<{ caseId: string }>();
  const [activeTab, setActiveTab] = useState('Overview');
  
  const tabs = ['Overview', 'Investigation', 'Payment', 'Recovery', 'Audit'];

  return (
    <div className="page-container case-detail">
      <Link to="/cases" className="back-link">
        <ArrowLeft size={16} /> Back to Cases
      </Link>
      
      <div className="case-header">
        <div>
          <h1 className="display-heading" style={{ fontSize: '2rem', marginBottom: '0.5rem' }}>
            Case {caseId || '#A129'}
          </h1>
          <div className="case-meta skeleton-pulse">
            <div className="skeleton-badge"></div>
            <div className="skeleton-badge"></div>
          </div>
        </div>
        <div className="case-metrics skeleton-pulse">
          <div className="skeleton-metric"></div>
          <div className="skeleton-metric"></div>
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

      <div className="tab-content skeleton-pulse">
        {activeTab === 'Investigation' && (
          <div className="ai-section">
            <h2 className="section-heading">AI Recommendation</h2>
            <div className="skeleton-text-lg" style={{ marginBottom: '1.5rem' }}></div>
            <h3 className="subsection-heading">Rationale</h3>
            <div className="skeleton-text-sm" style={{ width: '100%', marginBottom: '0.5rem' }}></div>
            <div className="skeleton-text-sm" style={{ width: '90%', marginBottom: '0.5rem' }}></div>
            <div className="skeleton-text-sm" style={{ width: '95%' }}></div>
          </div>
        )}
        {activeTab !== 'Investigation' && (
          <div className="generic-section">
            <div className="skeleton-row"></div>
            <div className="skeleton-row"></div>
          </div>
        )}
      </div>
    </div>
  );
};
