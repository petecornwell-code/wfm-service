package com.wfm.service;

import com.wfm.model.Schedule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Computes output views from raw AgentAssignment data.
 * Views are derived on-the-fly, not pre-computed.
 */
@Service
public class ScheduleOutputService {

    public List<Map<String, Object>> buildStaffingSummary(Schedule schedule) {
        // TODO: compute predicted vs actual hours per day per specialization
        return List.of();
    }

    public List<Map<String, Object>> buildAgentSchedule(Schedule schedule) {
        // TODO: compute per-agent per-day assignment details
        return List.of();
    }

    public Map<String, Object> buildPreferenceReport(Schedule schedule) {
        // TODO: compute preference honoured/overridden report
        return Map.of();
    }

    public List<Map<String, Object>> buildConstraintViolations(Schedule schedule) {
        // TODO: extract constraint violations from solver score explanation
        return List.of();
    }
}
