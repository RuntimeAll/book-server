-- PRD-028 deployment prerequisite; no historical data migration.
-- Back up the target database and inspect information_schema before applying.
CREATE TABLE IF NOT EXISTS biz_question_basket_scope (
    user_id BIGINT NOT NULL,
    namespace VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (user_id, namespace)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS biz_question_basket_entry (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    namespace VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    entry_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    question_id BIGINT NOT NULL,
    source_book_id BIGINT NULL,
    source_item_id BIGINT NULL,
    snapshot_json LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_basket_instance (user_id, namespace, entry_key),
    KEY idx_basket_page (user_id, namespace, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS biz_paper_create_request (
    user_id BIGINT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    paper_id BIGINT NULL,
    question_count INT NULL,
    PRIMARY KEY (user_id, request_id)
) ENGINE=InnoDB;

-- Apply once. If partially applied, only add verified missing columns.
ALTER TABLE biz_paper_question
    ADD COLUMN source_book_id BIGINT NULL,
    ADD COLUMN source_item_id BIGINT NULL,
    ADD COLUMN snapshot_json LONGTEXT NULL;
ALTER TABLE biz_paper MODIFY COLUMN score DECIMAL(8,2) DEFAULT 0;
