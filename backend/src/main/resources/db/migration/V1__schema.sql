-- CohortLens schema. Portable SQL: runs on PostgreSQL (production) and H2 in PostgreSQL mode (dev and tests).

CREATE SEQUENCE import_job_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE observation_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE validation_issue_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE audit_log_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE import_jobs (
    id            BIGINT PRIMARY KEY,
    filename      VARCHAR(255) NOT NULL,
    import_mode   VARCHAR(10)  NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    total_rows    INTEGER      NOT NULL,
    accepted_rows INTEGER      NOT NULL,
    rejected_rows INTEGER      NOT NULL,
    warnings      BIGINT       NOT NULL,
    errors        BIGINT       NOT NULL,
    issue_counts  VARCHAR(4000) NOT NULL,
    message       VARCHAR(1000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(100) NOT NULL,
    CONSTRAINT ck_import_mode   CHECK (import_mode IN ('APPEND', 'REPLACE')),
    CONSTRAINT ck_import_status CHECK (status IN ('COMPLETED', 'FAILED'))
);

-- One row per student per term. The student is stored only as a keyed hash, never as the original id.
CREATE TABLE observations (
    id                   BIGINT PRIMARY KEY,
    import_job_id        BIGINT           NOT NULL REFERENCES import_jobs (id),
    student_key          VARCHAR(32)      NOT NULL,
    school               VARCHAR(200)     NOT NULL,
    grade                INTEGER          NOT NULL,
    academic_year        INTEGER          NOT NULL,
    term                 VARCHAR(10)      NOT NULL,
    term_index           INTEGER          NOT NULL,
    economic_status      VARCHAR(20)      NOT NULL,
    english_learner      BOOLEAN          NOT NULL,
    math_score           DOUBLE PRECISION,
    reading_score        DOUBLE PRECISION,
    attendance_rate      DOUBLE PRECISION,
    discipline_incidents INTEGER          NOT NULL,
    CONSTRAINT uq_observation_student_term UNIQUE (student_key, term_index),
    CONSTRAINT ck_obs_grade      CHECK (grade BETWEEN 0 AND 12),
    CONSTRAINT ck_obs_term       CHECK (term IN ('FALL', 'SPRING')),
    CONSTRAINT ck_obs_status     CHECK (economic_status IN ('LOW_INCOME', 'NOT_LOW_INCOME')),
    CONSTRAINT ck_obs_math       CHECK (math_score IS NULL OR (math_score >= 0 AND math_score <= 100)),
    CONSTRAINT ck_obs_reading    CHECK (reading_score IS NULL OR (reading_score >= 0 AND reading_score <= 100)),
    CONSTRAINT ck_obs_attendance CHECK (attendance_rate IS NULL OR (attendance_rate >= 0 AND attendance_rate <= 1)),
    CONSTRAINT ck_obs_discipline CHECK (discipline_incidents >= 0)
);
CREATE INDEX idx_obs_school_term ON observations (school, term_index);
CREATE INDEX idx_obs_import_job  ON observations (import_job_id);

CREATE TABLE validation_issues (
    id            BIGINT PRIMARY KEY,
    import_job_id BIGINT       NOT NULL REFERENCES import_jobs (id),
    source_row    INTEGER      NOT NULL,
    field_name    VARCHAR(50)  NOT NULL,
    severity      VARCHAR(10)  NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    message       VARCHAR(500) NOT NULL,
    raw_value     VARCHAR(100) NOT NULL,
    CONSTRAINT ck_issue_severity CHECK (severity IN ('ERROR', 'WARNING'))
);
CREATE INDEX idx_issue_import_job ON validation_issues (import_job_id, source_row);

-- Who did what and when: imports, replacements, exports.
CREATE TABLE audit_log (
    id          BIGINT PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor       VARCHAR(100)  NOT NULL,
    action      VARCHAR(30)   NOT NULL,
    entity_type VARCHAR(50)   NOT NULL,
    detail      VARCHAR(2000) NOT NULL
);
CREATE INDEX idx_audit_time ON audit_log (occurred_at);
