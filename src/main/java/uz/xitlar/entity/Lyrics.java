package uz.xitlar.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "lyrics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_lyrics_music_id", columnNames = "music_id")
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Lyrics extends BaseEntity {

    @Column(nullable = false, columnDefinition = "TEXT")
    String text;

    @Column(length = 10)
    String language; // uz, en, ru

    //Matn musiqa vaqtiga moslashgan (LRC formatida) yoki shunchaki oddiy matn ekanligini bildiruvchi bayroqcha (boolean).
    @Builder.Default
    @Column(name = "is_synced", nullable = false)
    Boolean isSynced = false;

    @Column(name = "lrc_content", columnDefinition = "TEXT")
    String lrcContent; // Synchronized lyrics (LRC format)

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_id", nullable = false, unique = true)
    Music music;
}