CREATE TABLE IF NOT EXISTS artist_votes (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    artist_id INTEGER NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    CONSTRAINT uk_artist_votes_user_artist UNIQUE (user_id, artist_id)
);

CREATE INDEX IF NOT EXISTS idx_artist_votes_artist_id ON artist_votes(artist_id);
CREATE INDEX IF NOT EXISTS idx_artist_votes_user_id ON artist_votes(user_id);
