package com.relay.workflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.relay.workflow.model.WorkflowVersion;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion, Long> {
	
	List<WorkflowVersion> findByWorkflowIdOrderByVersionNumberDesc(Long workflowId);
	
	Optional<WorkflowVersion> findByWorkflowIdAndVersionNumber(Long workflowId, int versionNumber);
	
	Optional<WorkflowVersion> findTopByWorkflowIdOrderByVersionNumberDesc(Long workflowId);

}
