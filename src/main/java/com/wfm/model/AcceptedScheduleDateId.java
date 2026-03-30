package com.wfm.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class AcceptedScheduleDateId implements Serializable {

    private UUID scheduleId;
    private LocalDate date;

    public AcceptedScheduleDateId() {}

    public AcceptedScheduleDateId(UUID scheduleId, LocalDate date) {
        this.scheduleId = scheduleId;
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AcceptedScheduleDateId that)) return false;
        return Objects.equals(scheduleId, that.scheduleId) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scheduleId, date);
    }
}
