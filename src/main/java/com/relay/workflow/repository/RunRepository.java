package com.relay.workflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.relay.workflow.model.Run;
import com.relay.workflow.model.RunStatus;

public interface RunRepository extends JpaRepository<Run, Long> {
	
	List<Run> findByWorkflowId(Long workflowId);
	
	List<Run> findByStatus(RunStatus status);

}
