import { Routes, Route } from 'react-router-dom';
import { PageShell } from './components/layout/PageShell';
import { Overview } from './pages/Overview';
import { RecoveryCases } from './pages/RecoveryCases';
import { RecoveryCaseDetail } from './pages/RecoveryCaseDetail';
import { RecoveryActions } from './pages/RecoveryActions';
import { AuditLogs } from './pages/AuditLogs';
import './App.css';

function App() {
  return (
    <PageShell>
      <Routes>
        <Route path="/" element={<Overview />} />
        <Route path="/cases" element={<RecoveryCases />} />
        <Route path="/recovery-cases/:caseId" element={<RecoveryCaseDetail />} />
        <Route path="/actions" element={<RecoveryActions />} />
        <Route path="/audit" element={<AuditLogs />} />
      </Routes>
    </PageShell>
  );
}

export default App;
