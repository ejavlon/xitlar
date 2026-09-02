package uz.xitlar.dto.lyrics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LyricsCreateDto {

    @NotNull(message = "musicId bo'sh bo'lishi mumkin emas")
    @Positive(message = "musicId musbat son bo'lishi kerak")
    Integer musicId;

    @NotBlank(message = "Matn bo'sh bo'lishi mumkin emas")
    String text;

    @NotBlank(message = "Til bo'sh bo'lishi mumkin emas")
    @Pattern(regexp = "^(uz|en|ru)$", message = "Til uz, en yoki ru bo'lishi kerak")
    String language;

    @NotNull(message = "isSynced bo'sh bo'lishi mumkin emas")
    Boolean isSynced;

    String lrcContent;
}
