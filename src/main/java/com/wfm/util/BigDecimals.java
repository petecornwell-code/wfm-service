package com.wfm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BigDecimals {

    private BigDecimals() {}

    /**
     * Normalizes a BigDecimal to scale 2 with HALF_UP rounding.
     * Returns null if the input is null.
     */
    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
