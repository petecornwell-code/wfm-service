/** Shared timeslot parameters persisted per-desk in localStorage. */

export interface TimeslotParams {
  periodStart: string
  periodEnd: string
  startTime: string
  endTime: string
  increment: number
}

/** All schedule-setup fields persisted per-desk in localStorage. */
export interface ScheduleSetupParams extends TimeslotParams {
  breakBlocked: number
  breakDuration: number
  breakMinShift: number
  breakAlignment: string
  breakCluster: number
  defaultHours: number
  overallocationLimit: number
  underallocationLimit: number
  solveTimeMinutes: number
}

const STORAGE_KEY = (deskId: string) => `wfm:timeslotParams:${deskId}`
const SETUP_KEY = (deskId: string) => `wfm:scheduleSetup:${deskId}`

export function saveTimeslotParams(deskId: string, params: TimeslotParams): void {
  localStorage.setItem(STORAGE_KEY(deskId), JSON.stringify(params))
}

export function loadTimeslotParams(deskId: string): Partial<TimeslotParams> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY(deskId))
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export function saveScheduleSetup(deskId: string, params: ScheduleSetupParams): void {
  localStorage.setItem(SETUP_KEY(deskId), JSON.stringify(params))
}

export function loadScheduleSetup(deskId: string): Partial<ScheduleSetupParams> {
  try {
    const raw = localStorage.getItem(SETUP_KEY(deskId))
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export function clearScheduleSetup(deskId: string): void {
  localStorage.removeItem(SETUP_KEY(deskId))
}
