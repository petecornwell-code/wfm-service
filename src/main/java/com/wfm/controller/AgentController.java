package com.wfm.controller;

import com.wfm.model.Agent;
import com.wfm.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/agents")
    public List<Agent> listAgents(@RequestParam(required = false) String search,
                                  @RequestParam(required = false, defaultValue = "false") boolean unassigned,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(required = false, defaultValue = "50") int limit) {
        return agentService.listAgents(search, unassigned, cursor, limit);
    }

    @GetMapping("/agents/{agentId}")
    public ResponseEntity<Agent> getAgent(@PathVariable UUID agentId) {
        Agent agent = agentService.getAgent(agentId);
        return agent != null ? ResponseEntity.ok(agent) : ResponseEntity.notFound().build();
    }
}
