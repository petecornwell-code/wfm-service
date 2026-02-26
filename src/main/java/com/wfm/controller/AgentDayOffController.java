package com.wfm.controller;

import com.wfm.model.AgentDayOff;
import com.wfm.service.AgentDayOffService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AgentDayOffController {

    private final AgentDayOffService agentDayOffService;

    public AgentDayOffController(AgentDayOffService agentDayOffService) {
        this.agentDayOffService = agentDayOffService;
    }

    @GetMapping("/agents/{agentId}/days-off")
    public List<AgentDayOff> listDaysOffForAgent(@PathVariable UUID agentId,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to) {
        return agentDayOffService.listDaysOffForAgent(agentId, from, to);
    }

    @GetMapping("/days-off")
    public List<AgentDayOff> listAllDaysOff(@RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false, defaultValue = "50") int limit) {
        return agentDayOffService.listAllDaysOff(from, to, cursor, limit);
    }
}
