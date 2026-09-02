package uz.xitlar.dto.playlist;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistUpdateDto {

    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    String title;

    @Size(max = 100, message = "Tag name must not exceed 100 characters")
    String tagName;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description;
}
