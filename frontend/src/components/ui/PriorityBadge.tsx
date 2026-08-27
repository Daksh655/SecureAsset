import React from 'react';
import './PriorityBadge.css';

interface PriorityBadgeProps {
  priority: string;
}

export const PriorityBadge: React.FC<PriorityBadgeProps> = ({ priority }) => {
  const getStyle = (p: string) => {
    switch (p?.toUpperCase()) {
      case 'HIGH':
        return 'badge-high';
      case 'MEDIUM':
        return 'badge-medium';
      case 'LOW':
        return 'badge-low';
      default:
        return 'badge-default';
    }
  };

  return (
    <span className={`priority-badge ${getStyle(priority)}`}>
      {priority}
    </span>
  );
};
