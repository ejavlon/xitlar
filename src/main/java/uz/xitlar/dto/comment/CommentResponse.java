package uz.xitlar.dto.comment;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommentResponse {
    Integer id;
    String text;
    LocalDateTime createdAt;
    Integer musicId;
    Integer artistId;
    Integer userId;
    String userName;
}
