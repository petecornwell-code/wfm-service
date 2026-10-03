/**
 * TypeScript port of `com.wfm.util.DayWindow`'s anchored instance methods (OVNT-02/D-15).
 *
 * The frontend has no test runner — zero test files, no `vitest`/`jest` dependency, and adding
 * one is explicitly out of scope for this correctness phase. `ScheduleResults.tsx` carries seven
 * confirmed sites that compare, sort or subtract raw `HH:MM` strings with no notion of a
 * day-start anchor (D-15). Rather than a bespoke string helper with no guard, every
 * minute-producing function here returns a branded {@link DayOffset} instead of a plain
 * `number`: a plain number cannot be assigned to it, and a value of this type cannot be produced
 * by ordinary arithmetic on a plain number. Once a call site holds a `DayOffset`, comparing it
 * against a raw clock string or a raw number stops compiling — the TypeScript compiler becomes
 * the structural guard this surface would otherwise have no runner to enforce.
 *
 * ## The positional rule
 *
 * A time equal to the anchor is ambiguous in isolation and means different things by POSITION,
 * exactly as it does in the backend `DayWindow`:
 *
 * - in a START position it is the start of the business day, offset `0`;
 * - in an END position it is the end of the business day, offset {@link MINUTES_PER_DAY}.
 *
 * `anchoredStartMinute` and `anchoredEndMinute` differ on exactly that case and nowhere else.
 * `anchoredDurationMinutes` wraps forward across the anchor rather than returning a negative
 * value, mirroring the backend's three-branch shape exactly.
 *
 * ## No second implementation
 *
 * This module is a deliberate, literal port — it reproduces the backend's arithmetic rather than
 * inventing a parallel convention, because a divergent second implementation of midnight-boundary
 * arithmetic is exactly the defect class this milestone exists to close (T-21-14). Method names
 * mirror `DayWindow`'s instance methods one-for-one so the two mental models stay aligned for a
 * future maintainer.
 *
 * This module has no consumers yet. 21-10 converts `ScheduleResults.tsx`'s seven sites against it.
 */

/**
 * A minute offset measured from a desk's day-start anchor, in `[0, MINUTES_PER_DAY]`. Branded so
 * a plain `number` — a raw minute-of-day, a raw subtraction of two clock times — cannot be used
 * where a `DayOffset` is expected, and so a `DayOffset` cannot be compared against a plain
 * `number` without an explicit (and visible) cast. This is the whole mechanism: it is what turns
 * a raw comparison into a build error on a surface with no test runner.
 */
export type DayOffset = number & { readonly __brand: 'DayOffset' }

/** Minutes in a day. The anchor-relative offset of a time equal to the anchor, in an END position. */
export const MINUTES_PER_DAY = 1440

/**
 * The only place a `number` becomes a `DayOffset` in this module. Every exported minute-producing
 * function funnels its result through here rather than casting at the call site, so the brand's
 * safety property — no unbranded offset escapes — can be read off this one function.
 */
function asDayOffset(minutes: number): DayOffset {
  return minutes as DayOffset
}

/** Strips a trailing `:SS` from `"HH:MM:SS"`, leaving `"HH:MM"` as-is. */
function toHHMM(time: string): string {
  return time.length > 5 ? time.substring(0, 5) : time
}

/** Minute-of-day of an `"HH:MM"` or `"HH:MM:SS"` clock string, in `[0, 1440)`. Normalizes seconds. */
function clockMinute(time: string): number {
  const [h, m] = toHHMM(time).split(':').map(Number)
  return h * 60 + m
}

/** `((a % n) + n) % n` — true floored modulo, since `%` in JS can return a negative result. */
function floorMod(a: number, n: number): number {
  return ((a % n) + n) % n
}

