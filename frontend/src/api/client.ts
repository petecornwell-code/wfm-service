/**
 * API client for the WFM Service backend.
 * All requests include the X-Tenant-ID header.
 */

const API_BASE = '/api/v1'

// For development, default tenant ID = 1
let currentTenantId = '1'

export function getTenantId() {
  return currentTenantId
}

export function setTenantId(id: string) {
  currentTenantId = id
}

// --- Error types ---
export interface ApiErrorDetail {
  field?: string
  message: string
  value?: string
}

export interface ApiErrorBody {
  code: string
  message: string
  details?: ApiErrorDetail[]
}

export class ApiRequestError extends Error {
  status: number
  code: string
  details: ApiErrorDetail[]

  constructor(status: number, error: ApiErrorBody) {
    super(error.message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = error.code
    this.details = error.details ?? []
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-ID': currentTenantId,
      ...options.headers,
    },
  })

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    if (body?.error) {
      throw new ApiRequestError(response.status, body.error)
    }
    throw new ApiRequestError(response.status, {
      code: 'UNKNOWN',
      message: body?.message || response.statusText || `HTTP ${response.status}`,
    })
  }

  if (response.status === 204) return undefined as T
  return response.json()
}

/** Extract user-friendly error message from any caught error */
export function getErrorMessage(err: unknown): string {
  if (err instanceof ApiRequestError) {
    if (err.details.length > 0) {
      return err.details.map(d => d.field ? `${d.field}: ${d.message}` : d.message).join('; ')
    }
    return err.message
  }
  if (err instanceof Error) return err.message
  return String(err)
}

