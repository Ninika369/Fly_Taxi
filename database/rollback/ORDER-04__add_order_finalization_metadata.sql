-- Change ID: ORDER-04
-- Title: Roll back durable order finalization metadata
-- Owner: service-order
-- Target database: service-order
-- Target MySQL version: 8.0.30; reconfirm before execution.
-- Forward migration file: database/migrations/ORDER-04__add_order_finalization_metadata.sql
-- Paired verification file: database/verification/ORDER-04__add_order_finalization_metadata.sql
-- Reversibility: PARTIAL
-- Data-loss risk: finalization retry metadata will be removed.
-- Required backup: verified backup or disposable controlled environment snapshot.
--
-- ROLLBACK PRECONDITIONS
-- Execute only after confirming no required recovery process depends on the metadata.
-- MySQL DDL may implicitly commit; rehearse this rollback in a controlled environment.
-- Stop inbound order-finalization traffic and stop the Batch A6 scheduler before rollback.
-- Run the paired verification file's PRE-ROLLBACK ASSERTION first.
-- The unresolved_a6_finalization_rows result must be zero before executing ALTER TABLE.
-- If status 10 or 11 rows remain, do not execute this rollback and do not automatically map them to status 5, 6, or any other state.
-- Non-zero rows must be coordinated while the A6 code and schema are still available, or recovered from a verified snapshot.
--
-- ROLLBACK STEPS
-- This file is an operator-run rollback artifact; it does not automatically enforce the preconditions above.
-- After the PRE-ROLLBACK ASSERTION is zero, deploy the previous service-order code while temporarily retaining these extra columns.
-- Then drop the paired due-scan index and all ORDER-04 metadata columns.
-- After rollback, run the paired verification file's POST-ROLLBACK ASSERTIONS.

ALTER TABLE order_info
    DROP INDEX idx_order_finalization_due,
    DROP COLUMN finalization_trace_end_epoch_ms,
    DROP COLUMN finalization_last_error,
    DROP COLUMN finalization_next_retry_at,
    DROP COLUMN finalization_attempts;
