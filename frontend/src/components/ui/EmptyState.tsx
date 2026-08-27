import React from 'react';
import { Database } from 'lucide-react';
import './EmptyState.css';

interface EmptyStateProps {
  title?: string;
  message?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title = 'No Data Available',
  message = 'There is currently no data to display here.'
}) => {
  return (
    <div className="empty-state">
      <div className="empty-icon-container">
        <Database className="empty-icon" size={32} />
      </div>
      <h3 className="empty-title">{title}</h3>
      <p className="empty-message">{message}</p>
    </div>
  );
};