// --- Desks ---
export const desks = {
  list: () => request<Desk[]>('/desks'),
  create: (data: CreateDeskRequest) => request<Desk>('/desks', { method: 'POST', body: JSON.stringify(data) }),
  get: (id: string) => request<Desk>(`/desks/${id}`),
  update: (id: string, data: Partial<CreateDeskRequest>) => request<Desk>(`/desks/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  delete: (id: string) => request<void>(`/desks/${id}`, { method: 'DELETE' }),
}

// --- Agents (tenant-level) ---
export const agents = {
  list: (params?: { search?: string; unassigned?: boolean; cursor?: string; limit?: number }) => {
    const query = new URLSearchParams()
    if (params?.search) query.set('search', params.search)
    if (params?.unassigned) query.set('unassigned', 'true')
    if (params?.cursor) query.set('cursor', params.cursor)
    if (params?.limit) query.set('limit', String(params.limit))
    return request<PaginatedResponse<Agent>>(`/agents?${query}`)
  },
  get: (id: string) => request<Agent>(`/agents/${id}`),
  daysOff: (agentId: string, from?: string, to?: string) => {
    const query = new URLSearchParams()
    if (from) query.set('from', from)
    if (to) query.set('to', to)
    return request<DayOff[]>(`/agents/${agentId}/days-off?${query}`)
  },
}

// --- Days Off (bulk) ---
export const daysOff = {
  listAll: (from?: string, to?: string) => {
    const query = new URLSearchParams()
    if (from) query.set('from', from)
    if (to) query.set('to', to)
    query.set('limit', '1000')
    return request<PaginatedResponse<DayOffWithAgent>>(`/days-off?${query}`)
  },
  listForDesk: (deskId: string, from: string, to: string) =>
    request<DayOffWithAgent[]>(`/desks/${deskId}/days-off?from=${from}&to=${to}`),
}

// --- Desk Agents ---
export const deskAgents = {
  list: (deskId: string, params?: { search?: string; cursor?: string }) => {
    const query = new URLSearchParams()
    if (params?.search) query.set('search', params.search)
    if (params?.cursor) query.set('cursor', params.cursor)
    return request<PaginatedResponse<DeskAgent>>(`/desks/${deskId}/agents?${query}`)
  },
  assign: (deskId: string, agentIds: string[]) =>
    request<DeskAgent[]>(`/desks/${deskId}/agents`, { method: 'POST', body: JSON.stringify({ agentIds }) }),
  remove: (deskId: string, agentId: string) =>
    request<void>(`/desks/${deskId}/agents/${agentId}`, { method: 'DELETE' }),
  setSpecializations: (deskId: string, agentId: string, data: SpecializationAssignment) =>
    request<DeskAgent>(`/desks/${deskId}/agents/${agentId}/specializations`, { method: 'PUT', body: JSON.stringify(data) }),
  setContractedHours: (deskId: string, agentId: string, hours: number) =>
    request<DeskAgent>(`/desks/${deskId}/agents/${agentId}/contracted-hours`, { method: 'PUT', body: JSON.stringify({ contractedHoursPerDay: hours }) }),
  refresh: (deskId: string) =>
    request<void>(`/desks/${deskId}/agents/refresh`, { method: 'POST' }),
  exportToExcel: (deskId: string) =>
    fetch(`${API_BASE}/desks/${deskId}/agents/export`, {
      headers: { 'X-Tenant-ID': currentTenantId },
    }),
  uploadPreferences: async (deskId: string, file: File): Promise<PreferenceUploadResult> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch(`${API_BASE}/desks/${deskId}/agents/preferences/upload`, {
      method: 'POST',
      headers: { 'X-Tenant-ID': currentTenantId },
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      if (body?.error) throw new ApiRequestError(response.status, body.error)
      throw new ApiRequestError(response.status, { code: 'UNKNOWN', message: body?.message || response.statusText })
    }
    return response.json()
  },
}

// --- Specializations ---
export const specializations = {
  list: (deskId: string) => request<Specialization[]>(`/desks/${deskId}/specializations`),
  create: (deskId: string, name: string, color?: string) =>
    request<Specialization>(`/desks/${deskId}/specializations`, { method: 'POST', body: JSON.stringify({ name, color }) }),
  update: (deskId: string, id: string, name: string, color?: string) =>
    request<Specialization>(`/desks/${deskId}/specializations/${id}`, { method: 'PUT', body: JSON.stringify({ name, color }) }),
  delete: (deskId: string, id: string) =>
    request<void>(`/desks/${deskId}/specializations/${id}`, { method: 'DELETE' }),
}

// --- Timeslots ---
export const timeslots = {
  list: (deskId: string, from: string, to: string) =>
    request<Timeslot[]>(`/desks/${deskId}/timeslots?from=${from}&to=${to}`),
  bounds: (deskId: string) =>
    request<TimeslotBounds | undefined>(`/desks/${deskId}/timeslots/bounds`),
  generate: (deskId: string, data: GenerateTimeslotsRequest) =>
    request<Timeslot[]>(`/desks/${deskId}/timeslots/generate`, { method: 'POST', body: JSON.stringify(data) }),
  delete: (deskId: string, from: string, to: string) =>
    request<void>(`/desks/${deskId}/timeslots?from=${from}&to=${to}`, { method: 'DELETE' }),
}

// --- Staffing Requirements ---
export const staffingRequirements = {
  list: (deskId: string, params?: { from?: string; to?: string; cursor?: string }) => {
    const query = new URLSearchParams()
    if (params?.from) query.set('from', params.from)
    if (params?.to) query.set('to', params.to)
    if (params?.cursor) query.set('cursor', params.cursor)
    return request<PaginatedResponse<StaffingRequirement>>(`/desks/${deskId}/staffing-requirements?${query}`)
  },
  save: (deskId: string, requirements: StaffingRequirementItem[]) =>
    request<StaffingRequirementResponse>(`/desks/${deskId}/staffing-requirements`, { method: 'POST', body: JSON.stringify({ requirements }) }),
  calculateErlangX: (deskId: string, data: ErlangXRequest) =>
    request<StaffingRequirementResponse>(`/desks/${deskId}/staffing-requirements/erlang-x`, { method: 'POST', body: JSON.stringify(data) }),
  uploadFtes: async (deskId: string, file: File): Promise<FteUploadResult> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch(`${API_BASE}/desks/${deskId}/staffing-requirements/upload`, {
      method: 'POST',
      headers: { 'X-Tenant-ID': currentTenantId },
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      if (body?.error) throw new ApiRequestError(response.status, body.error)
      throw new ApiRequestError(response.status, { code: 'UNKNOWN', message: body?.message || response.statusText })
    }
    return response.json()
  },
}

// --- Constraint Weights ---
export const constraintWeights = {
  get: (deskId: string) => request<ConstraintWeightsData>(`/desks/${deskId}/constraint-weights`),
  update: (deskId: string, data: Partial<ConstraintWeightsData>) =>
    request<ConstraintWeightsData>(`/desks/${deskId}/constraint-weights`, { method: 'PUT', body: JSON.stringify(data) }),
}

// --- Schedules ---
export const schedules = {
  solve: (deskId: string, data: SolveRequest) =>
    request<ScheduleSummary>(`/desks/${deskId}/schedules/solve`, { method: 'POST', body: JSON.stringify(data) }),
  list: (deskId: string, params?: { cursor?: string }) => {
    const query = new URLSearchParams()
    if (params?.cursor) query.set('cursor', params.cursor)
    return request<PaginatedResponse<ScheduleSummary>>(`/desks/${deskId}/schedules?${query}`)
  },
  get: (deskId: string, id: string, date?: string) => {
    const query = date ? `?date=${date}` : ''
    return request<ScheduleDetail>(`/desks/${deskId}/schedules/${id}${query}`)
  },
  stop: (deskId: string, id: string) =>
    request<ScheduleSummary>(`/desks/${deskId}/schedules/${id}/stop`, { method: 'PUT' }),
  accept: (deskId: string, id: string, version: number) =>
    request<ScheduleSummary>(`/desks/${deskId}/schedules/${id}/accept?version=${version}`, { method: 'PUT' }),
  reject: (deskId: string, id: string) =>
    request<void>(`/desks/${deskId}/schedules/${id}/reject`, { method: 'PUT' }),
  export: (deskId: string, id: string) =>
    fetch(`${API_BASE}/desks/${deskId}/schedules/${id}/export`, {
      headers: { 'X-Tenant-ID': currentTenantId },
    }),
  delete: (deskId: string, id: string) =>
    request<void>(`/desks/${deskId}/schedules/${id}`, { method: 'DELETE' }),
}

// --- Preferences ---
export const preferences = {
  list: (deskId: string, agentId: string, from?: string, to?: string) => {
    const query = new URLSearchParams()
    if (from) query.set('from', from)
    if (to) query.set('to', to)
    return request<AgentPreference[]>(`/desks/${deskId}/agents/${agentId}/preferences?${query}`)
  },
  save: (deskId: string, agentId: string, prefs: AgentPreference[]) =>
    request<AgentPreference[]>(`/desks/${deskId}/agents/${agentId}/preferences`, { method: 'PUT', body: JSON.stringify(prefs) }),
  delete: (deskId: string, agentId: string, prefId: string) =>
    request<void>(`/desks/${deskId}/agents/${agentId}/preferences/${prefId}`, { method: 'DELETE' }),
}

// --- Exceptions ---
export const exceptions = {
  list: (deskId: string, agentId: string, from?: string, to?: string) => {
    const query = new URLSearchParams()
    if (from) query.set('from', from)
    if (to) query.set('to', to)
    return request<AgentException[]>(`/desks/${deskId}/agents/${agentId}/exceptions?${query}`)
  },
  save: (deskId: string, agentId: string, excs: AgentException[]) =>
    request<AgentException[]>(`/desks/${deskId}/agents/${agentId}/exceptions`, { method: 'PUT', body: JSON.stringify(excs) }),
  delete: (deskId: string, agentId: string, date: string) =>
    request<void>(`/desks/${deskId}/agents/${agentId}/exceptions/${date}`, { method: 'DELETE' }),
}

// --- Types ---
export interface Desk { id: string; name: string; description?: string; defaultContractedHoursPerDay: number }
export interface CreateDeskRequest { name: string; description?: string; defaultContractedHoursPerDay?: number }
export interface Agent { id: string; name: string; email: string; department: string; jobTitle: string; active: boolean; lastRefreshedAt: string }
export interface DeskAgent { id: string; deskId: string; bamboohrId: string; name: string; email: string; department: string; jobTitle: string; active: boolean; lastRefreshedAt: string; primarySpecialization?: Specialization; secondarySpecializations: Specialization[]; contractedHoursPerDay?: number; effectiveContractedHoursPerDay: number }
export interface Specialization { id: string; name: string; color?: string }
export interface SpecializationAssignment { primarySpecializationId: string; secondarySpecializationIds: string[] }
export interface Timeslot { id: string; date: string; startTime: string; endTime: string }
export interface TimeslotBounds { periodStart: string; periodEnd: string; startTime: string; endTime: string; incrementMinutes: number }
export interface GenerateTimeslotsRequest { periodStartDate: string; periodEndDate: string; startTime: string; endTime: string; incrementMinutes: number }
export interface StaffingRequirement { id: string; timeslotId: string; specializationId: string; date: string; startTime: string; endTime: string; specializationName: string; requiredFTEs: number; source: string }
export interface StaffingRequirementItem { timeslotId: string; specializationId: string; requiredFTEs: number }
export interface StaffingRequirementResponse { requirements: StaffingRequirement[] }
export interface ErlangXRequest { from: string; to: string; parameters: ErlangXParam[] }
export interface ErlangXParam { timeslotId: string; specializationId: string; callVolume: number; aht: number; patience: number; retryRate: number; serviceLevelTarget: number; serviceLevelThreshold: number }
export interface DayOff { id: string; date: string; type: string }
export interface DayOffWithAgent { id: string; date: string; type: string; agent: { id: string; name: string } | null }
export interface AgentPreference { id?: string; dayOfWeek: string; date?: string; isStanding: boolean; preferredStartTime?: string; preferredBreakTime?: string }
export interface AgentException { id?: string; date: string; contractedHoursOverride: number; reason: string }
export interface Score { hardScore: number; softScore: number }
export interface ConstraintWeightsData { [key: string]: Score }
export interface SolveRequest { periodStartDate: string; periodEndDate: string; startTime: string; endTime: string; incrementMinutes: number; [key: string]: unknown }
export interface ScheduleSummary { id: string; deskId: string; deskName?: string; status: string; periodStartDate: string; periodEndDate: string; startTime: string; endTime: string; incrementMinutes: number; score?: Score; feasible?: boolean; createdAt: string; version: number }

export interface StaffingSummaryEntry {
  date: string | null
  specializationName: string
  predictedHours: number
  actualHours: number
  deltaHours: number
  coveragePct: number
}

export interface AssignmentDetail {
  timeslotId: string
  startTime: string
  endTime: string
  specializationName: string
  matchType: string
}

export interface BreakDetail {
  startTime: string
  endTime: string
  durationMinutes: number
}

export interface AgentScheduleEntry {
  agentId: string
  agentName: string
  date: string
  shiftStart: string
  shiftEnd: string
  totalHours: number
  assignments: AssignmentDetail[]
  breaks: BreakDetail[]
}

export interface PreferenceReportEntry {
  agentId: string
  agentName: string
  date: string
  preferenceSource: string
  preferredStartTime: string | null
  actualStartTime: string | null
  startTimeHonoured: boolean
  preferredBreakTime: string | null
  actualBreakTime: string | null
  breakTimeHonoured: boolean
}

export interface PreferenceSummary {
  totalPreferences: number
  startTimeHonouredCount: number
  breakTimeHonouredCount: number
  overallHonouredPct: number
}

export interface PreferenceReport {
  entries: PreferenceReportEntry[]
  summary: PreferenceSummary
}

export interface ViolationDetail {
  agentId: string | null
  agentName: string | null
  timeslotId: string | null
  timeslotLabel: string | null
  description: string
}

export interface ConstraintViolationEntry {
  constraintName: string
  level: string
  weight: Score
  violationCount: number
  totalPenalty: Score
  violations: ViolationDetail[]
}

export interface ScheduleDetail extends ScheduleSummary {
  staffingSummary: StaffingSummaryEntry[]
  agentSchedule: AgentScheduleEntry[]
  preferenceReport: PreferenceReport | null
  constraintViolations: ConstraintViolationEntry[]
  violatedHardConstraints: string[]
  warnings?: string[]
  errorMessage?: string
}

export interface PreferenceUploadResult { savedCount: number; skippedCount: number; skippedDetails: string[] }
export interface FteUploadResult { savedCount: number; skippedCount: number; savedDetails: string[]; skippedDetails: string[]; periodStart: string; periodEnd: string; startTime: string; endTime: string; incrementMinutes: number }
export interface PaginatedResponse<T> { data: T[]; nextCursor?: string; hasMore: boolean; totalCount: number }

export interface BambooEmployeeResponse { id: string; displayName: string; workEmail: string; department: string; jobTitle: string; status: string }

// --- App Configuration ---
export const appConfiguration = {
  get: () => request<Record<string, string>>('/configuration'),
  update: (config: Record<string, string>) =>
    request<Record<string, string>>('/configuration', { method: 'PUT', body: JSON.stringify(config) }),
}

// --- Client Management ---
export const clientManagement = {
  listEmployees: (department: string, page = 1, pageSize = 20, refresh = false) =>
    request<PaginatedResponse<BambooEmployeeResponse>>(`/client-management/employees?department=${encodeURIComponent(department)}&page=${page}&pageSize=${pageSize}&refresh=${refresh}`),
  assignToDesk: (deskId: string, bambooEmployeeIds: string[]) =>
    request<Agent[]>(`/client-management/assign-to-desk`, { method: 'POST', body: JSON.stringify({ deskId, bambooEmployeeIds }) }),
  removeAgentFromDesk: (deskId: string, agentId: string) =>
    request<void>(`/client-management/desks/${deskId}/agents/${agentId}`, { method: 'DELETE' }),
  exportEmployees: (department: string) =>
    fetch(`${API_BASE}/client-management/employees/export?department=${encodeURIComponent(department)}`, {
      headers: { 'X-Tenant-ID': currentTenantId },
    }),
  uploadDeskAssignments: async (file: File): Promise<DeskAssignmentUploadResult> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch(`${API_BASE}/client-management/upload-desk-assignments`, {
      method: 'POST',
      headers: { 'X-Tenant-ID': currentTenantId },
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      if (body?.error) throw new ApiRequestError(response.status, body.error)
      throw new ApiRequestError(response.status, { code: 'UNKNOWN', message: body?.message || response.statusText })
    }
    return response.json()
  },
}

export interface DeskAssignmentUploadResult { assignedCount: number; skippedCount: number; assignedDetails: string[]; skippedDetails: string[] }
