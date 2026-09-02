-- Create user_liked_musics table
CREATE TABLE IF NOT EXISTS user_liked_musics (
    user_id INTEGER NOT NULL,
    music_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, music_id),
    CONSTRAINT fk_liked_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_liked_music FOREIGN KEY (music_id) REFERENCES musics(id) ON DELETE CASCADE
);

-- Create user_disliked_musics table
CREATE TABLE IF NOT EXISTS user_disliked_musics (
    user_id INTEGER NOT NULL,
    music_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, music_id),
    CONSTRAINT fk_disliked_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_disliked_music FOREIGN KEY (music_id) REFERENCES musics(id) ON DELETE CASCADE
);
