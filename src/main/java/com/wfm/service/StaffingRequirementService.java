package com.wfm.service;

import com.wfm.model.StaffingRequirement;
import com.wfm.repository.StaffingRequirementRepository;
import com.wfm.repository.TimeslotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StaffingRequirementService {

    private final StaffingRequirementRepository staffingRequirementRepository;
    private final TimeslotRepository timeslotRepository;

    public StaffingRequirementService(StaffingRequirementRepository staffingRequirementRepository,
                                      TimeslotRepository timeslotRepository) {
        this.staffingRequirementRepository = staffingRequirementRepository;
        this.timeslotRepository = timeslotRepository;
    }

    public List<StaffingRequirement> listRequirements(UUID deskId, String from, String to, String cursor, int limit) {
        // TODO: implement with pagination and date range filter
        return List.of();
    }

    @Transactional
    public List<StaffingRequirement> saveRequirements(UUID deskId, List<StaffingRequirement> requirements) {
        // TODO: validate timeslots and specializations exist, delete-and-insert in single tx
        return List.of();
    }

    @Transactional
    public List<StaffingRequirement> calculateErlangX(UUID deskId, Object erlangXRequest) {
        // TODO: calculate per-timeslot requirements from Erlang X inputs, persist results
        return List.of();
    }
}
