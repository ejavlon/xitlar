package uz.xitlar.dto.playlist;

import lombok.*;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistBulkAddResponse {

    Integer playlistId;
    Integer addedCount;
    Integer trackCount;
}
