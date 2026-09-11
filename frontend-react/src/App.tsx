import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { Dashboard } from './pages/Dashboard'
import { EvidenceRepository } from './pages/EvidenceRepository'
import { EvidenceDetail } from './pages/EvidenceDetail'
import { EvidenceQuery } from './pages/EvidenceQuery'
import { EvidenceLifecycle } from './pages/EvidenceLifecycle'
import { PredefinedQueries } from './pages/PredefinedQueries'
import { BulkUpload } from './pages/BulkUpload'
import { Scheduler } from './pages/Scheduler'
import { Applications } from './pages/Applications'
import { Onboarding } from './pages/Onboarding'
import { Admin } from './pages/Admin'
import { Agents } from './pages/Agents'
import { ControlResults } from './pages/ControlResults'
import { Completeness } from './pages/Completeness'
import { Compliance } from './pages/Compliance'
import { Leadership } from './pages/Leadership'
import { Comparison } from './pages/Comparison'
import { Enterprise } from './pages/Enterprise'
import { AuditPrep } from './pages/AuditPrep'
import { Trend } from './pages/Trend'
import { Reports } from './pages/Reports'
import { EvidenceReuse } from './pages/EvidenceReuse'
import { NlQuery } from './pages/NlQuery'

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="evidence" element={<EvidenceRepository />} />
        <Route path="evidence/query" element={<EvidenceQuery />} />
        <Route path="evidence-lifecycle" element={<EvidenceLifecycle />} />
        <Route path="predefined-queries" element={<PredefinedQueries />} />
        <Route path="evidence/:id" element={<EvidenceDetail />} />
        <Route path="bulk-upload" element={<BulkUpload />} />
        <Route path="scheduler" element={<Scheduler />} />
        <Route path="control-results" element={<ControlResults />} />
        <Route path="completeness" element={<Completeness />} />
        <Route path="compliance" element={<Compliance />} />
        <Route path="leadership" element={<Leadership />} />
        <Route path="comparison" element={<Comparison />} />
        <Route path="enterprise" element={<Enterprise />} />
        <Route path="audit-prep" element={<AuditPrep />} />
        <Route path="trend" element={<Trend />} />
        <Route path="reports" element={<Reports />} />
        <Route path="reuse" element={<EvidenceReuse />} />
        <Route path="nl-query" element={<NlQuery />} />
        <Route path="applications" element={<Applications />} />
        <Route path="onboarding" element={<Onboarding />} />
        <Route path="admin" element={<Admin />} />
        <Route path="agents" element={<Agents />} />
        <Route path="*" element={<p className="page">Not found.</p>} />
      </Route>
    </Routes>
  )
}
