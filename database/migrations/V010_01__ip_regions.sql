-- Additive M10 upgrade only. Historical values remain NULL; never backfill from current IPs.
ALTER TABLE guides ADD COLUMN ip_region VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL;
ALTER TABLE guide_comments ADD COLUMN ip_region VARCHAR(64) COLLATE utf8mb4_0900_ai_ci NULL;
