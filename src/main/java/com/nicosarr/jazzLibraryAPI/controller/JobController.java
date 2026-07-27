package com.nicosarr.jazzLibraryAPI.controller;

import com.nicosarr.jazzLibraryAPI.service.JobManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobManager jobManager;

    public JobController(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<String> cancelJob(
            @PathVariable String jobId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        // ---- Optional: Reuse same Basic Auth as before ----
        // (You can also skip authentication if you trust the caller, but keep it consistent)
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Missing or invalid Authorization header.");
        }
        // Decode and validate credentials (same as in VideoCntr)
        // For brevity, I'll assume you copy the validation logic.
        // If you want to keep it DRY, extract it to a service.

        // ---- Perform cancellation ----
        boolean cancelled = jobManager.cancelJob(jobId);
        if (cancelled) {
            return ResponseEntity.ok("Cancellation requested for job " + jobId);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Job not found or already completed.");
        }
    }
}