package com.nicosarr.jazzLibraryAPI.service;

import com.nicosarr.jazzLibraryAPI.util.JobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

@Service
public class JobManager {
    private static final Logger logger = LoggerFactory.getLogger(JobManager.class);
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, JobContext> activeJobs = new ConcurrentHashMap<>();

    /**
     * Start a new job with a given Runnable and return the job ID.
     * The Runnable should accept the JobContext to check cancellation.
     */
    public String startJob(RunnableWithContext job) {
        String jobId = UUID.randomUUID().toString();
        JobContext context = new JobContext(jobId);
        activeJobs.put(jobId, context);
        
        logger.info("Job {} started.", jobId);
        
        executor.submit(() -> {
            try {
                job.run(context); // the job implementation receives the context
                logger.info("Job {} finished successfully.", jobId);
            } catch (Exception e) {
                logger.error("Job {} failed: {}", jobId, e.getMessage(), e);
                context.setStatus("FAILED");
            } finally {
                activeJobs.remove(jobId);
                logger.info("Job {} removed from active jobs.", jobId);
            }
        });
        
        return jobId;
    }

    // Optional: get context for cancellation later
    public JobContext getJobContext(String jobId) {
        return activeJobs.get(jobId);
    }

    // Functional interface for jobs that need the context
    @FunctionalInterface
    public interface RunnableWithContext {
        void run(JobContext context);
    }
    
    /**
     * Request cancellation of a running job.
     * @param jobId the job ID
     * @return true if the job was found and cancellation was requested, false otherwise
     */
    public boolean cancelJob(String jobId) {
        JobContext context = activeJobs.get(jobId);
        if (context == null) {
            logger.warn("Cancel request for unknown job: {}", jobId);
            return false;
        }
        context.requestCancel();
        logger.info("Cancellation requested for job: {}", jobId);
        return true;
    }
    
}