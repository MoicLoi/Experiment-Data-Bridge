import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth/AuthProvider'
import { AppShell } from './components/AppShell'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'
import { TaskConsolePage } from './features/tasks/TaskConsolePage'
import { DataFileWorkbenchPage } from './features/datafiles/DataFileWorkbenchPage'
import { AgentWorkbenchPage } from './features/agent/AgentWorkbenchPage'
import { WorkflowEditorPage } from './features/workflows/WorkflowEditorPage'
import { AdminConsolePage } from './features/admin/AdminConsolePage'
import { AlgorithmLibraryPage } from './features/algorithms/AlgorithmLibraryPage'
import { DockerFleetPage } from './features/docker/DockerFleetPage'
import { DockerRebuildPage } from './features/docker/DockerRebuildPage'

function Protected() {
  const auth = useAuth(); const location = useLocation()
  if (!auth.ready) return <div className="boot">EDB<span /></div>
  return auth.token ? <AppShell /> : <Navigate to="/login" state={{ from: location.pathname }} replace />
}

export default function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<Protected />}>
      <Route index element={<OverviewPage />} />
      <Route path="tasks" element={<TaskConsolePage />} />
      <Route path="datafiles" element={<DataFileWorkbenchPage />} />
      <Route path="workflows" element={<WorkflowEditorPage />} />
      <Route path="agent" element={<AgentWorkbenchPage />} />
      <Route path="algorithms" element={<AlgorithmLibraryPage />} />
      <Route path="docker" element={<DockerFleetPage />} />
      <Route path="docker/rebuild" element={<DockerRebuildPage />} />
      <Route path="admin" element={<AdminConsolePage />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
