package com.wfm.controller;

import com.wfm.service.AppConfigurationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/configuration")
public class AppConfigurationController {

    private final AppConfigurationService configurationService;

    public AppConfigurationController(AppConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @GetMapping
    public Map<String, String> getConfiguration() {
        return configurationService.getAllConfig();
    }

    @PutMapping
    public Map<String, String> updateConfiguration(@RequestBody Map<String, String> config) {
        return configurationService.saveConfig(config);
    }
}
