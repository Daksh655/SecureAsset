import React from 'react';
import { Navbar } from './Navbar';
import './PageShell.css';

interface PageShellProps {
  children: React.ReactNode;
}

export const PageShell: React.FC<PageShellProps> = ({ children }) => {
  return (
    <div className="page-shell">
      <Navbar />
      <main className="page-content">
        {children}
      </main>
    </div>
  );
};
