import React, { useState } from 'react';
import './RecoveryActions.css';

export const RecoveryActions: React.FC = () => {
  const [activeTab, setActiveTab] = useState('Pending Approval');
  
  const tabs = ['Pending Approval', 'Executing', 'Completed', 'Failed', 'Rejected'];

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
            onClick={() => setActiveTab(tab)}
          >
            {tab}
          </button>
        ))}
      </div>

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
    </div>
  );
};
