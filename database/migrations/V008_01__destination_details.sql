-- Additive only. The runner checks metadata before each step; MySQL DDL implicitly commits.
ALTER TABLE destinations ADD COLUMN address VARCHAR(300) NULL;
ALTER TABLE destinations ADD COLUMN longitude DECIMAL(9,6) NULL;
ALTER TABLE destinations ADD COLUMN latitude DECIMAL(8,6) NULL;
ALTER TABLE destinations ADD COLUMN recommended_duration_minutes SMALLINT UNSIGNED NULL;
ALTER TABLE destinations ADD COLUMN recommended_days SMALLINT UNSIGNED NULL;
ALTER TABLE destinations ADD COLUMN opening_hours VARCHAR(1000) NULL;
ALTER TABLE destinations ADD COLUMN ticket_info VARCHAR(1000) NULL;
ALTER TABLE destinations ADD COLUMN official_url VARCHAR(1000) NULL;
ALTER TABLE destinations ADD COLUMN source_url VARCHAR(1000) NULL;
ALTER TABLE destinations ADD COLUMN verified_at DATETIME(3) NULL;
ALTER TABLE destinations ADD CONSTRAINT chk_destinations_coordinates CHECK ((longitude IS NULL AND latitude IS NULL) OR (longitude IS NOT NULL AND latitude IS NOT NULL AND longitude BETWEEN -180 AND 180 AND latitude BETWEEN -90 AND 90));
ALTER TABLE destinations ADD CONSTRAINT chk_destinations_duration CHECK (recommended_duration_minutes IS NULL OR recommended_duration_minutes BETWEEN 1 AND 1440);
ALTER TABLE destinations ADD CONSTRAINT chk_destinations_days CHECK (recommended_days IS NULL OR recommended_days BETWEEN 1 AND 30);
