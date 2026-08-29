package uz.xitlar.dto.playlist;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistBulkMusicAddDto {

    @NotNull(message = "Music IDs list must not be null")
    @NotEmpty(message = "Music IDs list must not be empty")
    @Size(min = 1, max = 50, message = "Music IDs list size must be between 1 and 50")
    List<@NotNull(message = "Music ID must not be null") Integer> musicIds;
}
