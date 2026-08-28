import { Navigate, Route, Routes } from 'react-router-dom';
import { AgentShell } from './layout/AgentShell';
import { AgentQueuePage } from './pages/AgentQueuePage';
import { AgentCallPage } from './pages/AgentCallPage';
import { CustomerTestPage } from './pages/CustomerTestPage';
import { InfraLoadTestPage } from './pages/InfraLoadTestPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/agent" replace />} />
      <Route path="/agent" element={<AgentShell />}>
        <Route index element={<AgentQueuePage />} />
        <Route path="call/:sessionId" element={<AgentCallPage />} />
      </Route>
      <Route path="/customer-test" element={<CustomerTestPage />} />
      <Route path="/infra-load-test" element={<InfraLoadTestPage />} />
    </Routes>
  );
}
