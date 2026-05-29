package com.wfm.controller;

import com.wfm.dto.BambooSyncEventResponse;
import com.wfm.service.AppConfigurationService;
import com.wfm.service.BambooSyncEventService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/configuration")
public class AppConfigurationController {

    private final AppConfigurationService configurationService;
    private final BambooSyncEventService bambooSyncEventService;

    public AppConfigurationController(AppConfigurationService configurationService,
                                      BambooSyncEventService bambooSyncEventService) {
        this.configurationService = configurationService;
        this.bambooSyncEventService = bambooSyncEventService;
    }

    @GetMapping
    public Map<String, String> getConfiguration() {
        return configurationService.getAllConfig();
    }

    @PutMapping
    public Map<String, String> updateConfiguration(@RequestBody Map<String, String> config) {
        return configurationService.saveConfig(config);
    }

    @GetMapping("/bamboohr/sync-status")
    public BambooSyncEventResponse getSyncStatus() {
        return bambooSyncEventService.getLatest();
    }
}
