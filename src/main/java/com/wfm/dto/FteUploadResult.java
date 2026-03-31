package com.wfm.dto;

import java.util.List;

public record FteUploadResult(
        int savedCount,
        int skippedCount,
        List<String> savedDetails,
        List<String> skippedDetails
) {}