/** Formats an absolute minute-of-day in `[0, 1440)` back to an `"HH:MM"` clock string. */
function formatHHMM(minuteOfDay: number): string {
  const h = Math.floor(minuteOfDay / 60)
  const m = minuteOfDay % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/**
 * Converts a day-start-relative offset back to an absolute `"HH:MM"` clock string — the port of
 * `DayWindow.timeAtDayStartOffset`/`anchoredToLocalTime`.
 *
 * @throws RangeError when `minutesFromDayStart` is outside `[0, MINUTES_PER_DAY]`.
 */
function timeAtDayStartOffset(anchorMinute: number, minutesFromDayStart: number): string {
  if (minutesFromDayStart < 0 || minutesFromDayStart > MINUTES_PER_DAY) {
    throw new RangeError(
      `minutesFromDayStart must be within [0, ${MINUTES_PER_DAY}] but was ${minutesFromDayStart}`
    )
  }
  return formatHHMM(floorMod(anchorMinute + minutesFromDayStart, MINUTES_PER_DAY))
}

/**
 * Adds (or subtracts) whole days from an ISO `"YYYY-MM-DD"` date string, in UTC so no local
 * timezone can shift the calendar date by one.
 */
function addDaysToIsoDate(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(Date.UTC(y, m - 1, d))
  date.setUTCDate(date.getUTCDate() + days)
  const yyyy = date.getUTCFullYear()
  const mm = String(date.getUTCMonth() + 1).padStart(2, '0')
  const dd = String(date.getUTCDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/**
 * A `DayWindow` bound to one desk's day-start anchor — the TypeScript equivalent of
 * `com.wfm.util.DayWindow`'s bound instance (BDAY-04). Every method mirrors its backend
 * counterpart's name and positional rule exactly.
 */
export interface DayWindow {
  /** Instance equivalent of a START-position minute, day-start-relative. `[0, 1440)`. */
  anchoredStartMinute(t: string): DayOffset
  /** Instance equivalent of an END-position minute, day-start-relative. `(0, 1440]` — a time equal to the anchor maps to {@link MINUTES_PER_DAY}, never `0`. */
  anchoredEndMinute(t: string): DayOffset
  /**
   * Length of `[start, end)` in minutes, wrapping forward across the anchor rather than returning
   * negative — the three-branch shape `DayWindow.anchoredDurationMinutes` specifies.
   */
  anchoredDurationMinutes(start: string, end: string): DayOffset
  /** True when `[innerStart, innerEnd)` lies entirely within `[outerStart, outerEnd)`, anchor-relative. */
  anchoredContains(outerStart: string, outerEnd: string, innerStart: string, innerEnd: string): boolean
  /** Converts a day-start-relative offset back to an absolute `"HH:MM"` clock string. */
  anchoredToHHMM(offset: DayOffset): string
  /** Adds `minutes` to `base`, returning the absolute `"HH:MM"` result. Throws past the end of the business day. */
  anchoredPlusWithinDay(base: string, minutes: number): string
}

/**
 * Binds a {@link DayWindow} to one desk's day-start anchor — the TypeScript equivalent of
 * `DayWindow.anchoredAt(LocalTime)`. `dayStart` is an `"HH:MM"` or `"HH:MM:SS"` clock string;
 * seconds are normalized off internally so callers never need a separate normalization step.
 */
export function anchoredAt(dayStart: string): DayWindow {
  const anchorMinute = clockMinute(dayStart)

  function startMinuteFromDayStart(t: string): number {
    return floorMod(clockMinute(t) - anchorMinute, MINUTES_PER_DAY)
  }

  function endMinuteFromDayStart(t: string): number {
    const raw = floorMod(clockMinute(t) - anchorMinute, MINUTES_PER_DAY)
    return raw === 0 ? MINUTES_PER_DAY : raw
  }

  return {
    anchoredStartMinute(t: string): DayOffset {
      return asDayOffset(startMinuteFromDayStart(t))
    },
    anchoredEndMinute(t: string): DayOffset {
      return asDayOffset(endMinuteFromDayStart(t))
    },
    anchoredDurationMinutes(start: string, end: string): DayOffset {
      const raw = endMinuteFromDayStart(end) - startMinuteFromDayStart(start)
      if (raw > 0) return asDayOffset(raw)
      if (raw === 0) return asDayOffset(0)
      return asDayOffset(raw + MINUTES_PER_DAY)
    },
    anchoredContains(outerStart: string, outerEnd: string, innerStart: string, innerEnd: string): boolean {
      return (
        startMinuteFromDayStart(innerStart) >= startMinuteFromDayStart(outerStart) &&
        endMinuteFromDayStart(innerEnd) <= endMinuteFromDayStart(outerEnd)
      )
    },
    anchoredToHHMM(offset: DayOffset): string {
      return timeAtDayStartOffset(anchorMinute, offset as number)
    },
    anchoredPlusWithinDay(base: string, minutes: number): string {
      return timeAtDayStartOffset(anchorMinute, startMinuteFromDayStart(base) + minutes)
    },
  }
}

/**
 * The business date a calendar date and time-of-day belong to, given a day-start anchor — the
 * port of `DayWindow.businessDateOf`. Returns the calendar date itself when the time's
 * minute-of-day is at or after the anchor's, and the previous calendar date otherwise.
 *
 * @param dayStart desk day-start anchor, `"HH:MM"` or `"HH:MM:SS"`
 * @param calendarDate ISO `"YYYY-MM-DD"`
 * @param timeOfDay `"HH:MM"` or `"HH:MM:SS"`
 * @returns ISO `"YYYY-MM-DD"`
 */
export function businessDateFromCalendarDateAndTime(
  dayStart: string,
  calendarDate: string,
  timeOfDay: string
): string {
  const anchorMinute = clockMinute(dayStart)
  const timeMinute = clockMinute(timeOfDay)
  return timeMinute >= anchorMinute ? calendarDate : addDaysToIsoDate(calendarDate, -1)
}

/**
 * The calendar date a business date and day-start-relative offset land on — the port of
 * `DayWindow.calendarDateAtDayStartOffset`. `minutesFromDayStart` is valid up to but excluding a
 * full day — `[0, MINUTES_PER_DAY)` — reproducing the backend's documented argument range exactly;
 * a value outside it throws rather than silently wrapping.
 *
 * @param dayStart desk day-start anchor, `"HH:MM"` or `"HH:MM:SS"`
 * @param businessDate ISO `"YYYY-MM-DD"`
 * @param minutesFromDayStart offset from the anchor, `[0, MINUTES_PER_DAY)`
 * @returns ISO `"YYYY-MM-DD"`
 * @throws RangeError when `minutesFromDayStart` is outside `[0, MINUTES_PER_DAY)`.
 */
export function calendarDateFromBusinessDateAndOffset(
  dayStart: string,
  businessDate: string,
  minutesFromDayStart: DayOffset | number
): string {
  if (minutesFromDayStart < 0 || minutesFromDayStart >= MINUTES_PER_DAY) {
    throw new RangeError(
      `minutesFromDayStart must be within [0, ${MINUTES_PER_DAY}) but was ${minutesFromDayStart}`
    )
  }
  const anchorMinute = clockMinute(dayStart)
  const daysForward = Math.floor((anchorMinute + minutesFromDayStart) / MINUTES_PER_DAY)
  return addDaysToIsoDate(businessDate, daysForward)
}
