-- Change ID: READ-02
-- Title: Verify order drive_time seconds normalization
-- Owner: service-order
-- Target database: service-order
-- Forward migration file: database/migrations/READ-02__normalize_order_drive_time_seconds.sql
-- Rollback file: database/rollback/READ-02__normalize_order_drive_time_seconds.sql
--
-- RULES
-- This file is read-only. Every query documents its expected result.
-- Historical rows normalized by READ-02 contain minute-granularity lower-bound
-- seconds. Newer rows already contain seconds. The old formula lost sub-minute
-- precision before storage, so this migration cannot restore true original
-- ride-duration seconds.

-- PRE-FLIGHT ASSERTION
-- Expected result: one row with a non-NULL cutover value.
SELECT @read02_seconds_cutover AS read02_seconds_cutover;

-- PRE-FLIGHT ASSERTION
-- Expected result: cutover_status = 'OK'. The cutover uses the same database
-- wall-clock semantics as passenger_getoff_time and must not be in the future.
SELECT CASE
           WHEN @read02_seconds_cutover IS NULL THEN 'MISSING'
           WHEN @read02_seconds_cutover > NOW() THEN 'FUTURE'
           ELSE 'OK'
       END AS read02_cutover_status;

-- PRE-FLIGHT ASSERTION
-- Expected result: if read02_candidate_rows is zero, this value must be 1 and
-- the operator must record the explicit zero-candidate acknowledgement.
SELECT COALESCE(@read02_allow_zero_candidates, 0) AS read02_allow_zero_candidates;

-- PRE-FLIGHT ASSERTION
-- Expected result: a positive reviewed candidate ceiling whenever candidates exist.
SELECT @read02_max_candidate_rows AS read02_max_candidate_rows;

-- PRE-FLIGHT ASSERTION
-- Expected result: read02_drive_time_column_status = 'OK'. READ-02 supports
-- only signed BIGINT order_info.drive_time and must not guess the column
-- capacity from an incomplete repository schema.
SELECT CASE
           WHEN COUNT(*) <> 1 THEN 'MISSING_OR_DUPLICATE'
           WHEN LOWER(COALESCE(MAX(data_type), '')) <> 'bigint' THEN 'UNSUPPORTED_DATA_TYPE'
           WHEN LOWER(COALESCE(MAX(column_type), '')) NOT LIKE 'bigint%' THEN 'UNSUPPORTED_COLUMN_TYPE'
           WHEN LOWER(COALESCE(MAX(column_type), '')) LIKE '%unsigned%' THEN 'UNSUPPORTED_UNSIGNED_COLUMN'
           ELSE 'OK'
       END AS read02_drive_time_column_status,
       MAX(data_type) AS drive_time_data_type,
       MAX(column_type) AS drive_time_column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name = 'drive_time';

-- PRE-FLIGHT ASSERTION
-- Expected result: both timestamp columns exist and the operator records the
-- actual extra value for each. The audited schema currently has
-- ON UPDATE CURRENT_TIMESTAMP for both columns; READ-02 preserves their values
-- through explicit UPDATE self-assignment and does not modify their DDL.
SELECT column_name,
       data_type,
       column_type,
       extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN ('gmt_create', 'gmt_modified')
ORDER BY column_name;

-- PRE-FLIGHT ASSERTION
-- Expected result: zero rows before migration.
SELECT COUNT(*) AS completed_pre_cutover_rows_with_null_drive_time
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND passenger_getoff_time < @read02_seconds_cutover
  AND drive_time IS NULL;

-- PRE-FLIGHT ASSERTION
-- Expected result: zero rows before migration.
SELECT COUNT(*) AS completed_rows_with_null_getoff_time
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND passenger_getoff_time IS NULL;

-- PRE-FLIGHT ASSERTION
-- Expected result: zero rows before migration.
SELECT COUNT(*) AS completed_pre_cutover_rows_with_negative_drive_time
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND passenger_getoff_time < @read02_seconds_cutover
  AND drive_time IS NOT NULL
  AND drive_time < 0;

-- PRE-FLIGHT ASSERTION
-- Expected result: zero rows before migration.
SELECT COUNT(*) AS read02_overflow_risk_rows
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND passenger_getoff_time < @read02_seconds_cutover
  AND drive_time IS NOT NULL
  AND drive_time > FLOOR(9223372036854775807 / 60);

-- PRE-FLIGHT ASSERTION
-- Expected result: record the rows that will be normalized.
SELECT COUNT(*) AS read02_candidate_rows
FROM order_info
WHERE order_status IN (6, 7, 8)
  AND passenger_getoff_time < @read02_seconds_cutover
  AND drive_time IS NOT NULL;

-- PRE-FLIGHT ASSERTION
-- Expected result: read02_candidate_ceiling_status = 'OK'. If candidates exist,
-- @read02_max_candidate_rows must be positive and candidate rows must not
-- exceed that reviewed maximum.
SELECT candidate_count AS read02_candidate_rows,
       @read02_max_candidate_rows AS read02_max_candidate_rows,
       CASE
           WHEN candidate_count > 0 AND COALESCE(@read02_max_candidate_rows, 0) <= 0 THEN 'MISSING_OR_NON_POSITIVE_MAX'
           WHEN candidate_count > @read02_max_candidate_rows THEN 'EXCEEDS_REVIEWED_MAX'
           ELSE 'OK'
       END AS read02_candidate_ceiling_status
