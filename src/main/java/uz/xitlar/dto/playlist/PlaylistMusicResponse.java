package uz.xitlar.dto.playlist;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import uz.xitlar.dto.album.AlbumResponse;
import uz.xitlar.dto.artist.ArtistResponse;
import uz.xitlar.enums.Genre;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistMusicResponse {
    Integer id;
    String title;
    String audioUrl;
    Integer duration;
    ArtistResponse artist;
    AlbumResponse album;
    Genre genre;
    Integer position;
}
