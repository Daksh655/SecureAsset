import React from 'react';
import './DatasetDialog.css';

interface DatasetDialogProps {
  isOpen: boolean;
  onClose: () => void;
  isReset?: boolean;
}

export const DatasetDialog: React.FC<DatasetDialogProps> = ({ isOpen, onClose, isReset = false }) => {
  if (!isOpen) return null;

  return (
    <div className="dialog-overlay">
      <div className="dialog-content">
        <h3 className="dialog-title">
          {isReset ? 'Reset Dataset' : 'New Demo Dataset'}
        </h3>
        
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
              If a dataset already exists, generating a new dataset will replace the current DEMO dataset.
            </p>
            
            <div className="dialog-form-group">
              <label>Dataset size:</label>
              <select className="dialog-select">
                <option value="small">Small (~1,000 txns)</option>
                <option value="medium">Medium (~10,000 txns)</option>
                <option value="large">Large (~30,000 txns)</option>
              </select>
            </div>
          </>
        )}

        <div className="dialog-actions">
          <button className="dialog-btn-cancel" onClick={onClose}>
            Cancel
          </button>
          <button className={`dialog-btn-confirm ${isReset ? 'danger' : 'primary'}`} onClick={onClose}>
            {isReset ? 'Reset Dataset' : 'Generate'}
          </button>
        </div>
      </div>
    </div>
  );
};
