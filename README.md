# Relay: AI Workflow Orchestrator

## Current state of this package
This is the **foundation layer** only: Maven project skeleton + JPA entity
model (Workflow, WorkflowVersion, Run, Step, Approval + their enums).
Repositories, services, the execution engine, AI node, guardrails, and the
CLI console will be added in subsequent slices.

## Requirements
- Java 17+
- Maven 3.8+
- MySQL 8.0.1+ running locally (needed later for `SELECT ... FOR UPDATE SKIP LOCKED`)

## Setup

1. Create the database (or let Hibernate auto-create it — see
   `createDatabaseIfNotExist=true` in the datasource URL):
   ```sql
   CREATE DATABASE relay_db;
   ```
2. Update `src/main/resources/application.properties` with your MySQL
   username/password if different from the defaults (`root` / `root`).
3. Import into STS / Eclipse:
   - File → Import → Maven → Existing Maven Projects → select this folder.
4. Build:
   ```bash
   mvn clean install
   ```
5. Run:
   ```bash
   mvn spring-boot:run
   ```
   On first run, Hibernate (`ddl-auto=update`) will create the tables:
   `workflows`, `workflow_versions`, `runs`, `steps`, `approvals`.

## Package layout so far
```
com.relay
├── RelayApplication.java
└── workflow.model
    ├── Workflow.java
    ├── WorkflowStatus.java
    ├── WorkflowVersion.java
    ├── Run.java
    ├── RunStatus.java
    ├── Step.java
    ├── StepStatus.java
    ├── Approval.java
    ├── ApprovalStatus.java
    └── NodeType.java
```

## Next slices (not yet included)
- Repositories (Spring Data JPA)
- Node-graph DTOs (Node, Edge, WorkflowGraph) for engine interpretation
- Workflow service (draft/publish lifecycle)
- Trigger controllers (webhook + manual)
- Execution engine + worker polling loop
- AI provider adapter + JSON schema validation
- Approval gate enforcement
- Guardrails (step cap, idempotency, prompt-injection handling)
- CLI console
- Tests
