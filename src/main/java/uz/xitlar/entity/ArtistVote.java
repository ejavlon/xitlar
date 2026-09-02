package uz.xitlar.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "artist_votes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_artist_votes_user_artist", columnNames = {"user_id", "artist_id"})
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ArtistVote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    Artist artist;

    @Column(nullable = false)
    Integer rating; // 1-5
}
