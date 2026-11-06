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
