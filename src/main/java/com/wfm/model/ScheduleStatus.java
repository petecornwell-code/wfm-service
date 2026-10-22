package com.wfm.model;

public enum ScheduleStatus {
    RUNNING,
    /**
     * In memory only: the solver has stopped (finished or was stopped) and the post-solve repairs
     * are still rewriting seats on this very object. Nothing may read, accept, reject or export it
     * until it becomes COMPLETED or STOPPED.
     */
    FINISHING,
    COMPLETED,
    STOPPED,
    FAILED,
    ACCEPTED
}
