package uz.xitlar.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentCreateDto {

    @NotBlank(message = "Comment text must not be blank")
    @Size(max = 2000, message = "Comment text must not exceed 2000 characters")
    String text;

    @Positive(message = "musicId must be positive")
    Integer musicId;

    @Positive(message = "artistId must be positive")
    Integer artistId;

    public CommentCreateDto(String text, Integer musicId) {
        this.text = text;
        this.musicId = musicId;
    }
}
