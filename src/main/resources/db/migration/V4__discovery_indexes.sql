-- Discovery reads available copies newest first, filtered by the owner's city.
CREATE INDEX copy_available_created_idx ON copy (created_at DESC) WHERE status = 'AVAILABLE';
CREATE INDEX users_city_lower_idx ON users (lower(city));