FROM (
    SELECT COUNT(*) AS candidate_count
    FROM order_info
    WHERE order_status IN (6, 7, 8)
      AND passenger_getoff_time < @read02_seconds_cutover
      AND drive_time IS NOT NULL
) candidates;

-- PRE-FLIGHT ASSERTION
-- Expected result: status distribution recorded for the PR or issue.
SELECT order_status, COUNT(*) AS row_count
FROM order_info
GROUP BY order_status
ORDER BY order_status;

-- POST-MIGRATION OR PRE-RERUN ASSERTION
-- Expected result: zero rows. A rerun is allowed only with the same cutover.
SELECT COUNT(*) AS read02_audit_cutover_mismatch_rows
FROM read02_drive_time_seconds_audit
WHERE seconds_cutover <> @read02_seconds_cutover;

-- POST-MIGRATION ASSERTION
-- Expected result: audit row count matches the reviewed affected-row count.
SELECT COUNT(*) AS read02_audit_rows
FROM read02_drive_time_seconds_audit;

-- POST-MIGRATION OR PARTIAL-RUN ASSERTION
-- Expected result: zero rows. Non-zero rows mean target rows were not audited
-- for this cutover and the same-cutover rerun is not safe yet.
SELECT COUNT(*) AS read02_unaudited_target_rows
FROM order_info o
LEFT JOIN read02_drive_time_seconds_audit a
       ON a.order_id = o.id
      AND a.seconds_cutover = @read02_seconds_cutover
WHERE o.order_status IN (6, 7, 8)
  AND o.passenger_getoff_time < @read02_seconds_cutover
  AND o.drive_time IS NOT NULL
  AND a.order_id IS NULL;

-- POST-MIGRATION OR PARTIAL-RUN ASSERTION
-- Expected result: zero rows. Non-zero rows mean the audit does not encode the
-- required minute-granularity lower-bound seconds calculation.
SELECT COUNT(*) AS read02_bad_audit_normalization_rows
FROM read02_drive_time_seconds_audit
WHERE seconds_cutover = @read02_seconds_cutover
  AND NOT (normalized_drive_time <=> original_drive_time * 60);

-- POST-MIGRATION ASSERTION
-- Expected result: zero rows; audited rows should equal normalized lower-bound seconds.
SELECT COUNT(*) AS read02_rows_not_normalized_from_audit
FROM order_info o
JOIN read02_drive_time_seconds_audit a
  ON a.order_id = o.id
WHERE NOT (o.drive_time <=> a.normalized_drive_time);

-- POST-MIGRATION ASSERTION
-- Expected result: informational count only. Historical zero-minute values are
-- legal when both original and normalized audited values are zero; they must not
-- be treated as double-conversion evidence.
SELECT COUNT(*) AS read02_zero_minute_rows
FROM read02_drive_time_seconds_audit
WHERE original_drive_time = 0
  AND normalized_drive_time = 0;

-- POST-MIGRATION OR SAME-CUTOVER RERUN ASSERTION
-- Expected result: zero rows. If this is zero together with the unaudited and
-- bad-normalization checks, a same-cutover rerun that updates zero rows is
-- complete rather than failed.
SELECT COUNT(*) AS read02_current_value_mismatch_rows
FROM order_info o
JOIN read02_drive_time_seconds_audit a
  ON a.order_id = o.id
 AND a.seconds_cutover = @read02_seconds_cutover
WHERE o.order_status IN (6, 7, 8)
  AND o.passenger_getoff_time < @read02_seconds_cutover
  AND NOT (o.drive_time <=> a.normalized_drive_time);

-- POST-MIGRATION ASSERTION
-- Expected result: zero rows. Non-zero rows mean audit references no longer
-- match an order row and restore evidence must be reviewed before rollback.
SELECT COUNT(*) AS read02_audit_rows_missing_order
FROM read02_drive_time_seconds_audit a
LEFT JOIN order_info o
       ON o.id = a.order_id
WHERE o.id IS NULL;

-- POST-MIGRATION ASSERTION
-- Expected result: sample rows for manual review, with normalized seconds exactly original minutes times 60.
SELECT a.order_id,
       a.original_drive_time,
       a.normalized_drive_time,
       o.drive_time AS current_drive_time,
       a.passenger_getoff_time
FROM read02_drive_time_seconds_audit a
JOIN order_info o
  ON o.id = a.order_id
ORDER BY a.order_id
LIMIT 20;

-- PRE-ROLLBACK ASSERTION
-- Expected result before restoring old representation: traffic is stopped and compatible old code has been deployed.
-- This condition is operational and must be recorded outside this SQL file.
SELECT COUNT(*) AS read02_audit_rows_available_for_restore
FROM read02_drive_time_seconds_audit;

-- POST-ROLLBACK ASSERTION
-- Expected result after an audited restore: zero rows; restored values match audited original minute values.
SELECT COUNT(*) AS read02_rows_not_restored_to_original
FROM order_info o
JOIN read02_drive_time_seconds_audit a
  ON a.order_id = o.id
WHERE NOT (o.drive_time <=> a.original_drive_time);
