package com.nicosarr.jazzLibraryAPI.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class JobContext {
    private final String jobId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String status = "RUNNING";

    public JobContext(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() { return jobId; }
    public boolean isCancelled() { return cancelled.get(); }
    public void requestCancel() {
        cancelled.set(true);
        this.status = "CANCELLING";
    }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}