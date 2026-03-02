package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.exception.ConflictException;
import com.wfm.exception.EntityNotFoundException;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final InMemoryScheduleStore inMemoryStore;

    public ScheduleService(ScheduleRepository scheduleRepository, InMemoryScheduleStore inMemoryStore) {
        this.scheduleRepository = scheduleRepository;
        this.inMemoryStore = inMemoryStore;
    }

    public List<Schedule> listSchedules(UUID deskId, String cursor, int limit) {
        // TODO (Phase 5, Task 23): merge in-memory + DB accepted schedules with cursor pagination
        long tenantId = TenantContext.getTenantId();
        List<Schedule> result = new ArrayList<>();
        inMemoryStore.getByDeskId(deskId).ifPresent(result::add);
        result.addAll(scheduleRepository.findByTenantIdAndDeskIdOrderByCreatedAtDesc(
                tenantId, deskId, org.springframework.data.domain.PageRequest.of(0, limit)));
        return result;
    }

    public Schedule getScheduleDetail(UUID deskId, UUID scheduleId, String date) {
        return inMemoryStore.get(scheduleId)
                .orElseGet(() -> scheduleRepository.findByIdAndTenantIdAndDeskId(
                        scheduleId, TenantContext.getTenantId(), deskId).orElse(null));
    }

    @Transactional
    public Schedule acceptSchedule(UUID deskId, UUID scheduleId) {
        Schedule schedule = inMemoryStore.get(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + scheduleId));

        if (schedule.getStatus() != ScheduleStatus.COMPLETED
                && schedule.getStatus() != ScheduleStatus.STOPPED) {
            throw new ConflictException("Schedule must be COMPLETED or STOPPED to accept (status: "
                    + schedule.getStatus() + ")");
        }

        // TODO (Phase 5, Task 26): full accept implementation
        //   - snapshot timeslots, remap staffing requirements & assignments
        //   - delete overlapping accepted schedules
        //   - persist everything in single tx
        schedule.setStatus(ScheduleStatus.ACCEPTED);
        inMemoryStore.remove(scheduleId);
        return schedule;
    }

    public void rejectSchedule(UUID deskId, UUID scheduleId) {
        Schedule schedule = inMemoryStore.get(scheduleId)
                .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + scheduleId));

        if (schedule.getStatus() != ScheduleStatus.COMPLETED
                && schedule.getStatus() != ScheduleStatus.STOPPED
                && schedule.getStatus() != ScheduleStatus.FAILED) {
            throw new ConflictException("Schedule must be COMPLETED, STOPPED, or FAILED to reject (status: "
                    + schedule.getStatus() + ")");
        }

        inMemoryStore.remove(scheduleId);
    }
}
