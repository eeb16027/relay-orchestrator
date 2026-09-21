package com.relay.engine;

import com.relay.workflow.model.Step;
import com.relay.workflow.model.StepStatus;
import com.relay.workflow.repository.StepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CrashRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(CrashRecoveryService.class);

    private final StepRepository stepRepository;

    public CrashRecoveryService(StepRepository stepRepository) {
        this.stepRepository = stepRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckSteps() {
        List<Step> stuck = stepRepository.findByStatus(StepStatus.RUNNING);
        for (Step step : stuck) {
            log.warn("Recovering step {} (run {}) stuck in RUNNING - likely from a crashed " +
                    "worker. Resetting to PENDING so it re-executes. Idempotency keys ensure " +
                    "any side effect already performed will not be duplicated.",
                    step.getId(), step.getRunId());
            step.setStatus(StepStatus.PENDING);
            stepRepository.save(step);
        }
        if (!stuck.isEmpty()) {
            log.info("Crash recovery reset {} step(s) back to PENDING.", stuck.size());
        }
    }
}