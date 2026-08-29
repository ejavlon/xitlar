package uz.xitlar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "playlist_musics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_playlist_musics_playlist_music", columnNames = {"playlist_id", "music_id"})
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistMusic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "playlist_id", nullable = false)
    Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "music_id", nullable = false)
    Music music;

    @Column(nullable = false)
    Integer position;
}
