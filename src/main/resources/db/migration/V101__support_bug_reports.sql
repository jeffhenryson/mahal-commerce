CREATE TABLE bug_reports (
    id          BIGSERIAL    PRIMARY KEY,
    reported_by VARCHAR(80)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    page_url    VARCHAR(500),
    user_agent  VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_bug_reports_reported_by ON bug_reports (reported_by);
CREATE INDEX idx_bug_reports_created_at  ON bug_reports (created_at);
