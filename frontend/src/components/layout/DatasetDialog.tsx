import React, { useState } from 'react';
import { generateDataset, resetDataset } from '../../api/datasetApi';
import { ApiError } from '../../api/client';
import { ErrorState } from '../ui/ErrorState';
import './DatasetDialog.css';

interface DatasetDialogProps {
  isOpen: boolean;
  onClose: () => void;
  isReset?: boolean;
}

export const DatasetDialog: React.FC<DatasetDialogProps> = ({ isOpen, onClose, isReset = false }) => {
  const [size, setSize] = useState<'SMALL' | 'MEDIUM' | 'LARGE'>('MEDIUM');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleConfirm = async () => {
    setLoading(true);
    setError(null);
    try {
      if (isReset) {
        await resetDataset();
      } else {
        await generateDataset(size);
      }
      window.dispatchEvent(new Event('dataset-changed'));
      onClose();
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 409 && !isReset) {
          setError('A dataset already exists or an operation is in progress. Please reset first.');
        } else {
          setError(err.message);
        }
      } else {
        setError('An unexpected error occurred.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="dialog-overlay">
      <div className="dialog-content">
        <h3 className="dialog-title">
          {isReset ? 'Reset Dataset' : 'New Demo Dataset'}
        </h3>
        
        <div className="dialog-body">
          {error && (
            <div style={{ marginBottom: '16px' }}>
              <ErrorState message={error} onRetry={() => setError(null)} />
            </div>
          )}

          {isReset ? (
            <>
              <p className="dialog-message">
                Reset the current synthetic demo dataset?
              </p>
              <p className="dialog-message-sub">
                This removes the current synthetic demo data and returns SecureAsset to an empty demo state.
              </p>
            </>
          ) : (
            <>
              <p className="dialog-message">
                Generate a fresh synthetic dataset.
              </p>
              <p className="dialog-message-sub warning">
                If an active dataset already exists, generating a new dataset is unavailable until you reset it according to the backend contract.
              </p>
              
              <div className="dialog-form-group">
                <label>Dataset size:</label>
                <select 
                  className="dialog-select"
                  value={size}
                  onChange={(e) => setSize(e.target.value as 'SMALL' | 'MEDIUM' | 'LARGE')}
                  disabled={loading}
                >
                  <option value="SMALL">Small (50 cust, 100 ord, 150 pay)</option>
                  <option value="MEDIUM">Medium (100 cust, 200 ord, 300 pay)</option>
                  <option value="LARGE">Large (500 cust, 1000 ord, 1500 pay)</option>
                </select>
              </div>
            </>
          )}
        </div>

        <div className="dialog-actions">
          <button className="dialog-btn-cancel" onClick={onClose} disabled={loading}>
            Cancel
          </button>
          <button 
            className={`dialog-btn-confirm ${isReset ? 'danger' : 'primary'}`} 
            onClick={handleConfirm}
            disabled={loading}
          >
            {loading ? (isReset ? 'Resetting...' : 'Generating...') : (isReset ? 'Reset Dataset' : 'Generate')}
          </button>
        </div>
      </div>
    </div>
  );
};
