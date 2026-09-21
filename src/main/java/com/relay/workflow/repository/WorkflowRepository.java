package com.relay.workflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.relay.workflow.model.Workflow;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
	Optional<Workflow> findByName(String name);

}
