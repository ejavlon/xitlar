package uz.xitlar.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "playlist_votes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_playlist_votes_user_playlist", columnNames = {"user_id", "playlist_id"})
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistVote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    Playlist playlist;

    @Column(nullable = false)
    Integer rating; // 1-5
}
