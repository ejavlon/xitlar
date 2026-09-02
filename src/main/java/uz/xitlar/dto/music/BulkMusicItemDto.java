package uz.xitlar.dto.music;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import uz.xitlar.dto.lyrics.LyricsCreateNestedDto;
import uz.xitlar.enums.Genre;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BulkMusicItemDto {

    String fileName;

    String title;

    Integer artistId;

    Integer albumId;

    Genre genre;

    Integer trackNumber;

    @Valid
    LyricsCreateNestedDto lyrics;
}
