package uz.xitlar.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LyricsResponse {
    Integer id;
    String text;
    String language;
    Boolean isSynced;
    String lrcContent;
    Integer musicId;
    String musicTitle;
}
