import React from 'react';
import { useNavigate } from 'react-router-dom';
import './RecoveryCases.css';

export const RecoveryCases: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="page-container">
      <div className="page-header">
        <h1 className="display-heading" style={{ fontSize: '2.5rem' }}>Recovery Cases</h1>
        <p className="hero-subtitle">Monitor, investigate and resolve revenue recovery opportunities.</p>
      </div>

      <div className="toolbar skeleton-pulse">
        <div className="skeleton-pill"></div>
        <div className="skeleton-pill"></div>
        <div className="skeleton-pill"></div>
        <div className="skeleton-pill"></div>
      </div>

      {/* Temporarily clickable skeleton table to demonstrate routing to detail page */}
      <div 
        className="skeleton-table interactive" 
        onClick={() => navigate('/cases/C-12345')}
        title="Click to view case detail skeleton"
      >
        <div className="skeleton-row header"></div>
        <div className="skeleton-row"></div>
        <div className="skeleton-row"></div>
        <div className="skeleton-row"></div>
      </div>
    </div>
  );
};
