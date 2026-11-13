-- 本脚本只操作调用方显式选择的数据库，不创建、不切换数据库。
-- 使用方式：mysql --database=<已确认的独立测试库> ... < database/schema.sql
-- 操作前请确认目标库为独立空测试库；本脚本不清空或重建已有数据。

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  avatar_url VARCHAR(500) NULL,
  bio VARCHAR(300) NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_status_created (status, created_at),
  CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
  CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS destinations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  destination_type VARCHAR(30) NOT NULL DEFAULT 'CITY',
  parent_id BIGINT NULL,
  country VARCHAR(80) NOT NULL DEFAULT '中国',
  province VARCHAR(80) NULL,
  city VARCHAR(80) NULL,
  description TEXT NULL,
  cover_image_url VARCHAR(500) NULL,
  recommended_seasons VARCHAR(120) NULL,
  address VARCHAR(300) NULL,
  longitude DECIMAL(9,6) NULL,
  latitude DECIMAL(8,6) NULL,
  recommended_duration_minutes SMALLINT UNSIGNED NULL,
  recommended_days SMALLINT UNSIGNED NULL,
  opening_hours VARCHAR(1000) NULL,
  ticket_info VARCHAR(1000) NULL,
  official_url VARCHAR(1000) NULL,
  source_url VARCHAR(1000) NULL,
  verified_at DATETIME(3) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  sort_order INT NOT NULL DEFAULT 0,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_destinations_name_type (name, destination_type),
  KEY idx_destinations_status_type (status, destination_type, parent_id),
  KEY idx_destinations_name (name),
  CONSTRAINT fk_destinations_parent FOREIGN KEY (parent_id) REFERENCES destinations(id) ON DELETE SET NULL,
  CONSTRAINT chk_destinations_type CHECK (destination_type IN ('CITY', 'SCENIC_SPOT', 'OTHER')),
  CONSTRAINT chk_destinations_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT chk_destinations_coordinates CHECK ((longitude IS NULL AND latitude IS NULL) OR (longitude IS NOT NULL AND latitude IS NOT NULL AND longitude BETWEEN -180 AND 180 AND latitude BETWEEN -90 AND 90)),
  CONSTRAINT chk_destinations_duration CHECK (recommended_duration_minutes IS NULL OR recommended_duration_minutes BETWEEN 1 AND 1440),
  CONSTRAINT chk_destinations_days CHECK (recommended_days IS NULL OR recommended_days BETWEEN 1 AND 30)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guides (
  id BIGINT NOT NULL AUTO_INCREMENT,
  author_id BIGINT NOT NULL,
  destination_id BIGINT NULL,
  title VARCHAR(180) NOT NULL,
  summary VARCHAR(500) NULL,
  cover_image_url VARCHAR(500) NULL,
  content LONGTEXT NOT NULL,
  travel_days SMALLINT UNSIGNED NULL,
  budget_per_person DECIMAL(10,2) NULL,
  season VARCHAR(40) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  ip_region VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL,
  published_at DATETIME(3) NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guides_public_time (status, is_deleted, published_at),
  KEY idx_guides_destination (destination_id, status, is_deleted),
  KEY idx_guides_author (author_id, created_at),
  KEY idx_guides_title (title),
  CONSTRAINT fk_guides_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT fk_guides_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE SET NULL,
  CONSTRAINT chk_guides_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
  CONSTRAINT chk_guides_budget CHECK (budget_per_person IS NULL OR budget_per_person >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guide_images (
  id BIGINT NOT NULL AUTO_INCREMENT,
  guide_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  alt_text VARCHAR(200) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guide_images_guide_order (guide_id, sort_order),
  CONSTRAINT fk_guide_images_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tags (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(40) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tags_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guide_tags (
  id BIGINT NOT NULL AUTO_INCREMENT,
  guide_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_tags_pair (guide_id, tag_id),
  KEY idx_guide_tags_tag_guide (tag_id, guide_id),
  CONSTRAINT fk_guide_tags_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE CASCADE,
  CONSTRAINT fk_guide_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guide_likes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  guide_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_likes_user_guide (user_id, guide_id),
  KEY idx_guide_likes_guide (guide_id),
  CONSTRAINT fk_guide_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_guide_likes_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guide_favorites (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  guide_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_favorites_user_guide (user_id, guide_id),
  KEY idx_guide_favorites_user_created (user_id, created_at),
  KEY idx_guide_favorites_guide (guide_id),
  CONSTRAINT fk_guide_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_guide_favorites_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guide_comments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  guide_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(2000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  ip_region VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_guide_comments_guide_status_time (guide_id, status, created_at),
  KEY idx_guide_comments_user (user_id, created_at),
  CONSTRAINT fk_guide_comments_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE CASCADE,
  CONSTRAINT fk_guide_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
  CONSTRAINT chk_guide_comments_status CHECK (status IN ('NORMAL', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS itineraries (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  destination_id BIGINT NULL,
  title VARCHAR(160) NOT NULL,
  client_request_id CHAR(36) NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  notes TEXT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_itineraries_client_request (user_id,client_request_id),
  KEY idx_itineraries_user_status_created (user_id, status, created_at),
  CONSTRAINT fk_itineraries_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_itineraries_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE SET NULL,
  CONSTRAINT chk_itineraries_dates CHECK (end_date >= start_date),
  CONSTRAINT chk_itineraries_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS itinerary_days (
  id BIGINT NOT NULL AUTO_INCREMENT,
  itinerary_id BIGINT NOT NULL,
  day_no SMALLINT UNSIGNED NOT NULL,
  travel_date DATE NOT NULL,
  title VARCHAR(160) NULL,
  notes TEXT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_itinerary_days_no (itinerary_id, day_no),
  UNIQUE KEY uk_itinerary_days_date (itinerary_id, travel_date),
  KEY idx_itinerary_days_order (itinerary_id, sort_order),
  CONSTRAINT fk_itinerary_days_itinerary FOREIGN KEY (itinerary_id) REFERENCES itineraries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS itinerary_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  itinerary_day_id BIGINT NOT NULL,
  destination_id BIGINT NULL,
  place_name VARCHAR(160) NOT NULL,
  address_text VARCHAR(300) NULL,
  start_time TIME NULL,
  end_time TIME NULL,
  notes TEXT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_itinerary_items_day_order (itinerary_day_id, sort_order),
  CONSTRAINT fk_itinerary_items_day FOREIGN KEY (itinerary_day_id) REFERENCES itinerary_days(id) ON DELETE CASCADE,
  CONSTRAINT fk_itinerary_items_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE SET NULL,
  CONSTRAINT chk_itinerary_items_time CHECK (start_time IS NULL OR end_time IS NULL OR end_time >= start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS destination_images (
  id BIGINT NOT NULL AUTO_INCREMENT,
  destination_id BIGINT NOT NULL,
  image_url VARCHAR(500) NOT NULL,
  alt_text VARCHAR(200) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  source_url VARCHAR(1000) NOT NULL,
  author VARCHAR(300) NOT NULL,
  license VARCHAR(100) NOT NULL,
  license_url VARCHAR(1000) NOT NULL,
  retrieved_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_destination_images_path (destination_id,image_url),
  KEY idx_destination_images_order (destination_id,sort_order,id),
  CONSTRAINT fk_destination_images_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS guide_destinations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  guide_id BIGINT NOT NULL,
  destination_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_guide_destinations_pair (guide_id,destination_id),
  KEY idx_guide_destinations_reverse (destination_id,guide_id),
  CONSTRAINT fk_guide_destinations_guide FOREIGN KEY (guide_id) REFERENCES guides(id) ON DELETE RESTRICT,
  CONSTRAINT fk_guide_destinations_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
