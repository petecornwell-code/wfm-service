package com.wfm.service;

import com.wfm.model.Schedule;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory store for non-accepted schedules.
 * Accepted schedules are persisted to the database; this store holds RUNNING/COMPLETED/STOPPED/FAILED.
 */
@Component
public class InMemoryScheduleStore {

    private final ConcurrentHashMap<UUID, Schedule> scheduleMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> deskToScheduleIndex = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void put(Schedule schedule) {
        lock.lock();
        try {
            scheduleMap.put(schedule.getId(), schedule);
            deskToScheduleIndex.put(schedule.getDeskId(), schedule.getId());
        } finally {
            lock.unlock();
        }
    }

    public Optional<Schedule> get(UUID scheduleId) {
        return Optional.ofNullable(scheduleMap.get(scheduleId));
    }

    public Optional<Schedule> getByDeskId(UUID deskId) {
        UUID scheduleId = deskToScheduleIndex.get(deskId);
        if (scheduleId == null) return Optional.empty();
        return Optional.ofNullable(scheduleMap.get(scheduleId));
    }

    public boolean hasDeskSchedule(UUID deskId) {
        return deskToScheduleIndex.containsKey(deskId);
    }

    public void remove(UUID scheduleId) {
        lock.lock();
        try {
            Schedule removed = scheduleMap.remove(scheduleId);
            if (removed != null) {
                deskToScheduleIndex.remove(removed.getDeskId(), scheduleId);
            }
        } finally {
            lock.unlock();
        }
    }
}
