package com.wfm.service;

import com.wfm.config.TenantContext;
import com.wfm.model.Schedule;
import com.wfm.model.ScheduleStatus;
import com.wfm.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // TODO: merge in-memory (current) schedule with database (accepted) schedules
        return List.of();
    }

    public Schedule getScheduleDetail(UUID deskId, UUID scheduleId, String date) {
        // In-memory first, then database
        return inMemoryStore.get(scheduleId)
                .orElseGet(() -> scheduleRepository.findByIdAndTenantIdAndDeskId(
                        scheduleId, TenantContext.getTenantId(), deskId).orElse(null));
    }

    @Transactional
    public Schedule acceptSchedule(UUID deskId, UUID scheduleId) {
        // TODO: validate status is COMPLETED or STOPPED
        // TODO: persist schedule + assignments to database in single tx
        // TODO: delete overlapping accepted schedules
        // TODO: remove from in-memory store
        return null;
    }

    public void rejectSchedule(UUID deskId, UUID scheduleId) {
        // TODO: validate status is COMPLETED, STOPPED, or FAILED
        // TODO: remove from in-memory store
        inMemoryStore.remove(scheduleId);
    }
}
