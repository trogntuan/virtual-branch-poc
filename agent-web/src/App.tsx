import { Navigate, Route, Routes, Link } from 'react-router-dom';
import { AgentQueuePage } from './pages/AgentQueuePage';
import { AgentCallPage } from './pages/AgentCallPage';
import { CustomerTestPage } from './pages/CustomerTestPage';

export default function App() {
  return (
    <div className="app-shell">
      <nav className="top-nav">
        <strong>Chi nhánh số</strong>
        <div className="nav-links">
          <Link to="/agent">Hàng chờ tổng đài</Link>
          <Link to="/customer-test">Mô phỏng mobile</Link>
        </div>
      </nav>
      <main>
        <Routes>
          <Route path="/" element={<Navigate to="/agent" replace />} />
          <Route path="/agent" element={<AgentQueuePage />} />
          <Route path="/agent/call/:sessionId" element={<AgentCallPage />} />
          <Route path="/customer-test" element={<CustomerTestPage />} />
        </Routes>
      </main>
    </div>
  );
}
