/** Shared timeslot parameters persisted per-desk in localStorage. */

export interface TimeslotParams {
  periodStart: string
  periodEnd: string
  startTime: string
  endTime: string
  increment: number
}

const STORAGE_KEY = (deskId: string) => `wfm:timeslotParams:${deskId}`

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
