package uz.xitlar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentCreateDto {

    @NotBlank(message = "Comment text must not be blank")
    String text;

    @NotNull(message = "musicId must not be null")
    @Positive(message = "musicId must be positive")
    Integer musicId;
}
