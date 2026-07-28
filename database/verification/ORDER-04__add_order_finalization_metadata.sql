-- Change ID: ORDER-04
-- Title: Verify durable order finalization metadata
-- Owner: service-order
-- Target database: service-order
-- Forward migration file: database/migrations/ORDER-04__add_order_finalization_metadata.sql
-- Rollback file: database/rollback/ORDER-04__add_order_finalization_metadata.sql
--
-- RULES
-- This file is read-only. Every query documents its expected result.

-- PRE-FLIGHT ASSERTION
-- Expected result before migration: one row showing the order_info table exists.
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'order_info';

-- PRE-FLIGHT ASSERTION
-- Expected result before migration: zero rows; ORDER-04 columns should not already exist.
SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN (
      'finalization_attempts',
      'finalization_next_retry_at',
      'finalization_last_error',
      'finalization_trace_end_epoch_ms'
  )
ORDER BY column_name;

-- PRE-FLIGHT ASSERTION
-- Expected result before migration: zero rows; ORDER-04 index should not already exist.
SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND index_name = 'idx_order_finalization_due'
ORDER BY seq_in_index;

-- PRE-FLIGHT ASSERTION
-- Expected result: inspect current order_status distribution before introducing 10 and 11.
SELECT order_status, COUNT(*) AS row_count
FROM order_info
GROUP BY order_status
ORDER BY order_status;

-- PRE-FLIGHT ASSERTION
-- Expected result: min and max should be within the currently implemented status range.
SELECT MIN(order_status) AS min_order_status,
       MAX(order_status) AS max_order_status
FROM order_info;

-- PRE-FLIGHT ASSERTION
-- Expected result before migration: zero rows with status 10 or 11.
SELECT order_status, COUNT(*) AS row_count
FROM order_info
WHERE order_status IN (10, 11)
GROUP BY order_status
ORDER BY order_status;

-- PRE-FLIGHT ASSERTION
-- Expected result: total row count that will receive finalization_attempts default value.
SELECT COUNT(*) AS rows_receiving_finalization_attempts_default
FROM order_info;

-- PRE-DEPLOYMENT ASSERTION
-- Expected result before starting Batch A6 service-order code: zero rows.
-- Completed orders are treated as idempotently finalized, so historical completed rows must already have finalization outputs.
SELECT COUNT(*) AS completed_rows_missing_finalization_outputs
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND (
      drive_mile IS NULL
      OR drive_time IS NULL
      OR price IS NULL
  );

-- POST-MIGRATION ASSERTION
-- Expected result: four rows with the documented types, nullability, and default.
SELECT column_name,
       column_type,
       is_nullable,
       column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN (
      'finalization_attempts',
      'finalization_next_retry_at',
      'finalization_last_error',
      'finalization_trace_end_epoch_ms'
  )
ORDER BY FIELD(
    column_name,
    'finalization_attempts',
    'finalization_next_retry_at',
    'finalization_last_error',
    'finalization_trace_end_epoch_ms'
);

-- POST-MIGRATION ASSERTION
-- Expected result: index columns in order_status, finalization_next_retry_at, finalization_attempts order.
SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND index_name = 'idx_order_finalization_due'
ORDER BY seq_in_index;

-- POST-MIGRATION ASSERTION
-- Expected result: zero rows; attempts must be not null after default backfill.
SELECT COUNT(*) AS rows_with_null_finalization_attempts
FROM order_info
WHERE finalization_attempts IS NULL;

-- POST-MIGRATION ASSERTION
-- Expected result for healthy pending rows: rows with status 10 should have retry metadata.
SELECT COUNT(*) AS pending_rows_missing_retry_metadata
FROM order_info
WHERE order_status = 10
  AND (
      finalization_attempts IS NULL
      OR finalization_attempts <= 0
      OR finalization_trace_end_epoch_ms IS NULL
      OR finalization_next_retry_at IS NULL
  );

-- POST-MIGRATION ASSERTION
-- Expected result: zero rows; failed finalization rows should not retain any next retry.
SELECT COUNT(*) AS failed_rows_with_next_retry
FROM order_info
WHERE order_status = 11
  AND finalization_next_retry_at IS NOT NULL;

-- POST-MIGRATION ASSERTION
-- Expected result: zero rows; attempts are bounded by the service-order retry limit.
SELECT COUNT(*) AS rows_exceeding_finalization_attempt_limit
FROM order_info
WHERE finalization_attempts > 3;

-- POST-MIGRATION ASSERTION
-- Expected result after the scheduler runs: zero rows; expired max-attempt leases should become status 11.
SELECT COUNT(*) AS expired_max_attempt_pending_rows
FROM order_info
WHERE order_status = 10
  AND finalization_attempts >= 3
  AND finalization_next_retry_at <= NOW();

-- POST-MIGRATION ASSERTION
-- Expected result: zero rows; terminal completed rows should not retain retry scheduling.
SELECT COUNT(*) AS completed_rows_with_next_retry
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND finalization_next_retry_at IS NOT NULL;

-- PRE-ROLLBACK ASSERTION
-- Expected result before executing schema rollback: zero rows.
-- Non-zero rows mean rollback must stop while A6 code and schema remain available for coordination or verified snapshot recovery.
SELECT COUNT(*) AS unresolved_a6_finalization_rows
FROM order_info
WHERE order_status IN (10, 11);

-- POST-ROLLBACK ASSERTION
-- Expected result after rollback: zero rows; ORDER-04 columns should be gone.
SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN (
      'finalization_attempts',
      'finalization_next_retry_at',
      'finalization_last_error',
      'finalization_trace_end_epoch_ms'
  )
ORDER BY column_name;

-- POST-ROLLBACK ASSERTION
-- Expected result after rollback: zero rows; ORDER-04 index should be gone.
SELECT index_name, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND index_name = 'idx_order_finalization_due'
ORDER BY seq_in_index;
