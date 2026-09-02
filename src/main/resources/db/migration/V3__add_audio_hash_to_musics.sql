-- Flyway migration: V3__add_audio_hash_to_musics.sql
-- Add audio_hash column and unique index to musics table

ALTER TABLE musics ADD COLUMN IF NOT EXISTS audio_hash VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_musics_audio_hash
ON musics (audio_hash)
WHERE audio_hash IS NOT NULL;
