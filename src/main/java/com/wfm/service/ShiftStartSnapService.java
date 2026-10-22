package com.wfm.service;

import com.wfm.model.SchedulingMode;
import com.wfm.model.ShiftBandPair;
import com.wfm.model.ShiftTemplate;
import com.wfm.repository.DeskRepository;
import com.wfm.repository.ShiftTemplateRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Snaps a preferred start time to the nearest shift start on a shift-scheduled desk.
 *
 * <p>On a SHIFT desk an agent can only ever start at a shift template's start time, so a preferred
 * start no template begins at (Vinted test data: "16.00 - 01.00" against templates starting
 * 08:00-15:00) can never be met. Scored as a hard rule ("Preferred start (shift mode)" with a hard
 * weight) that is a guaranteed violation with nothing on screen saying why. Preferences are therefore
 * moved to the nearest template start when they are saved, and the caller reports the change.
 *
 * <p>"Nearest" is {@link ShiftBandPair#startDeviationMinutes} -- the same distance the constraint
 * penalises -- with ties going to the earlier start. Only templates that can actually be used on the
 * preference's day count: in effect on its date (a dated preference) or today (a standing one), and
 * valid on its weekday. SLOT desks, a start that already matches, and a day with no usable template
 * are all left untouched.
 */
@Service
public class ShiftStartSnapService {

    private final DeskRepository deskRepository;
    private final ShiftTemplateRepository shiftTemplateRepository;

    public ShiftStartSnapService(DeskRepository deskRepository, ShiftTemplateRepository shiftTemplateRepository) {
        this.deskRepository = deskRepository;
        this.shiftTemplateRepository = shiftTemplateRepository;
    }

    /** A preferred start that was moved: from {@code original} to {@code snapped}, the start of {@code templateName}. */
    public record Snap(LocalTime original, LocalTime snapped, String templateName) {}

    /** The desk's templates, loaded once, to snap any number of preferences against. */
    public Snapper forDesk(long tenantId, UUID deskId) {
        boolean shiftDesk = deskRepository.findByIdAndTenantId(deskId, tenantId)
                .map(d -> d.getSchedulingMode() == SchedulingMode.SHIFT)
                .orElse(false);
        return new Snapper(shiftDesk ? shiftTemplateRepository.findByTenantIdAndDeskId(tenantId, deskId) : List.of());
    }

    public static final class Snapper {
        private final List<ShiftTemplate> templates;

        Snapper(List<ShiftTemplate> templates) {
            this.templates = templates;
        }

        /**
         * The snap for one preference, or empty when nothing changes.
         *
         * @param date      the preference's date, or null for a standing (weekly) preference
         * @param dayOfWeek the weekday it applies to
         */
        public Optional<Snap> snap(LocalTime start, LocalDate date, DayOfWeek dayOfWeek) {
            if (start == null || templates.isEmpty()) {
                return Optional.empty();
            }
            LocalDate effectiveDate = date != null ? date : LocalDate.now();
            List<ShiftTemplate> usable = templates.stream()
                    .filter(t -> t.isEffectiveOn(effectiveDate))
                    .filter(t -> dayOfWeek == null || t.getValidWeekdays().contains(dayOfWeek))
                    .toList();
            if (usable.isEmpty() || usable.stream().anyMatch(t -> t.getStartTime().equals(start))) {
                return Optional.empty();
            }
            ShiftTemplate nearest = usable.stream()
                    .min(Comparator.comparingInt((ShiftTemplate t) -> ShiftBandPair.startDeviationMinutes(t.getStartTime(), start))
                            .thenComparing(ShiftTemplate::getStartTime)
                            .thenComparing(ShiftTemplate::getName))
                    .orElseThrow();
            return Optional.of(new Snap(start, nearest.getStartTime(), nearest.getName()));
        }
    }
}
