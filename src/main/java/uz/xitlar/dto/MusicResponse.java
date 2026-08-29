package uz.xitlar.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import uz.xitlar.enums.AudioFormat;
import uz.xitlar.enums.Genre;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MusicResponse {
    Integer id;
    String title;
    String audioUrl;
    Integer duration;
    Integer bitrate;
    Integer sampleRate;
    String originalFileName;
    Long audioSize;
    String audioContentType;
    ArtistResponse artist;
    AlbumResponse album;
    Genre genre;
    Integer trackNumber;
    Integer likeCount;
    Integer dislikeCount;
    AudioFormat audioFormat;
    LocalDateTime addedDate;
    LyricsResponse lyrics;
}
