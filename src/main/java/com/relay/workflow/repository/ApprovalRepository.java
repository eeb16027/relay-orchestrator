package com.relay.workflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.relay.workflow.model.Approval;
import com.relay.workflow.model.ApprovalStatus;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
	
	Optional<Approval> findByStepId(Long stepId);
	
	Optional<Approval> findByStepIdAndStatus(Long stepId,ApprovalStatus status);

}
