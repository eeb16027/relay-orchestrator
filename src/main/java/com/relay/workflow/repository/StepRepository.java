package com.relay.workflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.relay.workflow.model.Step;
import com.relay.workflow.model.StepStatus;

public interface StepRepository extends JpaRepository<Step, Long> {
	
	List<Step> findByRunIdOrderBySequenceIndexAsc(Long runId);
	
	//Optional<Step> findByRunIdOrderBySequenceIndex(Long runId, int sequenceIndex);
	
	Optional<Step> findByRunIdAndSequenceIndex(Long runId, int sequenceIndex);
	
	/*
	 * steps left in RUNNING are leftovers from a crashed worker-used by
	 * CrashRecoverService on startup to reset them to PENDING
	 */
	List<Step> findByStatus(StepStatus status);
	
	/*
	 * pulls the next pending step for a run,skipping rows already locked by another
	 * worker thread/instance & This is what make worker loop safe to run with
	 * multiple threads/instances without duplicate execution.
	 */
	/*
	 * Note:-Pessimistic_write requires query to be native for Mysql's skipLock to
	 * apply because spring's
	 * 
	 * @Lock annotation alone does not emit SKIPLocked.
	 */
	
	@Query(value="""
			select * from steps where run_id=:runId and status= 'PENDING'
			order by sequence_index asc  
			limit 1
			FOR UPDATE SKIP LOCKED
			""", nativeQuery=true)
	Optional<Step> findNextPendingStepForRun(@Param("runId") Long runId);
	
	/*
	 * Any worker instance polls across All runs for the oldest pending step &
	 * claims skipped one for that this is the query the main worker polling loop
	 * will actually use.
	 */
	
	@Query(value="""
			select * from steps where status='PENDING' order by id ASC
			LIMIT 1
			FOR UPDATE SKIP LOCKED
			""",nativeQuery=true)
	Optional<Step> claimNextPendingStep();
	
	//Note:-'FOR UPDATE SKIP LOCKED' only actually holds the lock for the duration of enclosing
	//transaction.so whoever calls this method  must do inside a @Transactional service method that
	//immidiately flips the step to RUNNING and commits,before releasing the row for other workers
	//to see.
	
	
	Optional<Step> findByIdempotencyKey(String idempotencyKey);
	
	long countByRunIdAndStatus(Long runId, StepStatus status);
	
	

}
