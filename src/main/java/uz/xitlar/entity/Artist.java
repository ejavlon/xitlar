package uz.xitlar.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uz.xitlar.enums.Genre;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "artists")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Artist extends BaseEntity{

    @Column(nullable = false)
    String name;

    @Builder.Default
    @Column(nullable = false)
    Integer countOfTrack = 0;

    @Enumerated(EnumType.STRING)
    Genre genre;

    @Builder.Default
    Integer voteCount = 0;

    // min = 0, max = 5
    @Builder.Default
    Double averageRating = 0.0;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JoinColumn(name = "image_id")
    Image image;

    @Builder.Default
    @OneToMany(mappedBy = "artist", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Music> musics = new ArrayList<>();

    public void addMusic(Music music) {
        musics.add(music);
        music.setArtist(this);
    }

    public void removeMusic(Music music) {
        musics.remove(music);
        music.setArtist(null);
    }
}
