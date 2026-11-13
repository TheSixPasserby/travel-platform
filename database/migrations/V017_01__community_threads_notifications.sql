-- M17 additive migration. Execute only against an explicitly selected backed-up database.
SET @m17_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='guide_comments' AND COLUMN_NAME='root_id')=0, 'ALTER TABLE guide_comments ADD COLUMN root_id BIGINT NULL', 'SELECT 1');
PREPARE m17_stmt FROM @m17_sql;
EXECUTE m17_stmt;
DEALLOCATE PREPARE m17_stmt;
SET @m17_sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='guide_comments' AND COLUMN_NAME='reply_to_id')=0, 'ALTER TABLE guide_comments ADD COLUMN reply_to_id BIGINT NULL', 'SELECT 1');
PREPARE m17_stmt FROM @m17_sql;
EXECUTE m17_stmt;
DEALLOCATE PREPARE m17_stmt;
SET @m17_sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='guide_comments' AND INDEX_NAME='idx_guide_comments_root')=0, 'ALTER TABLE guide_comments ADD KEY idx_guide_comments_root (root_id,status,is_deleted,created_at,id)', 'SELECT 1');
PREPARE m17_stmt FROM @m17_sql;
EXECUTE m17_stmt;
DEALLOCATE PREPARE m17_stmt;
SET @m17_sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='guide_comments' AND CONSTRAINT_NAME='fk_comments_root')=0, 'ALTER TABLE guide_comments ADD CONSTRAINT fk_comments_root FOREIGN KEY (root_id) REFERENCES guide_comments(id) ON DELETE CASCADE', 'SELECT 1');
PREPARE m17_stmt FROM @m17_sql;
EXECUTE m17_stmt;
DEALLOCATE PREPARE m17_stmt;
SET @m17_sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='guide_comments' AND CONSTRAINT_NAME='fk_comments_reply_to')=0, 'ALTER TABLE guide_comments ADD CONSTRAINT fk_comments_reply_to FOREIGN KEY (reply_to_id) REFERENCES guide_comments(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE m17_stmt FROM @m17_sql;
EXECUTE m17_stmt;
DEALLOCATE PREPARE m17_stmt;

CREATE TABLE IF NOT EXISTS comment_mentions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  comment_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_comment_mentions (comment_id, user_id),
  CONSTRAINT fk_mentions_comment FOREIGN KEY (comment_id) REFERENCES guide_comments(id) ON DELETE CASCADE,
  CONSTRAINT fk_mentions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  recipient_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  guide_id BIGINT NOT NULL,
  comment_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  read_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notifications_recipient_comment (recipient_id, comment_id),
  KEY idx_notifications_recipient_read (recipient_id, read_at, created_at, id),
  CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE CASCADE,
  CONSTRAINT fk_notifications_comment FOREIGN KEY (comment_id) REFERENCES guide_comments(id) ON DELETE CASCADE,
  CONSTRAINT chk_notifications_type CHECK (type IN ('COMMENT','REPLY','MENTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comment_write_locks (
  user_id BIGINT NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_comment_write_locks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
