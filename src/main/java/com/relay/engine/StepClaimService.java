package com.relay.engine;

import com.relay.workflow.model.Step;
import com.relay.workflow.repository.StepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Owns the short, tightly-scoped transaction that claims a step off the
 * queue. FOR UPDATE SKIP LOCKED only holds its lock for the life of this
 * transaction - by flipping status to RUNNING and committing quickly,
 * we release the row immediately so other worker threads can move on to
 * the next pending step without waiting on us.
 */
@Service
public class StepClaimService {

    private final StepRepository stepRepository;

    public StepClaimService(StepRepository stepRepository) {
        this.stepRepository = stepRepository;
    }

    @Transactional
    public Optional<Step> claimNextStep() {
        Optional<Step> claimed = stepRepository.claimNextPendingStep();
        claimed.ifPresent(step -> {
            step.markRunning();
            stepRepository.save(step);
        });
        return claimed;
    }
}
