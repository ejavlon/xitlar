package uz.xitlar.dto.playlist;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import uz.xitlar.dto.image.ImageResponse;
import uz.xitlar.dto.user.UserResponse;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlaylistResponse {
    Integer id;
    String title;
    String tagName;
    String description;
    ImageResponse image;
    List<PlaylistMusicResponse> musics;
    Integer trackCount;
    Integer voteCount;
    Double averageRating;
    Integer userRating;
    LocalDateTime createdAt;
    UserResponse createdBy;
}
