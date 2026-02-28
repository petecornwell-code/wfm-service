package com.wfm.dto;

import java.util.List;
import java.util.UUID;

public record AssignAgentsRequest(List<UUID> agentIds) {}
