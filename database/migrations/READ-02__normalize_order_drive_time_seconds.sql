-- Change ID: READ-02
-- Title: Normalize order drive_time values to seconds
-- Owner: service-order
-- Target database: service-order
-- Target MySQL version: 8.0.30; reconfirm before execution.
-- Paired rollback file: database/rollback/READ-02__normalize_order_drive_time_seconds.sql
-- Paired verification file: database/verification/READ-02__normalize_order_drive_time_seconds.sql
--
-- PURPOSE
-- Normalize historical completed order_info.drive_time rows from the pre-A2
-- minute-based storage semantics to seconds while retaining the physical
-- column name for compatibility.
--
-- IMPORTANT PRECISION BOUNDARY
-- Multiplying pre-A2 rows by 60 restores minute-granularity lower-bound seconds
-- only. The old formula already truncated sub-minute precision before storing
-- drive_time, so this migration cannot recover the original ride-duration
-- seconds. This known granularity difference is not introduced by READ-02.
--
-- OPERATOR INPUT
-- Before running this file in a MySQL session, set the real A2 seconds-code
-- deployment cutover time, the maximum reviewed candidate count, and an
-- explicit zero-candidate decision:
--
--   SET @read02_seconds_cutover = TIMESTAMP('YYYY-MM-DD HH:MM:SS');
--   SET @read02_max_candidate_rows = 1000;
--   SET @read02_allow_zero_candidates = 0;
--
-- Set @read02_allow_zero_candidates = 1 only after confirming that the target
-- database is empty, disposable, already reseeded, or otherwise expected to
-- contain no pre-A2 completed rows.
-- Set @read02_max_candidate_rows to the reviewed maximum candidate count
-- approved for this execution. If candidates exist and this value is missing,
-- non-positive, or lower than the actual candidate count, the migration stops.
--
-- The cutover is the actual environment deployment time and uses the same
-- database wall-clock semantics as order_info.passenger_getoff_time. It is not
-- a Git commit or merge time. If old and new service-order instances overlapped
-- so that no single cutover can classify historical rows, stop and use
-- independent evidence or reset/reseed the disposable database.

DROP PROCEDURE IF EXISTS read02_validate_preconditions;

