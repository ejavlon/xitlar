-- Allow music_id to be nullable in comments
ALTER TABLE comments ALTER COLUMN music_id DROP NOT NULL;

-- Add artist_id to comments pointing to artists table
ALTER TABLE comments ADD COLUMN IF NOT EXISTS artist_id INTEGER;

-- Add foreign key constraint for artist_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_comments_artist'
    ) THEN
        ALTER TABLE comments ADD CONSTRAINT fk_comments_artist FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE;
    END IF;
END $$;

-- Add index on artist_id for fast queries
CREATE INDEX IF NOT EXISTS idx_comments_artist_id ON comments(artist_id);
