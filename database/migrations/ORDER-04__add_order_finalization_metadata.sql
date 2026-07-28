-- Change ID: ORDER-04
-- Title: Add durable order finalization metadata
-- Owner: service-order
-- Target database: service-order
-- Target MySQL version: 8.0.30; reconfirm before execution.
-- Related workflow: database/README.md
-- Paired rollback file: database/rollback/ORDER-04__add_order_finalization_metadata.sql
-- Paired verification file: database/verification/ORDER-04__add_order_finalization_metadata.sql
--
-- PURPOSE
-- Add durable retry metadata for passenger get-off order finalization.
-- Java order status 10 means FINALIZATION_PENDING.
-- Java order status 11 means FINALIZATION_FAILED.
--
-- SAFETY
-- Do not execute without first running the paired read-only preflight checks.
-- Do not include credentials, connection strings, production data, or copied dumps.
-- MySQL DDL may implicitly commit; verify backup and rollback readiness first.
-- This migration intentionally changes only order finalization metadata.

ALTER TABLE order_info
    ADD COLUMN finalization_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN finalization_next_retry_at DATETIME NULL,
    ADD COLUMN finalization_last_error VARCHAR(255) NULL,
    ADD COLUMN finalization_trace_end_epoch_ms BIGINT NULL,
    ADD INDEX idx_order_finalization_due (
        order_status,
        finalization_next_retry_at,
        finalization_attempts
    );
