package com.pramaan.backend.scheduler;

/** A schedule already has a PENDING / RUNNING run; mapped to 409 RUN_IN_PROGRESS. */
public class RunInProgressException extends RuntimeException {

    private final String runId;

    public RunInProgressException(String scheduleName, String runId) {
        super("Schedule " + scheduleName + " already has a run in progress");
        this.runId = runId;
    }

    public String runId() {
        return runId;
    }
}
