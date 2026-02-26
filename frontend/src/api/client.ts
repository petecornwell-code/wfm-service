/**
 * API client for the WFM Service backend.
 * All requests include the X-Tenant-ID header.
 */

const API_BASE = '/api/v1'

// For development, default tenant ID = 1
let currentTenantId = '1'

export function setTenantId(id: string) {
  currentTenantId = id
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
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(error.error?.message || error.message || `HTTP ${response.status}`)
  }

  if (response.status === 204) return undefined as T
  return response.json()
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
    request<DeskAgent[]>(`/desks/${deskId}/agents/refresh`, { method: 'POST' }),
}

// --- Specializations ---
export const specializations = {
  list: (deskId: string) => request<Specialization[]>(`/desks/${deskId}/specializations`),
  create: (deskId: string, name: string) =>
    request<Specialization>(`/desks/${deskId}/specializations`, { method: 'POST', body: JSON.stringify({ name }) }),
  update: (deskId: string, id: string, name: string) =>
    request<Specialization>(`/desks/${deskId}/specializations/${id}`, { method: 'PUT', body: JSON.stringify({ name }) }),
  delete: (deskId: string, id: string) =>
    request<void>(`/desks/${deskId}/specializations/${id}`, { method: 'DELETE' }),
}

// --- Timeslots ---
export const timeslots = {
  list: (deskId: string, from: string, to: string) =>
    request<Timeslot[]>(`/desks/${deskId}/timeslots?from=${from}&to=${to}`),
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
  accept: (deskId: string, id: string) =>
    request<ScheduleDetail>(`/desks/${deskId}/schedules/${id}/accept`, { method: 'PUT' }),
  reject: (deskId: string, id: string) =>
    request<void>(`/desks/${deskId}/schedules/${id}/reject`, { method: 'PUT' }),
  export: (deskId: string, id: string) =>
    fetch(`${API_BASE}/desks/${deskId}/schedules/${id}/export`, {
      headers: { 'X-Tenant-ID': currentTenantId },
    }),
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
export interface DeskAgent { id: string; deskId: string; agent: Agent; primarySpecialization?: Specialization; secondarySpecializations: Specialization[]; contractedHoursPerDay?: number; effectiveContractedHoursPerDay: number }
export interface Specialization { id: string; name: string }
export interface SpecializationAssignment { primarySpecializationId: string; secondarySpecializationIds: string[] }
export interface Timeslot { id: string; date: string; startTime: string; endTime: string }
export interface GenerateTimeslotsRequest { periodStartDate: string; periodEndDate: string; startTime: string; endTime: string; incrementMinutes: number }
export interface StaffingRequirement { id: string; timeslotId: string; specializationId: string; date: string; startTime: string; endTime: string; specializationName: string; requiredAgents: number; source: string }
export interface StaffingRequirementItem { timeslotId: string; specializationId: string; requiredAgents: number }
export interface StaffingRequirementResponse { requirements: StaffingRequirement[] }
export interface ErlangXRequest { from: string; to: string; parameters: ErlangXParam[] }
export interface ErlangXParam { timeslotId: string; specializationId: string; callVolume: number; aht: number; patience: number; retryRate: number; serviceLevelTarget: number; serviceLevelThreshold: number }
export interface DayOff { id: string; date: string; type: string }
export interface AgentPreference { id?: string; dayOfWeek: string; date?: string; isStanding: boolean; preferredStartTime?: string; preferredBreakTime?: string }
export interface AgentException { id?: string; date: string; contractedHoursOverride: number; reason: string }
export interface Score { hardScore: number; softScore: number }
export interface ConstraintWeightsData { [key: string]: Score }
export interface SolveRequest { periodStartDate: string; periodEndDate: string; startTime: string; endTime: string; incrementMinutes: number; [key: string]: unknown }
export interface ScheduleSummary { id: string; deskId: string; status: string; periodStartDate: string; periodEndDate: string; startTime: string; endTime: string; incrementMinutes: number; score?: Score; feasible?: boolean; createdAt: string }
export interface ScheduleDetail extends ScheduleSummary { staffingSummary: unknown[]; agentSchedule: unknown[]; preferenceReport: unknown; constraintViolations: unknown[]; violatedHardConstraints: string[]; errorMessage?: string }
export interface PaginatedResponse<T> { data: T[]; nextCursor?: string; hasMore: boolean }
