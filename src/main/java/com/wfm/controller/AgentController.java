package com.wfm.controller;

import com.wfm.dto.AgentResponse;
import com.wfm.dto.PaginatedResponse;
import com.wfm.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/agents")
    public PaginatedResponse<AgentResponse> listAgents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return agentService.listAgents(search, unassigned, cursor, limit);
    }

    @GetMapping("/agents/{agentId}")
    public AgentResponse getAgent(@PathVariable UUID agentId) {
        return agentService.getAgent(agentId);
    }
}