DELIMITER //
CREATE PROCEDURE read02_validate_preconditions()
BEGIN
    DECLARE drive_time_column_count BIGINT DEFAULT 0;
    DECLARE drive_time_data_type VARCHAR(64) DEFAULT NULL;
    DECLARE drive_time_column_type VARCHAR(255) DEFAULT NULL;
    DECLARE timestamp_column_count BIGINT DEFAULT 0;
    DECLARE candidate_count BIGINT DEFAULT 0;
    DECLARE null_drive_time_count BIGINT DEFAULT 0;
    DECLARE null_getoff_count BIGINT DEFAULT 0;
    DECLARE negative_drive_time_count BIGINT DEFAULT 0;
    DECLARE overflow_count BIGINT DEFAULT 0;
    DECLARE audit_table_count BIGINT DEFAULT 0;
    DECLARE audit_cutover_mismatch_count BIGINT DEFAULT 0;

    IF @read02_seconds_cutover IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 requires @read02_seconds_cutover in the current MySQL session';
    END IF;

    IF @read02_seconds_cutover > NOW() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 cutover must not be later than current database time';
    END IF;

    SELECT COUNT(*), MAX(data_type), MAX(column_type)
    INTO drive_time_column_count, drive_time_data_type, drive_time_column_type
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'order_info'
      AND column_name = 'drive_time';

    IF drive_time_column_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 requires exactly one order_info.drive_time column';
    END IF;

    IF LOWER(COALESCE(drive_time_data_type, '')) <> 'bigint'
            OR LOWER(COALESCE(drive_time_column_type, '')) NOT LIKE 'bigint%'
            OR LOWER(COALESCE(drive_time_column_type, '')) LIKE '%unsigned%' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 supports only signed BIGINT order_info.drive_time; create a reviewed DDL/data plan first';
    END IF;

    SELECT COUNT(*)
    INTO timestamp_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'order_info'
      AND column_name IN ('gmt_create', 'gmt_modified');

    IF timestamp_column_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 requires order_info.gmt_create and order_info.gmt_modified columns';
    END IF;

    SELECT COUNT(*)
    INTO candidate_count
    FROM order_info
    WHERE order_status IN (6, 7, 8)
      AND passenger_getoff_time < @read02_seconds_cutover
      AND drive_time IS NOT NULL;

    IF candidate_count > 0 AND COALESCE(@read02_max_candidate_rows, 0) <= 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 requires a positive @read02_max_candidate_rows when candidates exist';
    END IF;

    IF candidate_count > @read02_max_candidate_rows THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 candidate count exceeds the reviewed @read02_max_candidate_rows ceiling';
    END IF;

    IF candidate_count = 0 AND COALESCE(@read02_allow_zero_candidates, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 found zero candidate rows without explicit operator acknowledgement';
    END IF;

    SELECT COUNT(*)
    INTO null_drive_time_count
    FROM order_info
    WHERE order_status IN (6, 7, 8)
      AND passenger_getoff_time < @read02_seconds_cutover
      AND drive_time IS NULL;

    IF null_drive_time_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 found completed pre-cutover rows with NULL drive_time';
    END IF;

    SELECT COUNT(*)
    INTO null_getoff_count
    FROM order_info
    WHERE order_status IN (6, 7, 8)
      AND passenger_getoff_time IS NULL;

    IF null_getoff_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 found completed rows with NULL passenger_getoff_time';
    END IF;

    SELECT COUNT(*)
    INTO negative_drive_time_count
    FROM order_info
    WHERE order_status IN (6, 7, 8)
      AND passenger_getoff_time < @read02_seconds_cutover
      AND drive_time IS NOT NULL
      AND drive_time < 0;

    IF negative_drive_time_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 found completed pre-cutover rows with negative drive_time';
    END IF;

    SELECT COUNT(*)
    INTO overflow_count
    FROM order_info
    WHERE order_status IN (6, 7, 8)
      AND passenger_getoff_time < @read02_seconds_cutover
      AND drive_time IS NOT NULL
      AND drive_time > FLOOR(9223372036854775807 / 60);

    IF overflow_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 drive_time multiplication would overflow BIGINT';
    END IF;

    SELECT COUNT(*)
    INTO audit_table_count
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'read02_drive_time_seconds_audit';

    IF audit_table_count > 0 THEN
        SELECT COUNT(*)
        INTO audit_cutover_mismatch_count
        FROM read02_drive_time_seconds_audit
        WHERE seconds_cutover <> @read02_seconds_cutover;

        IF audit_cutover_mismatch_count > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'READ-02 existing audit cutover does not match current @read02_seconds_cutover';
        END IF;
    END IF;

    SET @read02_candidate_count = candidate_count;

    SELECT candidate_count AS read02_candidate_rows,
           @read02_max_candidate_rows AS read02_max_candidate_rows,
           COALESCE(@read02_allow_zero_candidates, 0) AS read02_allow_zero_candidates;
END//
DELIMITER ;

CALL read02_validate_preconditions();

-- Record audited schema timestamp metadata before creating READ-02 artifacts.
-- The audited order_info.gmt_create and order_info.gmt_modified columns both
-- have ON UPDATE CURRENT_TIMESTAMP. The later UPDATE uses explicit
-- self-assignment to preserve both business timestamps and does not change
-- their DDL.
SELECT column_name,
       data_type,
       column_type,
       extra
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'order_info'
  AND column_name IN ('gmt_create', 'gmt_modified')
ORDER BY column_name;

DROP PROCEDURE read02_validate_preconditions;

CREATE TABLE IF NOT EXISTS read02_drive_time_seconds_audit (
    order_id BIGINT NOT NULL PRIMARY KEY,
    original_drive_time BIGINT NOT NULL,
    normalized_drive_time BIGINT NOT NULL,
    passenger_getoff_time DATETIME NOT NULL,
    seconds_cutover DATETIME NOT NULL,
    migrated_at DATETIME NOT NULL
);

-- Audit candidate rows before updating. Rows already present in the audit table
-- are not inserted again, which prevents a second READ-02 run from multiplying
-- already-normalized values. The migrated_at timestamp records migration time;
-- order_info timestamp columns are preserved by the UPDATE self-assignment.
INSERT INTO read02_drive_time_seconds_audit (
    order_id,
    original_drive_time,
    normalized_drive_time,
    passenger_getoff_time,
    seconds_cutover,
    migrated_at
)
SELECT o.id,
       o.drive_time,
       o.drive_time * 60,
       o.passenger_getoff_time,
       @read02_seconds_cutover,
       NOW()
FROM order_info o
LEFT JOIN read02_drive_time_seconds_audit a
       ON a.order_id = o.id
WHERE o.order_status IN (6, 7, 8)
  AND o.passenger_getoff_time < @read02_seconds_cutover
  AND o.drive_time IS NOT NULL
  AND a.order_id IS NULL;

SET @read02_audit_rows_inserted = ROW_COUNT();

-- The update intentionally multiplies only rows whose current value still
-- equals the audited original minute value. This avoids double conversion.
-- The result is minute-granularity lower-bound seconds, not recovered true
-- ride-duration seconds. The audited schema gives gmt_create and gmt_modified
-- ON UPDATE CURRENT_TIMESTAMP; explicit self-assignment prevents this data
-- normalization from changing those business timestamps.
UPDATE order_info o
JOIN read02_drive_time_seconds_audit a
  ON a.order_id = o.id
 AND a.seconds_cutover = @read02_seconds_cutover
SET o.drive_time = a.normalized_drive_time,
    o.gmt_create = o.gmt_create,
    o.gmt_modified = o.gmt_modified
WHERE o.order_status IN (6, 7, 8)
  AND o.passenger_getoff_time < @read02_seconds_cutover
  AND o.drive_time <=> a.original_drive_time
  AND a.normalized_drive_time <=> a.original_drive_time * 60;

SET @read02_rows_updated = ROW_COUNT();

DROP PROCEDURE IF EXISTS read02_validate_target_rows;

DELIMITER //
CREATE PROCEDURE read02_validate_target_rows()
BEGIN
    DECLARE missing_audit_count BIGINT DEFAULT 0;
    DECLARE bad_normalization_count BIGINT DEFAULT 0;
    DECLARE current_value_mismatch_count BIGINT DEFAULT 0;

    SELECT COUNT(*)
    INTO missing_audit_count
    FROM order_info o
    LEFT JOIN read02_drive_time_seconds_audit a
           ON a.order_id = o.id
          AND a.seconds_cutover = @read02_seconds_cutover
    WHERE o.order_status IN (6, 7, 8)
      AND o.passenger_getoff_time < @read02_seconds_cutover
      AND o.drive_time IS NOT NULL
      AND a.order_id IS NULL;

    IF missing_audit_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 target rows are missing audit entries for the current cutover';
    END IF;

    SELECT COUNT(*)
    INTO bad_normalization_count
    FROM read02_drive_time_seconds_audit
    WHERE seconds_cutover = @read02_seconds_cutover
      AND NOT (normalized_drive_time <=> original_drive_time * 60);

    IF bad_normalization_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 audit normalization does not equal original_drive_time * 60';
    END IF;

    SELECT COUNT(*)
    INTO current_value_mismatch_count
    FROM order_info o
    JOIN read02_drive_time_seconds_audit a
      ON a.order_id = o.id
     AND a.seconds_cutover = @read02_seconds_cutover
    WHERE o.order_status IN (6, 7, 8)
      AND o.passenger_getoff_time < @read02_seconds_cutover
      AND NOT (o.drive_time <=> a.normalized_drive_time);

    IF current_value_mismatch_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'READ-02 target rows do not match audited normalized drive_time values';
    END IF;
END//
DELIMITER ;

CALL read02_validate_target_rows();

DROP PROCEDURE read02_validate_target_rows;

SELECT @read02_audit_rows_inserted AS read02_audit_rows_inserted,
       @read02_rows_updated AS read02_rows_updated;
