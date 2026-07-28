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
--
-- ROLLBACK STEPS
-- Drop the paired due-scan index and all ORDER-04 metadata columns.

ALTER TABLE order_info
    DROP INDEX idx_order_finalization_due,
    DROP COLUMN finalization_trace_end_epoch_ms,
    DROP COLUMN finalization_last_error,
    DROP COLUMN finalization_next_retry_at,
    DROP COLUMN finalization_attempts;
