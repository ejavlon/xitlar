package uz.xitlar.dto.album;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uz.xitlar.dto.image.ImageResponse;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AlbumResponse {
    Integer id;
    String title;
    Integer artistId;
    String artistName;
    ImageResponse image;
}
