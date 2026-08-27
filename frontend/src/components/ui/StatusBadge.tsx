import React from 'react';
import './StatusBadge.css';

interface StatusBadgeProps {
  status: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const getStyle = (s: string) => {
    switch (s?.toUpperCase()) {
      case 'RECOVERED':
        return 'badge-success';
      case 'FAILED':
        return 'badge-failure';
      case 'ACTION_REQUIRED':
      case 'PENDING_APPROVAL':
        return 'badge-warning';
      case 'NEW':
      case 'ANALYZING':
      case 'EXECUTING':
        return 'badge-info';
      case 'DISMISSED':
      case 'EXPIRED':
      default:
        return 'badge-neutral';
    }
  };

  const formatText = (s: string) => {
    if (!s) return '';
    return s.replace(/_/g, ' ');
  };

  return (
    <span className={`status-badge ${getStyle(status)}`}>
      {formatText(status)}
    </span>
  );
};
