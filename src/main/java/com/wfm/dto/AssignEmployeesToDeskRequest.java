package com.wfm.dto;

import java.util.List;
import java.util.UUID;

public record AssignEmployeesToDeskRequest(UUID deskId, List<String> bambooEmployeeIds) {}
