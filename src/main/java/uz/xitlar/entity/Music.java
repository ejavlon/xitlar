package uz.xitlar.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import uz.xitlar.enums.AudioFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "musics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_musics_title_artist", columnNames = {"title", "artist_id"})
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Music extends BaseEntity {

    @Column(nullable = false)
    String title;

    @Column(nullable = false)
    String storedName;

    @Column(nullable = false)
    String originalFileName;

    @Column(nullable = false)
    Long audioSize;

    @Column(nullable = false)
    String audioContentType;

    Integer duration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    Artist artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    Album album;

    @Enumerated(EnumType.STRING)
    uz.xitlar.enums.Genre genre;

    Integer trackNumber;

    @CreatedDate
    @Column(name = "added_date", nullable = false, updatable = false)
    LocalDateTime addedDate;

    Integer bitrate; // kbps

    Integer sampleRate; // Hz

    @Column(nullable = false)
    @Builder.Default
    Integer likeCount = 0;

    @Column(nullable = false)
    @Builder.Default
    Integer dislikeCount = 0;

    @Enumerated(EnumType.STRING)
    AudioFormat audioFormat;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_id")
    User addedBy;

    // Music klassi ichida
    @Builder.Default
    @OneToMany(mappedBy = "music", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Comment> comments = new ArrayList<>();

    @OneToOne(mappedBy = "music", cascade = CascadeType.ALL, orphanRemoval = true)
    Lyrics lyrics;

    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setMusic(this);
    }

    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setMusic(null);
    }

    public void addLyrics(Lyrics lyrics) {
        this.lyrics = lyrics;
        lyrics.setMusic(this);
    }

    public void removeLyrics() {
        if (this.lyrics != null) {
            this.lyrics.setMusic(null);
            this.lyrics = null;
        }
    }
}