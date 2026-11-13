-- 增量迁移。只在显式选择、已备份并完成恢复验证的库上执行。
-- 保留所有旧行程和项目。回退应用版本时保留本表及其幂等凭据。
CREATE TABLE IF NOT EXISTS itinerary_guide_imports (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  client_request_id CHAR(36) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  itinerary_id BIGINT NOT NULL,
  day_id BIGINT NOT NULL,
  response_json TEXT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_imports_user_request (user_id, client_request_id),
  KEY idx_guide_imports_itinerary (itinerary_id),
  CONSTRAINT fk_guide_imports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_guide_imports_itinerary FOREIGN KEY (itinerary_id) REFERENCES itineraries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
