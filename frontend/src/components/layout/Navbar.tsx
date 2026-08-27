import React, { useState, useEffect } from 'react';
import { NavLink, Link } from 'react-router-dom';
import { Shield } from 'lucide-react';
import './Navbar.css';
import clsx from 'clsx';

export const Navbar: React.FC = () => {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div className={clsx('navbar-wrapper', scrolled && 'scrolled')}>
      <nav className={clsx('navbar', scrolled && 'scrolled')}>
        <Link to="/" className="navbar-brand">
          <Shield className="navbar-brand-icon" size={24} />
          SecureAsset
        </Link>
        
        <div className="navbar-nav">
          <NavLink 
            to="/" 
            className={({ isActive }) => clsx('nav-link', isActive && 'active')}
            end
          >
            Overview
          </NavLink>
          <NavLink 
            to="/cases" 
            className={({ isActive }) => clsx('nav-link', isActive && 'active')}
          >
            Recovery Cases
          </NavLink>
          <NavLink 
            to="/actions" 
            className={({ isActive }) => clsx('nav-link', isActive && 'active')}
          >
            Recovery Actions
          </NavLink>
          <NavLink 
            to="/audit" 
            className={({ isActive }) => clsx('nav-link', isActive && 'active')}
          >
            Audit Logs
          </NavLink>
        </div>
      </nav>
    </div>
  );
};
