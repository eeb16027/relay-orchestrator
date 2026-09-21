package com.relay.engine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The durable queue's worker pool. Runs N independent polling threads
 * (configurable via relay.engine.worker.thread-pool-size), each
 * repeatedly trying to claim and execute the next PENDING step.
 * FOR UPDATE SKIP LOCKED in StepClaimService ensures no two threads -
 * in this JVM or another instance entirely - ever claim the same step.
 */
@Component
public class WorkflowEngineWorker {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineWorker.class);

    private final StepExecutionService stepExecutionService;
    private final int threadPoolSize;
    private final long pollIntervalMs;
    private ScheduledExecutorService executorService;

    public WorkflowEngineWorker(StepExecutionService stepExecutionService,
                                 @Value("${relay.engine.worker.thread-pool-size}") int threadPoolSize,
                                 @Value("${relay.engine.worker.poll-interval-ms}") long pollIntervalMs) {
        this.stepExecutionService = stepExecutionService;
        this.threadPoolSize = threadPoolSize;
        this.pollIntervalMs = pollIntervalMs;
    }

    @PostConstruct
    public void start() {
        executorService = Executors.newScheduledThreadPool(threadPoolSize);
        for (int i = 0; i < threadPoolSize; i++) {
            executorService.scheduleWithFixedDelay(this::pollOnce, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
        }
        log.info("WorkflowEngineWorker started with {} threads, polling every {}ms", threadPoolSize, pollIntervalMs);
    }

    @PreDestroy
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
            log.info("WorkflowEngineWorker stopped");
        }
    }

    private void pollOnce() {
        try {
            stepExecutionService.claimAndExecuteNextStep();
        } catch (Exception e) {
            // A crashed poll iteration must never kill the scheduled
            // thread permanently - log and let the next tick try again.
            log.error("Unhandled error in worker poll loop", e);
        }
    }
}
