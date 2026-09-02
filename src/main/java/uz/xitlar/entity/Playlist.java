package uz.xitlar.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "playlists")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Playlist extends BaseEntity {

    @Column(nullable = false)
    String title;

    @Column(length = 100)
    String tagName;

    @Builder.Default
    Integer voteCount = 0;

    @Builder.Default
    Double averageRating = 0.0;

    @Column(length = 1000)
    String description;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "image_id")
    Image image;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    List<PlaylistMusic> playlistMusics = new ArrayList<>();

    public void addPlaylistMusic(PlaylistMusic playlistMusic) {
        playlistMusics.add(playlistMusic);
        playlistMusic.setPlaylist(this);
    }

    public void removePlaylistMusic(PlaylistMusic playlistMusic) {
        playlistMusics.remove(playlistMusic);
        playlistMusic.setPlaylist(null);
    }
}
