import React, { useState, useEffect } from 'react';
import { NavLink, Link } from 'react-router-dom';
import { ShieldCheck, Plus } from 'lucide-react';
import { DatasetDialog } from './DatasetDialog';
import './Navbar.css';

export const Navbar: React.FC = () => {
  const [scrolled, setScrolled] = useState(false);
  const [isDatasetDialogOpen, setIsDatasetDialogOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <>
      <header className={`navbar-container ${scrolled ? 'scrolled' : ''}`}>
        <div className="navbar-inner">
          <Link to="/" className="navbar-logo">
            <ShieldCheck className="logo-icon" size={24} />
            <span className="logo-text">SecureAsset</span>
          </Link>

          <nav className="navbar-nav">
            <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Overview
            </NavLink>
            <NavLink to="/cases" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Recovery Cases
            </NavLink>
            <NavLink to="/actions" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Recovery Actions
            </NavLink>
            <NavLink to="/audit" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
              Audit Logs
            </NavLink>
          </nav>

          <div className="navbar-actions">
            <button 
              className="new-dataset-btn"
              onClick={() => setIsDatasetDialogOpen(true)}
            >
              <Plus size={16} /> New Dataset
            </button>
          </div>
        </div>
      </header>
      
      {isDatasetDialogOpen && (
        <DatasetDialog 
          isOpen={isDatasetDialogOpen} 
          onClose={() => setIsDatasetDialogOpen(false)} 
        />
      )}
    </>
  );
};
