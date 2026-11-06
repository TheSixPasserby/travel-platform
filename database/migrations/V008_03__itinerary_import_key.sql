ALTER TABLE itineraries ADD COLUMN client_request_id CHAR(36) NULL;
ALTER TABLE itineraries ADD UNIQUE KEY uk_itineraries_client_request (user_id,client_request_id);
