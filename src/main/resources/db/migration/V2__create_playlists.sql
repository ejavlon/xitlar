-- Create playlists table
CREATE TABLE IF NOT EXISTS playlists (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    image_id INTEGER,
    created_by_id INTEGER,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_playlists_image FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL,
    CONSTRAINT fk_playlists_created_by FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Create playlist_musics junction table
CREATE TABLE IF NOT EXISTS playlist_musics (
    id SERIAL PRIMARY KEY,
    playlist_id INTEGER NOT NULL,
    music_id INTEGER NOT NULL,
    position INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_playlist_musics_playlist FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_musics_music FOREIGN KEY (music_id) REFERENCES musics(id) ON DELETE CASCADE,
    CONSTRAINT uk_playlist_musics_playlist_music UNIQUE (playlist_id, music_id)
);

-- Indexes for performance and foreign key lookups
CREATE INDEX IF NOT EXISTS idx_playlists_created_by ON playlists(created_by_id);
CREATE INDEX IF NOT EXISTS idx_playlist_musics_playlist_id ON playlist_musics(playlist_id);
CREATE INDEX IF NOT EXISTS idx_playlist_musics_music_id ON playlist_musics(music_id);
CREATE INDEX IF NOT EXISTS idx_playlist_musics_playlist_position ON playlist_musics(playlist_id, position);
