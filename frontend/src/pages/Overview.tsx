import React from 'react';
import './Overview.css';

export const Overview: React.FC = () => {
  return (
    <div className="page-container">
      <div className="hero-section">
        <h1 className="display-heading">Recover revenue intelligently.</h1>
        <p className="hero-subtitle">
          SecureAsset identifies payment failures, evaluates recovery risk, investigates cases using AI, and executes governed recovery actions.
        </p>
      </div>

      <div className="metrics-grid">
        <div className="metric-card skeleton-pulse">
          <div className="skeleton-text-sm"></div>
          <div className="skeleton-text-lg"></div>
        </div>
        <div className="metric-card skeleton-pulse">
          <div className="skeleton-text-sm"></div>
          <div className="skeleton-text-lg"></div>
        </div>
        <div className="metric-card skeleton-pulse">
          <div className="skeleton-text-sm"></div>
          <div className="skeleton-text-lg"></div>
        </div>
        <div className="metric-card skeleton-pulse">
          <div className="skeleton-text-sm"></div>
          <div className="skeleton-text-lg"></div>
        </div>
      </div>

      <div className="content-section">
        <h2 className="section-heading">Recent High-Risk Cases</h2>
        <div className="skeleton-table"></div>
      </div>
    </div>
  );
};
