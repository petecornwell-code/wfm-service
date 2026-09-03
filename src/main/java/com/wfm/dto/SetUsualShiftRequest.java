package com.wfm.dto;

import java.util.UUID;

public record SetUsualShiftRequest(UUID shiftTemplateId, Boolean clearRow) {}
